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

import analysis.model.Instance
import model.DataModel
import org.dom4j._
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
import scala.collection.JavaConverters._
import org.orbeon.oxf.util.{XPathCache, IndentedLogger}
import org.orbeon.oxf.util.DebugLogger._
import org.orbeon.oxf.common.OXFException
import state.InstanceState
import org.orbeon.oxf.xforms.XFormsServerSharedInstancesCache.Loader
import java.util.{StringTokenizer, List ⇒ JList}
import collection.mutable.HashSet
import org.orbeon.saxon.om.{VirtualNode, DocumentInfo, Item}

// Caching information associated with an instance loaded with xxf:cache="true"
case class InstanceCaching(
        timeToLive: Long,
        handleXInclude: Boolean,
        sourceURI: String,
        requestBodyHash: String) {
    
    require(! isInline, """Only XForms instances externally loaded through the src attribute may have xxforms:cache="true".""")

    def isInline = sourceURI eq null

    def debugPairs = Seq(
        "timeToLive"      → timeToLive.toString,
        "handleXInclude"  → handleXInclude.toString,
        "sourceURI"       → sourceURI,
        "requestBodyHash" → requestBodyHash
    )
    
    def writeAttributes(att: (String, String) ⇒ Unit) {
        att("cache", "true")
        if (timeToLive >= 0)         att("ttl", timeToLive.toString)
        if (handleXInclude)          att("xinclude", "true")
        if (sourceURI ne null)       att("source-uri", sourceURI)
        if (requestBodyHash ne null) att("request-body-hash", requestBodyHash)
    }
}

object InstanceCaching {

    // Not using "apply" as that causes issues for Java callers
    def fromValues(timeToLive: Long, handleXInclude: Boolean, sourceURI: String, requestBodyHash: String): InstanceCaching =
        InstanceCaching(timeToLive, handleXInclude, sourceURI, requestBodyHash)

    // Not using "apply" as that causes issues for Java callers
    def fromInstance(instance: Instance, sourceURI: String, requestBodyHash: String): InstanceCaching =
        InstanceCaching(instance.timeToLive, instance.handleXInclude, sourceURI, requestBodyHash)
}

/**
 * Represent an XForms instance. An instance is made of:
 *
 * - immutable information
 *   - a reference to its parent model
 *   - a reference to its static representation
 * - mutable information
 *   - XML document with the instance content
 *   - information to reload the instance after deserialization if needed
 *   - whether the instance is readonly
 *   - whether the instance has been modified
 */
class XFormsInstance(
        val parent: XFormsModel,                                // concrete parent model
        val instance: Instance,                                 // static instance
        private var _instanceCaching: Option[InstanceCaching],  // information to restore cached instance content
        private var _documentInfo: DocumentInfo,                // fully wrapped document
        private var _readonly: Boolean,                         // whether the instance is readonly (can change upon submission)
        private var _modified: Boolean)                         // whether the instance was modified
    extends ListenersTrait
    with XFormsEventObserver {

    require(! (_readonly && _documentInfo.isInstanceOf[VirtualNode]))

    // Getters
    def instanceCaching = _instanceCaching
    def documentInfo = _documentInfo
    def readonly = _readonly
    def modified = _modified

    // NOTE: `replaced`: Whether the instance was ever replaced. This is useful so that we know whether we can use an
    // instance from the static state or not: if it was ever replaced, then we can't use instance information from the
    // static state.
    // Mark the instance as modified
    // This is used so we can optimize serialization: if an instance is inline and not modified, we don't need to
    // serialize its content
    def markModified() = _modified = true

    // Update the instance upon submission with instance replacement
    def update(instanceCaching: Option[InstanceCaching], documentInfo: DocumentInfo, readonly: Boolean): Unit = {
        _instanceCaching = instanceCaching
        _documentInfo = documentInfo
        _readonly = readonly

        markModified()
    }

    def exposeXPathTypes = instance.isExposeXPathTypes

    // Don't serialize if the instance is inline and hasn't been modified
    // NOTE: If the instance is cacheable, its metadata gets serialized, but not it's XML content
    def mustSerialize = ! (instance.useInlineContent && ! _modified)

    // Return the model that contains this instance
    def model = parent

    def getInstanceRootElementInfo = DataModel.firstChildElement(_documentInfo)

    def getId = instance.staticId
    def getPrefixedId = XFormsUtils.getPrefixedId(getEffectiveId)
    def scope = model.getStaticModel.scope
    def getEffectiveId = XFormsUtils.getRelatedEffectiveId(parent.getEffectiveId, instance.staticId)
    def container = model.container

    def isLaxValidation = (instance.validation eq null) || instance.validation == "lax"
    def isStrictValidation = instance.validation == "strict"
    def isSchemaValidation = (isLaxValidation || isStrictValidation) && ! _readonly

    // Output the instance document to the specified ContentHandler
    def write(xmlReceiver: XMLReceiver) =
        TransformerUtils.sourceToSAX(_documentInfo, xmlReceiver)

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
        if (_documentInfo.isInstanceOf[DocumentWrapper])
            XFormsUtils.getNodeLocationData(getDocument.getRootElement)
        else
            new LocationData(_documentInfo.getSystemId, _documentInfo.getLineNumber, -1)

    def parentEventObserver: XFormsEventObserver = model

    def performDefaultAction(event: XFormsEvent) =
        event.getName match {
            case XFormsEvents.XXFORMS_INSTANCE_INVALIDATE ⇒
                implicit val indentedLogger = event.containingDocument.getIndentedLogger(XFormsModel.LOGGING_CATEGORY)
                // Invalidate instance if it is cached
                _instanceCaching match {
                    case Some(instanceCaching) ⇒
                        XFormsServerSharedInstancesCache.remove(indentedLogger, instanceCaching.sourceURI, null, instanceCaching.handleXInclude)
                    case None ⇒
                        warn("xxforms-instance-invalidate event dispatched to non-cached instance", Seq("instance id" → getEffectiveId))

                }
            case _ ⇒ 
        }

    def performTargetAction(event: XFormsEvent) =
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
            val instanceScope = container.getPartAnalysis.scopeForPrefixedId(getPrefixedId)
            
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
        _documentInfo match {
            case virtualNode: VirtualNode ⇒
                virtualNode.getUnderlyingNode.asInstanceOf[Document]
            case _ ⇒
                null
        }

    // LATER: Measure performance of Dom4jUtils.domToString(instance.getDocument)
    def contentAsString =
        Option(getDocument) map
            (TransformerUtils.dom4jToString(_, false)) getOrElse
                TransformerUtils.tinyTreeToString(_documentInfo)

    def logInstance(indentedLogger: IndentedLogger, message: String): Unit = {
        implicit val logger = indentedLogger
        debug(message, Seq(
            "model effective id"    → parent.getEffectiveId,
            "instance effective id" → getEffectiveId,
            "instance"              → TransformerUtils.tinyTreeToString(getInstanceRootElementInfo)
        ))
    }

    // Don't allow any external events
    def allowExternalEvent(eventName: String) = false
}

object XFormsInstance {

    // Create an initial instance without caching information
    def apply(model: XFormsModel, instance: Instance, documentInfo: DocumentInfo) =
        new XFormsInstance(
            model,
            instance,
            None,
            documentInfo,
            instance.readonly,
            false)

    def createDocumentInfo(documentOrDocumentInfo: AnyRef, exposeXPathTypes: Boolean) = documentOrDocumentInfo match {
        case dom4jDocument: Document    ⇒ wrapDocument(dom4jDocument, exposeXPathTypes)
        case documentInfo: DocumentInfo ⇒ documentInfo
        case _ ⇒ throw new OXFException("Invalid type for instance document: " + documentOrDocumentInfo.getClass.getName)
    }

    def createDocumentInfo(xmlString: String, readonly: Boolean, exposeXPathTypes: Boolean) =
        if (readonly)
            TransformerUtils.stringToTinyTree(XPathCache.getGlobalConfiguration, xmlString, false, true)
        else
            wrapDocument(Dom4jUtils.readDom4j(xmlString), exposeXPathTypes)

    def wrapDocument(document: Document, exposeXPathTypes: Boolean) =
        if (exposeXPathTypes)
            new TypedDocumentWrapper(Dom4jUtils.normalizeTextNodes(document).asInstanceOf[Document], null, XPathCache.getGlobalConfiguration)
        else
            new DocumentWrapper(Dom4jUtils.normalizeTextNodes(document).asInstanceOf[Document], null, XPathCache.getGlobalConfiguration)

    // Take a non-wrapped DocumentInfo and wrap it if needed
    def wrapDocumentInfo(documentInfo: DocumentInfo, readonly: Boolean, exposeXPathTypes: Boolean) = {
        assert(! documentInfo.isInstanceOf[VirtualNode], "DocumentInfo must not be a VirtualNode, i.e. it must be a native readonly tree like TinyTree")
        assert(! (readonly && exposeXPathTypes), "can't expose XPath types on readonly instances")

        if (readonly)
            documentInfo // the optimal case: no copy of the cached document is needed
        else
            wrapDocument(TransformerUtils.tinyTreeToDom4j2(documentInfo), exposeXPathTypes)
    }

    // Restore an instance on the model, given InstanceState
    def restoreInstanceFromState(model: XFormsModel, instanceState: InstanceState, loader: Loader): Unit = {

        implicit val logger = model.indentedLogger

        val instance = model.staticModel.instances(XFormsUtils.getStaticIdFromId(instanceState.effectiveId))

        val (caching, documentInfo) =
            instanceState.cachingOrContent match {
                case Left(caching)  ⇒
                    debug("restoring instance from instance cache", Seq("id" → instanceState.effectiveId))

                    // NOTE: No XInclude supported to read instances with @src for now
                    // TODO: must pass method and request body in case of POST/PUT

                    (Some(caching),
                        XFormsServerSharedInstancesCache.findContentOrLoad(logger, instance, caching, instanceState.readonly, loader))

                case Right(content) ⇒
                    debug("using initialized instance from state", Seq("id" → instanceState.effectiveId))
                    (None,
                        createDocumentInfo(content, instanceState.readonly, instance.isExposeXPathTypes))
            }

        model.indexInstance(
            new XFormsInstance(
                model,
                instance,
                caching,
                documentInfo,
                instanceState.readonly,
                instanceState.modified))
    }

    private def extractDocument(element: Element, excludeResultPrefixes: String): Document = {
        // Extract document and adjust namespaces
        // TODO: Implement exactly as per XSLT 2.0
        // TODO: Must implement namespace fixup, the code below can break serialization
        if (excludeResultPrefixes == "#all") {
            // Special #all
            Dom4jUtils.createDocumentCopyElement(element)
        } else if ((excludeResultPrefixes ne null) && excludeResultPrefixes.trim.nonEmpty) {
            // List of prefixes
            val st = new StringTokenizer(excludeResultPrefixes)
            val prefixesToExclude = new HashSet[String]
            while (st.hasMoreTokens)
                prefixesToExclude += st.nextToken()
            Dom4jUtils.createDocumentCopyParentNamespaces(element, prefixesToExclude.asJava)
        } else {
            // No exclusion
            Dom4jUtils.createDocumentCopyParentNamespaces(element)
        }
    }

    // Extract the document starting at the given root element
    // This always creates a copy of the original sub-tree
    //
    // @readonly         if true, the document returned is a compact TinyTree, otherwise a DocumentWrapper
    // @exposeXPathTypes if true, use a TypedDocumentWrapper
    def extractDocument(element: Element, excludeResultPrefixes: String, readonly: Boolean, exposeXPathTypes: Boolean): DocumentInfo = {

        require(! (readonly && exposeXPathTypes), "can't expose XPath types on readonly content")

        val document = extractDocument(element, excludeResultPrefixes)
        if (readonly)
            TransformerUtils.dom4jToTinyTree(XPathCache.getGlobalConfiguration, document, false)
        else
            wrapDocument(document, exposeXPathTypes)
    }
}
