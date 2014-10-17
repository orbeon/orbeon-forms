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
import collection.JavaConverters._
import org.orbeon.oxf.xml._
import org.orbeon.oxf.xml.dom4j.LocationDocumentResult
import org.orbeon.oxf.xml.XMLConstants._
import java.util.{List ⇒ JList}
import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.xforms.{XFormsProperties ⇒ P}
import org.orbeon.oxf.common.{OXFException, Version}
import org.orbeon.oxf.xml.XMLReceiver
import org.xml.sax.Attributes
import org.orbeon.oxf.xforms.XFormsStaticStateImpl.StaticStateDocument
import state.AnnotatedTemplate
import xbl.Scope
import org.dom4j.{Element, Document}
import org.orbeon.oxf.util.{XPath, Whitespace, StringReplacer, NumberUtils}
import org.orbeon.oxf.util.ScalaUtils.stringOptionToSet
import org.orbeon.saxon.dom4j.DocumentWrapper
import org.orbeon.oxf.xforms.library.XFormsFunctionLibrary
import org.orbeon.oxf.xforms.XFormsProperties._
import org.orbeon.oxf.util.XPath.CompiledExpression

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

    implicit val getIndentedLogger = Loggers.getIndentedLogger("analysis")

    // Create top-level part once vals are all initialized
    val topLevelPart = new PartAnalysisImpl(this, None, startScope, metadata, staticStateDocument)

    // Analyze top-level part
    topLevelPart.analyze()

    // Delegation to top-level part
    def dumpAnalysis() = topLevelPart.dumpAnalysis()
    def toXML(helper: XMLReceiverHelper) = topLevelPart.toXML(helper)

    // Properties
    lazy val allowedExternalEvents = stringOptionToSet(Option(staticStringProperty(P.EXTERNAL_EVENTS_PROPERTY)))
    lazy val isHTMLDocument        = staticStateDocument.isHTMLDocument
    lazy val isXPathAnalysis       = Version.instance.isPEFeatureEnabled(staticBooleanProperty(P.XPATH_ANALYSIS_PROPERTY), P.XPATH_ANALYSIS_PROPERTY)

    lazy val sanitizeInput         = StringReplacer(staticStringProperty(P.SANITIZE_PROPERTY))

    def isCacheDocument       = staticBooleanProperty(P.CACHE_DOCUMENT_PROPERTY)
    def isClientStateHandling = staticStringProperty(P.STATE_HANDLING_PROPERTY) == P.STATE_HANDLING_CLIENT_VALUE
    def isServerStateHandling = staticStringProperty(P.STATE_HANDLING_PROPERTY) == P.STATE_HANDLING_SERVER_VALUE

    private lazy val nonDefaultPropertiesOnly: Map[String, Either[Any, CompiledExpression]] =
        staticStateDocument.nonDefaultProperties map { case (name, rawPropertyValue) ⇒
            name → {
                val maybeAVT = XFormsUtils.maybeAVT(rawPropertyValue)
                topLevelPart.defaultModel match {
                    case Some(model) if maybeAVT ⇒
                        Right(XPath.compileExpression(rawPropertyValue, model.namespaceMapping, null, XFormsFunctionLibrary, avt = true))
                    case None if maybeAVT ⇒
                        throw new IllegalArgumentException("can only evaluate AVT properties if a model is present")
                    case _ ⇒
                        Left(P.getPropertyDefinition(name).parseProperty(rawPropertyValue))
                }
            }
        }

    // For properties which can be AVTs
    def propertyMaybeAsExpression(name: String): Either[Any, CompiledExpression] =
        nonDefaultPropertiesOnly.getOrElse(name, Left(P.getPropertyDefinition(name).defaultValue))

    def stringPropertyMaybeAsExpression(name: String): Either[String, CompiledExpression] =
        propertyMaybeAsExpression(name: String).left map (_.toString)

    def booleanPropertyMaybeAsExpression(name: String): Either[Boolean, CompiledExpression] =
        propertyMaybeAsExpression(name: String).left map (_.asInstanceOf[Boolean])
    
    def intPropertyMaybeAsExpression(name: String): Either[Int, CompiledExpression] =
        propertyMaybeAsExpression(name: String).left map (_.asInstanceOf[Int])

    // For properties known to be static
    override def staticProperty(name: String) =
        propertyMaybeAsExpression(name).left.get

    def staticStringProperty(name: String) =
        stringPropertyMaybeAsExpression(name).left.get

    def staticBooleanProperty(name: String) =
        booleanPropertyMaybeAsExpression(name).left.get

    def staticIntProperty(name: String) =
        intPropertyMaybeAsExpression(name).left.get

    // 2014-05-02: Used by XHTMLHeadHandler only
    def clientNonDefaultProperties = staticStateDocument.nonDefaultProperties filter
        { case (propertyName, _) ⇒ getPropertyDefinition(propertyName).isPropagateToClient }
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
        ).asJava)

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

    // Used by xxf:dynamic and unit tests.
    private def createFromDocument[T](formDocument: Document, startScope: Scope, create: (Document, String, Metadata, AnnotatedTemplate) ⇒ T): (SAXStore, T) = {
        val identity = TransformerUtils.getIdentityTransformerHandler

        val documentResult = new LocationDocumentResult
        identity.setResult(documentResult)

        val metadata = new Metadata
        val digestContentHandler = new DigestContentHandler
        val template = new SAXStore

        val prefix = startScope.fullPrefix

        // Annotator with prefix
        class Annotator(extractorReceiver: XMLReceiver) extends XFormsAnnotator(template, extractorReceiver, metadata, startScope.isTopLevelScope) {
            protected override def rewriteId(id: String) = prefix + id
        }

        // Extractor with prefix
        class Extractor(xmlReceiver: XMLReceiver) extends XFormsExtractor(xmlReceiver, metadata, AnnotatedTemplate(template), ".", XXBLScope.inner, startScope.isTopLevelScope, false, false) {
            override def indexElementWithScope(uri: String, localname: String, attributes: Attributes, scope: XFormsConstants.XXBLScope) {
                val staticId = attributes.getValue("id")
                if (staticId ne null) {
                    val prefixedId = prefix + staticId
                    if (metadata.getNamespaceMapping(prefixedId) ne null) {
                        if (startScope.contains(staticId))
                            throw new OXFException("Duplicate id found for static id: " + staticId)
                        startScope += staticId → prefixedId

                        if (uri == XXFORMS_NAMESPACE_URI && localname == "attribute") {
                            val forStaticId = attributes.getValue("for")
                            val forPrefixedId = prefix + forStaticId
                            startScope += forStaticId → forPrefixedId
                        }
                    }
                }
            }
        }

        // Read the input through the annotator and gather namespace mappings
        TransformerUtils.writeDom4j(
            formDocument,
            new WhitespaceXMLReceiver(
                new Annotator(
                    new Extractor(
                        new WhitespaceXMLReceiver(
                            new TeeXMLReceiver(identity, digestContentHandler),
                            Whitespace.defaultBasePolicy,
                            Whitespace.basePolicyMatcher
                        )
                    )
                ),
                Whitespace.defaultHTMLPolicy,
                Whitespace.htmlPolicyMatcher
            )
        )

        // Get static state document and create static state object
        val staticStateXML = documentResult.getDocument
        val digest = NumberUtils.toHexString(digestContentHandler.getResult)

        (template, create(staticStateXML, digest, metadata, AnnotatedTemplate(template)))
    }

    def isStoreNoscriptTemplate(nonDefaultProperties: collection.Map[String, String]) = {

        def defaultPropertyValueAsString(propertyName: String) =
            P.getPropertyDefinition(propertyName).defaultValue.toString

        def propertyAsString(propertyName: String) =
            nonDefaultProperties.getOrElse(propertyName, defaultPropertyValueAsString(propertyName))

        propertyAsString(P.NOSCRIPT_SUPPORT_PROPERTY).toBoolean &&
            propertyAsString(P.NOSCRIPT_TEMPLATE) == P.NOSCRIPT_TEMPLATE_STATIC_VALUE
    }

    // Represent the static state XML document resulting from the extractor
    //
    // - The underlying document produced by the extractor used to be further transformed to extract various documents.
    //   This is no longer the case and the underlying document should be considered immutable (it would be good if it
    //   was in fact immutable).
    // - The template, when kept for full update marks, is stored in the static state document as Base64. In noscript
    //   mode, it is stored in the dynamic state.
    class StaticStateDocument(val xmlDocument: Document) {

        require(xmlDocument ne null)

        private def staticStateElement = xmlDocument.getRootElement

        val documentWrapper = new DocumentWrapper(xmlDocument, null, XPath.GlobalConfiguration)

        // Pointers to nested elements
        def rootControl = staticStateElement.element("root")
        def xblElements = rootControl.elements(XBL_XBL_QNAME).asInstanceOf[JList[Element]].asScala

        // TODO: if staticStateDocument contains XHTML document, get controls and models from there?

        // Return the last id generated
        def lastId: Int = {
            val idElement = staticStateElement.element(XFormsExtractor.LAST_ID_QNAME)
            require(idElement ne null)
            val lastId = XFormsUtils.getElementId(idElement)
            require(lastId ne null)
            lastId.toInt
        }

        // Optional template as Base64
        def template = Option(staticStateElement.element("template")) map (_.getText)

        // Extract properties
        // NOTE: XFormsExtractor takes care of propagating only non-default properties
        val nonDefaultProperties = {
            for {
                element       ← Dom4j.elements(staticStateElement, STATIC_STATE_PROPERTIES_QNAME)
                attribute     ← Dom4j.attributes(element)
                propertyName  = attribute.getName
                propertyValue = attribute.getValue
            } yield
                (propertyName, propertyValue)
        } toMap
        
        val isHTMLDocument = Option(staticStateElement.attributeValue("is-html")) exists (_ == "true")
        
        def getOrComputeDigest(digest: Option[String]) =
            digest getOrElse {
                val digestContentHandler = new DigestContentHandler
                TransformerUtils.writeDom4j(xmlDocument, digestContentHandler)
                NumberUtils.toHexString(digestContentHandler.getResult)
            }

        private def isClientStateHandling = {
            def nonDefault = nonDefaultProperties.get(P.STATE_HANDLING_PROPERTY) map (_ == STATE_HANDLING_CLIENT_VALUE)
            def default    = P.getPropertyDefinition(P.STATE_HANDLING_PROPERTY).defaultValue.toString == STATE_HANDLING_CLIENT_VALUE

            nonDefault getOrElse default
        }
        
        // Get the encoded static state
        // If an existing state is passed in, use it, otherwise encode from XML, encrypting if necessary.
        // NOTE: We do compress the result as we think we can afford this for the static state (probably not so for the dynamic state).
        def asBase64 =
            XFormsUtils.encodeXML(xmlDocument, true, isClientStateHandling, true) // compress = true, encrypt = isClientStateHandling, location = true
    }
}