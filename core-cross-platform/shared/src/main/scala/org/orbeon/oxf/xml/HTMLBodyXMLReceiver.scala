package org.orbeon.oxf.xml

import org.xml.sax.*
import org.xml.sax.helpers.AttributesImpl


/**
 * This ContentHandler receives an XHTML document or pseudo-XHTML document (XHTML in no namespace), and outputs SAX
 * events for the content of the body only.
 */
class HTMLBodyXMLReceiver(xmlReceiver: XMLReceiver, private var xhtmlPrefix: String)
  extends ForwardingXMLReceiver(xmlReceiver) {

  private var level = 0
  private var inBody = false

  override def startDocument(): Unit = ()
  override def endDocument(): Unit = ()
  override def startPrefixMapping(s: String, s1: String): Unit = ()
  override def endPrefixMapping(s: String): Unit = ()
  override def setDocumentLocator(locator: Locator): Unit = ()

  @throws[SAXException]
  override def startElement(uri: String, localname: String, qName: String, attributes: Attributes): Unit = {
    if (! inBody && level == 1 && "body" == localname)
      inBody = true
    else if (inBody && level > 1) {
      val xhtmlQName = XMLUtils.buildQName(xhtmlPrefix, localname)
      var newAttributes: Attributes = null
      // Remove attributes which are in a namespace other than `""`
      val attributesCount = attributes.getLength
      if (attributesCount > 0) {
        val newAttributesImpl = new AttributesImpl
        for (i <- 0 until attributesCount) {
          val currentAttributeName = attributes.getLocalName(i)
          val currentAttributeValue = attributes.getValue(i)
          if ("" == attributes.getURI(i))
            newAttributesImpl.addAttribute("", currentAttributeName, currentAttributeName, XMLReceiverHelper.CDATA, currentAttributeValue)
        }
        newAttributes = newAttributesImpl
      }
      else
        newAttributes = attributes
      super.startElement(XMLConstants.XHTML_NAMESPACE_URI, localname, xhtmlQName, newAttributes)
    }
    level += 1
  }

  @throws[SAXException]
  override def endElement(uri: String, localname: String, qName: String): Unit = {
    level -= 1
    if (inBody && level == 1)
      inBody = false
    else if (inBody && level > 1) {
      val xhtmlQName = XMLUtils.buildQName(xhtmlPrefix, localname)
      super.endElement(XMLConstants.XHTML_NAMESPACE_URI, localname, xhtmlQName)
    }
  }

  @throws[SAXException]
  override def characters(chars: Array[Char], start: Int, length: Int): Unit = {
    if (inBody)
      super.characters(chars, start, length)
  }

  @throws[SAXException]
  override def ignorableWhitespace(chars: Array[Char], start: Int, length: Int): Unit = {
    if (inBody)
      super.ignorableWhitespace(chars, start, length)
  }

  @throws[SAXException]
  override def processingInstruction(s: String, s1: String): Unit = {
    if (inBody)
      super.processingInstruction(s, s1)
  }

  @throws[SAXException]
  override def skippedEntity(s: String): Unit = {
    if (inBody)
      super.skippedEntity(s)
  }
}