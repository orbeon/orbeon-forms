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
import org.orbeon.oxf.xml._
import dom4j.{LocationDocumentResult, Dom4jUtils, LocationData}
import org.orbeon.oxf.xml.XMLConstants._
import java.util.{List ⇒ JList, Set ⇒ JSet, Map ⇒ JMap}
import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.xforms.{XFormsProperties ⇒ P}
import org.orbeon.oxf.common.{OXFException, Version}
import org.orbeon.oxf.pipeline.api.XMLReceiver
import org.xml.sax.Attributes
import org.orbeon.oxf.xforms.XFormsStaticStateImpl.StaticStateDocument
import state.AnnotatedTemplate
import xbl.Scope
import org.dom4j.{Element, Document}
import org.orbeon.oxf.util.{StringReplacer, NumberUtils}
import org.orbeon.oxf.util.ScalaUtils.{stringOptionToSet, nonEmptyOrNone}

class XFormsStaticStateImpl(
        val encodedState: String,
        val digest: String,
        val startScope: Scope,
        metadata: Metadata,
        val template: Option[AnnotatedTemplate],
        val staticStateDocument: StaticStateDocument)
    extends XFormsStaticState {

    require(encodedState ne null)
    require(digest ne null)

    val getIndentedLogger = Loggers.getIndentedLogger("analysis")
    val locationData = staticStateDocument.locationData

    // Create top-level part once vals are all initialized
    val topLevelPart = new PartAnalysisImpl(this, None, startScope, metadata, staticStateDocument)

    // Analyze top-level part
    topLevelPart.analyze()

    // Delegation to top-level part
    def dumpAnalysis() = topLevelPart.dumpAnalysis()
    def toXML(helper: ContentHandlerHelper) = topLevelPart.toXML(helper)

    // Properties
    lazy val allowedExternalEvents = stringOptionToSet(Option(getProperty[String](P.EXTERNAL_EVENTS_PROPERTY)))
    lazy val isNoscript            = XFormsStaticStateImpl.isNoscript(staticStateDocument.nonDefaultProperties)
    lazy val isHTMLDocument        = staticStateDocument.isHTMLDocument
    lazy val isXPathAnalysis       = Version.instance.isPEFeatureEnabled(getProperty[Boolean](P.XPATH_ANALYSIS_PROPERTY), P.XPATH_ANALYSIS_PROPERTY)

    lazy val sanitizeInput    = StringReplacer(getProperty[String](P.SANITIZE_PROPERTY))(getIndentedLogger)

    def isCacheDocument       = staticStateDocument.isCacheDocument
    def isClientStateHandling = staticStateDocument.isClientStateHandling
    def isServerStateHandling = staticStateDocument.isServerStateHandling

    // Whether to keep the annotated template in the document itself (dynamic state)
    // See: http://wiki.orbeon.com/forms/doc/contributor-guide/xforms-state-handling#TOC-Handling-of-the-HTML-template
    def isDynamicNoscriptTemplate = isNoscript && ! template.isDefined

    def getProperty[T](propertyName: String): T = staticStateDocument.getProperty[T](propertyName)

    // Legacy methods
    def getAllowedExternalEvents: JSet[String]       = allowedExternalEvents
    def getNonDefaultProperties: Map[String, AnyRef] = staticStateDocument.nonDefaultProperties
    def getStringProperty(propertyName: String)      = getProperty[String](propertyName)
    def getBooleanProperty(propertyName: String)     = getProperty[Boolean](propertyName)
    def getIntegerProperty(propertyName: String)     = getProperty[Int](propertyName)
}

object XFormsStaticStateImpl {

    val BASIC_NAMESPACE_MAPPING =
        new NamespaceMapping(Map(
            XFORMS_PREFIX        → XFORMS_NAMESPACE_URI,
            XFORMS_SHORT_PREFIX  → XFORMS_NAMESPACE_URI,
            XXFORMS_PREFIX       → XXFORMS_NAMESPACE_URI,
            XXFORMS_SHORT_PREFIX → XXFORMS_NAMESPACE_URI,
            XML_EVENTS_PREFIX    → XML_EVENTS_NAMESPACE_URI,
            XHTML_PREFIX         → XMLConstants.XHTML_NAMESPACE_URI,
            XHTML_SHORT_PREFIX   → XMLConstants.XHTML_NAMESPACE_URI
        ))

    // Create static state from an encoded version. This is used when restoring a static state from a serialized form.
    // NOTE: `digest` can be None when using client state, if all we have are serialized static and dynamic states.
    def restore(digest: Option[String], encodedState: String) = {

        val staticStateDocument = new StaticStateDocument(XFormsUtils.decodeXML(encodedState))

        // Restore template
        val template = staticStateDocument.template map AnnotatedTemplate.apply

        // Restore metadata
        val metadata = Metadata(staticStateDocument, template)

        new XFormsStaticStateImpl(
            encodedState,
            staticStateDocument.getOrComputeDigest(digest),
            new Scope(null, ""),
            metadata,
            template,
            staticStateDocument
        )
    }

    // Create analyzed static state for the given static state document.
    // Used by XFormsToXHTML.
    def createFromStaticStateBits(staticStateXML: Document, digest: String, metadata: Metadata, template: AnnotatedTemplate): XFormsStaticStateImpl = {
        val startScope = new Scope(null, "")
        val staticStateDocument = new StaticStateDocument(staticStateXML)

        new XFormsStaticStateImpl(
            staticStateDocument.asBase64,
            digest,
            startScope,
            metadata,
            staticStateDocument.template map (_ ⇒ template),    // only keep the template around if needed
            staticStateDocument
        )
    }

    // Create analyzed static state for the given XForms document.
    // Used by unit tests.
    def createFromDocument(formDocument: Document): (SAXStore, XFormsStaticState) = {
        
        val startScope = new Scope(null, "")
        
        def create(staticStateXML: Document, digest: String, metadata: Metadata, template: AnnotatedTemplate): XFormsStaticStateImpl = {
            val staticStateDocument = new StaticStateDocument(staticStateXML)

            new XFormsStaticStateImpl(
                staticStateDocument.asBase64,
                digest,
                startScope,
                metadata,
                staticStateDocument.template map (_ ⇒ template),    // only keep the template around if needed
                staticStateDocument
            )
        }
        
        createFromDocument(formDocument, startScope, create)
    }

    // Create template and analyzed part for the given XForms document.
    // Used by xxf:dynamic.
    def createPart(staticState: XFormsStaticState, parent: PartAnalysis, formDocument: Document, startScope: Scope) =
        createFromDocument(formDocument, startScope, (staticStateDocument: Document, digest: String, metadata: Metadata, _) ⇒ {
            val part = new PartAnalysisImpl(staticState, Some(parent), startScope, metadata, new StaticStateDocument(staticStateDocument))
            part.analyze()
            part
        })

    private def createFromDocument[T](formDocument: Document, startScope: Scope, create: (Document, String, Metadata, AnnotatedTemplate) ⇒ T): (SAXStore, T) = {
        val identity = TransformerUtils.getIdentityTransformerHandler

        val documentResult = new LocationDocumentResult
        identity.setResult(documentResult)

        val metadata = new Metadata
        val digestContentHandler = new XMLUtils.DigestContentHandler
        val template = new SAXStore

        val prefix = startScope.fullPrefix

        // Annotator with prefix
        class Annotator(extractorReceiver: XMLReceiver) extends XFormsAnnotatorContentHandler(template, extractorReceiver, metadata) {
            protected override def rewriteId(id: String) = prefix + id
        }

        // Extractor with prefix
        class Extractor(xmlReceiver: XMLReceiver) extends XFormsExtractorContentHandler(xmlReceiver, metadata, AnnotatedTemplate(template), ".", XXBLScope.inner, startScope.isTopLevelScope, false) {
            override def startXFormsOrExtension(uri: String, localname: String, attributes: Attributes, scope: XFormsConstants.XXBLScope) {
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
        val staticStateXML = documentResult.getDocument
        val digest = NumberUtils.toHexString(digestContentHandler.getResult)

        (template, create(staticStateXML, digest, metadata, AnnotatedTemplate(template)))
    }

    def getPropertyJava[T](nonDefaultProperties: JMap[String, AnyRef], propertyName: String) =
        getProperty[T](nonDefaultProperties.asScala, propertyName)

    private def defaultPropertyValue(propertyName: String) =
        Option(P.getPropertyDefinition(propertyName)) map (_.defaultValue) orNull

    def getProperty[T](nonDefaultProperties: collection.Map[String, AnyRef], propertyName: String): T =
        nonDefaultProperties.getOrElse(propertyName, defaultPropertyValue(propertyName)).asInstanceOf[T]

    // For Java callers
    def isNoscriptJava(nonDefaultProperties: JMap[String, AnyRef]) =
        isNoscript(nonDefaultProperties.asScala)

    // Determine, based on configuration and properties, whether noscript is allowed and enabled
    def isNoscript(nonDefaultProperties: collection.Map[String, AnyRef]) = {
        val noscriptRequested =
            getProperty[Boolean](nonDefaultProperties, P.NOSCRIPT_PROPERTY) &&
                getProperty[Boolean](nonDefaultProperties, P.NOSCRIPT_SUPPORT_PROPERTY)
        Version.instance.isPEFeatureEnabled(noscriptRequested, P.NOSCRIPT_PROPERTY)
    }

    // Represent the static state XML document resulting from the extractor
    //
    // - The underlying document produced by the extractor used to be further transformed to extract various documents.
    //   This is no longer the case and the underlying document should be considered immutable (it would be good if it
    //   was in fact immutable).
    // - The template, when kept for full update marks, is stored in the static state document as Base64. In noscript
    //   mode, it is stored in the dynamic state.
    class StaticStateDocument(val xmlDocument: Document) {

        private def staticStateElement = xmlDocument.getRootElement

        require(xmlDocument ne null)

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
            val lastId = XFormsUtils.getElementId(idElement)
            require(lastId ne null)
            Integer.parseInt(lastId)
        }

        // Optional template as Base64
        def template = Option(staticStateElement.element("template")) map (_.getText)

        // Extract properties
        // NOTE: XFormsExtractorContentHandler takes care of propagating only non-default properties
        val nonDefaultProperties = {
            for {
                element       ← Dom4jUtils.elements(staticStateElement, STATIC_STATE_PROPERTIES_QNAME).asScala
                attribute     ← Dom4jUtils.attributes(element).asScala
                propertyName  = attribute.getName
                propertyValue = P.parseProperty(propertyName, attribute.getValue)
            } yield
                (propertyName, propertyValue)
        } toMap

        // Get a property by name
        def getProperty[T](propertyName: String): T =
            XFormsStaticStateImpl.getProperty[T](nonDefaultProperties, propertyName)

        def isCacheDocument = getProperty[Boolean](P.CACHE_DOCUMENT_PROPERTY)
        def isClientStateHandling = getProperty[String](P.STATE_HANDLING_PROPERTY) == P.STATE_HANDLING_CLIENT_VALUE
        def isServerStateHandling = getProperty[String](P.STATE_HANDLING_PROPERTY) == P.STATE_HANDLING_SERVER_VALUE
        
        val isHTMLDocument  = Option(staticStateElement.attributeValue("is-html")) exists (_ == "true")
        
        def getOrComputeDigest(digest: Option[String]) =
            digest getOrElse {
                val digestContentHandler = new XMLUtils.DigestContentHandler
                TransformerUtils.writeDom4j(xmlDocument, digestContentHandler)
                NumberUtils.toHexString(digestContentHandler.getResult)
            }
        
        // Get the encoded static state
        // If an existing state is passed in, use it, otherwise encode from XML, encrypting if necessary.
        // NOTE: We do compress the result as we think we can afford this for the static state (probably not so for the dynamic state).
        def asBase64 =
            XFormsUtils.encodeXML(xmlDocument, compress = true, encrypt = isClientStateHandling, location = true)
    }
}