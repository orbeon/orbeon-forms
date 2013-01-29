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
import events.{XXFormsInstanceInvalidate, XXFormsActionErrorEvent, XFormsDeleteEvent, XFormsInsertEvent}
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import org.orbeon.oxf.xml.dom4j.LocationData
import org.orbeon.saxon.dom4j.DocumentWrapper
import javax.xml.transform.stream.StreamResult
import scala.collection.JavaConverters._
import org.orbeon.oxf.common.OXFException
import state.InstanceState
import org.orbeon.oxf.xforms.XFormsServerSharedInstancesCache.Loader
import org.orbeon.saxon.om.{VirtualNode, DocumentInfo, Item}
import org.orbeon.oxf.util._

// Caching information associated with an instance loaded with xxf:cache="true"
case class InstanceCaching(
        timeToLive: Long,
        handleXInclude: Boolean,
        sourceURI: String,
        requestBodyHash: Option[String]) {
    
    require(sourceURI ne null, """Only XForms instances externally loaded through the src attribute may have xxf:cache="true".""")

    def debugPairs = Seq(
        "timeToLive"      → timeToLive.toString,
        "handleXInclude"  → handleXInclude.toString,
        "sourceURI"       → sourceURI,
        "requestBodyHash" → requestBodyHash.orNull
    )
    
    def writeAttributes(att: (String, String) ⇒ Unit) {
        att("cache", "true")
        if (timeToLive >= 0) att("ttl", timeToLive.toString)
        if (handleXInclude)  att("xinclude", "true")
        att("source-uri", sourceURI)
        requestBodyHash foreach (att("request-body-hash", _))
    }
}

object InstanceCaching {

    // Not using "apply" as that causes issues for Java callers
    def fromValues(timeToLive: Long, handleXInclude: Boolean, sourceURI: String, requestBodyHash: String): InstanceCaching =
        InstanceCaching(timeToLive, handleXInclude, sourceURI, Option(requestBodyHash))

    // Not using "apply" as that causes issues for Java callers
    def fromInstance(instance: Instance, sourceURI: String, requestBodyHash: String): InstanceCaching =
        InstanceCaching(instance.timeToLive, instance.handleXInclude, sourceURI, Option(requestBodyHash))
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
        private var _modified: Boolean,                         // whether the instance was modified
        var valid: Boolean)                                     // whether the instance was valid as of the last revalidation
    extends ListenersTrait
    with XFormsEventObserver
    with Logging {

    require(! (_readonly && _documentInfo.isInstanceOf[VirtualNode]))

    def containingDocument = parent.containingDocument

    // Getters
    def instanceCaching = _instanceCaching
    def documentInfo = _documentInfo
    def readonly = _readonly
    def modified = _modified

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

    def exposeXPathTypes = instance.exposeXPathTypes
    def isSchemaValidation = instance.isSchemaValidation && ! _readonly

    // Don't serialize if the instance is inline and hasn't been modified
    // NOTE: If the instance is cacheable, its metadata gets serialized, but not it's XML content
    def mustSerialize = ! (instance.useInlineContent && ! _modified)

    // Return the model that contains this instance
    def model = parent

    def instanceRoot = DataModel.firstChildElement(_documentInfo)

    def getId = instance.staticId
    def getPrefixedId = XFormsUtils.getPrefixedId(getEffectiveId)
    def getEffectiveId = XFormsUtils.getRelatedEffectiveId(parent.getEffectiveId, instance.staticId)

    def scope = model.getStaticModel.scope
    def container = model.container

    def getLocationData =
        if (_documentInfo.isInstanceOf[DocumentWrapper])
            XFormsUtils.getNodeLocationData(underlyingDocumentOrNull.getRootElement)
        else
            new LocationData(_documentInfo.getSystemId, _documentInfo.getLineNumber, -1)

    def parentEventObserver: XFormsEventObserver = model

    def performDefaultAction(event: XFormsEvent) =
        event match {
            case ev: XXFormsInstanceInvalidate ⇒
                implicit val indentedLogger = event.containingDocument.getIndentedLogger(XFormsModel.LOGGING_CATEGORY)
                _instanceCaching match {
                    case Some(instanceCaching) ⇒
                        XFormsServerSharedInstancesCache.remove(indentedLogger, instanceCaching.sourceURI, null, instanceCaching.handleXInclude)
                    case None ⇒
                        warn("xxforms-instance-invalidate event dispatched to non-cached instance", Seq("instance id" → getEffectiveId))

                }
            case ev: XXFormsActionErrorEvent ⇒
                XFormsError.handleNonFatalActionError(this, ev.throwable)
            case _ ⇒ 
        }

    def performTargetAction(event: XFormsEvent) =
        event match {
            case insertEvent: XFormsInsertEvent ⇒
                // New nodes were just inserted
    
                // As per XForms 1.1, this is where repeat indexes must be adjusted, and where new repeat items must be
                // inserted.
    
                // Find affected repeats
                val insertedNodes = insertEvent.insertedItems
    
                //didInsertNodes = insertedNodes.size() != 0
                
                // Find affected repeats and update their node-sets and indexes
                val controls = container.getContainingDocument.getControls
                updateRepeatNodesets(controls, insertedNodes)
            case deleteEvent: XFormsDeleteEvent ⇒
                // New nodes were just deleted
                if (deleteEvent.deletedNodes.nonEmpty) {
                    // Find affected repeats and update them
                    val controls = container.getContainingDocument.getControls
                    updateRepeatNodesets(controls, null)
                }
            case _ ⇒
        }

    private def updateRepeatNodesets(controls: XFormsControls, insertedNodes: Seq[Item]) {
        val repeatControlsMap = controls.getCurrentControlTree.getRepeatControls.asScala
        if (repeatControlsMap.nonEmpty) {
            val instanceScope = container.getPartAnalysis.scopeForPrefixedId(getPrefixedId)
            
            // NOTE: Copy into List as the list of repeat controls may change within updateNodesetForInsertDelete()
            val repeatControls = repeatControlsMap.values.toList
            for {
                repeatControl ← repeatControls
                // Get a new reference to the control, in case it is no longer present in the tree due to earlier updates
                newRepeatControl ← Option(controls.getObjectByEffectiveId(repeatControl.getEffectiveId).asInstanceOf[XFormsRepeatControl])
                if newRepeatControl.getResolutionScope == instanceScope
            } yield
                // Only update controls within same scope as modified instance
                // NOTE: This can clearly break with e.g. xxf:instance()
                // NOTE: the control may be non-relevant
                newRepeatControl.updateSequenceForInsertDelete(insertedNodes)
        }
    }

    // Return the instance document as a dom4j Document
    // If the instance is readonly, this returns null. Callers should use instanceRoot() whenever possible.
    def underlyingDocumentOrNull: Document =
        _documentInfo match {
            case virtualNode: VirtualNode ⇒ virtualNode.getUnderlyingNode.asInstanceOf[Document]
            case _ ⇒ null
        }

    // LATER: Measure performance of Dom4jUtils.domToString(instance.getDocument)
    def contentAsString =
        Option(underlyingDocumentOrNull) map
            (TransformerUtils.dom4jToString(_, false)) getOrElse
                TransformerUtils.tinyTreeToString(_documentInfo)

    // Don't allow any external events
    def allowExternalEvent(eventName: String) = false

    // Write the instance document to the specified ContentHandler
    def write(xmlReceiver: XMLReceiver) =
        TransformerUtils.sourceToSAX(_documentInfo, xmlReceiver)

    // Log the instance
    def logContent(indentedLogger: IndentedLogger, message: String): Unit = {
        implicit val logger = indentedLogger
        debug(message, Seq(
            "model effective id"    → parent.getEffectiveId,
            "instance effective id" → getEffectiveId,
            "instance"              → TransformerUtils.tinyTreeToString(instanceRoot)
        ))
    }

    // Print the instance with extra annotation attributes to Console.out. For debug only.
    def debugPrintOut() = {
        val identityTransformerHandler: TransformerXMLReceiver = TransformerUtils.getIdentityTransformerHandler
        identityTransformerHandler.setResult(new StreamResult(Console.out))
        write(identityTransformerHandler)
    }

    // Log the current MIP values applied to this instance
    def debugLogMIPs() = {
        val result = Dom4jUtils.createDocument
        underlyingDocumentOrNull.accept(new VisitorSupport {
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

    // Replace the instance with the given document
    // This includes marking the structural change as well as dispatching events
    def replace(newDocumentInfo: DocumentInfo, dispatch: Boolean = true, instanceCaching: Option[InstanceCaching] = instanceCaching, isReadonly: Boolean = readonly): Unit = {
        // Update the instance (this also marks it as modified)
        update(
            instanceCaching,
            newDocumentInfo,
            isReadonly)

        // Call this directly, since we are not using insert/delete here
        model.markStructuralChange(this)

        val newDocumentRootElement = instanceRoot

        // Dispatch xforms-delete event
        // NOTE: Do NOT dispatch so we are compatible with the regular root element replacement
        // (see below). In the future, we might want to dispatch this, especially if
        // XFormsInsertAction dispatches xforms-delete when removing the root element
        //Dispatch.dispatchEvent(new XFormsDeleteEvent(this, Seq(destinationNodeInfo).asJava, 1));

        // Dispatch xforms-insert event
        // NOTE: use the root node as insert location as it seems to make more sense than pointing to the earlier root element
        if (dispatch)
            Dispatch.dispatchEvent(
                new XFormsInsertEvent(
                    this,
                    Seq[Item](newDocumentRootElement).asJava,
                    null,
                    newDocumentRootElement.getDocumentRoot, "after")
            )
    }
}

object XFormsInstance extends Logging {

    import Instance._

    // Create an initial instance without caching information
    def apply(model: XFormsModel, instance: Instance, documentInfo: DocumentInfo) =
        new XFormsInstance(
            model,
            instance,
            None,
            documentInfo,
            instance.readonly,
            false,
            true)

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

    // Take a non-wrapped DocumentInfo and wrap it if needed
    def wrapDocumentInfo(documentInfo: DocumentInfo, readonly: Boolean, exposeXPathTypes: Boolean) = {
        assert(! documentInfo.isInstanceOf[VirtualNode], "DocumentInfo must not be a VirtualNode, i.e. it must be a native readonly tree like TinyTree")

        // NOTE: We don't honor exposeXPathTypes on readonly instances, anyway they don't support MIPs at this time

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
                        createDocumentInfo(content, instanceState.readonly, instance.exposeXPathTypes))
            }

        model.indexInstance(
            new XFormsInstance(
                model,
                instance,
                caching,
                documentInfo,
                instanceState.readonly,
                instanceState.modified,
                instanceState.valid))
    }
}
