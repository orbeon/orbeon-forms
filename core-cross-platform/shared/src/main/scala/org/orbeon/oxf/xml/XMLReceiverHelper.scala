package org.orbeon.oxf.xml

import org.orbeon.oxf.common.OXFException
import org.xml.sax.{Attributes, ContentHandler, SAXException}
import org.xml.sax.helpers.AttributesImpl


/**
 * Wrapper to an XML receiver. Provides more high-level methods to send events to a XML receiver.
 */
object XMLReceiverHelper {

  val CDATA = "CDATA"

  private case class ElementInfo private (uri: String, name: String, qName: String)

  def populateAttributes(attributesImpl: AttributesImpl, attributes: Array[String]): Unit =
    if (attributes != null)
      for (i <- 0 until attributes.length / 2) {
        val attributeName = attributes(i * 2)
        val attributeValue = attributes(i * 2 + 1)
        if (attributeName != null && attributeValue != null) attributesImpl.addAttribute("", attributeName, attributeName, CDATA, attributeValue)
      }

  // Copied from `SAXUtils`
  def streamNullDocument(contentHandler: ContentHandler): Unit = {
    contentHandler.startDocument()
    contentHandler.startPrefixMapping(XMLConstants.XSI_PREFIX, XMLConstants.XSI_URI)
    val attributes = new AttributesImpl
    attributes.addAttribute(XMLConstants.XSI_URI, "nil", "xsi:nil", "CDATA", "true")
    contentHandler.startElement("", "null", "null", attributes)
    contentHandler.endElement("", "null", "null")
    contentHandler.endPrefixMapping(XMLConstants.XSI_PREFIX)
    contentHandler.endDocument()
  }
}

class XMLReceiverHelper(xmlReceiver: XMLReceiver) {

  private var elements: List[XMLReceiverHelper.ElementInfo] = Nil
  private val attributesImpl = new AttributesImpl

  /**
   * ContentHandler to write to.
   *
   * @param xmlReceiver    receiver to write to
   * @param validateStream true if the stream must be validated by InspectingContentHandler
   */
  def this(xmlReceiver: XMLReceiver, validateStream: Boolean) =
    this(
      if (validateStream)
        new InspectingXMLReceiver(xmlReceiver)
      else
        xmlReceiver
    )

  def getXmlReceiver: XMLReceiver = xmlReceiver

  def startElement(name: String): Unit =
    startElement("", name)

  def startElement(namespaceURI: String, name: String): Unit =
    startElement("", namespaceURI, name)

  def startElement(prefix: String, namespaceURI: String, name: String): Unit = {
    attributesImpl.clear()
    startElement(prefix, namespaceURI, name, attributesImpl)
  }

  def startElement(name: String, attributes: Attributes): Unit =
    startElement("", "", name, attributes)

  def startElement(prefix: String, namespaceURI: String, name: String, attributes: Attributes): Unit =
    try {
      val qName = XMLUtils.buildQName(prefix, name)
      xmlReceiver.startElement(namespaceURI, name, qName, attributes)
      elements ::= XMLReceiverHelper.ElementInfo(namespaceURI, name, qName)
    } catch {
      case e: SAXException =>
        throw new OXFException(e)
    }

  def startElement(name: String, attributes: Array[String]): Unit =
    startElement("", name, attributes)

  def startElement(namespaceURI: String, name: String, attributes: Array[String]): Unit =
    startElement("", namespaceURI, name, attributes)

  def startElement(prefix: String, namespaceURI: String, name: String, attributes: Array[String]): Unit = {
    attributesImpl.clear()
    XMLReceiverHelper.populateAttributes(attributesImpl, attributes)
    startElement(prefix, namespaceURI, name, attributesImpl)
  }

  def endElement(): Unit =
    try {
      val elementInfo = elements.head
      elements = elements.tail
      xmlReceiver.endElement(elementInfo.uri, elementInfo.name, elementInfo.qName)
    } catch {
      case e: SAXException =>
        throw new OXFException(e)
    }

  def element(prefix: String, namespaceURI: String, name: String, attributes: Attributes): Unit = {
    startElement(prefix, namespaceURI, name, attributes)
    endElement()
  }

  def element(namespaceURI: String, name: String, attributes: Array[String]): Unit = {
    startElement("", namespaceURI, name, attributes)
    endElement()
  }

  def element(name: String, attributes: Array[String]): Unit = {
    startElement("", "", name, attributes)
    endElement()
  }

  def element(prefix: String, namespaceURI: String, name: String, attributes: Array[String]): Unit = {
    startElement(prefix, namespaceURI, name, attributes)
    endElement()
  }

  def element(name: String, text: String): Unit =
    element("", name, text)

  def element(namespaceURI: String, name: String, text: String): Unit =
    element("", namespaceURI, name, text)

  def element(prefix: String, namespaceURI: String, name: String, text: String): Unit = {
    startElement(prefix, namespaceURI, name)
    this.text(text)
    endElement()
  }

  def element(name: String, number: Long): Unit =
    element("", name, number)

  private def element(namespaceURI: String, name: String, number: Long): Unit =
    element("", namespaceURI, name, number)

  private def element(prefix: String, namespaceURI: String, name: String, number: Long): Unit = {
    attributesImpl.clear()
    startElement(prefix, namespaceURI, name)
    text(number.toString)
    endElement()
  }

  def text(text: String): Unit =
    try if (text != null) xmlReceiver.characters(text.toCharArray, 0, text.length)
    catch {
      case e: SAXException =>
        throw new OXFException(e)
    }

  def startDocument(): Unit =
    try xmlReceiver.startDocument()
    catch {
      case e: SAXException =>
        throw new OXFException(e)
    }

  def endDocument(): Unit =
    try {
      if (elements.nonEmpty)
        throw new OXFException(s"Element `${elements.head}` not closed")
      xmlReceiver.endDocument()
    } catch {
      case e: SAXException =>
        throw new OXFException(e)
    }

  def startPrefixMapping(prefix: String, uri: String): Unit =
    try xmlReceiver.startPrefixMapping(prefix, uri)
    catch {
      case e: SAXException =>
        throw new OXFException(e)
    }

  def endPrefixMapping(prefix: String): Unit =
    try xmlReceiver.endPrefixMapping(prefix)
    catch {
      case e: SAXException =>
        throw new OXFException(e)
    }
}