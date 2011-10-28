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
import collection.JavaConverters._
import org.dom4j.io.DocumentSource
import org.orbeon.oxf.xforms.processor.XFormsServer
import org.orbeon.oxf.xml._
import dom4j.{LocationDocumentResult, Dom4jUtils, LocationData}
import org.orbeon.saxon.dom4j.DocumentWrapper
import org.orbeon.oxf.xml.XMLConstants._
import java.util.{List ⇒ JList, Set ⇒ JSet}
import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.xforms.{XFormsProperties ⇒ P}
import org.orbeon.oxf.common.{OXFException, Version}
import org.orbeon.oxf.pipeline.api.XMLReceiver
import org.xml.sax.Attributes
import org.orbeon.oxf.util.{NumberUtils, LoggerFactory, XPathCache}
import org.orbeon.oxf.xforms.XFormsStaticStateImpl.StaticStateDocument
import xbl.Scope
import org.dom4j.{Element, Document}

class XFormsStaticStateImpl(val encodedState: String, val digest: String, val startScope: Scope,
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
        case s: String ⇒ s split """\s+""" toSet
        case _ ⇒ Set.empty[String]
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
    def getNonDefaultProperties: Map[String, AnyRef] =  staticStateDocument.nonDefaultProperties
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
            XFORMS_PREFIX → XFORMS_NAMESPACE_URI,
            XXFORMS_PREFIX → XXFORMS_NAMESPACE_URI,
            XML_EVENTS_PREFIX → XML_EVENTS_NAMESPACE_URI,
            XHTML_PREFIX → XMLConstants.XHTML_NAMESPACE_URI
        ))

    /**
     * Create static state from an encoded version. This is used when restoring a static state from a serialized form.
     *
     * @param digest        digest of the static state if known
     * @param encodedState  encoded static state (digest + serialized XML)
     */
    def restore(digest: String, encodedState: String) = {

        // Decode encodedState
        val staticStateXML = XFormsUtils.decodeXML(encodedState)
        val staticStateDocument = new StaticStateDocument(staticStateXML)

        // Recompute namespace mappings and ids
        val metadata = new Metadata(new IdGenerator(staticStateDocument.lastId))
        TransformerUtils.sourceToSAX(new DocumentSource(staticStateXML), new XFormsAnnotatorContentHandler(metadata))

        new XFormsStaticStateImpl(encodedState, digest, new Scope(null, ""), metadata, staticStateDocument)
    }

    /**
     * Create analyzed static state for the given static state document.
     */
    def create(staticStateDocument: Document, digest: String, metadata: Metadata): XFormsStaticStateImpl =
        create(staticStateDocument, None, digest, metadata, new Scope(null, ""))

    /**
     * Create analyzed static state for the given XForms document.
     *
     * Used by unit tests.
     */
    def create(formDocument: Document): XFormsStaticStateImpl = {
        val startScope = new Scope(null, "")
        create(formDocument, startScope, create(_, None, _, _, startScope))._2
    }

    /**
     * Create template and analyzed part for the given XForms document.
     */
    def createPart(staticState: XFormsStaticState, parent: PartAnalysis, formDocument: Document, startScope: Scope) =
        create(formDocument, startScope, (staticStateDocument: Document, digest: String, metadata: Metadata) ⇒ {
            val part = new PartAnalysisImpl(staticState, Some(parent), startScope, metadata, new StaticStateDocument(staticStateDocument))
            part.analyze()
            part
        })

    private def create[T](formDocument: Document, startScope: Scope, c: (Document, String, Metadata) ⇒ T): (SAXStore, T) = {
        val identity = TransformerUtils.getIdentityTransformerHandler

        val documentResult = new LocationDocumentResult
        identity.setResult(documentResult)

        val metadata = new Metadata
        val digestContentHandler = new XMLUtils.DigestContentHandler("MD5")
        val annotatedTemplate = new SAXStore

        val prefix = startScope.fullPrefix

        // Annotator with prefix
        class Annotator(extractorReceiver: XMLReceiver) extends XFormsAnnotatorContentHandler(annotatedTemplate, extractorReceiver, metadata) {
            override def addNamespaces(id: String) = super.addNamespaces(prefix + id)
            override def addMark(id: String, mark: SAXStore#Mark) = super.addMark(prefix + id, mark)
        }

        // Extractor with prefix
        class Extractor(xmlReceiver: XMLReceiver) extends XFormsExtractorContentHandler(xmlReceiver, metadata) {
            override def startXFormsOrExtension(uri: String, localname: String, qName: String, attributes: Attributes, scope: XFormsConstants.XXBLScope) {
                val staticId = attributes.getValue("id")
                if (staticId ne null) {
                    val prefixedId = prefix + staticId
                    if (metadata.getNamespaceMapping(prefixedId) ne null) {
                        if (startScope.contains(staticId))
                            throw new OXFException("Duplicate id found for static id: " + staticId)
                        startScope += staticId → prefixedId
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

    def create(staticStateXML: Document, encodedState: Option[String], digest: String, metadata: Metadata, startScope: Scope): XFormsStaticStateImpl = {
        val staticStateDocument = new StaticStateDocument(staticStateXML)
        new XFormsStaticStateImpl(staticStateDocument.getEncodedState(encodedState), digest, startScope, metadata, staticStateDocument)
    }

    // Represent the static state XML document resulting from the extractor
    //
    // NOTES:
    //
    // - The underlying document produced by the extractor used to be further transformed to extract various documents.
    //   This is no longer the case and the underlying document should be considered immutable (it would be good if it
    //   was in fact immutable).
    // - The HTML template, when kept (for noscript and when full update marks are present) is stored in the dynamic
    //   state.
    class StaticStateDocument(private val staticStateDocument: Document) {

        private def staticStateElement = staticStateDocument.getRootElement

        require(staticStateDocument ne null)

        // Pointers to nested elements
        def rootControl = staticStateElement.element("root")
        def xblElements = rootControl.elements(XBL_XBL_QNAME).asInstanceOf[JList[Element]].asScala

        // TODO: if staticStateDocument contains XHTML document, get controls and models from there

        // Extract location data
        val locationData = staticStateElement.attributeValue("system-id") match {
            case systemId: String ⇒ new LocationData(systemId, staticStateElement.attributeValue("line").toInt, staticStateElement.attributeValue("column").toInt)
            case _ ⇒ null
        }

        // Return the last id generated
        def lastId: Int = {
            val idElement = staticStateElement.element(XFormsExtractorContentHandler.LAST_ID_QNAME)
            require(idElement ne null)
            val lastId = XFormsUtils.getElementStaticId(idElement)
            require(lastId ne null)
            Integer.parseInt(lastId)
        }

        // Extract properties
        // NOTE: XFormsExtractorContentHandler takes care of propagating only non-default properties
        val nonDefaultProperties = {
            for {
                element ← Dom4jUtils.elements(staticStateElement, STATIC_STATE_PROPERTIES_QNAME)
                attribute ← Dom4jUtils.attributes(element)
                propertyName = attribute.getName
                propertyValue = P.parseProperty(propertyName, attribute.getValue)
            } yield
                (propertyName, propertyValue)
        } toMap

        // Get a property by name
        def getProperty[T](propertyName: String): T =
            nonDefaultProperties.getOrElse(propertyName, {
                val definition = P.getPropertyDefinition(propertyName)
                Option(definition) map (_.defaultValue) orNull
            }).asInstanceOf[T]

        def isCacheDocument = getProperty[Boolean](P.CACHE_DOCUMENT_PROPERTY)
        def isClientStateHandling = getProperty[String](P.STATE_HANDLING_PROPERTY) == P.STATE_HANDLING_CLIENT_VALUE
        def isServerStateHandling = getProperty[String](P.STATE_HANDLING_PROPERTY) == P.STATE_HANDLING_SERVER_VALUE
        
        val isHTMLDocument  = Option(staticStateElement.attributeValue("is-html")) exists (_ == "true")
        
        // Get the encoded static state
        // If an existing state is passed in, use it, otherwise encode from XML, encrypting if necessary.
        // NOTE: We do compress the result as we think we can afford this for the static state (probably not so for the dynamic state).
        def getEncodedState(encodedState: Option[String]) =
            encodedState getOrElse
                (XFormsUtils.encodeXML(staticStateDocument, true, if (isClientStateHandling) P.getXFormsPassword else null, true))
    }
}