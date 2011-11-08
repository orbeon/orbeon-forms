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

import org.orbeon.oxf.xforms.control.{XFormsSingleNodeContainerControl, XFormsControl}
import org.orbeon.oxf.xforms.XFormsContextStack.BindingContext
import org.w3c.dom.Node.{ELEMENT_NODE, ATTRIBUTE_NODE}
import org.orbeon.oxf.xforms.xbl.{XBLBindingsBase, XBLContainer}
import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import org.xml.sax.helpers.AttributesImpl
import org.orbeon.oxf.xforms._
import action.actions.{XFormsDeleteAction, XFormsInsertAction}
import collection.JavaConverters._
import event.events.{XFormsDeleteEvent, XFormsInsertEvent, XXFormsValueChanged}
import event.{EventListener, XFormsEvent, XFormsEvents}
import model.DataModel
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.saxon.dom4j.{DocumentWrapper, NodeWrapper}
import org.orbeon.saxon.om.{DocumentInfo, NodeInfo, Navigator}
import org.orbeon.oxf.xml._
import org.orbeon.saxon.value.StringValue
import java.util.{Collections => JCollections, Map => JMap, List => JList}
import org.orbeon.oxf.util.{IndentedLogger, XPathCache}
import java.lang.{IllegalStateException, IllegalArgumentException}
import org.dom4j._
import org.orbeon.scaxon.XML.evalOne

class XXFormsDynamicControl(container: XBLContainer, parent: XFormsControl, element: Element, name: String, effectiveId: String, state: JMap[String, Element])
    extends XFormsSingleNodeContainerControl(container, parent, element, name, effectiveId) {

    implicit def toEventListener(f: XFormsEvent => Any) = new EventListener {
        def handleEvent(event: XFormsEvent) { f(event) }
    }

    class Nested(val container: XBLContainer, val partAnalysis: PartAnalysis, val template: SAXStore, val outerListener: EventListener)

    var nested: Option[Nested] = None

    var previousChangeCount = -1
    var changeCount = 0

    var newScripts: Seq[Script] = Seq.empty

    // For Java callers
    def getNestedContainer = nested map (_.container) orNull
    def getNestedPartAnalysis = nested map (_.partAnalysis) orNull
    def getNestedTemplate = nested map (_.template) orNull

    // TODO: we should not override this, but currently due to the way XFormsContextStack works with XBL, even non-relevant
    // XFormsComponentControl expect the binding to be set.
    override def setBindingContext(bindingContext: XFormsContextStack.BindingContext) {
        super.setBindingContext(bindingContext)

        nested foreach { n =>
            n.container.setBindingContext(bindingContext)
            n.container.getContextStack.resetBindingContext()
        }
    }

    override def onCreate() {
        getBoundElement match {
            case Some(node) => updateSubTree(node)
            case _ => // don't create binding (maybe we could for a read-only instance)
                nested = None
        }
    }

    override def onDestroy() {
        // TODO: XXX remove child container from parent
        nested = None
        previousChangeCount = 0
        changeCount = 0
    }

    override def onBindingUpdate(oldBinding: BindingContext, newBinding: BindingContext) {
        getBoundElement match {
            case Some(node) => updateSubTree(node)
            case _ =>
        }
    }

    private def updateSubTree(node: NodeWrapper) {
        if (previousChangeCount != changeCount) {
            // Document has changed and needs to be fully recreated
            previousChangeCount = changeCount

            // Outer instance
            val outerInstance = containingDocument.getInstanceForNode(node)
            if (outerInstance eq null)
                throw new IllegalArgumentException

            // Remove children controls if any
            val tree = containingDocument.getControls.getCurrentControlTree
            if (getSize > 0) {
                // TODO: PERF: dispatching destruction events takes a lot of time, what can we do?
                //tree.dispatchDestructionEventsForRemovedContainer(this, false)
                tree.deindexSubtree(this, false)
                getChildren.clear()
            }

            nested foreach { n =>
                // Remove container and associated models
                n.container.destroy()
                // Remove part and associated scopes
                containingDocument.getStaticOps.removePart(n.partAnalysis)
                // Remove listeners we added to the outer instance (better do this or we will badly leak)
                // WARNING: Make sure n.outerListener is the exact same object passed to addListener. There can be a
                // conversion from a function to a listener, in which case identity won't be preserved!
                for (eventName <- InstanceMirror.mutationEvents)
                    outerInstance.removeListener(eventName, n.outerListener)
            }

            // Create new part
            val element = node.getUnderlyingNode.asInstanceOf[Element]
            val (template, partAnalysis) = createPartAnalysis(Dom4jUtils.createDocumentCopyElement(element), getXBLContainer.getPartAnalysis)

            // Save new scripts if any
//            val newScriptCount = containingDocument.getStaticState.getScripts.size
//            if (newScriptCount > scriptCount)
//                newScripts = containingDocument.getStaticState.getScripts.values.view(scriptCount, newScriptCount).toSeq

            // Nested container is initialized after binding and before control tree
            // TODO: Support updating models/instances
            val container = getXBLContainer.createChildContainer(this, partAnalysis)

            container.addAllModels()
            container.setLocationData(getLocationData)
            container.initializeModels(Array(
                XFormsEvents.XFORMS_MODEL_CONSTRUCT,
                XFormsEvents.XFORMS_MODEL_CONSTRUCT_DONE)
            )

            // Add listener to the single outer instance
            val docWrapper = new DocumentWrapper(element.getDocument, null, XPathCache.getGlobalConfiguration)
            val outerListener: EventListener = InstanceMirror.mirrorListener(containingDocument, getIndentedLogger,
                InstanceMirror.toInnerNode(docWrapper, partAnalysis, container), () => changeCount += 1) _
            for (eventName <- InstanceMirror.mutationEvents)
                outerInstance.addListener(eventName, outerListener)

            // Add mutation listeners to all top-level inline instances, which upon value change propagate the value
            // change to the related node in the source
            val innerListener: EventListener = InstanceMirror.mirrorListener(containingDocument, getIndentedLogger,
                InstanceMirror.toOuterNode(docWrapper, partAnalysis), () => ()) _

            partAnalysis.getModelsForScope(partAnalysis.startScope).asScala foreach {
                _.instances.values filter (_.src eq null) foreach { instance =>
                    val innerInstance = container.findInstance(instance.staticId)
                    for (eventName <- InstanceMirror.mutationEvents)
                        innerInstance.addListener(eventName, innerListener)
                }
            }

            // Remember all that we created
            nested = Some(new Nested(container, partAnalysis, template, outerListener))
            
            // Create new control subtree
            tree.createAndInitializeSubTree(containingDocument, this)
        }
    }

    private def getBoundElement =
        getBindingContext.getSingleItem match {
            case nodeWrapper: NodeWrapper if nodeWrapper.getNodeKind == ELEMENT_NODE => Some(nodeWrapper)
            case _ => None
        }

    private def createPartAnalysis(doc: Document, parent: PartAnalysis) = {
        val newScope = new XBLBindingsBase.Scope(getResolutionScope, getPrefixedId)
        val templateAndPart = XFormsStaticStateImpl.createPart(containingDocument.getStaticState, parent, doc, newScope)
        containingDocument.getStaticOps.addPart(templateAndPart._2)
        templateAndPart
    }

    override def getBackCopy = {
        val cloned = super.getBackCopy.asInstanceOf[XXFormsDynamicControl]
        cloned.previousChangeCount = -1 // unused
        cloned.changeCount = previousChangeCount
        cloned.nested = None

        cloned
    }

    override def equalsExternal(other: XFormsControl) =
        changeCount == other.asInstanceOf[XXFormsDynamicControl].changeCount && newScripts.isEmpty

    override def outputAjaxDiff(ch: ContentHandlerHelper, other: XFormsControl, attributesImpl: AttributesImpl, isNewlyVisibleSubtree: Boolean) {}
}

// Logic to mirror mutation changes between an outer and an inner instance
object InstanceMirror {

    val mutationEvents = Seq(XFormsEvents.XXFORMS_VALUE_CHANGED, XFormsEvents.XFORMS_INSERT, XFormsEvents.XFORMS_DELETE)

    // Find the inner instance node from a node in an outer instance
    def toInnerNode(outerDoc: DocumentInfo, partAnalysis: PartAnalysis, container: XBLContainer)(outerNode: NodeInfo, sourceId: String, into: Boolean) = {

        // In "into" mode, use ancestor-or-self because outerNode passed is the containing node (node into which other
        // nodes are inserted, node from which other nodes are removed, or node which text value changes), which in the
        // case of a root element is the xforms:instance element. The exception is when you insert a node before or
        // after an xforms:instance element, in which case the change is not in the instance.

        val axis = if (! into) "ancestor" else if (outerNode.getNodeKind == ATTRIBUTE_NODE) "../ancestor" else "ancestor-or-self"
        val expr = "(" + axis + "::xforms:instance)[1]"

        evalOne(outerNode, expr) match {
            case instanceWrapper: NodeWrapper if instanceWrapper.getUnderlyingNode.isInstanceOf[Element] =>
                // This is a change to an instance

                // Find instance id
                val instanceId = instanceWrapper.getUnderlyingNode.asInstanceOf[Element].attributeValue("id")
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
                val innerInstance = container.findInstance(instanceId)

                if (innerInstance ne null) { // may not be found if instance was just created
                    // Find destination path in instance
                    val namespaces = partAnalysis.getNamespaceMapping(partAnalysis.startScope.getFullPrefix, instanceWrapper.getUnderlyingNode.asInstanceOf[Element])
                    evalOne(innerInstance.getDocumentInfo, innerPath, namespaces) match {
                        case newNode: NodeWrapper => Some(newNode)
                        case _ => throw new IllegalStateException
                    }
                } else
                    None

            case _ => None // ignore change as it is not within an instance
        }
    }

    // Find the outer node in an inline instance from a node in an inner instance
    def toOuterNode(outerDoc: DocumentInfo, partAnalysis: PartAnalysis)(innerNode: NodeInfo, sourceId: String, into: Boolean) = {

        // Find path in instance
        val path = dropStartingSlash(Navigator.getPath(innerNode))

        // Find instance in original doc
        evalOne(outerDoc, "//xforms:instance[@id = $sourceId]",
                variables = Map("sourceId" -> StringValue.makeStringValue(sourceId))) match {
            case instanceWrapper: NodeWrapper if instanceWrapper.getUnderlyingNode.isInstanceOf[Element] =>
                // Find destination node in inline instance in original doc
                val namespaces = partAnalysis.getNamespaceMapping(partAnalysis.startScope.getFullPrefix, instanceWrapper.getUnderlyingNode.asInstanceOf[Element])
                evalOne(instanceWrapper, path, namespaces) match {
                    case newNode: NodeWrapper => Some(newNode)
                    case _ => throw new IllegalStateException
                }
            case _ => throw new IllegalStateException
        }
    }

    // Listener that mirrors changes from one document to the other
    def mirrorListener(containingDocument: XFormsContainingDocument, indentedLogger: IndentedLogger,
                       findMatchingNode: (NodeInfo, String, Boolean) => Option[NodeInfo],
                       notifyOtherChange: () => Unit)(e: XFormsEvent) = e match {

        case valueChanged: XXFormsValueChanged =>
            findMatchingNode(valueChanged.node, valueChanged.getTargetObject.getId, true) match {
                case Some(newNode) =>
                    DataModel.setValueIfChanged(newNode, valueChanged.newValue)
                case None => // change not in an instance
                    notifyOtherChange()
            }
        case insert: XFormsInsertEvent =>
            findMatchingNode(insert.getInsertLocationNodeInfo, insert.getTargetObject.getId, insert.getPosition == "into") match {
                case Some(insertNode) =>
                    insert.getPosition match {
                        case "into" =>
                            XFormsInsertAction.doInsert(containingDocument, indentedLogger, "after", null,
                                insertNode, insert.getOriginItems, -1, doClone = true, doDispatch = false)
                        case position @ ("before" | "after") =>
                            XFormsInsertAction.doInsert(containingDocument, indentedLogger, position, JCollections.singletonList(insertNode),
                                null, insert.getOriginItems, 1, doClone = true, doDispatch = false)
                        case _ => throw new IllegalStateException
                    }
                case None => // change not in an instance
                    notifyOtherChange()

            }
        case delete: XFormsDeleteEvent =>
            delete.deleteInfos.asScala foreach { deleteInfo => // more than one node might have been removed

                val removedNodeInfo = deleteInfo.nodeInfo
                val removedNodeIndex = deleteInfo.index

                // Find the corresponding parent of the removed node and run the body on it. The body returns Some(Node)
                // if that node can be removed.
                def withNewParent(body: Node => Option[Node]) {

                    // If parent is available, find matching node and call body
                    Option(deleteInfo.parent) match {
                        case Some(removedParentNodeInfo) =>
                            findMatchingNode(removedParentNodeInfo, delete.getTargetObject.getId, true) match {
                                case Some(newParentNodeInfo) =>

                                    val docWrapper = newParentNodeInfo.getDocumentRoot.asInstanceOf[DocumentWrapper]
                                    val newParentNode = XFormsUtils.getNodeFromNodeInfo(newParentNodeInfo, "")

                                    body(newParentNode) match {
                                        case Some(nodeToRemove: Node) =>
                                            XFormsDeleteAction.doDelete(containingDocument, indentedLogger, JCollections.singletonList(docWrapper.wrap(nodeToRemove)), -1, doDispatch = false)
                                        case _ => notifyOtherChange()
                                    }
                                case _ => notifyOtherChange()
                            }
                        case _ => notifyOtherChange()
                    }
                }

                // Handle removed node dpending on type
                removedNodeInfo.getNodeKind match {
                    case org.w3c.dom.Node.ATTRIBUTE_NODE =>
                        // An attribute was removed
                        withNewParent {
                            case newParentElement: Element =>
                                // Find the attribute  by name (as attributes are unique for a given QName)
                                val removedAttribute = XFormsUtils.getNodeFromNodeInfo(removedNodeInfo, "").asInstanceOf[Attribute]
                                newParentElement.attribute(removedAttribute.getQName) match {
                                    case newAttribute: Attribute => Some(newAttribute)
                                    case _ => None // out of sync, so probably safer
                                }
                            case _ => None
                        }
                    case org.w3c.dom.Node.ELEMENT_NODE =>
                        // An element was removed
                        withNewParent {
                            case newParentDocument: Document =>
                                // Element removed was root element
                                val removedElement = XFormsUtils.getNodeFromNodeInfo(removedNodeInfo, "").asInstanceOf[Element]
                                val newRootElement =  newParentDocument.getRootElement

                                if (newRootElement.getQName == removedElement.getQName)
                                    Some(newRootElement)
                                else
                                    None

                            case newParentElement: Element =>
                                // Element removed had a parent element
                                val removedElement = XFormsUtils.getNodeFromNodeInfo(removedNodeInfo, "").asInstanceOf[Element]

                                // If we can identify the position
                                val content = newParentElement.content.asInstanceOf[JList[Node]]
                                if (content.size > removedNodeIndex) {
                                    content.get(removedNodeIndex) match {
                                        case newElement: Element if newElement.getQName == removedElement.getQName => Some(newElement)
                                        case _ => None // out of sync, so probably safer
                                    }
                                } else
                                    None // out of sync, so probably safer
                            case _ => None
                        }
                    case org.w3c.dom.Node.TEXT_NODE =>
                        // TODO
                        notifyOtherChange()
                    case _ => notifyOtherChange() // we don't know how to propagate the change
                }
            }
        case _ => throw new IllegalStateException
    }
}