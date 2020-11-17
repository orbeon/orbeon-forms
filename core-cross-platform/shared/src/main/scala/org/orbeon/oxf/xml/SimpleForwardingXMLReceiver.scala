package org.orbeon.oxf.xml

import org.xml.sax.{Attributes, ContentHandler, Locator}
import org.xml.sax.ext.LexicalHandler


/**
 * Simple `XMLReceiver` able to forward to another `XMLReceiver` or `ContentHandler`.
 */
class SimpleForwardingXMLReceiver(out: ContentHandler Either XMLReceiver) extends XMLReceiver {

  private var contentHandler: ContentHandler = out.fold(identity, identity)
  private var lexicalHandler: LexicalHandler = out.right.getOrElse(null)

  require(contentHandler ne null)

  def this(xmlReceiver: XMLReceiver) =
    this(Right(xmlReceiver))

  def this(contentHandler: ContentHandler) =
    this(Left(contentHandler))

  override def characters(chars: Array[Char], start: Int, length: Int): Unit =
    contentHandler.characters(chars, start, length)

  override def endDocument(): Unit = {
    contentHandler.endDocument()
    contentHandler = null
    lexicalHandler = null
  }

  override def endElement(uri: String, localname: String, qName: String): Unit =
    contentHandler.endElement(uri, localname, qName)

  override def endPrefixMapping(s: String): Unit =
    contentHandler.endPrefixMapping(s)

  override def ignorableWhitespace(chars: Array[Char], start: Int, length: Int): Unit =
    contentHandler.ignorableWhitespace(chars, start, length)

  override def processingInstruction(s: String, s1: String): Unit =
    contentHandler.processingInstruction(s, s1)

  override def setDocumentLocator(locator: Locator): Unit =
    contentHandler.setDocumentLocator(locator)

  override def skippedEntity(s: String): Unit =
    contentHandler.skippedEntity(s)

  override def startDocument(): Unit =
    contentHandler.startDocument()

  override def startElement(uri: String, localname: String, qName: String, attributes: Attributes): Unit =
    contentHandler.startElement(uri, localname, qName, attributes)

  override def startPrefixMapping(s: String, s1: String): Unit =
    contentHandler.startPrefixMapping(s, s1)

  override def startDTD(name: String, publicId: String, systemId: String): Unit =
    if (lexicalHandler ne null) lexicalHandler.startDTD(name, publicId, systemId)

  override def endDTD(): Unit =
    if (lexicalHandler ne null) lexicalHandler.endDTD()

  override def startEntity(name: String): Unit =
    if (lexicalHandler ne null) lexicalHandler.startEntity(name)

  override def endEntity(name: String): Unit =
    if (lexicalHandler ne null) lexicalHandler.endEntity(name)

  override def startCDATA(): Unit =
    if (lexicalHandler ne null) lexicalHandler.startCDATA()

  override def endCDATA(): Unit =
    if (lexicalHandler ne null) lexicalHandler.endCDATA()

  override def comment(ch: Array[Char], start: Int, length: Int): Unit =
    if (lexicalHandler ne null) lexicalHandler.comment(ch, start, length)

  def endDocumentCalled: Boolean = contentHandler eq null
}