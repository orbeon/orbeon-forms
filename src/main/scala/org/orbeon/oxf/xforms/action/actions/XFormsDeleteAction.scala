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

import org.dom4j.{Attribute, Document}
import org.orbeon.oxf.util.{IndentedLogger, Logging}
import org.orbeon.oxf.xforms._
import org.orbeon.oxf.xforms.action.{DynamicActionContext, XFormsAction}
import org.orbeon.oxf.xforms.event.Dispatch
import org.orbeon.oxf.xforms.event.events.XFormsDeleteEvent
import org.orbeon.oxf.xforms.model.NoDefaultsStrategy
import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import org.orbeon.saxon.om.{Item, NodeInfo}

import scala.collection.JavaConverters._
import scala.language.postfixOps

/**
 * 9.3.6 The delete Element
 */
class XFormsDeleteAction extends XFormsAction {

  import XFormsDeleteAction._

  override def execute(actionContext: DynamicActionContext): Unit = {

    implicit val indentedLogger = actionContext.logger

    val atAttributeOpt        = Option(actionContext.element.attributeValue("at"))
    val contextAttributeOpt   = Option(actionContext.element.attributeValue(XFormsConstants.CONTEXT_QNAME))

    val collectionToUpdate    = actionContext.interpreter.actionXPathContext.getCurrentBindingContext.nodeset.asScala
    val isEmptyNodesetBinding = collectionToUpdate.isEmpty

    // "The delete action is terminated with no effect if [...] the context attribute is not given and the Node
    // Set Binding node-set is empty."
    if (contextAttributeOpt.isEmpty && isEmptyNodesetBinding) {
      debug("context is empty, terminating")
      return
    }

    // Handle insert context (with @context attribute)
    // "The delete action is terminated with no effect if the insert context is the empty node-set [...]."
    actionContext.overriddenContext match {
      case Some(node: NodeInfo) ⇒
        debug("overridden context is an empty nodeset or not a nodeset, terminating")
        return
      case _ ⇒
    }

    val deleteIndexOpt =
      if (isEmptyNodesetBinding) {
        // "If the Node Set Binding node-set empty, then this attribute is ignored"
        None
      } else {
        atAttributeOpt match {
          case Some(atAttribute) ⇒
            // "1. The evaluation context node is the first node in document order from the Node Set Binding
            // node-set, the context size is the size of the Node Set Binding node-set, and the context
            // position is 1."

            // "2. The return value is processed according to the rules of the XPath function round()"
            val insertionIndexString =
              actionContext.interpreter.evaluateAsString(
                actionContext.element,
                collectionToUpdate.asJava,
                1,
                "round(" + atAttribute + ")"
              )

            // "3. If the result is in the range 1 to the Node Set Binding node-set size, then the insert
            // location is equal to the result. If the result is non-positive, then the insert location is
            // 1. Otherwise, the result is NaN or exceeds the Node Set Binding node-set size, so the insert
            // location is the Node Set Binding node-set size."

            // Don't think we will get NaN with XPath 2.0...
            val tentativeDeleteIndex =
              if (insertionIndexString == "NaN") collectionToUpdate.size else insertionIndexString.toInt

            Some(tentativeDeleteIndex min collectionToUpdate.size max 1)
          case None ⇒
            // "If there is no delete location, each node in the Node Set Binding node-set is deleted, except
            // if the node is a readonly node or the root document element of an instance then that particular
            // node is not deleted."
            None
        }
      }

    doDelete(
      containingDocument = actionContext.containingDocument,
      collectionToUpdate = collectionToUpdate,
      deleteIndexOpt     = deleteIndexOpt,
      doDispatch         = true
    )
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

    require(nodeInfo ne null)

    doDelete(
      containingDocument = containingDocument,
      collectionToUpdate = List(nodeInfo),
      deleteIndexOpt     = Some(1),
      doDispatch         = doDispatch
    ).headOption
  }

  def doDelete(
    containingDocument : XFormsContainingDocument,
    collectionToUpdate : Seq[Item],
    deleteIndexOpt     : Option[Int],
    doDispatch         : Boolean)(implicit
    indentedLogger     : IndentedLogger
  ): List[DeletionDescriptor] = {

    require(collectionToUpdate ne null)

    if (collectionToUpdate.isEmpty) {
      debugAllowNull("empty collection, terminating")
      Nil
    } else {

      val collectionToUpdateWithDefiniteSize =
        if (collectionToUpdate.hasDefiniteSize) collectionToUpdate else collectionToUpdate.to[List]

      val deletionDescriptors =
        deleteIndexOpt match {
          case Some(index) ⇒ doDeleteOneImpl(collectionToUpdateWithDefiniteSize(index - 1)).to[List]
          case None        ⇒ collectionToUpdateWithDefiniteSize.flatMap(doDeleteOneImpl).to[List]
        }

      if (deletionDescriptors.nonEmpty && (containingDocument ne null)) {
        // Identify the instance that actually changes
        // NOTE: More than one instance may be modified! For now we look at the first one only.
        val modifiedInstanceOpt = Option(containingDocument.getInstanceForNode(deletionDescriptors.head.nodeInfo))

        debugAllowNull(
          "deleted nodes",
          List("count" → deletionDescriptors.size.toString) ++ (modifiedInstanceOpt map ("instance" → _.getEffectiveId))
        )

        // Instance can be missing if document into which delete is performed is not in an instance!
        modifiedInstanceOpt foreach { modifiedInstance ⇒

          // "XForms Actions that change the tree structure of instance data result in setting all four flags to true"
          modifiedInstance.markModified()
          modifiedInstance.model.markStructuralChange(modifiedInstanceOpt, NoDefaultsStrategy)

          // "4. If the delete is successful, the event xforms-delete is dispatched."
          if (doDispatch)
            Dispatch.dispatchEvent(new XFormsDeleteEvent(modifiedInstance, deletionDescriptors, deleteIndexOpt))
        }
      }

      deletionDescriptors
    }
  }

  private def doDeleteOneImpl(
    itemToRemove   : Item)(implicit
    indentedLogger : IndentedLogger
  ): Option[DeletionDescriptor] =
    itemToRemove match {
      case nodeInfoToRemove: NodeInfo ⇒

        val nodeToRemove   = XFormsUtils.getNodeFromNodeInfo(nodeInfoToRemove, CannotDeleteReadonlyMessage)
        val parentNodeInfo = nodeInfoToRemove.getParent // obtain *before* deletion
        val parentElement  = nodeToRemove.getParent

        val (contentToUpdate, mustNormalize) =
          if (nodeToRemove.isInstanceOf[Attribute]) {
            (parentElement.attributes, false)
          } else if (parentElement ne null) {
            (parentElement.content, true)
          } else if ((nodeToRemove.getDocument ne null) && (nodeToRemove eq nodeToRemove.getDocument.getRootElement)) {
            (nodeToRemove.getDocument.content, false)
          } else if (nodeToRemove.isInstanceOf[Document]) {
            debugAllowNull("ignoring attempt to delete document node")
            return None
          } else {
            debugAllowNull("ignoring attempt to delete parentless node")
            return None
          }

        val indexInContentToUpdate = contentToUpdate.indexOf(nodeToRemove)

        // Actual remove operation
        contentToUpdate.remove(indexInContentToUpdate)

        if (mustNormalize)
          Dom4jUtils.normalizeTextNodes(parentElement)

        Some(DeletionDescriptor(parentNodeInfo, nodeInfoToRemove, indexInContentToUpdate))
      case _ ⇒
        debugAllowNull("ignoring attempt to delete atomic value")
        None
    }

  private def debugAllowNull(
    message        : ⇒ String,
    parameters     : ⇒ Seq[(String, String)] = Nil)(implicit
    indentedLogger : IndentedLogger
  ) =
    if (indentedLogger ne null)
      debug(message, parameters)
}
