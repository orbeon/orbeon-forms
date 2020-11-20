/**
 * Copyright (C) 2010 Orbeon, Inc.
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

import java.util
import java.util.Collections

import cats.data.NonEmptyList
import cats.syntax.option._
import org.orbeon.dom._
import org.orbeon.dom.saxon.DocumentWrapper
import org.orbeon.oxf.util.CollectionUtils.InsertPosition
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.{IndentedLogger, StaticXPath}
import org.orbeon.oxf.xforms.action.{DynamicActionContext, XFormsAction}
import org.orbeon.oxf.xforms.event.Dispatch
import org.orbeon.oxf.xforms.event.events.{XFormsInsertEvent, XXFormsReplaceEvent}
import org.orbeon.oxf.xforms.model.{DataModel, FlaggedDefaultsStrategy, InstanceDataOps, XFormsInstance}
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.saxon.om
import org.orbeon.saxon.value.AtomicValue
import org.orbeon.scaxon.NodeInfoConversions
import org.orbeon.xforms.XFormsNames
import shapeless.syntax.typeable._

import scala.jdk.CollectionConverters._
import scala.collection.mutable

/**
 * 9.3.5 The insert Element
 */
object XFormsInsertAction {

  private val CannotInsertReadonlyMessage = "Cannot perform insertion into read-only instance."

  def doInsert(
    containingDocumentOpt             : Option[XFormsContainingDocument],
    insertPosition                    : InsertPosition,
    insertLocation                    : (NonEmptyList[om.NodeInfo], Int) Either om.NodeInfo,
    originItemsOpt                    : Option[Seq[om.Item]],
    doClone                           : Boolean,
    doDispatch                        : Boolean,
    requireDefaultValues              : Boolean,
    searchForInstance                 : Boolean,
    removeInstanceDataFromClonedNodes : Boolean)(implicit
    indentedLogger                    : IndentedLogger
  ): util.List[om.NodeInfo] = {

    val insertLocationNodeInfo =
      insertLocation match {
        case Left((collectionToUpdate, insertionIndex)) => collectionToUpdate.toList(insertionIndex - 1)
        case Right(insertContextNodeInfo)               => insertContextNodeInfo
      }

    // Identify the instance that actually changes
    val modifiedInstanceOpt =
      searchForInstance flatOption {
        for {
          containingDocument <- containingDocumentOpt
          modifiedInstance   <- containingDocument.instanceForNodeOpt(insertLocationNodeInfo)
        } yield
          modifiedInstance
      }

    // NOTE: The check on `hasAnyCalculationBind` is not optimal: we should check whether specifically there are
    // any `xxf:default` which can touch this instance, ideally.
    // NOTE: We do this test here so that we don't unnecessarily annotate nodes.
    val applyDefaults =
      requireDefaultValues &&
      modifiedInstanceOpt.exists(modifiedInstance =>
        modifiedInstance.model.staticModel.hasDefaultValueBind &&
          modifiedInstance.containingDocument.xpathDependencies.hasAnyCalculationBind(
            modifiedInstance.model.staticModel,
            modifiedInstance.getPrefixedId
          )
      )

    // "3. The origin node-set is determined."
    // "5. Each node in the origin node-set is cloned in the order it appears in the origin node-set."
    val clonedNodesTmp =
      originItemsOpt match {
        case None =>
          // There are no explicitly specified origin objects, use node from Node Set Binding node-set
          // "If the origin attribute is not given and the Node Set Binding node-set is empty, then the origin
          // node-set is the empty node-set. [...] The insert action is terminated with no effect if the
          // origin node-set is the empty node-set."

          insertLocation match {
            case Left((collectionToUpdate, _)) =>
              // "Otherwise, if the origin attribute is not given, then the origin node-set consists of the last
              // node of the Node Set Binding node-set."
              val singleSourceNode =
                NodeInfoConversions.getNodeFromNodeInfoConvert(collectionToUpdate.last)

              // TODO: check namespace handling might be incorrect. Should use copyElementCopyParentNamespaces() instead?

              Iterator(singleSourceNode.createCopy)
            case Right(_) =>
              if (indentedLogger != null && indentedLogger.debugEnabled)
                indentedLogger.logDebug("xf:insert", "origin node-set from node-set binding is empty, terminating")
              return Collections.emptyList[om.NodeInfo]
          }

        case Some(originItems) =>
          // There are explicitly specified origin objects
          // "The insert action is terminated with no effect if the origin node-set is the empty node-set."
          if (originItems.isEmpty) {
            if (indentedLogger != null && indentedLogger.debugEnabled)
              indentedLogger.logDebug("xf:insert", "origin node-set is empty, terminating")
            return Collections.emptyList[om.NodeInfo]
          }
          // "Each node in the origin node-set is cloned in the order it appears in the origin node-set."
          for (currentItem <- originItems.iterator)
            yield currentItem match {
              case nodeInfo: om.NodeInfo =>
                // This is the regular case covered by XForms 1.1 / XPath 1.0
                val sourceNode = NodeInfoConversions.getNodeFromNodeInfoConvert(nodeInfo)
                if (doClone)
                  sourceNode match {
                    case elem: Element => elem.createCopy
                    case _             => sourceNode.deepCopy
                  }
                else
                  sourceNode

              case atomicValue: AtomicValue =>
                // This is an extension: support sequences containing atomic values
                // Convert the result to a text node
                Text(atomicValue.getStringValue)
              case _ =>
                throw new IllegalStateException
            }
        }

    val clonedNodes =
      if (removeInstanceDataFromClonedNodes) {

        def processDefaultsAsNeeded(node: Node): Node =
          if (applyDefaults)
            InstanceDataOps.setRequireDefaultValueRecursively(node)
          else
            InstanceDataOps.removeRecursively(node)

        // Remove instance data from cloned nodes and perform Document node adjustment
        val it =
          for (clonedNode <- clonedNodesTmp)
            yield clonedNode match {
              case _: Element | _: Attribute =>
                processDefaultsAsNeeded(clonedNode).detach().some
              case _: Document =>
                // Case of documents without root element
                clonedNode.getDocument.rootElementOpt map { clonedNodeRootElem =>
                  // We can never really insert a document into anything at this point, but we assume that this means the root element
                  processDefaultsAsNeeded(clonedNodeRootElem).detach()
                }
              case _ =>
                clonedNode.detach().some
            }

        it.flatten.toList
      } else {
        // Just make sure the cloned nodes are detached
        val it =
          for (clonedNode <- clonedNodesTmp)
            yield clonedNode.detach()

        it.toList
      }

    // "6. The target location of each cloned node or nodes is determined"
    // "7. The cloned node or nodes are inserted in the order they were cloned at their target location
    // depending on their node type."
    // Find actual insertion point and insert

    val (insertLocationIndexWithinParentBeforeUpdate, insertedNodes, beforeAfterInto) = {
      val insertLocationIndexWithinParentBeforeUpdate = findNodeIndex(insertLocationNodeInfo)

      insertLocation match {
        case Left(_) =>
          val insertLocationNode = NodeInfoConversions.unwrapNode(insertLocationNodeInfo) getOrElse (throw new IllegalArgumentException(CannotInsertReadonlyMessage))
          val insertLocationNodeDocumentOpt = insertLocationNode.documentOpt

          val (insertedNodes, beforeAfterInto) =
            if (insertLocationNodeDocumentOpt.exists(_.getRootElement eq insertLocationNode)) {
              // "c. if insert location node is the root element of an instance, then that instance root element
              // location is the target location. If there is more than one cloned node to insert, only the
              // first node that does not cause a conflict is considered."

              // NOTE: Don't need to normalize text nodes in this case, as no new text node is inserted

              (
                doInsert(insertLocationNode.getDocument, clonedNodes, modifiedInstanceOpt, doDispatch),
                insertPosition.entryName // TODO: ideally normalize to "into document node"?
              )

            } else {
              // "d. Otherwise, the target location is immediately before or after the insert location
              // node, based on the position attribute setting or its default."
              val insertedNodes =
                if (insertLocationNode.isInstanceOf[Attribute]) {
                  // Special case for "next to an attribute"
                  // NOTE: In XML, attributes are unordered. Our DOM handles them as a list so has order, but
                  // the XForms spec shouldn't rely on attribute order. We could try to keep the order, but it
                  // is harder as we have to deal with removing duplicate attributes and find a reasonable
                  // insertion strategy.
                  // TODO: Don't think we should even do this now in XForms 1.1
                  doInsert(insertLocationNode.getParent, clonedNodes, modifiedInstanceOpt, doDispatch)
                } else {
                  // Other node types
                  val parentNode      = insertLocationNode.getParent
                  val siblingElements = parentNode.jContent
                  val actualIndex     = siblingElements.indexOf(insertLocationNode)

                  // Prepare insertion of new element
                  val actualInsertionIndex =
                    if (insertPosition == InsertPosition.Before)
                      actualIndex
                    else
                      actualIndex + 1

                  // "7. The cloned node or nodes are inserted in the order they were cloned at their target
                  // location depending on their node type."

                  val (nodesToInsert, otherNodes) = clonedNodes partition {
                    case _: Attribute | _: Namespace => false
                    case _                           => true
                  }

                  otherNodes foreach { node =>
                    // We never insert attributes or namespace nodes as siblings
                    if (indentedLogger != null && indentedLogger.debugEnabled)
                      indentedLogger.logDebug(
                        "xf:insert",
                        "skipping insertion of node as sibling in element content",
                        "type", Node.nodeTypeName(node),
                        "node", node match {
                          case att: Attribute => att.toDebugString
                          case _              => node.toString
                        }
                      )
                  }

                  val insertedNodes =
                    nodesToInsert.zipWithIndex map { case (nodeToInsert, index) =>
                      siblingElements.add(actualInsertionIndex + index, nodeToInsert)
                      nodeToInsert
                    }

                  if (nodesToInsert exists(_.isInstanceOf[Text]))
                    parentNode.normalizeTextNodes

                  insertedNodes
                }

              (
                insertedNodes,
                insertPosition.entryName
              )
            }

          (insertLocationIndexWithinParentBeforeUpdate, insertedNodes, beforeAfterInto)
        case Right(insertContextNodeInfo) =>

          val insertLocationNode = NodeInfoConversions.unwrapNode(insertContextNodeInfo) getOrElse (throw new IllegalArgumentException(CannotInsertReadonlyMessage))
          val insertedNodes = doInsert(insertLocationNode, clonedNodes, modifiedInstanceOpt, doDispatch)

          // Normalize text nodes if needed to respect XPath 1.0 constraint
          if (clonedNodes exists(_.isInstanceOf[Text]))
            insertLocationNode.normalizeTextNodes

          (insertLocationIndexWithinParentBeforeUpdate, insertedNodes, "into")
      }
    }

    // Whether some nodes were inserted
    val didInsertNodes = insertedNodes.nonEmpty

    // Log stuff
    if (indentedLogger != null && indentedLogger.debugEnabled)
      if (didInsertNodes)
        indentedLogger.logDebug(
          "xf:insert",
          "inserted nodes",
          "count", insertedNodes.size.toString,
          "instance", modifiedInstanceOpt map (_.getEffectiveId) orNull
        )
      else
        indentedLogger.logDebug("xf:insert", "no node inserted")

    // "XForms Actions that change the tree structure of instance data result in setting all four flags to true"
    val insertedNodeInfos =
      if (didInsertNodes) {
        modifiedInstanceOpt match {
          case Some(modifiedInstance) =>

            modifiedInstance.markModified()
            modifiedInstance.model.markStructuralChange(Option(modifiedInstance), FlaggedDefaultsStrategy)

            val documentWrapper = modifiedInstance.documentInfo.asInstanceOf[DocumentWrapper]
            insertedNodes map documentWrapper.wrap
          case None =>
            Nil
        }
      } else
        Nil

    // "4. If the insert is successful, the event xforms-insert is dispatched."
    // XFormsInstance handles index and repeat items updates
    if (doDispatch && didInsertNodes)
      modifiedInstanceOpt foreach { modifiedInstance =>
        // Adjust insert location node and before/after/into in case the root element was replaced

        val (adjustedInsertLocationNodeInfo, adjustedBeforeAfterInto) = {

          val parentOpt =
            insertedNodeInfos.head.getNodeKind == org.w3c.dom.Node.ELEMENT_NODE flatOption
              Option(insertedNodeInfos.head.getParent)

          parentOpt match {
            case Some(parent) if parent == parent.getRoot =>
              // Node was inserted under document node
              (parent.getRoot, "into")
            case _ =>
              (insertLocationNodeInfo, beforeAfterInto)
          }
        }

        Dispatch.dispatchEvent(
          new XFormsInsertEvent(
            modifiedInstance,
            insertedNodeInfos.asJava,
            (originItemsOpt map (_.asJava)).orNull, // FIXME `null`
            adjustedInsertLocationNodeInfo,
            adjustedBeforeAfterInto,
            insertLocationIndexWithinParentBeforeUpdate
          )
        )
      }

    insertedNodeInfos.asJava
  }

  private def findNodeIndex(node: om.NodeInfo): Int = {

    if (node.getParent == null)
      return 0

    val it = node.iterateAxis(StaticXPath.PrecedingSiblingAxisType)
    var result = 0
    var i = it.next()
    while (i ne null) {
      result += 1
      i = it.next()
    }

    result
  }

  private def doInsert(
    insertionNode    : Node,
    clonedNodes      : List[Node],
    modifiedInstance : Option[XFormsInstance],
    doDispatch       : Boolean
  ): List[Node] = {

    def dispatchReplaceEventIfNeeded(formerNode: Node, currentNode: Node): Unit =
      if (doDispatch)
        modifiedInstance foreach { modifiedInstance =>
          val documentWrapper = modifiedInstance.documentInfo.asInstanceOf[DocumentWrapper]
          Dispatch.dispatchEvent(
            new XXFormsReplaceEvent(
              modifiedInstance,
              documentWrapper.wrap(formerNode),
              documentWrapper.wrap(currentNode)
            )
          )
        }

    insertionNode match {
      case insertContextElement: Element =>
        // Insert inside an element

        val insertedNodes =  mutable.ListBuffer[Node]()
        var otherNodeIndex = 0

        // NOTE: It would be good to do this loop in a more functional way, but we need to handle
        // `otherNodeIndex` and also, maybe, keep the relative order of nodes passed in `insertedNodes`?
        for (clonedNode <- clonedNodes) {
          clonedNode match {
            case clonedAtt: Attribute =>

              val existingAttributeOpt = insertContextElement.attributeOpt(clonedAtt.getQName)

              existingAttributeOpt foreach insertContextElement.remove

              insertContextElement.add(clonedAtt)

              // Dispatch `xxforms-replace` event if required and possible
              // NOTE: For now, still dispatch `xforms-insert` for backward compatibility.
              existingAttributeOpt foreach { existingAttribute =>
                dispatchReplaceEventIfNeeded(existingAttribute, clonedAtt)
              }

              insertedNodes += clonedAtt
            case _: Document =>
              // "If a cloned node cannot be placed at the target location due to a node type conflict, then the
              // insertion for that particular clone node is ignored."
              None
            case _ =>
              // Add other node to element
              insertContextElement.jContent.add(
                otherNodeIndex,
                clonedNode
              )
              otherNodeIndex += 1
              insertedNodes += clonedNode
          }
        }
        insertedNodes.result()
      case insertContextDocument: Document =>
        // "If there is more than one cloned node to insert, only the first node that does not cause a conflict is
        // considered."

        clonedNodes collectFirst { // only one element can be inserted at the root of an instance
          case e: Element => e
        } match {
          case Some(elem) =>
            val formerRootElement = insertContextDocument.getRootElement
            insertContextDocument.setRootElement(elem)
            dispatchReplaceEventIfNeeded(formerRootElement, insertContextDocument.getRootElement)
            List(elem)
          case None =>
            // We don't yet support inserting comments and PIs at the root of an instance document.
            Nil
        }
      case _ =>
        throw new IllegalArgumentException(s"Unsupported insertion node type: `${insertionNode.getClass.getName}`")
    }
  }
}

class XFormsInsertAction extends XFormsAction {

  override def execute(actionContext: DynamicActionContext)(implicit logger: IndentedLogger): Unit = {

    val containingDocument  = actionContext.containingDocument
    val contextStack        = actionContext.interpreter.actionXPathContext

    val atAttributeOpt      = actionContext.element.attributeValueOpt("at")
    val originAttributeOpt  = actionContext.element.attributeValueOpt("origin")
    val contextAttributeOpt = actionContext.element.attributeValueOpt(XFormsNames.CONTEXT_QNAME)

    // Extension: allow position to be an AVT
    val resolvedPositionAttributeOpt = Option(actionContext.interpreter.resolveAVT(actionContext.element, "position"))

    // Extension: xxf:default="true" AVT requires that recalculate apply default values on the inserted nodes.
    val setRequireDefaultValues = actionContext.interpreter.resolveAVT(actionContext.element, XFormsNames.XXFORMS_DEFAULTS_QNAME) == "true"

    // "2. The Node Set Binding node-set is determined."
    val currentBindingContext = contextStack.getCurrentBindingContext

    val collectionToUpdate =
      if (currentBindingContext.newBind)
        currentBindingContext.nodeset.asScala.toList.narrowTo[List[om.NodeInfo]] getOrElse
          (throw new IllegalArgumentException("xf:insert: collection to update must only contain nodes"))
      else
        Nil

    val isEmptyBinding = collectionToUpdate.isEmpty

    // "1. The insert context is determined."
    // "The insert action is terminated with no effect if [...] a. The context attribute is not given and the Node
    // Set Binding node-set is the empty node-set."
    if (contextAttributeOpt.isEmpty && isEmptyBinding) {
      debug("xf:insert: context is empty, terminating")
      return
    }

    // Handle insert context (with @context attribute)
    val insertContextItem =
      actionContext.overriddenContext match {
        case Some(nodeInfo: om.NodeInfo) =>
          nodeInfo
        case Some(_) =>
          debug("xf:insert: overridden context is an empty nodeset or not a nodeset, terminating")
          return
        case None =>
          contextStack.getCurrentBindingContext.getSingleItemOrNull
      }


    // "The insert action is terminated with no effect if [...] b. The context attribute is given, the insert
    // context does not evaluate to an element node and the Node Set Binding node-set is the empty node-set."
    // NOTE: In addition we support inserting into a context which is a document node
    if (
      contextAttributeOpt.nonEmpty             &&
      isEmptyBinding                           &&
      ! DataModel.isElement(insertContextItem) &&
      ! DataModel.isDocument(insertContextItem)
    ) {
      debug("xf:insert: insert context is not an element node and binding node-set is empty, terminating")
      return
    }

    val originItemsOpt =
      originAttributeOpt map { originAttribute =>
        // There is an @origin attribute
        // "If the origin attribute is given, the origin node-set is the result of the evaluation of the
        // origin attribute in the insert context."
        val originObjects =
          actionContext.interpreter.evaluateKeepItems(
            actionContext.element,
            Collections.singletonList(insertContextItem),
            1,
            originAttribute
          )

        if (originObjects.isEmpty) {
          debug("xf:insert: origin node-set is empty, terminating")
          return
        }

        originObjects
      }

    // "4. The insert location node is determined."
    val insertionIndex =
      if (isEmptyBinding)
        0 // "If the Node Set Binding node-set empty, then this attribute is ignored"
      else
        atAttributeOpt match {
          case None =>
            // "If the attribute is not given, then the default is the size of the Node Set Binding node-set"
            collectionToUpdate.size
          case Some(atAttribute) =>
            // "a. The evaluation context node is the first node in document order from the Node Set Binding
            // node-set, the context size is the size of the Node Set Binding node-set, and the context
            // position is 1."
            // "b. The return value is processed according to the rules of the XPath function round()"
            val insertionIndexString =
              actionContext.interpreter.evaluateAsString(
                actionContext.element,
                currentBindingContext.nodeset,
                1,
                "round(" + atAttribute + ")"
              )

            // "c. If the result is in the range 1 to the Node Set Binding node-set size, then the insert
            // location is equal to the result. If the result is non-positive, then the insert location is
            // 1. Otherwise, the result is NaN or exceeds the Node Set Binding node-set size, so the insert
            // location is the Node Set Binding node-set size."
            // Don't think we will get NaN with XPath 2.0...
            val insertionIndex =
              if ("NaN" == insertionIndexString)
                collectionToUpdate.size
              else
                insertionIndexString.toInt

            // Adjust index to be in range
            insertionIndex min collectionToUpdate.size max 1
        }

    val normalizedPosition =
      resolvedPositionAttributeOpt match {
        case Some("before")        => InsertPosition.Before
        case Some("after") | None  => InsertPosition.After
        case Some(unsupported)     =>
          warn(
            s"xf:insert: invalid position attribute `$unsupported`, defaulting to `after`",
            resolvedPositionAttributeOpt.toList map ("value" ->)
          )
          InsertPosition.After
      }

    val insertLocation =
      NonEmptyList.fromList(collectionToUpdate).map(_ -> insertionIndex).toLeft(insertContextItem.asInstanceOf[om.NodeInfo])

    XFormsInsertAction.doInsert(
      containingDocumentOpt             = containingDocument.some,
      insertPosition                    = normalizedPosition,
      insertLocation                    = insertLocation,
      originItemsOpt                    = originItemsOpt,
      doClone                           = true,
      doDispatch                        = true,
      requireDefaultValues              = setRequireDefaultValues,
      searchForInstance                 = true,
      removeInstanceDataFromClonedNodes = true
    )
  }
}