/**
 * Copyright (C) 2011 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.control.controls

import org.w3c.dom.Node.{ELEMENT_NODE, ATTRIBUTE_NODE, TEXT_NODE}
import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.oxf.xforms._
import org.orbeon.oxf.xforms.event.events.{XXFormsReplaceEvent, XFormsDeleteEvent, XFormsInsertEvent, XXFormsValueChangedEvent}
import event.XFormsEvents._
import model.DataModel
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.saxon.dom4j.DocumentWrapper
import org.orbeon.saxon.value.StringValue
import java.util.{List ⇒ JList}
import org.orbeon.oxf.util.IndentedLogger
import org.dom4j._
import org.orbeon.scaxon.XML._
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.xforms.event.{Dispatch, XFormsEvent, ListenersTrait}
import Dispatch.EventListener
import org.orbeon.saxon.om._
import org.orbeon.oxf.xml.NamespaceMapping
import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import org.orbeon.oxf.xforms.action.XFormsAPI

// Logic to mirror mutations between an outer and an inner instance
object InstanceMirror {

    // LATER: Handle xxforms-replace for instance root replacement. Right now, we rely on xforms-insert only and this
    // specially takes care of instance root replacement. However in order to ensure that indexes are properly
    // maintained in the case of two linked instances, xxforms-replace will be needed. For now this doesn't happen
    // with Form Builder.
    private val MutationEvents = Seq(XXFORMS_VALUE_CHANGED, XFORMS_INSERT, XFORMS_DELETE, XXFORMS_REPLACE)

    // (sourceInstance, sourceNode, siblingIndexOpt) ⇒ Option[(destinationInstance, destinationNode)]
    type NodeMatcher = (XFormsInstance, NodeInfo, Option[Int]) ⇒ Option[(XFormsInstance, NodeInfo)]

    def addListener(observer: ListenersTrait, listener: EventListener): Unit =
        for (eventName ← MutationEvents)
            observer.addListener(eventName, listener)

    def removeListener(observer: ListenersTrait, listener: EventListener): Unit =
        for (eventName ← MutationEvents)
            observer.removeListener(eventName, listener)

    // Factory function to create listeners which check whether they called as the result of an action caused by another
    // listener. If a cycle is detected, we interrupt it and just say that we have processed the event.
    class ListenerCycleDetector(implicit logger: IndentedLogger)
        extends ((XFormsContainingDocument, NodeMatcher) ⇒ MirrorEventListener) {

        private var inListener = false

        def apply(containingDocument: XFormsContainingDocument, findMatchingNode: NodeMatcher) =
            wrap(mirrorListener(containingDocument, findMatchingNode))

        private def wrap(listener: MirrorEventListener): MirrorEventListener = {
            event ⇒
                if (! inListener) {
                    inListener = true
                    try
                        listener(event)
                    finally
                        inListener = false
                } else
                    true
        }
    }

    // Type of an event listener
    type MirrorEventListener = XFormsEvent ⇒ Boolean

    // Implicitly convert a MirrorEventListener to a plain EventListener
    def toEventListener(f: MirrorEventListener) = new EventListener {
        def apply(event: XFormsEvent) = f(event)
    }

    case class InstanceDetails(id: String, root: VirtualNode, namespaces: NamespaceMapping)

    // Find outer instance details when the change occurs within an instance containing an XHTML+XForms document. This
    // is used by xxf:dynamic.
    def findOuterInstanceDetailsDynamic(
        container : XBLContainer,
        outerNode : NodeInfo,
        into      : Boolean
    ): Option[InstanceDetails] = {
        // In "into" mode, use ancestor-or-self because outerNode passed is the containing node (node into which other
        // nodes are inserted, node from which other nodes are removed, or node which text value changes), which in the
        // case of a root element is the xf:instance element. The exception is when you insert a node before or
        // after an xf:instance element, in which case the change is not in the instance.
        val axis =
            if (! into)
                "ancestor"
            else if (outerNode.getNodeKind == ATTRIBUTE_NODE)
                "../ancestor"
            else
                "ancestor-or-self"

        val findInstanceExpr = "(" + axis + "::xf:instance)[1]"

        Option(evalOne(outerNode, findInstanceExpr)) collect {
            case instanceWrapper: VirtualNode if instanceWrapper.getUnderlyingNode.isInstanceOf[Element] ⇒

                val element = instanceWrapper.getUnderlyingNode.asInstanceOf[Element]
                val instanceId = XFormsUtils.getElementId(element) ensuring (_ ne null)

                def namespaces = {
                    val partAnalysis = container.partAnalysis
                    partAnalysis.getNamespaceMapping(partAnalysis.startScope.fullPrefix, unwrapElement(instanceWrapper))
                }

                InstanceDetails(instanceId, instanceWrapper, namespaces)
        }
    }

    // Find outer instance details when the change occurs in a single instance. This is used by XBL.
    def findOuterInstanceDetailsXBL(
        innerInstance : XFormsInstance,
        referenceNode : VirtualNode)(
        container     : XBLContainer,
        outerNode     : NodeInfo,
        into          : Boolean
    ): Option[InstanceDetails] = {

        // Only changes to nodes "under" the binding node correspond to changes to the inner instance
        val ancestors =
            if (! into)
                outerNode ancestor *
            else if (outerNode.getNodeKind == ATTRIBUTE_NODE) // should be only for attribute setvalue
                outerNode ancestor *
            else
                outerNode ancestorOrSelf *

        val inScope = ancestors intersect Seq(referenceNode) nonEmpty

        def namespaces = new NamespaceMapping(Dom4jUtils.getNamespaceContextNoDefault(unwrapElement(referenceNode)))

        inScope option InstanceDetails(innerInstance.getId, referenceNode.getParent.asInstanceOf[VirtualNode], namespaces)
    }

    // Find the inner instance node from a node in an outer instance
    def toInnerInstanceNode(
        outerDoc                 : DocumentInfo,
        partAnalysis             : PartAnalysis,
        container                : XBLContainer,
        findOuterInstanceDetails : (XBLContainer, NodeInfo, Boolean) ⇒ Option[InstanceDetails]
    ): NodeMatcher = {

        (_, outerNode, siblingIndexOpt) ⇒

            findOuterInstanceDetails(container, outerNode, siblingIndexOpt.isEmpty) flatMap {
                case InstanceDetails(instanceId, referenceNode, namespaces) ⇒
                    // This is a change to an instance

                    // Find path rooted at wrapper
                    val innerPath = {
                        val pathToWrapper   = Navigator.getPath(referenceNode)
                        val pathToOuterNode = getNodePath(outerNode, siblingIndexOpt)

                        assert(pathToOuterNode.startsWith(pathToWrapper))

                        if (pathToWrapper == "/")
                            Seq("", "*") ++ (pathToOuterNode split '/' drop 2) mkString "/"
                        else  if (pathToOuterNode.size > pathToWrapper.size)
                            Seq("", "*") ++ (pathToOuterNode.substring(pathToWrapper.size) split '/' drop 2) mkString "/"
                        else
                            "/"
                    }

                    // Find inner instance
                    container.findInstance(instanceId) match {
                        case Some(innerInstance) ⇒
                            // Find destination path in instance
                            evalOne(innerInstance.documentInfo, innerPath, namespaces) match {
                                case newNode: VirtualNode ⇒ Some(innerInstance, newNode)
                                case _                    ⇒ throw new IllegalStateException
                            }
                        case None ⇒
                            // May not be found if instance was just created
                            None
                    }
            }
    }

    // Get the path of the node given an optional node position within its parent
    private def getNodePath(node: NodeInfo, siblingIndexOpt: Option[Int]) = {
        siblingIndexOpt match {
            case Some(siblingIndex) if node.getNodeKind != ATTRIBUTE_NODE ⇒
                Navigator.getPath(node.getParent) + s"/node()[${siblingIndex + 1}]"
            case _ ⇒
                Navigator.getPath(node)
        }
    }

    // Find the outer node in an inline instance from a node in an inner instance
    def toOuterInstanceNodeDynamic(
            outerInstance: XFormsInstance,
            outerDoc: DocumentInfo,
            partAnalysis: PartAnalysis): NodeMatcher = {

        (innerInstance, innerNode, siblingIndexOpt) ⇒

            // Find instance in original doc
            // could write:(id($sourceId)[self::xf:instance], //xf:instance[@id = $sourceId])[1]
            evalOne(outerDoc, "//xf:instance[@id = $sourceId]",
                    variables = Map("sourceId" → StringValue.makeStringValue(innerInstance.getId))) match {
                case instanceWrapper: VirtualNode if instanceWrapper.getUnderlyingNode.isInstanceOf[Element] ⇒
                    // Outer xf:instance found
                    innerNode match {
                        case document: DocumentInfo ⇒
                            // Root element replaced
                            Some(outerInstance, instanceWrapper) ensuring siblingIndexOpt.isEmpty
                        case _ ⇒
                            // All other cases

                            val path = dropStartingSlash(getNodePath(innerNode, siblingIndexOpt))

                            // NOTE: Namespace handling makes assumption that all namespaces are visible at the level of
                            // xf:instance. This is not general enough. It stems from the use of getPath, which loses
                            // namespace mappings.
                            val namespaces =
                                partAnalysis.getNamespaceMapping(
                                    partAnalysis.startScope.fullPrefix,
                                    instanceWrapper.getUnderlyingNode.asInstanceOf[Element]
                                )

                            // Find destination node in inline instance in original doc
                            evalOne(instanceWrapper, path, namespaces) match {
                                case newNode: VirtualNode ⇒ Some(outerInstance, newNode)
                                case _                    ⇒ throw new IllegalStateException
                            }
                    }
                case _ ⇒ throw new IllegalStateException
            }
    }

    def toOuterInstanceNodeXBL(
            outerInstance: XFormsInstance,
            outerNode: NodeInfo,
            partAnalysis: PartAnalysis): NodeMatcher = {

        (_, innerNode, siblingIndexOpt) ⇒

            // The path to the inner node looks like /a/b[i1]/c[i2], where "a" is the root element. The outer element is
            // allowed to have another name. So we create a relative path starting at /a, which can be applied to the
            // outer element.
            val relativePath  = getNodePath(innerNode, siblingIndexOpt) split '/' drop 2 mkString "/"

            if (relativePath.isEmpty)
                // The root element
                Some(outerInstance, outerNode)
            else {
                // NOTE: Namespace handling makes assumption that all namespaces are visible at the level of
                // xf:instance. This is not general enough. It stems from the use of getPath, which loses namespace
                // mappings.
                val namespaces = outerInstance.instance.namespaceMapping

                // Apply path to find node in outer instance
                Option(evalOne(outerNode, relativePath, namespaces)) collect {
                    case newNode: NodeInfo ⇒ outerInstance → newNode
                }
            }
    }

    // Listener that mirrors changes from one document to the other
    def mirrorListener(
        containingDocument : XFormsContainingDocument,
        findMatchingNode   : NodeMatcher)(implicit
        logger             : IndentedLogger
    ): MirrorEventListener = {

        case valueChanged: XXFormsValueChangedEvent ⇒
            findMatchingNode(valueChanged.targetInstance, valueChanged.node, None) match {
                case Some((matchingInstance, matchingNode)) ⇒
                    DataModel.setValueIfChanged(
                        matchingNode,
                        valueChanged.newValue,
                        DataModel.logAndNotifyValueChange(
                            containingDocument,
                            "mirror",
                            matchingNode,
                            _,
                            valueChanged.newValue,
                            isCalculate = false
                        ),
                        reason ⇒ throw new OXFException(reason.message)
                    )
                    true
                case _ ⇒
                    false
            }

        case insert: XFormsInsertEvent if ! insert.isRootElementReplacement ⇒
            // Insert except root element replacement
            findMatchingNode(
                insert.targetInstance,
                insert.insertLocationNode,
                insert.position != "into" option insert.insertLocationIndex
            ) match {
                case Some((matchingInstance, matchingInsertLocation)) ⇒
                    insert.position match {
                        case "into"   ⇒ XFormsAPI.insert(insert.originItems, into   = matchingInsertLocation)
                        case "before" ⇒ XFormsAPI.insert(insert.originItems, before = matchingInsertLocation)
                        case "after"  ⇒ XFormsAPI.insert(insert.originItems, after  = matchingInsertLocation)
                    }
                    true
                case _ ⇒
                    false
            }

        case insert: XFormsInsertEvent if insert.isRootElementReplacement ⇒ true // handled by xxforms-replace

        case delete: XFormsDeleteEvent ⇒

            val successes =
                delete.deleteInfos map { deleteInfo ⇒ // more than one node might have been removed

                    val removedNodeInfo  = deleteInfo.nodeInfo
                    val removedNodeIndex = deleteInfo.index

                    // Find the corresponding parent of the removed node and run the body on it. The body returns
                    // Some(Node) if that node can be removed.
                    def withNewParent(body: Node ⇒ (Option[Node], Boolean)) = {

                        // If parent is available, find matching node and call body
                        Option(deleteInfo.parent) match {
                            case Some(removedParentNodeInfo) ⇒
                                findMatchingNode(delete.targetInstance, removedParentNodeInfo, None) match {
                                    case Some((matchingInstance, matchingParentNode)) ⇒

                                        val docWrapper    = matchingParentNode.getDocumentRoot.asInstanceOf[DocumentWrapper]
                                        val newParentNode = XFormsUtils.getNodeFromNodeInfo(matchingParentNode, "")

                                        body(newParentNode) match {
                                            case (Some(nodeToRemove: Node), result) ⇒
                                                XFormsAPI.delete(Seq(docWrapper.wrap(nodeToRemove)))
                                                result
                                            case (_, result) ⇒
                                                result
                                        }
                                    case _ ⇒
                                        false
                                }
                            case _ ⇒
                                false
                        }
                    }

                    // Handle removed node depending on type
                    removedNodeInfo.getNodeKind match {
                        case ATTRIBUTE_NODE ⇒
                            // An attribute was removed
                            withNewParent {
                                case newParentElement: Element ⇒
                                    // Find the attribute  by name (as attributes are unique for a given QName)
                                    val removedAttribute =
                                        XFormsUtils.getNodeFromNodeInfo(removedNodeInfo, "").asInstanceOf[Attribute]

                                    newParentElement.attribute(removedAttribute.getQName) match {
                                        case newAttribute: Attribute ⇒ (Some(newAttribute), true)
                                        case _ ⇒ (None, false) // out of sync, so probably safer
                                    }
                                case _ ⇒
                                    (None, false)
                            }
                        case ELEMENT_NODE ⇒
                            // An element was removed
                            withNewParent {
                                case newParentDocument: Document ⇒
                                    // Element removed was root element

                                    // Don't perform the deletion of the root element because we don't support
                                    // this in the data model (although maybe we should). However, consider this
                                    // a supported change. If the root element is replaced, the subsequent
                                    // xxforms-replace event will take care of the replacement.
                                    (None, true)

                                case newParentElement: Element ⇒
                                    // Element removed had a parent element
                                    val removedElement =
                                        XFormsUtils.getNodeFromNodeInfo(removedNodeInfo, "").asInstanceOf[Element]

                                    // If we can identify the position
                                    val content = newParentElement.content.asInstanceOf[JList[Node]]
                                    if (content.size > removedNodeIndex) {
                                        content.get(removedNodeIndex) match {
                                            case newElement: Element if newElement.getQName == removedElement.getQName ⇒
                                                (Some(newElement), true)
                                            case _ ⇒
                                                (None, false) // out of sync, so probably safer
                                        }
                                    } else
                                        (None, false) // out of sync, so probably safer
                                case _ ⇒
                                    (None, false)
                            }
                        case TEXT_NODE ⇒
                            false // TODO
                        case _ ⇒
                            false // we don't know how to propagate the change
                    }
                }

            successes exists identity // "at least one item is true"

        case replace: XXFormsReplaceEvent if replace.currentNode.getNodeKind == ELEMENT_NODE ⇒
            // Element replacement
            findMatchingNode(replace.targetInstance, replace.currentNode.getParent, None) match {
                case Some((matchingInstance, matchingParent)) ⇒
                    val deleted  = XFormsAPI.delete(matchingParent child *, doDispatch = false)
                    val inserted = XFormsAPI.insert(replace.currentNode, into = matchingParent, doDispatch = false)

                    Dispatch.dispatchEvent(new XXFormsReplaceEvent(matchingInstance, deleted.head, inserted.head))
                    true
                case _ ⇒
                    false
            }
        case replace: XXFormsReplaceEvent ⇒ true // handled by xforms-insert
        case _ ⇒ throw new IllegalStateException // no other events supported
    }
}