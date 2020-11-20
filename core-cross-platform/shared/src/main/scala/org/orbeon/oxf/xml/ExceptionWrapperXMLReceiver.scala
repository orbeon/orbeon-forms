package org.orbeon.oxf.xml

import java.{lang => jl, util => ju}

import org.orbeon.oxf.common.OrbeonLocationException
import org.orbeon.oxf.xml.dom.{XmlExtendedLocationData, XmlLocationData}
import org.xml.sax.{Attributes, ContentHandler, Locator, SAXException}


/**
 * XMLReceiver which wraps exceptions with location information if possible.
 */
class ExceptionWrapperXMLReceiver(out: ContentHandler Either XMLReceiver) extends SimpleForwardingXMLReceiver(out) {

  private var locator: Locator = null
  private var message: String = null

  def this(xmlReceiver: XMLReceiver, message: String) = {
    this(Right(xmlReceiver))
    this.message = message
  }

  def this(contentHandler: ContentHandler, message: String) = {
    this(Left(contentHandler))
    this.message = message
  }

  override def setDocumentLocator(locator: Locator): Unit = {
    super.setDocumentLocator(locator)
    this.locator = locator
  }

  override def startElement(uri: String, localname: String, qName: String, attributes: Attributes): Unit =
    try super.startElement(uri, localname, qName, attributes)
    catch {
      case e: RuntimeException => wrapException(e, uri, qName, attributes)
    }

  override def endElement(uri: String, localname: String, qName: String): Unit =
    try super.endElement(uri, localname, qName)
    catch {
      case e: RuntimeException => wrapException(e)
    }

  override def characters(chars: Array[Char], start: Int, length: Int): Unit =
    try super.characters(chars, start, length)
    catch {
      case e: RuntimeException => wrapException(e)
    }

  override def startPrefixMapping(s: String, s1: String): Unit =
    try super.startPrefixMapping(s, s1)
    catch {
      case e: RuntimeException => wrapException(e)
    }

  override def endPrefixMapping(s: String): Unit =
    try super.endPrefixMapping(s)
    catch {
      case e: RuntimeException => wrapException(e)
    }

  override def ignorableWhitespace(chars: Array[Char], start: Int, length: Int): Unit =
    try super.ignorableWhitespace(chars, start, length)
    catch {
      case e: RuntimeException => wrapException(e)
    }

  override def skippedEntity(s: String): Unit =
    try super.skippedEntity(s)
    catch {
      case e: RuntimeException => wrapException(e)
    }

  override def processingInstruction(s: String, s1: String): Unit =
    try super.processingInstruction(s, s1)
    catch {
      case e: RuntimeException => wrapException(e)
    }

  override def endDocument(): Unit =
    try super.endDocument()
    catch {
      case e: RuntimeException => wrapException(e)
    }

  override def startDocument(): Unit =
    try super.startDocument()
    catch {
      case e: RuntimeException => wrapException(e)
    }

  override def startDTD(name: String, publicId: String, systemId: String): Unit =
    try super.startDTD(name, publicId, systemId)
    catch {
      case e: RuntimeException => wrapException(e)
    }

  override def endDTD(): Unit =
    try super.endDTD()
    catch {
      case e: RuntimeException => wrapException(e)
    }

  override def startEntity(name: String): Unit =
    try super.startEntity(name)
    catch {
      case e: RuntimeException => wrapException(e)
    }

  override def endEntity(name: String): Unit =
    try super.endEntity(name)
    catch {
      case e: RuntimeException => wrapException(e)
    }

  override def startCDATA(): Unit =
    try super.startCDATA()
    catch {
      case e: RuntimeException => wrapException(e)
    }

  override def endCDATA(): Unit =
    try super.endCDATA()
    catch {
      case e: RuntimeException => wrapException(e)
    }

  override def comment(ch: Array[Char], start: Int, length: Int): Unit =
    try super.comment(ch, start, length)
    catch {
      case e: RuntimeException => wrapException(e)
    }

  private def wrapException(e: Exception): Unit = {
    if (locator ne null)
      throw OrbeonLocationException.wrapException(e, XmlExtendedLocationData(XmlLocationData(locator), message))
    else e match {
      case exception: SAXException     => throw exception
      case exception: RuntimeException => throw exception
      case _ => throw new IllegalStateException
    }
  }

  private def wrapException(e: Exception, uri: String, qName: String, attributes: Attributes): Unit = {
    if (locator ne null)
      throw OrbeonLocationException.wrapException(e, XmlExtendedLocationData(XmlLocationData(locator), message, Array[String]("element", saxElementToDebugString(uri, qName, attributes))))
    else e match {
      case exception: SAXException     => throw exception
      case exception: RuntimeException => throw exception
      case _ => throw new IllegalStateException
    }
  }

  def saxElementToDebugString(uri: String, qName: String, attributes: Attributes): String = {
    // Open start tag
    val sb = new jl.StringBuilder("<")
    sb.append(qName)
    val declaredPrefixes = new ju.HashSet[String]
    mapPrefixIfNeeded(declaredPrefixes, uri, qName, sb)
    // Attributes if any
    for (i <- 0 until attributes.getLength) {
      mapPrefixIfNeeded(declaredPrefixes, attributes.getURI(i), attributes.getQName(i), sb)
      sb.append(' ')
      sb.append(attributes.getQName(i))
      sb.append("=\"")
      sb.append(attributes.getValue(i))
      sb.append('\"')
    }
    // Close start tag
    sb.append('>')
    // Content
    sb.append("[...]")
    // Close element with end tag
    sb.append("</")
    sb.append(qName)
    sb.append('>')
    sb.toString
  }

  private def mapPrefixIfNeeded(declaredPrefixes: ju.Set[String], uri: String, qName: String, sb: jl.StringBuilder) {
    val prefix = XMLUtils.prefixFromQName(qName)
    if (prefix.length > 0 && !declaredPrefixes.contains(prefix)) {
      sb.append(" xmlns:")
      sb.append(prefix)
      sb.append("=\"")
      sb.append(uri)
      sb.append("\"")
      declaredPrefixes.add(prefix)
    }
  }
}