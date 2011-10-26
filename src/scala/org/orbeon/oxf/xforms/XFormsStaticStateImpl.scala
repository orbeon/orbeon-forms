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
package org.orbeon.oxf.xforms

import analysis._
import collection.JavaConversions._
import org.dom4j.io.DocumentSource
import org.orbeon.oxf.xforms.processor.XFormsServer
import org.orbeon.oxf.xml._
import dom4j.{LocationDocumentResult, Dom4jUtils, LocationData}
import org.orbeon.saxon.dom4j.DocumentWrapper
import org.orbeon.oxf.xml.XMLConstants._
import java.util.{Map => JMap, Set => JSet}
import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.xforms.{XFormsProperties => P}
import org.orbeon.oxf.common.{OXFException, Version}
import org.orbeon.oxf.pipeline.api.XMLReceiver
import org.xml.sax.Attributes
import org.orbeon.oxf.util.{NumberUtils, LoggerFactory, XPathCache}
import org.orbeon.oxf.xforms.XFormsStaticStateImpl.StaticStateDocument
import org.dom4j.Document
import xbl.XBLBindingsBase
import xbl.XBLBindingsBase.Scope

class XFormsStaticStateImpl(val encodedState: String, val digest: String, val startScope: XBLBindingsBase.Scope,
                            metadata: Metadata, staticStateDocument: StaticStateDocument)
    extends XFormsStaticState {

    val getIndentedLogger = XFormsContainingDocument.getIndentedLogger(XFormsStaticStateImpl.logger, XFormsServer.getLogger, XFormsStaticStateImpl.LOGGING_CATEGORY)
    val locationData = staticStateDocument.locationData
    val documentWrapper = new DocumentWrapper(Dom4jUtils.createDocument, null, XPathCache.getGlobalConfiguration)

    // Create top-level part once vals are all initialized
    val topLevelPart = new PartAnalysisImpl(this, None, startScope, metadata, staticStateDocument)

    // Analyze top-level part
    topLevelPart.analyze()

    // Properties
    lazy val allowedExternalEvents = getProperty[String](P.EXTERNAL_EVENTS_PROPERTY) match {
        case s: String => s split """\s+""" toSet
        case _ => Set.empty[String]
    }
    lazy val isNoscript = Version.instance.isPEFeatureEnabled(getProperty[Boolean](P.NOSCRIPT_PROPERTY) && getBooleanProperty(P.NOSCRIPT_SUPPORT_PROPERTY), P.NOSCRIPT_PROPERTY)
    lazy val isXPathAnalysis = Version.instance.isPEFeatureEnabled(getProperty[Boolean](P.XPATH_ANALYSIS_PROPERTY), P.XPATH_ANALYSIS_PROPERTY)
    lazy val isHTMLDocument = staticStateDocument.isHTMLDocument

    def isCacheDocument = staticStateDocument.isCacheDocument
    def isClientStateHandling = staticStateDocument.isClientStateHandling
    def isServerStateHandling = staticStateDocument.isServerStateHandling

    def isKeepAnnotatedTemplate = isNoscript || metadata.hasTopLevelMarks

    def getProperty[T](propertyName: String): T = staticStateDocument.getProperty[T](propertyName)

    // Legacy methods
    def getAllowedExternalEvents: JSet[String] = allowedExternalEvents
    def getNonDefaultProperties: JMap[String, AnyRef] =  staticStateDocument.nonDefaultProperties
    def getStringProperty(propertyName: String) = getProperty[String](propertyName)
    def getBooleanProperty(propertyName: String) = getProperty[Boolean](propertyName)
    def getIntegerProperty(propertyName: String) = getProperty[Int](propertyName)

    // Delegation to top-level part
    def dumpAnalysis() = topLevelPart.dumpAnalysis()
    def toXML(helper: ContentHandlerHelper) = topLevelPart.toXML(helper)
}

object XFormsStaticStateImpl {

    val LOGGING_CATEGORY = "analysis"
    val logger = LoggerFactory.createLogger(classOf[XFormsStaticState])
    val DIGEST_LENGTH = 32
    val BASIC_NAMESPACE_MAPPING =
        new NamespaceMapping(Map(
            XFORMS_PREFIX -> XFORMS_NAMESPACE_URI,
            XXFORMS_PREFIX -> XXFORMS_NAMESPACE_URI,
            XML_EVENTS_PREFIX -> XML_EVENTS_NAMESPACE_URI,
            XHTML_PREFIX -> XMLConstants.XHTML_NAMESPACE_URI
        ))

    /**
     * Create static state from an encoded version. This is used when restoring a static state from a serialized form.
     *
     * @param digest        digest of the static state if known
     * @param encodedState  encoded static state (digest + serialized XML)
     */
    def restore(digest: String, encodedState: String) = {

        // Decode encodedState
        val staticStateDocument = XFormsUtils.decodeXML(encodedState)

        // Recompute namespace mappings and ids
        val metadata = {
            val idGenerator = {
                val currentIdElement = staticStateDocument.getRootElement.element(XFormsExtractorContentHandler.LAST_ID_QNAME)
                assert(currentIdElement != null)
                val lastId = XFormsUtils.getElementStaticId(currentIdElement)
                assert(lastId != null)
                new IdGenerator(Integer.parseInt(lastId))
            }
            new Metadata(idGenerator)
        }
        TransformerUtils.sourceToSAX(new DocumentSource(staticStateDocument), new XFormsAnnotatorContentHandler(metadata))

        new XFormsStaticStateImpl(encodedState, digest, new XBLBindingsBase.Scope(null, ""), metadata, new StaticStateDocument(staticStateDocument))
    }

    /**
     * Create analyzed static state for the given static state document.
     */
    def create(staticStateDocument: Document, digest: String, metadata: Metadata): XFormsStaticStateImpl =
        create(staticStateDocument, null, digest, metadata, new XBLBindingsBase.Scope(null, ""))

    /**
     * Create analyzed static state for the given XForms document.
     *
     * Used by unit tests.
     */
    def create(formDocument: Document): XFormsStaticStateImpl = {
        val startScope = new XBLBindingsBase.Scope(null, "")
        create(formDocument, startScope, create(_, null, _, _, startScope))._2
    }

    /**
     * Create template and analyzed part for the given XForms document.
     */
    def createPart(staticState: XFormsStaticState, parent: PartAnalysis, formDocument: Document, startScope: XBLBindingsBase.Scope) =
        create(formDocument, startScope, (staticStateDocument: Document, digest: String, metadata: Metadata) => {
            val part = new PartAnalysisImpl(staticState, Some(parent), startScope, metadata, new StaticStateDocument(staticStateDocument))
            part.analyze()
            part
        })

    private def create[T](formDocument: Document, startScope: Scope, c: (Document, String, Metadata) => T): (SAXStore, T) = {
        val identity = TransformerUtils.getIdentityTransformerHandler

        val documentResult = new LocationDocumentResult
        identity.setResult(documentResult)

        val metadata = new Metadata
        val digestContentHandler = new XMLUtils.DigestContentHandler("MD5")
        val annotatedTemplate = new SAXStore

        val prefix = startScope.getFullPrefix

        // Annotator with prefix
        class Annotator(extractorReceiver: XMLReceiver) extends XFormsAnnotatorContentHandler(annotatedTemplate, extractorReceiver, metadata) {
            override def addNamespaces(id: String) = super.addNamespaces(prefix + id)
            override def addMark(id: String, mark: SAXStore#Mark) = super.addMark(prefix + id, mark)
        }

        // Extractor with prefix
        class Extractor(xmlReceiver: XMLReceiver) extends XFormsExtractorContentHandler(xmlReceiver, metadata) {
            override def startXFormsOrExtension(uri: String, localname: String, qName: String, attributes: Attributes) {
                val staticId = attributes.getValue("id")
                if (staticId ne null) {
                    val prefixedId = prefix + staticId
                    if (metadata.getNamespaceMapping(prefixedId) ne null) {
                        if (startScope.idMap.containsKey(staticId))
                            throw new OXFException("Duplicate id found for static id: " + staticId)
                        startScope.idMap += (staticId -> prefixedId)
                    }
                }
            }
        }

        // Read the input through the annotator and gather namespace mappings
        TransformerUtils.writeDom4j(formDocument, new Annotator(new Extractor(new TeeXMLReceiver(identity, digestContentHandler))))

        // Get static state document and create static state object
        val staticStateDocument = documentResult.getDocument
        val digest = NumberUtils.toHexString(digestContentHandler.getResult)

        (annotatedTemplate, c(staticStateDocument, digest, metadata))
    }

    def create(staticStateDocument: Document, encodedState: String, digest: String, metadata: Metadata, startScope: XBLBindingsBase.Scope): XFormsStaticStateImpl = {

        // Encode state if not already available
        // NOTE: We do compress the result as we think we can afford this for the static state (probably not so for the dynamic state)
        val doc = new StaticStateDocument(staticStateDocument)
        val newEncodedState = if (encodedState eq null) XFormsUtils.encodeXML(staticStateDocument, true,
            if (doc.isClientStateHandling) P.getXFormsPassword else null, true) else encodedState

        new XFormsStaticStateImpl(newEncodedState, digest, startScope, metadata, doc)
    }

    // Class to represent and take apart a static state document initially in XML form (resulting from the extractor)
    class StaticStateDocument(private val staticStateDocument: Document) {

        private def staticStateElement = staticStateDocument.getRootElement

        require(staticStateDocument ne null)

        // TODO: if staticStateDocument contains XHTML document, get controls and models from there

        // Extract location data
        val locationData = staticStateElement.attributeValue("system-id") match {
            case systemId: String => new LocationData(systemId, staticStateElement.attributeValue("line").toInt, staticStateElement.attributeValue("column").toInt)
            case _ => null
        }

        // Extract properties
        // NOTE: XFormsExtractorContentHandler takes care of propagating only non-default properties
        val nonDefaultProperties = {
            for {
                element <- Dom4jUtils.elements(staticStateElement, STATIC_STATE_PROPERTIES_QNAME)
                attribute <- Dom4jUtils.attributes(element)
                propertyName = attribute.getName
                propertyValue = P.parseProperty(propertyName, attribute.getValue)
            } yield
                (propertyName, propertyValue)
        } toMap

        // Find all top-level controls
        val controlElements =
            for {
                element <- Dom4jUtils.elements(staticStateElement)
                qName = element.getQName
                if qName != XFORMS_MODEL_QNAME
                if qName != XHTML_HTML_QNAME
                if XBL_NAMESPACE_URI != element.getNamespaceURI
                if element.getNamespaceURI != null && element.getNamespaceURI != ""
            } yield
                Dom4jUtils.createDocumentCopyParentNamespaces(element, false).getRootElement

        // Find all top-level models
        val modelDocuments = for (element <- Dom4jUtils.elements(staticStateElement, XFORMS_MODEL_QNAME))
            yield Dom4jUtils.createDocumentCopyParentNamespaces(element, false)

        // Inline XBL documents
        val xblDocuments = for (element <- Dom4jUtils.elements(staticStateElement, XBL_XBL_QNAME))
            yield Dom4jUtils.createDocumentCopyParentNamespaces(element, false)

        // Get a property by name
        def getProperty[T](propertyName: String): T =
            nonDefaultProperties.getOrElse(propertyName, {
                val definition = P.getPropertyDefinition(propertyName)
                if (definition ne null) definition.getDefaultValue else null
            }).asInstanceOf[T]

        def isCacheDocument = getProperty[Boolean](P.CACHE_DOCUMENT_PROPERTY)
        def isClientStateHandling = getProperty[String](P.STATE_HANDLING_PROPERTY) == P.STATE_HANDLING_CLIENT_VALUE
        def isServerStateHandling = getProperty[String](P.STATE_HANDLING_PROPERTY) == P.STATE_HANDLING_SERVER_VALUE
        
        val isHTMLDocument  = Option(staticStateElement.attributeValue("is-html")) exists (_ == "true")
    }
}