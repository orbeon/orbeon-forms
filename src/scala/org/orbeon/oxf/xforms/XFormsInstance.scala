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
package org.orbeon.oxf.xforms

import model.DataModel
import org.dom4j._
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.pipeline.api.TransformerXMLReceiver
import org.orbeon.oxf.pipeline.api.XMLReceiver
import org.orbeon.oxf.xforms.control.controls.XFormsRepeatControl
import org.orbeon.oxf.xforms.event._
import org.orbeon.oxf.xforms.event.events.XFormsDeleteEvent
import org.orbeon.oxf.xforms.event.events.XFormsInsertEvent
import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import org.orbeon.oxf.xml.dom4j.LocationData
import org.orbeon.saxon.dom4j.DocumentWrapper
import org.orbeon.saxon.dom4j.TypedDocumentWrapper
import javax.xml.transform.stream.StreamResult
import java.util.{List ⇒ JList}
import scala.collection.JavaConverters._
import org.orbeon.oxf.util.{XPathCache, IndentedLogger}
import org.orbeon.saxon.om.{DocumentInfo, Item}

/**
 * Represent an XForms instance.
 *
 * TODO: Too many uses of null, use Option when callers are converted to Scala.
 */
class XFormsInstance(
        val staticId: String,
        var modelEffectiveId: String,

        val sourceURI: String,  // Option
    
        val cache: Boolean,
        val timeToLive: Long,   // Option
        val requestBodyHash: String, // Option
        
        val readonly: Boolean,
        val validation: String,      // Option
        val handleXInclude: Boolean,
        val exposeXPathTypes: Boolean,

        val documentInfo: DocumentInfo, // Option

        var replaced: Boolean = false)

    extends ListenersTrait with XFormsEventObserver {
    
    if (cache && (sourceURI eq null))
        throw new OXFException("""Only XForms instances externally loaded through the src attribute may have xxforms:cache="true".""")

    def updateModelEffectiveId(modelEffectiveId: String) =
        this.modelEffectiveId = modelEffectiveId

    // NOTE: `replaced`: Whether the instance was ever replaced. This is useful so that we know whether we can use an
    // instance from the static state or not: if it was ever replaced, then we can't use instance information from the
    // static state.
    def setReplaced(replaced: Boolean) =
        this.replaced = replaced

    // Create a mutable version of this instance with the same instance document
    def createMutableInstance =
        new XFormsInstance(
            staticId,
            modelEffectiveId,

            sourceURI,

            cache,
            timeToLive,
            requestBodyHash,

            readonly,
            validation,
            handleXInclude,
            exposeXPathTypes,
            XFormsInstance.wrapDocument(TransformerUtils.tinyTreeToDom4j2(documentInfo), exposeXPathTypes),

            replaced // ???
        )

    // Encode to an XML representation (as of 2012-02-05, used only by unit tests)
    def toXML(serializeDocument: Boolean): Element = {
        val instanceElement = Dom4jUtils.createElement("instance")

        def att(name: String,  value: String) = instanceElement.addAttribute(name, value)

        att("id", staticId)
        att("model-id", modelEffectiveId)

        if (sourceURI ne null) att("source-uri", sourceURI)

        if (cache) att("cache", "true")
        if (timeToLive >= 0) att("ttl", timeToLive.toString)
        if (requestBodyHash ne null) att("request-body-hash", requestBodyHash)

        if (readonly) att("readonly", "true")
        if (validation ne null) att("validation", validation)
        if (handleXInclude) att("xinclude", "true")
        if (exposeXPathTypes) att("types", "true")

        if (replaced) att("replaced", "true")

        if (serializeDocument) {
            val instanceString =
                Option(getDocument) map
                    (TransformerUtils.dom4jToString(_, false)) getOrElse
                        TransformerUtils.tinyTreeToString(documentInfo)

            instanceElement.addText(instanceString)
        }

        instanceElement
    }

    // Don't serialize if readonly, not replaced, and inline
    // NOTE: If the instance is cacheable, its metadata gets serialized, but not it's XML document
    def mustSerialize = ! (readonly && ! replaced && (sourceURI eq null))

    // Return the model that contains this instance
    def getModel(containingDocument: XFormsContainingDocument) =
        containingDocument.getObjectByEffectiveId(modelEffectiveId).asInstanceOf[XFormsModel]

    def getInstanceRootElementInfo = DataModel.firstChildElement(documentInfo)

    def getId = staticId
    def getPrefixedId = XFormsUtils.getPrefixedId(getEffectiveId)
    def getScope(containingDocument: XFormsContainingDocument) = getModel(containingDocument).getStaticModel.scope
    def getEffectiveId = XFormsUtils.getRelatedEffectiveId(modelEffectiveId, staticId)
    def getXBLContainer(containingDocument: XFormsContainingDocument) = getModel(containingDocument).getXBLContainer

    def isLaxValidation = (validation eq null) || validation == "lax"
    def isStrictValidation = validation == "strict"
    def isSchemaValidation = (isLaxValidation || isStrictValidation) && ! readonly

    // Output the instance document to the specified ContentHandler
    def write(xmlReceiver: XMLReceiver) =
        TransformerUtils.sourceToSAX(documentInfo, xmlReceiver)

    // Print the instance with extra annotation attributes to Console.out. For debug only.
    def debugPrintOut() = {
        val identityTransformerHandler: TransformerXMLReceiver = TransformerUtils.getIdentityTransformerHandler
        identityTransformerHandler.setResult(new StreamResult(Console.out))
        write(identityTransformerHandler)
    }

    // Log the current MIP values applied to this instance
    def debugLogMIPs() = {
        val result = Dom4jUtils.createDocument
        getDocument.accept(new VisitorSupport {
            final override def visit(element: Element) = {
                currentElement = rootElement.addElement("element")
                currentElement.addAttribute("qname", element.getQualifiedName)
                currentElement.addAttribute("namespace-uri", element.getNamespaceURI)
                addMIPInfo(currentElement, element)
            }

            final override def visit(attribute: Attribute) = {
                val attributeElement = currentElement.addElement("attribute")
                attributeElement.addAttribute("qname", attribute.getQualifiedName)
                attributeElement.addAttribute("namespace-uri", attribute.getNamespaceURI)
                addMIPInfo(attributeElement, attribute)
            }

            private def addMIPInfo(parentInfoElement: Element, node: Node) = {
                parentInfoElement.addAttribute("readonly", InstanceData.getInheritedReadonly(node).toString)
                parentInfoElement.addAttribute("relevant", InstanceData.getInheritedRelevant(node).toString)
                parentInfoElement.addAttribute("required", InstanceData.getRequired(node).toString)
                parentInfoElement.addAttribute("valid", InstanceData.getValid(node).toString)
                val typeQName = InstanceData.getType(node)
                parentInfoElement.addAttribute("type", Option(typeQName) map (_.getQualifiedName) getOrElse "")
            }

            private val rootElement = result.addElement("mips")
            private var currentElement: Element = null
        })

        XFormsUtils.logDebugDocument("MIPs: ", result)
    }

    def getLocationData =
        if (documentInfo.isInstanceOf[DocumentWrapper])
            XFormsUtils.getNodeLocationData(getDocument.getRootElement)
        else
            new LocationData(documentInfo.getSystemId, documentInfo.getLineNumber, -1)

    def getParentEventObserver(containingDocument: XFormsContainingDocument): XFormsEventObserver =
        getModel(containingDocument)

    def performDefaultAction(event: XFormsEvent) =
        event.getName match {
            case XFormsEvents.XXFORMS_INSTANCE_INVALIDATE ⇒
                val indentedLogger = event.containingDocument.getIndentedLogger(XFormsModel.LOGGING_CATEGORY)
                // Invalidate instance if it is cached
                if (cache)
                    XFormsServerSharedInstancesCache.instance.remove(indentedLogger, sourceURI, null, handleXInclude)
                else
                    indentedLogger.logWarning("", "XForms - xxforms-instance-invalidate event dispatched to non-cached instance", "instance id", getEffectiveId)
            case _ ⇒ 
        }

    def performTargetAction(container: XBLContainer, event: XFormsEvent) =
        event match {
            case insertEvent: XFormsInsertEvent ⇒
                // New nodes were just inserted
    
                // As per XForms 1.1, this is where repeat indexes must be adjusted, and where new repeat items must be
                // inserted.
    
                // Find affected repeats
                val insertedNodes = insertEvent.getInsertedItems
    
                //didInsertNodes = insertedNodes.size() != 0
                
                // Find affected repeats and update their node-sets and indexes
                val controls = container.getContainingDocument.getControls
                updateRepeatNodesets(controls, insertedNodes)
            case deleteEvent: XFormsDeleteEvent ⇒
                // New nodes were just deleted
                val deletedNodes = deleteEvent.deleteInfos
                val didDeleteNodes = deletedNodes.size != 0
                if (didDeleteNodes) {
                    // Find affected repeats and update them
                    val controls = container.getContainingDocument.getControls
                    updateRepeatNodesets(controls, null)
                }
            case _ ⇒
        }

    private def updateRepeatNodesets(controls: XFormsControls, insertedNodes: JList[Item]) {
        val repeatControlsMap = controls.getCurrentControlTree.getRepeatControls
        if (! repeatControlsMap.isEmpty) {
            val instanceScope = getXBLContainer(controls.getContainingDocument).getPartAnalysis.scopeForPrefixedId(getPrefixedId)
            
            // NOTE: Copy into List as the list of repeat controls may change within updateNodesetForInsertDelete()
            val repeatControls = repeatControlsMap.values.asScala.toList
            for {
                repeatControl ← repeatControls
                // Get a new reference to the control, in case it is no longer present in the tree due to earlier updates
                newRepeatControl ← Option(controls.getObjectByEffectiveId(repeatControl.getEffectiveId).asInstanceOf[XFormsRepeatControl])
                if newRepeatControl.getResolutionScope == instanceScope
            } yield
                // Only update controls within same scope as modified instance
                // NOTE: This can clearly break with e.g. xxforms:instance()
                newRepeatControl.updateNodesetForInsertDelete(insertedNodes)
        }
    }

    // Return the instance document as a dom4j Document.
    // NOTE: Should use getInstanceDocumentInfo() whenever possible.
    def getDocument: Document =
        documentInfo match {
            case documentWrapper: DocumentWrapper ⇒
                documentWrapper.getUnderlyingNode.asInstanceOf[Document]
            case _ ⇒
                null
        }

    def logInstance(indentedLogger: IndentedLogger, message: String) =
        if (indentedLogger.isDebugEnabled)
            indentedLogger.logDebug("", message, "effective model id", modelEffectiveId, "effective instance id", getEffectiveId, "instance", TransformerUtils.tinyTreeToString(getInstanceRootElementInfo))

    // Don't allow any external events
    def allowExternalEvent(eventName: String) = false
}

object XFormsInstance {

    def isReadonlyHint(element: Element) =
        element.attributeValue(XFormsConstants.XXFORMS_READONLY_ATTRIBUTE_QNAME) == "true"

    def isCacheHint(element: Element) =
        element.attributeValue(XFormsConstants.XXFORMS_CACHE_QNAME) == "true"

    def getTimeToLive(element: Element) = {
        val timeToLiveValue = element.attributeValue(XFormsConstants.XXFORMS_TIME_TO_LIVE_QNAME)
        Option(timeToLiveValue) map (_.toLong) getOrElse -1L
    }

    def createDocumentInfo(xmlString: String, readonly: Boolean, exposeXPathTypes: Boolean) =
        if (readonly)
            TransformerUtils.stringToTinyTree(XPathCache.getGlobalConfiguration, xmlString, false, true)
        else
            XFormsInstance.wrapDocument(Dom4jUtils.readDom4j(xmlString), exposeXPathTypes)

    def wrapDocument(instanceDocument: Document, exposeXPathTypes: Boolean) =
        if (exposeXPathTypes)
            new TypedDocumentWrapper(Dom4jUtils.normalizeTextNodes(instanceDocument).asInstanceOf[Document], null, XPathCache.getGlobalConfiguration)
        else
            new DocumentWrapper(Dom4jUtils.normalizeTextNodes(instanceDocument).asInstanceOf[Document], null, XPathCache.getGlobalConfiguration)
}
