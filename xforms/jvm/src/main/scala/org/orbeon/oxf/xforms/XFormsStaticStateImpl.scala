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

import org.orbeon.datatypes.MaximumSize
import org.orbeon.dom.Document
import org.orbeon.dom.io.XMLWriter
import org.orbeon.dom.saxon.DocumentWrapper
import org.orbeon.oxf.common.{OXFException, Version}
import org.orbeon.oxf.processor.generator.RequestGenerator
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.XPath.CompiledExpression
import org.orbeon.oxf.util._
import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.xforms.XFormsProperties._
import org.orbeon.oxf.xforms.XFormsStaticStateImpl.StaticStateDocument
import org.orbeon.oxf.xforms.analysis._
import org.orbeon.oxf.xforms.library.XFormsFunctionLibrary
import org.orbeon.oxf.xforms.state.AnnotatedTemplate
import org.orbeon.oxf.xforms.xbl.{Scope, XBLSupport}
import org.orbeon.oxf.xforms.{XFormsProperties => P}
import org.orbeon.oxf.xml.XMLConstants._
import org.orbeon.oxf.xml.dom4j.{Dom4jUtils, LocationDocumentResult}
import org.orbeon.oxf.xml.{XMLReceiver, _}
import org.orbeon.saxon.`type`.BuiltInAtomicType
import org.orbeon.saxon.functions.{FunctionLibrary, FunctionLibraryList}
import org.orbeon.saxon.sxpath.XPathExpression
import org.xml.sax.Attributes

import scala.collection.JavaConverters._
import scala.reflect.ClassTag
import scala.util.Try

class XFormsStaticStateImpl(
  val encodedState        : String,
  val digest              : String,
  val startScope          : Scope,
  metadata                : Metadata,
  val template            : Option[AnnotatedTemplate],
  val staticStateDocument : StaticStateDocument
) extends XFormsStaticState {

  require(encodedState ne null)
  require(digest ne null)

  implicit val getIndentedLogger = Loggers.getIndentedLogger("analysis")

  // Create top-level part once `val`s are all initialized
  val topLevelPart = new PartAnalysisImpl(this, None, startScope, metadata, staticStateDocument)

  // Analyze top-level part
  topLevelPart.analyze()

  // Delegation to top-level part
  def dumpAnalysis() = topLevelPart.dumpAnalysis()
  def toXML(helper: XMLReceiverHelper) = topLevelPart.toXML(helper)

  // Properties
  // These are `lazy val`s because they depend on the default model being found, which is done when
  // the `PartAnalysisImpl` is created above. Yes, this is tricky and not ideal.
  lazy val allowedExternalEvents   = staticStringProperty(P.EXTERNAL_EVENTS_PROPERTY).tokenizeToSet
  lazy val isHTMLDocument          = staticStateDocument.isHTMLDocument
  lazy val isXPathAnalysis         = Version.instance.isPEFeatureEnabled(staticBooleanProperty(P.XPATH_ANALYSIS_PROPERTY),     P.XPATH_ANALYSIS_PROPERTY)
  lazy val isCalculateDependencies = Version.instance.isPEFeatureEnabled(staticBooleanProperty(P.CALCULATE_ANALYSIS_PROPERTY), P.CALCULATE_ANALYSIS_PROPERTY)
  lazy val sanitizeInput           = StringReplacer(staticStringProperty(P.SANITIZE_PROPERTY))
  lazy val isInlineResources       = staticBooleanProperty(P.INLINE_RESOURCES_PROPERTY)

  lazy val assets: XFormsAssets =
    XFormsAssets.updateAssets(
      XFormsAssets.fromJSONProperty,
      staticStringProperty(P.ASSETS_BASELINE_EXCLUDES_PROPERTY),
      staticStringProperty(P.ASSETS_BASELINE_UPDATES_PROPERTY)
    )

  private def loadClass[T : ClassTag](propertyName: String): Option[T] =
    staticStateDocument.nonDefaultProperties.get(propertyName) map (_._1) flatMap trimAllToOpt match {
      case Some(className) =>

        def tryFromScalaObject: Try[AnyRef] = Try {
          Class.forName(className + "$").getDeclaredField("MODULE$").get(null)
        }

        def fromJavaClass: AnyRef =
          Class.forName(className).getDeclaredMethod("instance").invoke(null)

        tryFromScalaObject getOrElse fromJavaClass match {
          case instance: T => Some(instance)
          case _ =>
            throw new ClassCastException(
              s"property `$propertyName` does not refer to a ${implicitly[ClassTag[T]].runtimeClass.getName} with `$className`"
            )
        }
      case None => None
    }

  // This is a bit tricky because during analysis, XPath expression require the function library. This means this
  // property cannot use `nonDefaultPropertiesOnly` below, which collects all non-default properties and attempts to
  // evaluates AVT properties, which themselves require finding the default model, which is not yet ready! So we
  // use `staticStateDocument.nonDefaultProperties` instead.
  lazy val functionLibrary: FunctionLibrary =
    loadClass[FunctionLibrary](FUNCTION_LIBRARY_PROPERTY) match {
      case Some(library) =>
        new FunctionLibraryList                         |!>
          (_.addFunctionLibrary(XFormsFunctionLibrary)) |!>
          (_.addFunctionLibrary(library))
      case None =>
        XFormsFunctionLibrary
    }

  lazy val xblSupport: Option[XBLSupport] =
    loadClass[XBLSupport](XBL_SUPPORT_PROPERTY)

  lazy val uploadMaxSize: MaximumSize =
    staticStringProperty(UPLOAD_MAX_SIZE_PROPERTY).trimAllToOpt flatMap
      MaximumSize.unapply orElse
      MaximumSize.unapply(RequestGenerator.getMaxSizeProperty.toString) getOrElse
      MaximumSize.LimitedSize(0L)

  lazy val uploadMaxSizeAggregate: MaximumSize =
    staticStringProperty(UPLOAD_MAX_SIZE_AGGREGATE_PROPERTY).trimAllToOpt flatMap
      MaximumSize.unapply getOrElse
      MaximumSize.UnlimitedSize

  lazy val uploadMaxSizeAggregateExpression: Option[CompiledExpression] = {

    val compiledExpressionOpt =
      for {
        rawProperty <- staticStringProperty(UPLOAD_MAX_SIZE_AGGREGATE_EXPRESSION_PROPERTY).trimAllToOpt
        model       <- topLevelPart.defaultModel // ∃ property => ∃ model, right?
      } yield
        XPath.compileExpression(
          xpathString      = rawProperty,
          namespaceMapping = model.namespaceMapping,
          locationData     = null,
          functionLibrary  = functionLibrary,
          avt              = false
        )

    def getExpressionType(expr: XPathExpression) = {
      val internalExpr = expr.getInternalExpression
      internalExpr.getItemType(internalExpr.getExecutable.getConfiguration.getTypeHierarchy)
    }

    compiledExpressionOpt match {
      case Some(CompiledExpression(expr, _, _)) if getExpressionType(expr) == BuiltInAtomicType.INTEGER =>
        compiledExpressionOpt
      case Some(_) =>
        throw new IllegalArgumentException(s"property `$UPLOAD_MAX_SIZE_AGGREGATE_EXPRESSION_PROPERTY` must return `xs:integer` type")
      case None =>
        None
    }
  }

  def isClientStateHandling = staticStringProperty(P.STATE_HANDLING_PROPERTY) == P.STATE_HANDLING_CLIENT_VALUE
  def isServerStateHandling = staticStringProperty(P.STATE_HANDLING_PROPERTY) == P.STATE_HANDLING_SERVER_VALUE

  private lazy val nonDefaultPropertiesOnly: Map[String, Either[Any, CompiledExpression]] =
    staticStateDocument.nonDefaultProperties map { case (name, (rawPropertyValue, isInline)) =>
      name -> {
        val maybeAVT = XFormsUtils.maybeAVT(rawPropertyValue)
        topLevelPart.defaultModel match {
          case Some(model) if isInline && maybeAVT =>
            Right(XPath.compileExpression(rawPropertyValue, model.namespaceMapping, null, functionLibrary, avt = true))
          case None if isInline && maybeAVT =>
            throw new IllegalArgumentException("can only evaluate AVT properties if a model is present") // 2016-06-27: Uncommon case but really?
          case _ =>
            Left(P.getPropertyDefinition(name).parseProperty(rawPropertyValue))
        }
      }
    }

  // For properties which can be AVTs
  def propertyMaybeAsExpression(name: String): Either[Any, CompiledExpression] =
    nonDefaultPropertiesOnly.getOrElse(name, Left(P.getPropertyDefinition(name).defaultValue))

  // For properties known to be static
  private def staticPropertyOrDefault(name: String) =
    staticStateDocument.nonDefaultProperties.get(name) map
      (_._1)                                           map
      P.getPropertyDefinition(name).parseProperty      getOrElse
      P.getPropertyDefinition(name).defaultValue

  def staticProperty       (name: String) = staticPropertyOrDefault(name: String)
  def staticStringProperty (name: String) = staticPropertyOrDefault(name: String).toString
  def staticBooleanProperty(name: String) = staticPropertyOrDefault(name: String).asInstanceOf[Boolean]
  def staticIntProperty    (name: String) = staticPropertyOrDefault(name: String).asInstanceOf[Int]

  // 2014-05-02: Used by XHTMLHeadHandler only
  def clientNonDefaultProperties: Map[String, AnyRef] =
    for {
      (propertyName, _) <- staticStateDocument.nonDefaultProperties
      if getPropertyDefinition(propertyName).isPropagateToClient
    } yield
      propertyName -> staticProperty(propertyName)
}

object XFormsStaticStateImpl {

  val BASIC_NAMESPACE_MAPPING =
    NamespaceMapping(Map(
      XFORMS_PREFIX        -> XFORMS_NAMESPACE_URI,
      XFORMS_SHORT_PREFIX  -> XFORMS_NAMESPACE_URI,
      XXFORMS_PREFIX       -> XXFORMS_NAMESPACE_URI,
      XXFORMS_SHORT_PREFIX -> XXFORMS_NAMESPACE_URI,
      XML_EVENTS_PREFIX    -> XML_EVENTS_NAMESPACE_URI,
      XHTML_PREFIX         -> XMLConstants.XHTML_NAMESPACE_URI,
      XHTML_SHORT_PREFIX   -> XMLConstants.XHTML_NAMESPACE_URI
    ))

  // Create static state from an encoded version. This is used when restoring a static state from a serialized form.
  // NOTE: `digest` can be None when using client state, if all we have are serialized static and dynamic states.
  def restore(digest: Option[String], encodedState: String, forceEncryption: Boolean): XFormsStaticStateImpl = {

    val staticStateDocument = new StaticStateDocument(EncodeDecode.decodeXML(encodedState, forceEncryption))

    // Restore template
    val template = staticStateDocument.template map AnnotatedTemplate.apply

    // Restore metadata
    val metadata = Metadata(staticStateDocument, template)

    new XFormsStaticStateImpl(
      encodedState,
      staticStateDocument.getOrComputeDigest(digest),
      new Scope(None, ""),
      metadata,
      template,
      staticStateDocument
    )
  }

  // Create analyzed static state for the given static state document.
  // Used by XFormsToXHTML.
  def createFromStaticStateBits(staticStateXML: Document, digest: String, metadata: Metadata, template: AnnotatedTemplate): XFormsStaticStateImpl = {
    val startScope = new Scope(None, "")
    val staticStateDocument = new StaticStateDocument(staticStateXML)

    new XFormsStaticStateImpl(
      staticStateDocument.asBase64,
      digest,
      startScope,
      metadata,
      staticStateDocument.template map (_ => template),    // only keep the template around if needed
      staticStateDocument
    )
  }

  // Create analyzed static state for the given XForms document.
  // Used by unit tests.
  def createFromDocument(formDocument: Document): XFormsStaticState = {

    val startScope = new Scope(None, "")

    def create(staticStateXML: Document, digest: String, metadata: Metadata, template: AnnotatedTemplate): XFormsStaticStateImpl = {
      val staticStateDocument = new StaticStateDocument(staticStateXML)

      new XFormsStaticStateImpl(
        staticStateDocument.asBase64,
        digest,
        startScope,
        metadata,
        staticStateDocument.template map (_ => template),    // only keep the template around if needed
        staticStateDocument
      )
    }

    createFromDocument(formDocument, startScope, create)._2
  }

  // Create template and analyzed part for the given XForms document.
  // Used by `xxf:dynamic`.
  def createPart(staticState: XFormsStaticState, parent: PartAnalysis, formDocument: Document, startScope: Scope): (SAXStore, PartAnalysisImpl) =
    createFromDocument(formDocument, startScope, (staticStateDocument: Document, _: String, metadata: Metadata, _) => {
      val part = new PartAnalysisImpl(staticState, Some(parent), startScope, metadata, new StaticStateDocument(staticStateDocument))
      part.analyze()
      part
    })

  // Extractor with prefix
  private class Extractor(
    extractorReceiver : XMLReceiver,
    metadata          : Metadata,
    startScope        : Scope,
    template          : SAXStore,
    prefix            : String
  ) extends XFormsExtractor(
    xmlReceiverOpt               = Some(extractorReceiver),
    metadata                     = metadata,
    templateUnderConstructionOpt = Some(AnnotatedTemplate(template)),
    baseURI                      = ".",
    startScope                   = XXBLScope.Inner,
    isTopLevel                   = startScope.isTopLevelScope,
    outputSingleTemplate         = false
  ) {

    override def getPrefixedId(staticId: String) = prefix + staticId

    override def indexElementWithScope(uri: String, localname: String, attributes: Attributes, scope: XXBLScope): Unit = {
      val staticId = attributes.getValue("id")
      if (staticId ne null) {
        val prefixedId = prefix + staticId
        if (metadata.getNamespaceMapping(prefixedId).isDefined) {
          if (startScope.contains(staticId))
            throw new OXFException("Duplicate id found for static id: " + staticId)
          startScope += staticId -> prefixedId

          if (uri == XXFORMS_NAMESPACE_URI && localname == "attribute") {
            val forStaticId = attributes.getValue("for")
            val forPrefixedId = prefix + forStaticId
            startScope += forStaticId -> forPrefixedId
          }
        }
      }
    }
  }

  // Annotator with prefix
  private class Annotator(
    extractorReceiver : XMLReceiver,
    metadata          : Metadata,
    startScope        : Scope,
    template          : XMLReceiver,
    prefix            : String
  ) extends XFormsAnnotator(
    template,
    extractorReceiver,
    metadata,
    startScope.isTopLevelScope
  ) {
    protected override def rewriteId(id: String) = prefix + id
  }

  // Used by xxf:dynamic and unit tests.
  private def createFromDocument[T](
    formDocument : Document,
    startScope   : Scope,
    create       : (Document, String, Metadata, AnnotatedTemplate) => T
  ): (SAXStore, T) = {
    val identity = TransformerUtils.getIdentityTransformerHandler

    val documentResult = new LocationDocumentResult
    identity.setResult(documentResult)

    val metadata             = Metadata(isTopLevelPart = startScope.isTopLevelScope)
    val digestContentHandler = new DigestContentHandler
    val template             = new SAXStore
    val prefix               = startScope.fullPrefix

    // Read the input through the annotator and gather namespace mappings
    TransformerUtils.writeDom4j(
      formDocument,
      new WhitespaceXMLReceiver(
        new Annotator(
          new Extractor(
            new WhitespaceXMLReceiver(
              new TeeXMLReceiver(identity, digestContentHandler),
              WhitespaceMatching.defaultBasePolicy,
              WhitespaceMatching.basePolicyMatcher
            ),
            metadata,
            startScope,
            template,
            prefix
          ),
          metadata,
          startScope,
          template,
          prefix
        ),
        WhitespaceMatching.defaultHTMLPolicy,
        WhitespaceMatching.htmlPolicyMatcher
      )
    )

    // Get static state document and create static state object
    val staticStateXML = documentResult.getDocument
    val digest = NumberUtils.toHexString(digestContentHandler.getResult)

    (template, create(staticStateXML, digest, metadata, AnnotatedTemplate(template)))
  }

  // Represent the static state XML document resulting from the extractor
  //
  // - The underlying document produced by the extractor used to be further transformed to extract various documents.
  //   This is no longer the case and the underlying document should be considered immutable (it would be good if it
  //   was in fact immutable).
  // - The template, when kept for full update marks, is stored in the static state document as Base64.
  class StaticStateDocument(val xmlDocument: Document) {

    require(xmlDocument ne null)

    private def staticStateElement = xmlDocument.getRootElement

    val documentWrapper = new DocumentWrapper(xmlDocument, null, XPath.GlobalConfiguration)

    // Pointers to nested elements
    def rootControl = staticStateElement.element("root")
    def xblElements = rootControl.elements(XBL_XBL_QNAME).asScala

    // TODO: if staticStateDocument contains XHTML document, get controls and models from there?

    // Return the last id generated
    def lastId: Int = {
      val idElement = staticStateElement.element(XFormsExtractor.LastIdQName)
      require(idElement ne null)
      val lastId = XFormsUtils.getElementId(idElement)
      require(lastId ne null)
      lastId.toInt
    }

    // Optional template as Base64
    def template: Option[String] = staticStateElement.elementOpt("template") map (_.getText)

    // Extract properties
    // NOTE: XFormsExtractor takes care of propagating only non-default properties
    val nonDefaultProperties: Map[String, (String, Boolean)] = {
      for {
        element       <- Dom4j.elements(staticStateElement, STATIC_STATE_PROPERTIES_QNAME)
        propertyName  = element.attributeValue("name")
        propertyValue = element.attributeValue("value")
        isInline      = element.attributeValue("inline") == true.toString
      } yield
        (propertyName, propertyValue -> isInline)
    } toMap

    val isHTMLDocument: Boolean =
      staticStateElement.attributeValueOpt("is-html") contains "true"

    def getOrComputeDigest(digest: Option[String]): String =
      digest getOrElse {
        val digestContentHandler = new DigestContentHandler
        TransformerUtils.writeDom4j(xmlDocument, digestContentHandler)
        NumberUtils.toHexString(digestContentHandler.getResult)
      }

    private def isClientStateHandling: Boolean = {
      def nonDefault = nonDefaultProperties.get(P.STATE_HANDLING_PROPERTY) map (_._1 == STATE_HANDLING_CLIENT_VALUE)
      def default    = P.getPropertyDefinition(P.STATE_HANDLING_PROPERTY).defaultValue.toString == STATE_HANDLING_CLIENT_VALUE

      nonDefault getOrElse default
    }

    // Get the encoded static state
    // If an existing state is passed in, use it, otherwise encode from XML, encrypting if necessary.
    // NOTE: We do compress the result as we think we can afford this for the static state (probably not so for the dynamic state).
    def asBase64 =
      EncodeDecode.encodeXML(xmlDocument, true, isClientStateHandling, true) // compress = true, encrypt = isClientStateHandling, location = true

    def dump() =
      println(xmlDocument.getRootElement.serializeToString(XMLWriter.PrettyFormat))
  }
}