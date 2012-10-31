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
import action.actions.XFormsDeleteAction.doDelete
import action.actions.XFormsInsertAction.doInsert
import collection.JavaConverters._
import event.events.{XFormsDeleteEvent, XFormsInsertEvent, XXFormsValueChanged}
import event.XFormsEvents._
import model.DataModel
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.saxon.dom4j.DocumentWrapper
import org.orbeon.saxon.value.StringValue
import java.util.{List ⇒ JList}
import org.orbeon.oxf.util.IndentedLogger
import java.lang.{IllegalStateException, IllegalArgumentException}
import org.dom4j._
import org.orbeon.scaxon.XML._
import org.orbeon.oxf.common.OXFException
import XXFormsDynamicControl._
import org.orbeon.oxf.xforms.event.{ListenersTrait, EventListener ⇒ JEventListener}
import org.orbeon.saxon.om._

// Logic to mirror mutations between an outer and an inner instance
object InstanceMirror {

    private val mutationEvents = Seq(XXFORMS_VALUE_CHANGED, XFORMS_INSERT, XFORMS_DELETE)

    def addListener(observer: ListenersTrait, listener: JEventListener): Unit =
        for (eventName ← mutationEvents)
            observer.addListener(eventName, listener)

    def removeListener(observer: ListenersTrait, listener: JEventListener): Unit =
        for (eventName ← mutationEvents)
            observer.removeListener(eventName, listener)

    // Find the inner instance node from a node in an outer instance
    def toInnerInstanceNode(outerDoc: DocumentInfo, partAnalysis: PartAnalysis, container: XBLContainer)
            (outerNode: NodeInfo, sourceId: String, into: Boolean): Option[NodeInfo] = {

        // In "into" mode, use ancestor-or-self because outerNode passed is the containing node (node into which other
        // nodes are inserted, node from which other nodes are removed, or node which text value changes), which in the
        // case of a root element is the xforms:instance element. The exception is when you insert a node before or
        // after an xforms:instance element, in which case the change is not in the instance.

        val axis = if (! into) "ancestor" else if (outerNode.getNodeKind == ATTRIBUTE_NODE) "../ancestor" else "ancestor-or-self"
        val findInstanceExpr = "(" + axis + "::xforms:instance)[1]"

        evalOne(outerNode, findInstanceExpr) match {
            case instanceWrapper: VirtualNode if instanceWrapper.getUnderlyingNode.isInstanceOf[Element] ⇒
                // This is a change to an instance

                // Find instance id
                val instanceId = XFormsUtils.getElementId(instanceWrapper.getUnderlyingNode.asInstanceOf[Element])
                if (instanceId eq null)
                    throw new IllegalArgumentException

                // Find path rooted at wrapper
                val innerPath = {
                    val pathToInstance = Navigator.getPath(instanceWrapper)
                    val pathToOuterNode = Navigator.getPath(outerNode)

                    assert(pathToOuterNode.startsWith(pathToInstance))

                    if (pathToOuterNode.size > pathToInstance.size)
                        pathToOuterNode.substring(pathToInstance.size)
                    else
                        "/"
                }

                // Find inner instance
                container.findInstance(instanceId) match {
                    case Some(innerInstance) ⇒
                        // Find destination path in instance
                        val namespaces = partAnalysis.getNamespaceMapping(partAnalysis.startScope.fullPrefix, instanceWrapper.getUnderlyingNode.asInstanceOf[Element])
                        evalOne(innerInstance.documentInfo, innerPath, namespaces) match {
                            case newNode: VirtualNode ⇒ Some(newNode)
                            case _                    ⇒ throw new IllegalStateException
                        }
                    case None ⇒
                        // May not be found if instance was just created
                        None
                }

            case _ ⇒
                None // ignore change as it is not within an instance
        }
    }

    // Find the outer node in an inline instance from a node in an inner instance
    def toOuterInstanceNode(outerDoc: DocumentInfo, partAnalysis: PartAnalysis)(innerNode: NodeInfo, sourceId: String, into: Boolean): Option[NodeInfo] = {

        // Find path in instance
        val path = dropStartingSlash(Navigator.getPath(innerNode))

        // Find instance in original doc
        evalOne(outerDoc, "//xforms:instance[@id = $sourceId]",
                variables = Map("sourceId" → StringValue.makeStringValue(sourceId))) match {
            case instanceWrapper: VirtualNode if instanceWrapper.getUnderlyingNode.isInstanceOf[Element] ⇒
                // Find destination node in inline instance in original doc
                val namespaces = partAnalysis.getNamespaceMapping(partAnalysis.startScope.fullPrefix, instanceWrapper.getUnderlyingNode.asInstanceOf[Element])
                evalOne(instanceWrapper, path, namespaces) match {
                    case newNode: VirtualNode ⇒ Some(newNode)
                    case _ ⇒ throw new IllegalStateException
                }
            case _ ⇒ throw new IllegalStateException
        }
    }

    // Listener that mirrors changes from one document to the other
    def mirrorListener(
            containingDocument: XFormsContainingDocument,
            indentedLogger: IndentedLogger,
            findMatchingNode: (NodeInfo, String, Boolean) ⇒ Option[NodeInfo]): EventListener = {

        case valueChanged: XXFormsValueChanged ⇒
            findMatchingNode(valueChanged.node, valueChanged.targetObject.getId, true) match {
                case Some(newNode) ⇒
                    DataModel.setValueIfChanged(
                        newNode,
                        valueChanged.newValue,
                        DataModel.logAndNotifyValueChange(containingDocument, indentedLogger, "mirror", newNode, _, valueChanged.newValue, isCalculate = false),
                        reason ⇒ throw new OXFException(reason.message)
                    )
                    true
                case _ ⇒
                    false
            }
        case insert: XFormsInsertEvent ⇒
            findMatchingNode(insert.insertLocationNode, insert.targetObject.getId, insert.position == "into") match {
                case Some(insertNode) ⇒
                    insert.position match {
                        case "into" ⇒
                            doInsert(containingDocument, indentedLogger, "after", null,
                                insertNode, insert.originItems.asJava, -1, doClone = true, doDispatch = false)
                        case position @ ("before" | "after") ⇒

                            def containsRootElement(items: Seq[Item]) =
                                items collect { case node: NodeInfo ⇒ node } exists (node ⇒ node == node.rootElement)

                            if (containsRootElement(insert.insertedItems)) {
                                // If the inserted items contain the root element it means the root element was replaced, so
                                // remove it first

                                assert(insert.insertedItems.size == 1)

                                val parent = insertNode.parentOption.get
                                doDelete(containingDocument, indentedLogger, Seq(insertNode).asJava, - 1, doDispatch = false)
                                doInsert(containingDocument, indentedLogger, position, null,
                                    parent, insert.originItems.asJava, 1, doClone = true, doDispatch = false)
                            } else {
                                // Not replacing the root element
                                doInsert(containingDocument, indentedLogger, position, Seq(insertNode).asJava,
                                    null, insert.originItems.asJava, 1, doClone = true, doDispatch = false)
                            }
                        case _ ⇒ throw new IllegalStateException
                    }
                    true
                case _ ⇒
                    false
            }
        case delete: XFormsDeleteEvent ⇒
            delete.deleteInfos map { deleteInfo ⇒ // more than one node might have been removed

                val removedNodeInfo = deleteInfo.nodeInfo
                val removedNodeIndex = deleteInfo.index

                // Find the corresponding parent of the removed node and run the body on it. The body returns Some(Node)
                // if that node can be removed.
                def withNewParent(body: Node ⇒ Option[Node]) = {

                    // If parent is available, find matching node and call body
                    Option(deleteInfo.parent) match {
                        case Some(removedParentNodeInfo) ⇒
                            findMatchingNode(removedParentNodeInfo, delete.targetObject.getId, true) match {
                                case Some(newParentNodeInfo) ⇒

                                    val docWrapper = newParentNodeInfo.getDocumentRoot.asInstanceOf[DocumentWrapper]
                                    val newParentNode = XFormsUtils.getNodeFromNodeInfo(newParentNodeInfo, "")

                                    body(newParentNode) match {
                                        case Some(nodeToRemove: Node) ⇒
                                            doDelete(containingDocument, indentedLogger, Seq(docWrapper.wrap(nodeToRemove)).asJava, -1, doDispatch = false)
                                            true
                                        case _ ⇒ false
                                    }
                                case _ ⇒ false
                            }
                        case _ ⇒ false
                    }
                }

                // Handle removed node depending on type
                removedNodeInfo.getNodeKind match {
                    case ATTRIBUTE_NODE ⇒
                        // An attribute was removed
                        withNewParent {
                            case newParentElement: Element ⇒
                                // Find the attribute  by name (as attributes are unique for a given QName)
                                val removedAttribute = XFormsUtils.getNodeFromNodeInfo(removedNodeInfo, "").asInstanceOf[Attribute]
                                newParentElement.attribute(removedAttribute.getQName) match {
                                    case newAttribute: Attribute ⇒ Some(newAttribute)
                                    case _ ⇒ None // out of sync, so probably safer
                                }
                            case _ ⇒ None
                        }
                    case ELEMENT_NODE ⇒
                        // An element was removed
                        withNewParent {
                            case newParentDocument: Document ⇒
                                // Element removed was root element
                                val removedElement = XFormsUtils.getNodeFromNodeInfo(removedNodeInfo, "").asInstanceOf[Element]
                                val newRootElement =  newParentDocument.getRootElement

                                if (newRootElement.getQName == removedElement.getQName)
                                    Some(newRootElement)
                                else
                                    None

                            case newParentElement: Element ⇒
                                // Element removed had a parent element
                                val removedElement = XFormsUtils.getNodeFromNodeInfo(removedNodeInfo, "").asInstanceOf[Element]

                                // If we can identify the position
                                val content = newParentElement.content.asInstanceOf[JList[Node]]
                                if (content.size > removedNodeIndex) {
                                    content.get(removedNodeIndex) match {
                                        case newElement: Element if newElement.getQName == removedElement.getQName ⇒ Some(newElement)
                                        case _ ⇒ None // out of sync, so probably safer
                                    }
                                } else
                                    None // out of sync, so probably safer
                            case _ ⇒ None
                        }
                    case TEXT_NODE ⇒
                        false // TODO
                    case _ ⇒
                        false // we don't know how to propagate the change
                }
            } exists identity // "at least one item is true"
        case _ ⇒ throw new IllegalStateException
    }
}