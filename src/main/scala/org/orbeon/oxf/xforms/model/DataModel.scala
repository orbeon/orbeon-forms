/**
 *  Copyright (C) 2011 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.model

import org.orbeon.oxf.xforms._
import event.events.{XXFormsBindingErrorEvent, XXFormsValueChangedEvent}
import event.{Dispatch, XFormsEventTarget}
import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.saxon.value.AtomicValue
import org.orbeon.oxf.xml.dom4j.LocationData
import org.dom4j.{Text ⇒ Text4j, Comment ⇒ Comment4j, _}
import org.orbeon.scaxon.XML._
import org.w3c.dom.Node._
import org.orbeon.saxon.om._

/**
 * Represent access to the data model via the NodeInfo abstraction.
 *
 * This covers setting and getting values.
 */
object DataModel {

    // Reasons that setting a value on a node can fail
    sealed trait Reason { val message: String }
    case object  DisallowedNodeReason extends Reason { val message = "Unable to set value on disallowed node" }
    case object  ReadonlyNodeReason   extends Reason { val message = "Unable to set value on read-only node" }

    /**
     * Whether the given item is acceptable as a bound item.
     *
     * NOTE: As of 2012-04-26, we allow binding non-value controls to any node type.
     */
    def isAllowedBoundItem(item: Item) = item ne null

    /**
     * Whether the given item is acceptable as a bound item storing a value.
     *
     * The thinking as of 2012-04-26 is that it is ok for value controls to bind to text, PI and comment nodes, which is
     * not disallowed by per XForms, however doing so might lead to funny results when writing values back. In
     * particular, writing an empty value to a text node causes the control to become non-relevant.
     */
    def isAllowedValueBoundItem(item: Item) = item match {
        case _: AtomicValue                                                                        ⇒ true
        case node: NodeInfo if isAttribute(node) || isElement(node) && supportsSimpleContent(node) ⇒ true
        case node: NodeInfo if node self (Text || PI || Comment)                                   ⇒ true
        case _                                                                                     ⇒ false
    }

    /**
     * If it is possible to write to the given item, return a VirtualNode, otherwise return a reason why not.
     *
     * It's not possible to write to:
     *
     * - atomic values (which are read-only)
     * - document nodes (even mutable ones)
     * - element nodes containing other elements
     * - items not backed by a mutable node (which are read-only)
     */
    def isWritableItem(item: Item): VirtualNode Either Reason = item match {
        case _: AtomicValue                             ⇒ Right(ReadonlyNodeReason)
        case _: DocumentInfo                            ⇒ Right(DisallowedNodeReason)
        case node: VirtualNode if hasChildElement(node) ⇒ Right(DisallowedNodeReason)
        case node: VirtualNode                          ⇒ Left(node)
        case _                                          ⇒ Right(ReadonlyNodeReason)
    }

    /**
     * Return the value of a bound item, whether a NodeInfo or an AtomicValue.
     *
     * NOTE: We used to return null for "disallowed" items, such as document nodes. However there doesn't seem to be
     * much of a drawback to just return the string value. Scenarios where this could happen:
     *
     * - bind to simple content element
     * - insert into that element
     * - manually run revalidation
     * - XFormsModelBinds gets node value for validation
     */
    def getValue(item: Item) = Option(item) map (_.getStringValue) orNull

    /**
     * Set a value on the instance using a NodeInfo and a value.
     *
     * @param nodeInfo              element or attribute NodeInfo to update
     * @param newValue              value to set
     * @param onSuccess             function called if the value was set
     * @param onError               function called if the value was not set
     *
     * Return true if the value was set.
     */
    def setValue(
        nodeInfo  : NodeInfo,
        newValue  : String,
        onSuccess : () ⇒ Unit     = () ⇒ (),
        onError   : Reason ⇒ Unit = _ ⇒ ()
    ) = {

        assert(nodeInfo ne null)
        assert(newValue ne null)

        isWritableItem(nodeInfo) match {
            case Left(virtualNode) ⇒
                setValueForNode(virtualNode.getUnderlyingNode.asInstanceOf[Node], newValue)
                onSuccess()
                true
            case Right(reason) ⇒
                onError(reason)
                false
        }
    }

    /**
     * Same as setValue but only attempts to set the value if the new value is different from the old value.
     *
     * Return true if the value was changed.
     */
    def setValueIfChanged(
        nodeInfo  : NodeInfo,
        newValue  : String,
        onSuccess : String ⇒ Unit = _ ⇒ (),
        onError   : Reason ⇒ Unit = _ ⇒ ()
    ) = {
        
        assert(nodeInfo ne null)
        assert(newValue ne null)
        
        val oldValue = getValue(nodeInfo)
        val doUpdate = oldValue != newValue

        // Do not require RRR / mark the instance dirty if the value hasn't actually changed
        doUpdate &&
            setValue(nodeInfo, newValue, () ⇒ onSuccess(oldValue), onError)
    }

    /**
     * Same as setValueIfChanged but with default error handling.
     *
     * Used by MIPs and when setting external values on controls.
     *
     * Return true if the value was changed.
     *
     * TODO: Move to use setValueIfChanged once callers are all in Scala.
     */
    def jSetValueIfChanged(
        containingDocument : XFormsContainingDocument,
        eventTarget        : XFormsEventTarget,
        locationData       : LocationData,
        nodeInfo           : NodeInfo,
        valueToSet         : String,
        source             : String,
        isCalculate        : Boolean)(implicit
        logger             : IndentedLogger
    ) = {

        assert(containingDocument ne null)
        assert(logger ne null)

        setValueIfChanged(
            nodeInfo,
            valueToSet,
            logAndNotifyValueChange(containingDocument, source, nodeInfo, _, valueToSet, isCalculate),
            reason ⇒ Dispatch.dispatchEvent(new XXFormsBindingErrorEvent(eventTarget, locationData, reason))
        )
    }

    // Standard success behavior: log and notify
    def logAndNotifyValueChange(
        containingDocument : XFormsContainingDocument,
        source             : String,
        nodeInfo           : NodeInfo,
        oldValue           : String,
        newValue           : String,
        isCalculate        : Boolean)(implicit
        logger             : IndentedLogger
    ) = {
        logValueChange(logger, source, oldValue,  newValue, findInstanceEffectiveId(containingDocument, nodeInfo))
        notifyValueChange(containingDocument, nodeInfo, oldValue, newValue, isCalculate)
    }

    private def findInstanceEffectiveId(containingDocument: XFormsContainingDocument, nodeInfo: NodeInfo) =
        Option(containingDocument.getInstanceForNode(nodeInfo)) map (_.getEffectiveId)
    
    private def logValueChange(indentedLogger: IndentedLogger, source: String, oldValue: String, newValue: String, instanceEffectiveId: Option[String]) =
        if (indentedLogger.isDebugEnabled)
            indentedLogger.logDebug("xf:setvalue", "setting instance value", "source", source,
                "old value", oldValue, "new value", newValue,
                "instance", instanceEffectiveId getOrElse "N/A")

    private def notifyValueChange(containingDocument: XFormsContainingDocument, nodeInfo: NodeInfo, oldValue: String, newValue: String, isCalculate: Boolean) =
        Option(containingDocument.getInstanceForNode(nodeInfo)) match {
            case Some(modifiedInstance) ⇒
                // Tell the model about the value change
                modifiedInstance.markModified()
                modifiedInstance.model.markValueChange(nodeInfo, isCalculate)

                // Dispatch extension event to instance
                Dispatch.dispatchEvent(new XXFormsValueChangedEvent(modifiedInstance, nodeInfo, oldValue, newValue))
            case None ⇒
                // Value modified is not in an instance
                // Q: Is the code below the right thing to do?
                containingDocument.getControls.markDirtySinceLastRequest(true)
        }

    private def setValueForNode(node: Node, newValue: String) =
        node match {
            case element: Element                       ⇒ element.clearContent(); if (newValue.nonEmpty) element.setText(newValue)
            case attribute: Attribute                   ⇒ attribute.setValue(newValue)
            case text: Text4j if newValue.nonEmpty      ⇒ text.setText(newValue)
            case text: Text4j                           ⇒ text.getParent.remove(text)
            case pi: ProcessingInstruction              ⇒ pi.setText(newValue)
            case comment: Comment4j                     ⇒ comment.setText(newValue)
            // Should not happen as caller checks for isWritableItem()
            case _ ⇒ throw new IllegalStateException("Setting value on disallowed node type: " + node.getNodeTypeName)
        }

    // Whether the item is an element node.
    def isElement(item: Item) = isNodeType(item, ELEMENT_NODE)

    // Whether the an item is a document node.
    def isDocument(item: Item) = isNodeType(item, DOCUMENT_NODE)
    
    private def isNodeType(item: Item, nodeType: Int) = item match {
        case nodeInfo: NodeInfo if nodeInfo.getNodeKind == nodeType ⇒ true
        case _ ⇒ false
    }

    // Return the given node's first child element
    def firstChildElement(node: NodeInfo): NodeInfo = {
        val iterator = node.iterateAxis(Axis.CHILD)
        var next = iterator.next()
        while (next ne null) {
            next match {
                case child: NodeInfo if child.getNodeKind == ELEMENT_NODE ⇒ return child
                case _ ⇒
            }
            next = iterator.next()
        }
        null
    }

    // For Java callers
    trait NodeVisitor { def visit(nodeInfo: NodeInfo) }
    def visitElementJava(root: NodeInfo, visitor: NodeVisitor) = visitElement(root, visitor.visit)

    // Visit the given element, its attributes, and its children nodes recursively
    // NOTE: Because this can be a hot spot, this is written more Java-like. Make changes carefully.
    def visitElement(e: NodeInfo, visit: NodeInfo ⇒ Unit) {

        visit(e)

        locally {
            val iterator = e.iterateAxis(Axis.ATTRIBUTE)
            var next = iterator.next()
            while (next ne null) {
                // We know that iterateAxis(Axis.ATTRIBUTE) returns only NodeInfo
                visit(next.asInstanceOf[NodeInfo])
                next = iterator.next()
            }
        }

        locally {
            val iterator = e.iterateAxis(Axis.CHILD)
            var next = iterator.next()
            while (next ne null) {
                if (next.isInstanceOf[NodeInfo]) {
                    val node = next.asInstanceOf[NodeInfo]
                    if (node.getNodeKind == ELEMENT_NODE)
                        visitElement(node, visit)
                }
                next = iterator.next()
            }
        }
    }
}
