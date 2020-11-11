package org.orbeon.oxf.xml

import java.util

import org.orbeon.oxf.common.ValidationException
import org.orbeon.oxf.xml.dom.XmlLocationData
import org.xml.sax.{Attributes, Locator}
import org.xml.sax.helpers.AttributesImpl


/**
 * Just like ForwardingXMLReceiver (a SAX handler that forwards SAX events to another handler), but
 * checks the validity of the SAX stream.
 *
 * TODO: check for duplicate attributes.
 */
object InspectingXMLReceiver {

  private class NameInfo(var uri: String, var localname: String, var qname: String, var attributes: AttributesImpl) {

    def compareNames(other: InspectingXMLReceiver.NameInfo): Boolean = {
      if (!(uri == other.uri)) return false
      if (!(localname == other.localname)) return false
      if (!(qname == other.qname)) return false
      true
    }

    override def toString = {
      val sb = new StringBuilder
      sb.append('[')
      sb.append("uri = ")
      sb.append(uri)
      sb.append(" | localname = ")
      sb.append(localname)
      sb.append(" | qname = ")
      sb.append(qname)
      sb.append(']')
      sb.toString
    }
  }
}

class InspectingXMLReceiver(val xmlReceiver: XMLReceiver) extends ForwardingXMLReceiver(xmlReceiver) {

  private var locator: Locator = null
  private val elementStack = new util.Stack[InspectingXMLReceiver.NameInfo]
  private var documentStarted = false
  private var documentEnded = false
  private val namespaceContext = new NamespaceContext

  override def startDocument() {
    if (documentStarted)
      throw new ValidationException("startDocument() called twice", XmlLocationData(locator))
    documentStarted = true
    super.startDocument()
  }

  override def endDocument() {
    if (elementStack.size != 0)
      throw new ValidationException("Document ended before all the elements are closed", XmlLocationData(locator))
    if (documentEnded)
      throw new ValidationException("endDocument() called twice", XmlLocationData(locator))
    documentEnded = true
    super.endDocument()
  }

  override def startElement(uri: String, localname: String, qname: String, attributes: Attributes) {
    namespaceContext.startElement()
    val error = checkInDocument
    if (error != null)
      throw new ValidationException(error + ": element " + qname, XmlLocationData(locator))
    elementStack.push(new InspectingXMLReceiver.NameInfo(uri, localname, qname, new AttributesImpl(attributes)))
    // Check names
    checkElementName(uri, localname, qname)
    for (i <- 0 until attributes.getLength)
      checkAttributeName(attributes.getURI(i), attributes.getLocalName(i), attributes.getQName(i))
    super.startElement(uri, localname, qname, attributes)
  }

  override def endElement(uri: String, localname: String, qname: String) {
    val error = checkInElement
    if (error != null)
      throw new ValidationException(error + ": element " + qname, XmlLocationData(locator))
    val startElementNameInfo = elementStack.pop
    val endElementNameInfo = new InspectingXMLReceiver.NameInfo(uri, localname, qname, null)
    if (! startElementNameInfo.compareNames(endElementNameInfo))
      throw new ValidationException("endElement() doesn't match startElement(). startElement(): " + startElementNameInfo.toString + "; endElement(): " + endElementNameInfo.toString, XmlLocationData(locator))
    // Check name
    checkElementName(uri, localname, qname)
    namespaceContext.endElement()
    super.endElement(uri, localname, qname)
  }

  override def characters(chars: Array[Char], start: Int, length: Int) {
    val error = checkInElement
    if (error != null)
      throw new ValidationException(error + ": '" + new String(chars, start, length) + "'", XmlLocationData(locator))
    super.characters(chars, start, length)
  }

  override def setDocumentLocator(locator: Locator) {
    this.locator = locator
    super.setDocumentLocator(locator)
  }

  private def checkInElement: String = {
    val error = checkInDocument
    if (error != null)
      error
    else if (elementStack.size == 0)
      "SAX event received after close of root element"
    else
      null
  }

  private def checkInDocument: String =
    if (! documentStarted)
      "SAX event received before document start"
    else if (documentEnded)
      "SAX event received after document end"
    else null

  private def checkAttributeName(uri: String, localname: String, qname: String): Unit = {
    if (uri != null && qname != null && !("" == uri) && qname.indexOf(':') == -1)
      throw new ValidationException("Non-prefixed attribute cannot be in a namespace. URI: " + uri + "; localname: " + localname + "; QName: " + qname, XmlLocationData(locator))
    checkName(uri, localname, qname)
  }

  private def checkElementName(uri: String, localname: String, qname: String): Unit =
    checkName(uri, localname, qname)

  override def startPrefixMapping(prefix: String, uri: String): Unit = {
    namespaceContext.startPrefixMapping(prefix, uri)
    super.startPrefixMapping(prefix, uri)
  }

  private def checkName(uri: String, localname: String, qname: String): Unit = {
    if ((localname eq null) || localname.isEmpty)
      throw new ValidationException("Empty local name in SAX event. QName: " + qname, XmlLocationData(locator))
    if ((qname eq null) || qname.isEmpty)
      throw new ValidationException("Empty qualified name in SAX event. Localname: " + localname + "; QName: " + qname, XmlLocationData(locator))
    if (uri == null)
      throw new ValidationException("Null URI. Localname: " + localname, XmlLocationData(locator))
    if (uri == "" && !(localname == qname))
      throw new ValidationException("Localname and QName must be equal when name is in no namespace. Localname: " + localname + "; QName: " + qname, XmlLocationData(locator))
    if (!(uri == "") && !(localname == qname.substring(qname.indexOf(':') + 1)))
      throw new ValidationException("Local part or QName must be equal to localname when name is in namespace. Localname: " + localname + "; QName: " + qname, XmlLocationData(locator))

    val colonIndex = qname.indexOf(':')
    // Check namespace mappings
    if (!(uri == "")) {
      // We are in a namespace
      if (colonIndex == -1) {
        // QName is not prefixed, check that we match the default namespace
        if (!(uri == namespaceContext.getURI("")))
          throw new ValidationException("Namespace doesn't match default namespace. Namespace: " + uri + "; QName: " + qname, XmlLocationData(locator))
      } else if (colonIndex == 0 || colonIndex == (qname.length - 1)) {
        // Invalid position of colon in QName
        throw new ValidationException("Invalid position of colon in QName: " + qname, XmlLocationData(locator))
      } else {
        // Name is prefixed: check that prefix is bound and maps to namespace
        val prefix = qname.substring(0, colonIndex)
        if (namespaceContext.getURI(prefix) == null)
          throw new ValidationException("QName prefix is not in scope: " + qname, XmlLocationData(locator))
        if (!(uri == namespaceContext.getURI(prefix)))
          throw new ValidationException("QName prefix maps to URI: " + namespaceContext.getURI(prefix) + "; but namespace provided is: " + uri, XmlLocationData(locator))
      }
    } else {
      // We are not in a namespace
      if (colonIndex != -1)
        throw new ValidationException("QName has prefix but we are not in a namespace: " + qname, XmlLocationData(locator))
    }
  }
}