/**
 * Copyright (C) 2015 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.action.actions

import java.{util => ju}

import cats.syntax.option._
import org.orbeon.dom.{Attribute, Document}
import org.orbeon.oxf.util.CollectionUtils._
import org.orbeon.oxf.util.{IndentedLogger, Logging}
import org.orbeon.oxf.xforms._
import org.orbeon.oxf.xforms.action.{DynamicActionContext, XFormsAction}
import org.orbeon.oxf.xforms.event.Dispatch
import org.orbeon.oxf.xforms.event.events.XFormsDeleteEvent
import org.orbeon.oxf.xforms.model.NoDefaultsStrategy
import org.orbeon.saxon.om.{Item, NodeInfo}
import org.orbeon.scaxon.NodeInfoConversions

import scala.jdk.CollectionConverters._
import scala.language.postfixOps

/**
 * 9.3.6 The delete Element
 */
class XFormsDeleteAction extends XFormsAction {

  import XFormsDeleteAction._

  override def execute(actionContext: DynamicActionContext)(implicit logger: IndentedLogger): Unit = {

    val atAttributeOpt     = actionContext.element.attributeValueOpt("at")
    val collectionToUpdate = actionContext.interpreter.actionXPathContext.getCurrentBindingContext.nodeset.asScala

    // NOTE: XForms says "Otherwise, the Sequence Binding is not expressed, so the Sequence Binding node-sequence
    // is set equal to the delete context node with a position and size of 1." We currently don't support this and
    // require a `ref` or `bind` returning a non-empty sequence.

    if (collectionToUpdate.nonEmpty) {

      val deleteIndexOpt =
        atAttributeOpt match {
          case Some(atAttribute) =>

            // "The return value is processed according to the rules of the XPath function round()"
            val insertionIndexString =
              actionContext.interpreter.evaluateAsString(
                actionContext.element,
                collectionToUpdate.asJava,
                1,
                "round(" + atAttribute + ")"
              )

            // "If the result is in the range 1 to the Sequence Binding node-sequence size, then the delete location is
            // equal to the result. If the result is non-positive, then the delete location is 1. Otherwise, if the
            // result is NaN or exceeds the Sequence Binding node-sequence size, the delete location is the Sequence
            // Binding node-sequence size."

            val tentativeDeleteIndex =
              if (insertionIndexString == "NaN") collectionToUpdate.size else insertionIndexString.toInt

            Some(tentativeDeleteIndex min collectionToUpdate.size max 1)
          case None =>
            // "If there is no delete location, each node in the Sequence Binding"
            None
        }

      doDelete(
        containingDocumentOpt = actionContext.containingDocument.some,
        collectionToUpdate    = collectionToUpdate,
        deleteIndexOpt        = deleteIndexOpt,
        doDispatch            = true
      )
    } else {
      // "The delete action is terminated with no effect if the Sequence Binding is expressed and the Sequence Binding
      // node-sequence is the empty sequence". Here our execution model always require

      // XForms also says "The delete action is terminated with no effect if the delete context is the empty sequence."
      // Currently, if the context is empty, we return an empty `nodeset`, so we satisfy that. But in the future we
      // would like to change this and allow the context to be empty yet support `ref` or `bind` returning a non-empty
      // sequence.

      debug("nothing to delete, ignoring")
    }
  }
}

object XFormsDeleteAction extends Logging {

  private val CannotDeleteReadonlyMessage = "Cannot perform deletion in read-only instance."

  case class DeletionDescriptor(parent: NodeInfo, nodeInfo: NodeInfo, index: Int)

  def doDeleteOne(
    containingDocument : XFormsContainingDocument,
    nodeInfo           : NodeInfo,
    doDispatch         : Boolean)(implicit
    indentedLogger     : IndentedLogger
  ): Option[DeletionDescriptor] = {

    require(containingDocument ne null)
    require(nodeInfo ne null)

    doDeleteItems(
      containingDocumentOpt  = containingDocument.some,
      itemsToDelete          = List(nodeInfo),
      deleteIndexForEventOpt = None,
      doDispatch             = doDispatch
    ).headOption
  }

  def doDelete(
    containingDocumentOpt : Option[XFormsContainingDocument],
    collectionToUpdate    : Seq[Item],
    deleteIndexOpt        : Option[Int],
    doDispatch            : Boolean)(implicit
    indentedLogger        : IndentedLogger
  ): List[DeletionDescriptor] = {

    require(collectionToUpdate ne null)

    if (collectionToUpdate.isEmpty) {
      debugAllowNull("empty collection, nothing to delete")
      Nil
    } else {
      deleteIndexOpt match {
        case Some(index) =>
          doDeleteItems(
            containingDocumentOpt,
            collectionToUpdate.lift(index - 1).toList,
            deleteIndexOpt,
            doDispatch
          )
        case None =>
          doDeleteItems(
            containingDocumentOpt,
            collectionToUpdate,
            deleteIndexOpt,
            doDispatch
          )
      }
    }
  }

  def doDeleteItems(
    containingDocumentOpt  : Option[XFormsContainingDocument],
    itemsToDelete          : Seq[Item],
    deleteIndexForEventOpt : Option[Int],
    doDispatch             : Boolean)(implicit
    indentedLogger         : IndentedLogger
): List[DeletionDescriptor] = {

    // Delete back to front and per DOM document
    // https://github.com/orbeon/orbeon-forms/issues/4492

    val nodesToDelete = (itemsToDelete.iterator collect { case n: NodeInfo => n }).toList

    // `groupByKeepOrder` uses universal equality but it works because `==` is `equals()` is `isSameNodeInfo()`.
    // NOTE: `getDocumentRoot` can be `null`.
    val instancesWithDescriptors =
      nodesToDelete.groupByKeepOrder(n => n.getDocumentRoot) map { case (docNodeOrNull, nodesToDelete) =>

        val nodeArray = nodesToDelete.toArray
        ju.Arrays.sort(nodeArray, ReverseLocalOrderComparator)
        val descriptors = nodeArray.iterator.flatMap(n => doDeleteOneImpl(n).iterator).toList

        val modifiedInstanceOpt =
          for {
            docNode  <- Option(docNodeOrNull)
            doc      <- containingDocumentOpt
            instance <- doc.instanceForNodeOpt(docNode)
          } yield
            instance

        modifiedInstanceOpt -> descriptors
      }

    // Side-effects: notify instances if needed
    instancesWithDescriptors foreach {
      case (Some(modifiedInstance), descriptors) =>

        // "XForms Actions that change the tree structure of instance data result in setting all four flags to true"
        modifiedInstance.markModified()
        modifiedInstance.model.markStructuralChange(modifiedInstance.some, NoDefaultsStrategy)

        debugAllowNull(
          "deleted nodes for instance",
          List(
            "instance" -> modifiedInstance.getEffectiveId,
            "count"    -> descriptors.size.toString)
        )

        if (doDispatch)
          Dispatch.dispatchEvent(new XFormsDeleteEvent(modifiedInstance, descriptors, deleteIndexForEventOpt))

      case (None, descriptors) =>
        debugAllowNull(
          "deleted nodes outside instance",
          List("count" -> descriptors.size.toString)
        )
    }

    instancesWithDescriptors flatMap (_._2)
  }

  private object ReverseLocalOrderComparator extends ju.Comparator[NodeInfo] {
    def compare(o1: NodeInfo, o2: NodeInfo): Int =
      -o1.compareOrder(o2)
  }

  private def doDeleteOneImpl(
    nodeInfoToRemove : NodeInfo)(implicit
    indentedLogger   : IndentedLogger
): Option[DeletionDescriptor] = {

    val nodeToRemove   = NodeInfoConversions.unwrapNode(nodeInfoToRemove) getOrElse (throw new IllegalArgumentException(CannotDeleteReadonlyMessage))
    val parentNodeInfo = nodeInfoToRemove.getParent // obtain *before* deletion
    val parentElement  = nodeToRemove.getParent

    val (contentToUpdate, mustNormalize) =
      if (nodeToRemove.isInstanceOf[Attribute]) {
        (parentElement.jAttributes, false)
      } else if (parentElement ne null) {
        (parentElement.jContent, true)
      } else if ((nodeToRemove.getDocument ne null) && (nodeToRemove eq nodeToRemove.getDocument.getRootElement)) {
        (nodeToRemove.getDocument.jContent, false)
      } else if (nodeToRemove.isInstanceOf[Document]) {
        debugAllowNull("ignoring attempt to delete document node")
        return None
      } else {
        debugAllowNull("ignoring attempt to delete parentless node")
        return None
      }

    val indexInContentToUpdate = contentToUpdate.indexOf(nodeToRemove)

    contentToUpdate.remove(indexInContentToUpdate)

    if (mustNormalize)
      parentElement.normalizeTextNodes

    Some(DeletionDescriptor(parentNodeInfo, nodeInfoToRemove, indexInContentToUpdate))
  }

  private def debugAllowNull(
    message        : => String,
    parameters     : => Seq[(String, String)] = Nil)(implicit
    indentedLogger : IndentedLogger
  ): Unit =
    if (indentedLogger ne null)
      debug(message, parameters)
}
