package org.orbeon.oxf.xml

import org.orbeon.oxf.common.OXFException
import org.xml.sax.Attributes
import org.xml.sax.helpers.AttributesImpl


/**
 * Receiver with an additional method allowing for adding attributes.
 */
class DeferredXMLReceiverImpl(val xmlReceiver: XMLReceiver)
  extends ForwardingXMLReceiver(xmlReceiver)
    with DeferredXMLReceiver {

  private var storedElement                  = false
  private var uri           : String         = null
  private var localname     : String         = null
  private var qName         : String         = null
  private var attributes    : AttributesImpl = null

  override def startElement(uri: String, localname: String, qName: String, attributes: Attributes): Unit = {
    flush()
    storedElement   = true
    this.uri        = uri
    this.localname  = localname
    this.qName      = qName
    this.attributes = new AttributesImpl(attributes)
  }

  def addAttribute(localname: String, value: String): Unit =
    addAttribute("", localname, localname, value)

  override def addAttribute(uri: String, localname: String, qName: String, value: String): Unit = {
    if (! storedElement)
      throw new OXFException("addAttribute called within no element.")
    attributes.addAttribute(uri, localname, qName, "CDATA", value)
  }

  override def characters(chars: Array[Char], start: Int, length: Int): Unit = {
    flush()
    super.characters(chars, start, length)
  }

  override def endDocument(): Unit =
    super.endDocument()

  override def endElement(uri: String, localname: String, qName: String): Unit = {
    flush()
    super.endElement(uri, localname, qName)
  }

  private def flush(): Unit =
    if (storedElement) {
      super.startElement(uri, localname, qName, attributes)
      storedElement = false
    }
}