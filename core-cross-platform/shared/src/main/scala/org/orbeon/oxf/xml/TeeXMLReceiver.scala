package org.orbeon.oxf.xml

import java.util

import org.xml.sax.{Attributes, Locator}


class TeeXMLReceiver extends XMLReceiver {

  // NOTE: Use an` Array`, as `List` and `Iterator` are less efficient (profiling)
  private var xmlReceivers: Array[XMLReceiver] = null

  def this(receivers: util.List[XMLReceiver]) = {
    this()
    xmlReceivers = new Array[XMLReceiver](receivers.size)
    receivers.toArray(xmlReceivers)
  }

  def this(xmlReceiver1: XMLReceiver, xmlReceiver2: XMLReceiver) = {
    this()
    xmlReceivers = new Array[XMLReceiver](2)
    xmlReceivers(0) = xmlReceiver1
    xmlReceivers(1) = xmlReceiver2
  }

  def this(xmlReceiver1: XMLReceiver, xmlReceiver2: XMLReceiver, xmlReceiver3: XMLReceiver) = {
    this()
    xmlReceivers = new Array[XMLReceiver](3)
    xmlReceivers(0) = xmlReceiver1
    xmlReceivers(1) = xmlReceiver2
    xmlReceivers(2) = xmlReceiver3
  }

  def setDocumentLocator(locator: Locator): Unit =
    for (i <- xmlReceivers.indices) {
      val contentHandler = xmlReceivers(i)
      contentHandler.setDocumentLocator(locator)
    }

  def startDocument(): Unit =
    for (i <- xmlReceivers.indices) {
      val contentHandler = xmlReceivers(i)
      contentHandler.startDocument()
    }

  def endDocument(): Unit =
    for (i <- xmlReceivers.indices) {
      val contentHandler = xmlReceivers(i)
      contentHandler.endDocument()
    }

  def startPrefixMapping(prefix: String, uri: String): Unit =
    for (i <- xmlReceivers.indices) {
      val contentHandler = xmlReceivers(i)
      contentHandler.startPrefixMapping(prefix, uri)
    }

  def endPrefixMapping(prefix: String): Unit =
    for (i <- xmlReceivers.indices) {
      val contentHandler = xmlReceivers(i)
      contentHandler.endPrefixMapping(prefix)
    }

  def startElement(namespaceURI: String, localName: String, qName: String, atts: Attributes): Unit =
    for (i <- xmlReceivers.indices) {
      val contentHandler = xmlReceivers(i)
      contentHandler.startElement(namespaceURI, localName, qName, atts)
    }

  def endElement(namespaceURI: String, localName: String, qName: String): Unit =
    for (i <- xmlReceivers.indices) {
      val contentHandler = xmlReceivers(i)
      contentHandler.endElement(namespaceURI, localName, qName)
    }

  def characters(ch: Array[Char], start: Int, length: Int): Unit =
    for (i <- xmlReceivers.indices) {
      val contentHandler = xmlReceivers(i)
      contentHandler.characters(ch, start, length)
    }

  def ignorableWhitespace(ch: Array[Char], start: Int, length: Int): Unit =
    for (i <- xmlReceivers.indices) {
      val contentHandler = xmlReceivers(i)
      contentHandler.ignorableWhitespace(ch, start, length)
    }

  def processingInstruction(target: String, data: String): Unit =
    for (i <- xmlReceivers.indices) {
      val contentHandler = xmlReceivers(i)
      contentHandler.processingInstruction(target, data)
    }

  def skippedEntity(name: String): Unit =
    for (i <- xmlReceivers.indices) {
      val contentHandler = xmlReceivers(i)
      contentHandler.skippedEntity(name)
    }

  def startDTD(name: String, publicId: String, systemId: String): Unit =
    for (xmlReceiver <- xmlReceivers)
      xmlReceiver.startDTD(name, publicId, systemId)

  def endDTD(): Unit =
    for (xmlReceiver <- xmlReceivers)
      xmlReceiver.endDTD()

  def startEntity(name: String): Unit =
    for (xmlReceiver <- xmlReceivers)
      xmlReceiver.startEntity(name)

  def endEntity(name: String): Unit =
    for (xmlReceiver <- xmlReceivers)
      xmlReceiver.endEntity(name)

  def startCDATA(): Unit =
    for (xmlReceiver <- xmlReceivers)
      xmlReceiver.startCDATA()

  def endCDATA(): Unit =
    for (xmlReceiver <- xmlReceivers)
      xmlReceiver.endCDATA()

  def comment(ch: Array[Char], start: Int, length: Int): Unit =
    for (xmlReceiver <- xmlReceivers)
      xmlReceiver.comment(ch, start, length)
}