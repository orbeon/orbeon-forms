/**
 *  Copyright (C) 2007 Orbeon, Inc.
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
import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import org.dom4j._
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.xforms.event.XFormsEventTarget
import org.orbeon.oxf.xforms._
import event.events.{XXFormsValueChanged, XXFormsBindingErrorEvent}
import org.w3c.dom.Node.{ELEMENT_NODE, ATTRIBUTE_NODE, TEXT_NODE, DOCUMENT_NODE}
import org.orbeon.oxf.util.{NetUtils, IndentedLogger}
import org.orbeon.saxon.value.AtomicValue
import org.orbeon.saxon.om.{Item, NodeInfo}
import org.orbeon.oxf.xml.{XMLUtils}
import org.orbeon.oxf.xml.XMLConstants._
import XFormsConstants._

object DataModel {

    // Reasons that setting a value on a node can fail
    sealed trait Reason { val message: String }
    case object  ComplexContentReason extends Reason { override val message = "Unable to set value on complex content" }
    case object  ReadonlyNodeReason   extends Reason { override val message = "Unable to set value on read-only node" }
    
    def setValue(
            containingDocument: Option[XFormsContainingDocument],
            indentedLogger: Option[IndentedLogger],
            currentNode: NodeInfo,
            newValue: String,
            dataType: Option[String],
            source: String,
            isCalculate: Boolean,
            error: Reason ⇒ Unit = _ ⇒ ()) = {
        
        assert(currentNode ne null)
        assert(newValue ne null)
        
        val oldValue = getValueForNodeInfo(currentNode)
        val changed = oldValue != newValue

        indentedLogger filter (_.isDebugEnabled) foreach { indentedLogger ⇒

            val instanceEffectiveId =
                containingDocument map
                    (_.getInstanceForNode(currentNode)) map
                        (_.getEffectiveId) getOrElse "N/A"
            
            indentedLogger.logDebug("xforms:setvalue", "setting instance value", "source", source, "value", newValue,
                "changed", changed.toString, "instance", instanceEffectiveId)
        }

        // We take the liberty of not requiring RRR / marking the instance dirty if the value hasn't actually changed
        if (changed) {
            // Actually set the value
            setValueForNodeInfo(currentNode, newValue, dataType, error(_))
            // Handle change notifications
            containingDocument foreach
                (handleValueChange(_, currentNode, oldValue, newValue, isCalculate))
        }

        changed
    }

    // For Java callers
    def jSetValue(containingDocument: XFormsContainingDocument, indentedLogger: IndentedLogger, eventTarget: XFormsEventTarget,
                   currentNode: NodeInfo, valueToSet: String, dataType: String, source: String, isCalculate: Boolean): Boolean = {
        setValue(Option(containingDocument), Option(indentedLogger), currentNode, valueToSet, Option(dataType),
            source, isCalculate, reason ⇒ handleSetValueError(containingDocument, eventTarget, reason))
    }
    
    private def handleValueChange(containingDocument: XFormsContainingDocument, nodeInfo: NodeInfo, oldValue: String, newValue: String, isCalculate: Boolean) =
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

    // Standard behavior for setvalue error
    // Used by MIPs and when setting external values on controls
    private def handleSetValueError(containingDocument: XFormsContainingDocument, eventTarget: XFormsEventTarget, reason: Reason) {
        Option(containingDocument) foreach { containingDocument ⇒
            // Throw an exception or log + add server error
            if (containingDocument.isInitializing || containingDocument.getStaticState.isNoscript) {
                throw new OXFException(reason.message)
            } else {
                val indentedLogger = containingDocument.getIndentedLogger
                indentedLogger.logWarning("", reason.message)
                containingDocument.addServerError(new XFormsContainingDocument.ServerError(reason.message))
            }

            // Dispatch xxforms-binding-error
            containingDocument.dispatchEvent(new XXFormsBindingErrorEvent(containingDocument, eventTarget, reason.message))
        }
    }
    
    /**
     * Set a value on the instance using a NodeInfo and a value.
     *
     * @param nodeInfo              element or attribute NodeInfo to update
     * @param newValue              value to set
     * @param type                  type of the value to set (xs:anyURI or xs:base64Binary), null if none
     * @param error                 function called if the value cannot be set
     */
    def setValueForNodeInfo(nodeInfo: NodeInfo, newValue: String, dataType: Option[String], error: Reason ⇒ Unit = _ ⇒ ()) =
        nodeInfo match {
            case nodeWrapper: NodeWrapper ⇒
                nodeWrapper.getUnderlyingNode match {
                    case node: Node if Dom4jUtils.isSimpleContent(node) ⇒
                        setValueForNode(node, newValue, dataType)
                    case _ ⇒
                        error(ComplexContentReason)
                }
            case _ ⇒
                error(ReadonlyNodeReason)
        }

    /**
     * Set a value on the instance using a Node and a value.
     *
     * @param node              element or attribute Node to update
     * @param newValue          value to set
     * @param dataType          type of the value to set (xs:anyURI or xs:base64Binary), null if none
     */
    private def setValueForNode(node: Node, newValue: String, dataType: Option[String]) {
        val convertedValue =
            dataType match {
                case Some(dataType) ⇒
                    val actualNodeType =
                        Option(InstanceData.getType(node)) map (Dom4jUtils.qNameToExplodedQName(_)) getOrElse
                            DEFAULT_UPLOAD_TYPE_EXPLODED_QNAME

                    convertUploadTypes(newValue, dataType, actualNodeType)
                case _ ⇒
                    newValue
            }

        node match {
            // NOTE: Previously, there was a "first text node rule" which ended up causing problems and was removed.
            case element: Element ⇒ element.setText(convertedValue)
            // "Attribute nodes: The string-value of the attribute is replaced with a string corresponding to the new
            // value."
            case attribute: Attribute ⇒ attribute.setValue(convertedValue)
            // "Text nodes: The text node is replaced with a new one corresponding to the new value."
            // NOTE: As of 2011-11-03, this should not happen as the caller tests for isSimpleContent() which excludes text nodes.
            case text: Text ⇒ text.setText(convertedValue)
            // "Namespace, processing instruction, comment, and the XPath root node: behavior is undefined."
            case _ ⇒ throw new OXFException("Setting value on node other than element, attribute or text is not supported for node type: " + node.getNodeTypeName)
        }
    }

    private val AllowedNodes = Set(ELEMENT_NODE, ATTRIBUTE_NODE, TEXT_NODE) map (_.toInt)

    def getValueForNodeInfo(nodeInfo: NodeInfo) =
        if (AllowedNodes(nodeInfo.getNodeKind))
            // NOTE: In XForms 1.1, all these node types return the string value. Note that previously, there was a
            // "first text node rule" which ended up causing problems and was removed.
            nodeInfo.getStringValue
        else
            // "Namespace, processing instruction, comment, and the XPath root node: behavior is undefined."
            throw new OXFException("Setting value on node other than element, attribute or text is not supported for node type: " + nodeInfo.getNodeKind)

    // Binary types supported for upload, images, etc.
    private val SupportedBinaryTypes =
        Set(XS_BASE64BINARY_QNAME, XS_ANYURI_QNAME, XFORMS_BASE64BINARY_QNAME, XFORMS_ANYURI_QNAME) map
            (qName ⇒ XMLUtils.buildExplodedQName(qName) → qName.getName) toMap

    /**
     * Convert a value used for xforms:upload depending on its type. If the local name of the current type and the new
     * type are the same, return the value as passed. Otherwise, convert to or from anyURI and base64Binary.
     *
     * @param value             value to convert
     * @param currentType       current type as exploded QName
     * @param newType           new type as exploded QName
     * @return converted value, or value passed
     */
    def convertUploadTypes(value: String, currentType: String, newType: String) = {

        def getOrThrow(dataType: String) = SupportedBinaryTypes.getOrElse(dataType,
            throw new UnsupportedOperationException("Unsupported type: " + dataType))

        val currentTypeLocalName = getOrThrow(currentType)
        val newTypeLocalName = getOrThrow(newType)

        if (currentTypeLocalName == newTypeLocalName)
            value
        else if (currentTypeLocalName == "base64Binary")
            // Convert from xs:base64Binary or xforms:base64Binary to xs:anyURI or xforms:anyURI
            NetUtils.base64BinaryToAnyURI(value, NetUtils.REQUEST_SCOPE)
        else
            // Convert from xs:anyURI or xforms:anyURI to xs:base64Binary or xforms:base64Binary
            NetUtils.anyURIToBase64Binary(value)
    }

    /**
     * Return the value of a bound item, whether a NodeInfo or an AtomicValue. If none of those, return null.
     */
    def getBoundItemValue(boundItem: Item) = boundItem match {
        // NOTE: As a special case, we sometimes allow binding to a document node, but consider the value is empty in this case
        case documentNode: NodeInfo if isDocument(documentNode) ⇒ null
        case nodeInfo: NodeInfo ⇒ getValueForNodeInfo(nodeInfo.asInstanceOf[NodeInfo])
        case atomicValue: AtomicValue ⇒ atomicValue.getStringValue
        case _ ⇒ null
    }

    /**
     * Check if an item is an element node.
     */
    def isElement(item: Item) = isNodeType(item, ELEMENT_NODE)

    /**
     * Check if an item is an document node.
     */
    def isDocument(item: Item) = isNodeType(item, DOCUMENT_NODE)
    
    private def isNodeType(item: Item, nodeType: Int) = item match {
        case nodeInfo: NodeInfo if nodeInfo.getNodeKind == nodeType ⇒ true
        case _ ⇒ false
    }
}
