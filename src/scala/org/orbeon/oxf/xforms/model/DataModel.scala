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

import org.orbeon.saxon.dom4j.NodeWrapper
import org.dom4j._
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.xforms.event.XFormsEventTarget
import org.orbeon.oxf.xforms._
import event.events.{XXFormsBindingErrorEvent, XXFormsValueChanged}
import org.w3c.dom.Node.{ELEMENT_NODE, ATTRIBUTE_NODE, TEXT_NODE, DOCUMENT_NODE}
import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.saxon.value.AtomicValue
import org.orbeon.saxon.om.{Item, NodeInfo}
import org.orbeon.oxf.xml.dom4j.{LocationData, Dom4jUtils}

/**
 * Represent access to the data model via the NodeInfo abstraction.
 *
 * This covers setting and getting values.
 */
object DataModel {

    // Reasons that setting a value on a node can fail
    sealed trait Reason { val message: String }
    case object  ComplexContentReason extends Reason { override val message = "Unable to set value on complex content" }
    case object  ReadonlyNodeReason   extends Reason { override val message = "Unable to set value on read-only node" }

    private val AllowedNodeTypesForGetValue = Set(ELEMENT_NODE, ATTRIBUTE_NODE, TEXT_NODE) map (_.toInt)

    /**
     * Return the value of a bound item, whether a NodeInfo or an AtomicValue. If disallowed, return null.
     */
    def getValue(item: Item) = item match {
        // XForms 1.1: "the XPath root node: an xforms-binding-exception occurs"
        // NOTE: We do allow binding to a document node, but consider the value is empty when read.
        case documentNode: NodeInfo if isDocument(documentNode) ⇒ null
        // Element, attribute, and text nodes.
        case node: NodeInfo if AllowedNodeTypesForGetValue(node.getNodeKind) ⇒ node.getStringValue
        // Atomic values
        case atomicValue: AtomicValue ⇒ atomicValue.getStringValue
        // XForms 1.1: "Namespace, processing instruction, and comment nodes: behavior is undefined (implementation-dependent)."
        case _ ⇒ null
    }

    /**
     * Set a value on the instance using a NodeInfo and a value.
     *
     * @param nodeInfo              element or attribute NodeInfo to update
     * @param newValue              value to set
     * @param dataType              type of the value to set (xs:anyURI or xs:base64Binary), null if none
     * @param onSuccess             function called if the value was set
     * @param onError               function called if the value was not set
     *
     * Return true if the value was set.
     */
    def setValue(
            nodeInfo: NodeInfo,
            newValue: String,
            onSuccess: () ⇒ Unit = () ⇒ (),
            onError: Reason ⇒ Unit = _ ⇒ ()) = {

        assert(nodeInfo ne null)
        assert(newValue ne null)

        nodeInfo match {
            case nodeWrapper: NodeWrapper ⇒
                nodeWrapper.getUnderlyingNode match {
                    case node: Node if Dom4jUtils.isSimpleContent(node) ⇒
                        setValueForNode(node, newValue)
                        onSuccess()
                        true
                    case _ ⇒
                        onError(ComplexContentReason)
                        false
                }
            case _ ⇒
                onError(ReadonlyNodeReason)
                false
        }
    }

    /**
     * Same as setValue but only attempts to set the value if the new value is different from the old value.
     *
     * Return true if the value was changed.
     */
    def setValueIfChanged(
            nodeInfo: NodeInfo,
            newValue: String,
            onSuccess: String ⇒ Unit = _ ⇒ (),
            onError: Reason ⇒ Unit = _ ⇒ ()) = {
        
        assert(nodeInfo ne null)
        assert(newValue ne null)
        
        val oldValue = getValue(nodeInfo)
        val doUpdate = oldValue != newValue

        // Do not require RRR / mark the instance dirty if the value hasn't actually changed
        doUpdate &&
            setValue(nodeInfo, newValue, () ⇒ onSuccess(oldValue), onError(_))
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
            containingDocument: XFormsContainingDocument,
            indentedLogger: IndentedLogger,
            eventTarget: XFormsEventTarget,
            locationData: LocationData,
            nodeInfo: NodeInfo,
            valueToSet: String,
            source: String,
            isCalculate: Boolean) = {

        assert(containingDocument ne null)
        assert(indentedLogger ne null)

        setValueIfChanged(nodeInfo, valueToSet,
            logAndNotifyValueChange(containingDocument, indentedLogger, source, nodeInfo, _, valueToSet, isCalculate),
            reason ⇒ containingDocument.dispatchEvent(new XXFormsBindingErrorEvent(containingDocument, eventTarget, locationData, reason)))
    }

    // Standard success behavior: log and notify
    def logAndNotifyValueChange(containingDocument: XFormsContainingDocument, indentedLogger: IndentedLogger, source: String, nodeInfo: NodeInfo, oldValue: String, newValue: String, isCalculate: Boolean) = {
        logValueChange(indentedLogger, source, oldValue,  newValue, findInstanceEffectiveId(containingDocument, nodeInfo))
        notifyValueChange(containingDocument, nodeInfo, oldValue, newValue, isCalculate)
    }

    private def findInstanceEffectiveId(containingDocument: XFormsContainingDocument, nodeInfo: NodeInfo) =
        Option(containingDocument.getInstanceForNode(nodeInfo)) map (_.getEffectiveId)
    
    private def logValueChange(indentedLogger: IndentedLogger, source: String, oldValue: String, newValue: String, instanceEffectiveId: Option[String]) =
        if (indentedLogger.isDebugEnabled)
            indentedLogger.logDebug("xforms:setvalue", "setting instance value", "source", source,
                "old value", oldValue, "new value", newValue,
                "instance", instanceEffectiveId getOrElse "N/A")

    private def notifyValueChange(containingDocument: XFormsContainingDocument, nodeInfo: NodeInfo, oldValue: String, newValue: String, isCalculate: Boolean) =
        Option(containingDocument.getInstanceForNode(nodeInfo)) match {
            case Some(modifiedInstance) ⇒
                // Tell the model about the value change
                modifiedInstance.getModel(containingDocument).markValueChange(nodeInfo, isCalculate)

                // Dispatch extension event to instance
                val modifiedContainer = modifiedInstance.getXBLContainer(containingDocument)
                modifiedContainer.dispatchEvent(new XXFormsValueChanged(containingDocument, modifiedInstance, nodeInfo, oldValue, newValue))
            case None ⇒
                // Value modified is not in an instance
                // Q: Is the code below the right thing to do?
                containingDocument.getControls.markDirtySinceLastRequest(true)
        }

    private def setValueForNode(node: Node, newValue: String) =
        node match {
            // NOTE: Previously, there was a "first text node rule" which ended up causing problems and was removed.
            case element: Element ⇒ element.setText(newValue)
            // "Attribute nodes: The string-value of the attribute is replaced with a string corresponding to the new
            // value."
            case attribute: Attribute ⇒ attribute.setValue(newValue)
            // "Text nodes: The text node is replaced with a new one corresponding to the new value."
            // NOTE: As of 2011-11-03, this should not happen as the caller tests for isSimpleContent() which excludes text nodes.
            case text: Text ⇒ text.setText(newValue)
            // "Namespace, processing instruction, comment, and the XPath root node: behavior is undefined."
            case _ ⇒ throw new OXFException("Setting value on node other than element, attribute or text is not supported for node type: " + node.getNodeTypeName)
        }

    /**
     * Whether the item is an element node.
     */
    def isElement(item: Item) = isNodeType(item, ELEMENT_NODE)

    /**
     * Whether the an item is a document node.
     */
    def isDocument(item: Item) = isNodeType(item, DOCUMENT_NODE)
    
    private def isNodeType(item: Item, nodeType: Int) = item match {
        case nodeInfo: NodeInfo if nodeInfo.getNodeKind == nodeType ⇒ true
        case _ ⇒ false
    }
}
