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
package org.orbeon.oxf.xforms.analysis

import java.net.{URI, URISyntaxException}

import org.orbeon.dom.QName
import org.orbeon.oxf.common.ValidationException
import org.orbeon.oxf.properties.Properties
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.xforms.XFormsNames._
import org.orbeon.oxf.xforms.XFormsProperties._
import org.orbeon.oxf.xforms.XFormsUtils
import org.orbeon.oxf.xforms.action.XFormsActions
import org.orbeon.oxf.xforms.analysis.controls.LHHA
import org.orbeon.oxf.xforms.state.AnnotatedTemplate
import org.orbeon.oxf.xml.XMLConstants._
import org.orbeon.oxf.xml.XMLReceiverSupport._
import org.orbeon.oxf.xml._
import org.orbeon.oxf.xml.dom.LocationData
import org.orbeon.xforms.{Constants, XXBLScope}
import org.xml.sax.helpers.AttributesImpl
import org.xml.sax.{Attributes, Locator}

import scala.collection.JavaConverters._
import scala.collection.{mutable => m}

object XFormsExtractor {

  val LastIdQName = QName("last-id")

  // TODO: Keeping this static list is not ideal
  private val AllowedXXFormsElements =
    Set(
      "dialog",
      "var",
      "variable",
      "sequence",
      "value",
      "attribute",
      "text",
      "context",
      "size", //xf:upload/xxf:size
      "dynamic",
      "param",
      "body"
    )

  private val AllowedEXFormElements =
    Set(
      "variable"
    )

  private val AllowedXBLElements =
    Set(
      "xbl",
      "binding",
      "handlers",
      "handler", // just for the case of top-level `<xbl:handler>`
      "implementation",
      "template"
    )
}

/**
  * This XMLReceiver extracts XForms information from an XHTML document and creates a static state document.
  *
  * NOTE: This must be independent from the actual request (including request path, etc.) so the state can be reused
  * between different requests. Request information, if needed, must go into the dynamic state.
  *
  * The static state document contains only models and controls, without interleaved XHTML elements in order to save
  * memory and to facilitate visiting controls. The exceptions are:
  *
  * - The content of inline XForms instances (xf:instance)
  * - The content of inline XML Schemas (xs:schema)
  * - The content of inline XBL definitions (xbl:xbl)
  * - The content of xf:label, xf:hint, xf:help, xf:alert (as they can contain XHTML)
  *
  * Notes:
  *
  * - xml:base attributes are added on the models and root control elements.
  * - XForms controls and AVTs outside the HTML body are also extracted.
  *
  * Structure:
  *
  * <static-state xmlns:xxf="..." is-html="..." ...> 
  *   <root>
  *     <!-- E.g. AVT on xhtml:html -->
  *     <xxf:attribute .../>
  *     <!-- E.g. xf:output within xhtml:title -->
  *     <xf:output .../>
  *     <!-- E.g. XBL component definitions -->
  *     <xbl:xbl .../>
  *     <xbl:xbl .../>
  *     <!-- Top-level models -->
  *     <xf:model ...>
  *     <xf:model ...>
  *     <!-- Top-level controls including XBL-bound controls -->
  *     <xf:group ...>
  *     <xf:input ...>
  *     <foo:bar ...>
  *   </root>
  *   <!-- Global properties -->
  *   <properties name="sanitize"            value="..."    inline="false"/>
  *   <properties name="readonly-appearance" value="static" inline="true"/>
  *   <!-- Last id used (for id generation in XBL after deserialization) -->
  *   <last-id id="123"/>
  *   <!-- Template (for full updates) -->
  *   <template>base64</template>
  * </static-state>
  */
class XFormsExtractor(
  val xmlReceiverOpt               : Option[XMLReceiver], // output receiver, which can be `None` when our subclass is `ScopeExtractor`
  val metadata                     : Metadata,
  val templateUnderConstructionOpt : Option[AnnotatedTemplate],
  val baseURI                      : String,
  val startScope                   : XXBLScope,
  val isTopLevel                   : Boolean,
  val outputSingleTemplate         : Boolean
) extends XMLReceiver
     with XMLReceiverUnneededEvents
     with ExtractorProperties
     with ExtractorOutput {

  import XFormsExtractor._

  require(baseURI ne null)

  // For subclasses
  protected def getPrefixedId(staticId: String) = staticId
  protected def indexElementWithScope(uri: String, localname: String, attributes: Attributes, scope: XXBLScope) = ()

  private case class XMLElementDetails(
    xmlBase             : URI,
    xmlLangOpt          : Option[String],
    xmlLangAvtIdOpt     : Option[String],
    scope               : XXBLScope,
    isModel             : Boolean,
    isXForms            : Boolean,
    isXXForms           : Boolean,
    isEXForms           : Boolean,
    isXBL               : Boolean,
    isXXBL              : Boolean,
    isExtension         : Boolean,
    isXFormsOrExtension : Boolean
  )

  private var elementStack: List[XMLElementDetails] =
    List(
      XMLElementDetails(
        xmlBase             = new URI(null, null, baseURI, null),
        xmlLangOpt          = None,
        xmlLangAvtIdOpt     = None,
        scope               = startScope,
        isModel             = false,
        isXForms            = false,
        isXXForms           = false,
        isEXForms           = false,
        isXBL               = false,
        isXXBL              = false,
        isExtension         = false,
        isXFormsOrExtension = false
      )
    )

  private var locator: Locator             = null

  private var level                        = 0
  private var mustOutputFirstElement       = xmlReceiverOpt.isDefined

  private var inXFormsOrExtension          = false // whether we are in a model
  private var xformsLevel                  = 0
  private var inPreserve                   = false // whether we are in a schema, instance, or xbl:xbl
  private var inForeign                    = false // whether we are in a foreign element section in the model
  private var inLHHA                       = false // whether we are in an LHHA element
  private var preserveOrLHHAOrForeignLevel = 0
  private var isHTMLDocument               = false // Whether this is an (X)HTML document

  override def setDocumentLocator(locator: Locator): Unit = {
    this.locator = locator
    xmlReceiverOpt foreach (_.setDocumentLocator(locator))
  }

  override def startDocument(): Unit =
    xmlReceiverOpt foreach (_.startDocument())

  private def outputFirstElementIfNeeded(): Unit =
    xmlReceiverOpt foreach { implicit xmlReceiver =>
      if (mustOutputFirstElement && ! outputSingleTemplate) {

        openElement(localName = "static-state", atts = List("is-html" -> isHTMLDocument.toString))
        openElement(localName = "root",         atts = List("id"      -> Constants.DocumentId))

        mustOutputFirstElement = false
      }
    }

  override def endDocument(): Unit =
    xmlReceiverOpt foreach { implicit xmlReceiver =>

      if (! outputSingleTemplate) {

        outputFirstElementIfNeeded()
        closeElement(localName = "root")
        outputNonDefaultProperties()

        if (isTopLevel) {
          // Remember the last id used for id generation. During state restoration, XBL components must start with this id.
          element(
            localName = XFormsExtractor.LastIdQName.localName,
            atts      = List("id" -> metadata.idGenerator.nextSequenceNumber.toString)
          )

          // TODO: It's not good to serialize this right here, since we have a live SAXStore anyway used to create the
          // static state and since the serialization is only needed if the static state is serialized. In other
          // words, serialization of the template should be lazy.

          // Remember the template (and marks if any) if there are top-level marks
          if (metadata.hasTopLevelMarks)
            templateUnderConstructionOpt foreach { templateUnderConstruction =>
              withElement(localName = "template") {
                // NOTE: At this point, the template has just received endDocument(), so is no longer under under
                // construction and can be serialized safely.
                text(templateUnderConstruction.asBase64)
              }
            }
        }
        closeElement(localName = "static-state")
      }

      xmlReceiver.endDocument()
    }

  override def startElement(uri: String, localname: String, qName: String, attributes: Attributes): Unit = {

    inputNamespaceContext.startElement()

    // Check for XForms or extension namespaces
    val isXForms            = uri == XFORMS_NAMESPACE_URI
    val isXXForms           = uri == XXFORMS_NAMESPACE_URI
    val isEXForms           = uri == EXFORMS_NAMESPACE_URI
    val isXBL               = uri == XBL_NAMESPACE_URI
    val isXXBL              = uri == XXBL_NAMESPACE_URI // for xxbl:global

    val staticId            = attributes.getValue("", "id")
    val prefixedId          = if (staticId ne null) getPrefixedId(staticId) else null

    val isExtension         = (prefixedId ne null) && metadata.prefixedIdHasBinding(prefixedId)
    val isXFormsOrExtension = isXForms || isXXForms || isEXForms || isXBL || isXXBL || isExtension

    val parentElementDetails = elementStack.head

    if (! inPreserve && ! inForeign) { // optimization

      // xbl:base
      val newBase =
        Option(attributes.getValue(XML_URI, "base")) match {
          case Some(xmlBaseAttribute) =>
            try {
              parentElementDetails.xmlBase.resolve(new URI(xmlBaseAttribute)).normalize // normalize to remove "..", etc.
            } catch {
              case e: URISyntaxException =>
                throw new ValidationException(
                  s"Error creating URI from: `$parentElementDetails` and `$xmlBaseAttribute`.",
                  e,
                  LocationData.createIfPresent(locator)
                )
            }
          case None =>
            parentElementDetails.xmlBase
        }

      // xml:lang
      val (newLangOpt, newLangAvtIdOpt) =
        Option(attributes.getValue(XML_URI, "lang")) match {
          case some @ Some(xmlLangAttribute) =>
            some -> (
              if (XFormsUtils.maybeAVT(xmlLangAttribute))
                Option(staticId)
              else
                parentElementDetails.xmlLangAvtIdOpt
            )
          case None =>
            parentElementDetails.xmlLangOpt ->
              parentElementDetails.xmlLangAvtIdOpt
        }

      // xxbl:scope
      val newScope =
        Option(attributes.getValue(XXBL_SCOPE_QNAME.namespace.uri, XXBL_SCOPE_QNAME.localName)) match {
          case Some(xblScopeAttribute) => XXBLScope.withName(xblScopeAttribute)
          case None                    => parentElementDetails.scope
        }

      elementStack ::=
        XMLElementDetails(
          xmlBase             = newBase,
          xmlLangOpt          = newLangOpt,
          xmlLangAvtIdOpt     = newLangAvtIdOpt,
          scope               = newScope,
          isModel             = isXForms && localname == "model",
          isXForms            = isXForms,
          isXXForms           = isXXForms,
          isEXForms           = isEXForms,
          isXBL               = isXBL,
          isXXBL              = isXXBL,
          isExtension         = isExtension,
          isXFormsOrExtension = isXFormsOrExtension
        )
    }

    // Handle properties of the form @xxf:* when outside of models or controls
    if (! inXFormsOrExtension && ! isXFormsOrExtension)
      addInlinePropertiesIfAny(attributes)

    if (level == 0 && isTopLevel)
      isHTMLDocument = localname == "html" && (uri == "" || uri == XHTML_NAMESPACE_URI)

    var outputAttributes = attributes

    // Start extracting model or controls
    if (! inXFormsOrExtension && isXFormsOrExtension) {

      inXFormsOrExtension = true
      xformsLevel         = level

      // Handle properties on top-level model elements
      if (isXForms && localname == "model")
        addInlinePropertiesIfAny(attributes)

      outputFirstElementIfNeeded()

      // Add xml:base on element
      outputAttributes = SAXUtils.addOrReplaceAttribute(outputAttributes, XML_URI, "xml", "base", elementStack.head.xmlBase.toString)

      // Add xml:lang on element if found
      elementStack.head.xmlLangOpt foreach { xmlLang =>
        val newXMLLang =
          elementStack.head.xmlLangAvtIdOpt match {
            case Some(xmlLangAvtId) if XFormsUtils.maybeAVT(xmlLang) =>
              // In this case the latest xml:lang on the stack might be an AVT and we set a special value for
              // xml:lang containing the id of the control that evaluates the runtime value.
              "#" + xmlLangAvtId
            case _ =>
              // No AVT
              xmlLang
          }

        outputAttributes = SAXUtils.addOrReplaceAttribute(outputAttributes, XML_URI, "xml", "lang", newXMLLang)
      }
    }

    // Check for preserved, foreign, or LHHA content
    if (inXFormsOrExtension && ! inPreserve && ! inForeign) {

      // TODO: Just warn?
      if (isXXForms) {
        if (! AllowedXXFormsElements(localname) && ! XFormsActions.isAction(QName(localname, XXFORMS_NAMESPACE)))
          throw new ValidationException(s"Invalid extension element in XForms document: `$qName`", LocationData.createIfPresent(locator))
      } else if (isEXForms) {
        if (! AllowedEXFormElements(localname))
          throw new ValidationException(s"Invalid eXForms element in XForms document: `$qName`", LocationData.createIfPresent(locator))
      } else if (isXBL) {
        if (! AllowedXBLElements(localname))
          throw new ValidationException(s"Invalid XBL element in XForms document: `$qName`", LocationData.createIfPresent(locator))
      }

      // Preserve as is the content of labels, etc., instances, and schemas
      if (! inLHHA) {
        if (LHHA.NamesSet(localname) && isXForms) { // LHHA may contain XHTML
          inLHHA                       = true
          preserveOrLHHAOrForeignLevel = level
        } else if (
             localname == "instance" && isXForms       // XForms instance
          || localname == "schema"   && uri == XSD_URI // XML schema
          || localname == "xbl"      && isXBL          // preserve everything under xbl:xbl so that templates may be processed by static state
          || isExtension
        ) {
          inPreserve                   = true
          preserveOrLHHAOrForeignLevel = level
        }
      }

      // Callback for elements of interest
      // NOTE: We call this also for HTML elements within LHHA so we can gather scope information for AVTs
      if (isXFormsOrExtension || inLHHA)
        indexElementWithScope(uri, localname, outputAttributes, elementStack.head.scope)
    }

    if (inXFormsOrExtension && ! inForeign && (inPreserve || inLHHA || isXFormsOrExtension)) {
      // We are within preserved content or we output regular XForms content
      startStaticStateElement(uri, localname, qName, outputAttributes)
    } else if (inXFormsOrExtension && ! isXFormsOrExtension && parentElementDetails.isModel) {
      // Start foreign content in the model
      inForeign                    = true
      preserveOrLHHAOrForeignLevel = level
    }

    level += 1
  }

  override def endElement(uri: String, localname: String, qName: String): Unit = {
    level -= 1

    // We are within preserved content or we output regular XForms content
    if (inXFormsOrExtension && ! inForeign && (inPreserve || inLHHA || elementStack.head.isXFormsOrExtension))
      endStaticStateElement(uri, localname, qName)

    if ((inPreserve || inLHHA || inForeign) && level == preserveOrLHHAOrForeignLevel) {
      // Leaving preserved, foreign or LHHA content
      inPreserve = false
      inForeign  = false
      inLHHA     = false
    }

    if (inXFormsOrExtension && level == xformsLevel) {
      // Leaving model or controls
      inXFormsOrExtension = false
    }

    if (! inPreserve && ! inForeign)
      elementStack = elementStack.tail

    inputNamespaceContext.endElement()
  }

  override def characters(ch: Array[Char], start: Int, length: Int): Unit =
    xmlReceiverOpt foreach { xmlReceiver =>
      if (inPreserve) {
        xmlReceiver.characters(ch, start, length)
      } else if (! inForeign) {
        // TODO: we must not output characters here if we are not directly within an XForms element
        // See: https://github.com/orbeon/orbeon-forms/issues/493
        if (inXFormsOrExtension)
          xmlReceiver.characters(ch, start, length)
      }
    }

  override def comment(ch: Array[Char], start: Int, length: Int) =
    xmlReceiverOpt foreach { xmlReceiver =>
      if (inPreserve)
        xmlReceiver.comment(ch, start, length)
    }

  override def processingInstruction(target: String, data: String) =
    xmlReceiverOpt foreach { xmlReceiver =>
      if (inPreserve)
        xmlReceiver.processingInstruction(target, data)
    }
}

trait ExtractorOutput extends XMLReceiver {

  // NOTE: Ugly until we get trait parameters!
  def xmlReceiverOpt: Option[XMLReceiver]

  protected val inputNamespaceContext       = new NamespaceContext
  private   var outputNamespaceContextStack = inputNamespaceContext.current :: Nil

  override def startPrefixMapping(prefix: String, uri: String): Unit =
    if (xmlReceiverOpt.isDefined)
      inputNamespaceContext.startPrefixMapping(prefix, uri)

  override def endPrefixMapping(s: String): Unit = ()

  def startStaticStateElement(uri: String, localname: String, qName: String, attributes: Attributes): Unit =
    xmlReceiverOpt foreach { xmlReceiver =>
      outputNamespaceContextStack ::= inputNamespaceContext.current
      iterateChangedMappings foreach (xmlReceiver.startPrefixMapping _).tupled
      xmlReceiver.startElement(uri, localname, qName, attributes)
    }

  def endStaticStateElement(uri: String, localname: String, qName: String): Unit =
    xmlReceiverOpt foreach { xmlReceiver =>
      xmlReceiver.endElement(uri, localname, qName)
      iterateChangedMappings foreach { case (prefix, _) => xmlReceiver.endPrefixMapping(prefix) }
      outputNamespaceContextStack = outputNamespaceContextStack.tail
    }

  // Compare the mappings for the last two elements output and return the mappings that have changed
  private def iterateChangedMappings = {

    val oldMappings = outputNamespaceContextStack.tail.head.mappings
    val newMappings = outputNamespaceContextStack.head.mappings

    if (oldMappings eq newMappings) {
      // Optimized case where mappings haven't changed at all
      Iterator.empty
    } else {
      for {
        newMapping @ (newPrefix, newURI) <- newMappings.iterator
        if (
          oldMappings.get(newPrefix) match {
            case None                             => true  // new mapping
            case Some(oldURI) if oldURI != newURI => true  // changed mapping, including to/from undeclaration with ""
            case _                                => false // unchanged mapping
          }
        )
      } yield
        newMapping
    }
  }
}

trait ExtractorProperties {

  // NOTE: Ugly until we get trait parameters!
  def xmlReceiverOpt: Option[XMLReceiver]

  // This code deals with property values not as parsed values (which are String, Boolean, Int), but as String values
  // only. This is because some XForms values can be AVTs. This means that it is possible that some values will be
  // kept because they differ lexically, even though their value might be the same. For example, `true` and `TRUE`
  // (although this case is degenerate: `TRUE` really shouldn't be allowed, but it is by Java!). In general, this
  // is not a problem, and the worst case scenario is that a few too many properties are kept in the static state.

  private val unparsedInlineProperties = m.HashMap[String, String]()

  protected def outputNonDefaultProperties(): Unit =
    xmlReceiverOpt foreach { implicit xmlReceiver =>

      val propertySet = Properties.instance.getPropertySet

      val propertiesToKeep = {
        for {
          (name, prop) <- SUPPORTED_DOCUMENT_PROPERTIES.asScala
          defaultValue = prop.defaultValue
          globalValue  = propertySet.getObject(XFORMS_PROPERTY_PREFIX + name, defaultValue)
        } yield
          unparsedInlineProperties.get(name) match {
            case Some(localValue) => localValue  != defaultValue.toString option (name, localValue          , true)
            case None             => globalValue != defaultValue          option (name, globalValue.toString, false)
          }
      } flatten

      for ((name, value, inline) <- propertiesToKeep) {
        val newAttributes = new AttributesImpl
        newAttributes.addAttribute("", "name",   "name",   XMLReceiverHelper.CDATA, name)
        newAttributes.addAttribute("", "value",  "value",  XMLReceiverHelper.CDATA, value)
        newAttributes.addAttribute("", "inline", "inline", XMLReceiverHelper.CDATA, inline.toString)
        element(localName = STATIC_STATE_PROPERTIES_QNAME.localName, atts = newAttributes)
      }
    }

  protected def addInlinePropertiesIfAny(attributes: Attributes): Unit =
    for {
      i <- 0 until attributes.getLength
      if attributes.getURI(i) == XXFORMS_NAMESPACE_URI
    } locally {
      unparsedInlineProperties.getOrElseUpdate(attributes.getLocalName(i), attributes.getValue(i))
    }
}
