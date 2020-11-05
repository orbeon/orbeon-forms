package org.orbeon.oxf.xforms.analysis

import org.orbeon.dom.io.XMLWriter
import org.orbeon.dom.{Document, Element}
import org.orbeon.oxf.util.NumberUtils
import org.orbeon.oxf.xforms.{XFormsProperties => P}
import org.orbeon.oxf.xml.dom.Extensions._
import org.orbeon.oxf.xml.{DigestContentHandler, EncodeDecode, TransformerUtils}
import org.orbeon.xforms.XFormsNames.{STATIC_STATE_PROPERTIES_QNAME, XBL_XBL_QNAME}


// Represent the static state XML document resulting from the extractor
//
// - The underlying document produced by the extractor used to be further transformed to extract various documents.
//   This is no longer the case and the underlying document should be considered immutable (it would be good if it
//   was in fact immutable).
// - The template, when kept for full update marks, is stored in the static state document as Base64.
class StaticStateDocument(val xmlDocument: Document) {

  require(xmlDocument ne null)

  private def staticStateElement = xmlDocument.getRootElement

  // Pointers to nested elements
  def rootControl: Element = staticStateElement.element("root")
  def xblElements: Seq[Element] = rootControl.elements(XBL_XBL_QNAME)

  // TODO: if staticStateDocument contains XHTML document, get controls and models from there?

  // Return the last id generated
  def lastId: Int = {
    val idElement = staticStateElement.element(XFormsExtractor.LastIdQName)
    require(idElement ne null)
    idElement.idOrThrow.toInt
  }

  // Optional template as Base64
  def template: Option[String] = staticStateElement.elementOpt("template") map (_.getText)

  // Extract properties
  // NOTE: XFormsExtractor takes care of propagating only non-default properties
  val nonDefaultProperties: Map[String, (String, Boolean)] = {
    for {
      element       <- staticStateElement.elements(STATIC_STATE_PROPERTIES_QNAME)
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
    def nonDefault = nonDefaultProperties.get(P.StateHandlingProperty) map (_._1 == P.StateHandlingClientValue)
    def default    = P.SupportedDocumentProperties(P.StateHandlingProperty).defaultValue.toString == P.StateHandlingClientValue

    nonDefault getOrElse default
  }

  // Get the encoded static state
  // If an existing state is passed in, use it, otherwise encode from XML, encrypting if necessary.
  // NOTE: We do compress the result as we think we can afford this for the static state (probably not so for the dynamic state).
  def asBase64: String =
    EncodeDecode.encodeXML(xmlDocument, true, isClientStateHandling, true) // compress = true, encrypt = isClientStateHandling, location = true

  def dump(): Unit =
    println(xmlDocument.getRootElement.serializeToString(XMLWriter.PrettyFormat))
}