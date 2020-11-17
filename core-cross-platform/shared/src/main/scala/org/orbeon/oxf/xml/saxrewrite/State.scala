package org.orbeon.oxf.xml.saxrewrite

import org.orbeon.oxf.xml.XMLReceiver
import org.xml.sax.{Attributes, Locator}


/**
 * Base state. Simply forwards data to the destination content handler and returns itself. That is unless the (element)
 * depth becomes negative after an end element event. In this case the previous state is returned. This means btw that
 * we are really only considering state changes on start and end element events.
 */
abstract class State(
  val previousState : State,
  val xmlReceiver   : XMLReceiver
) {

  /**
   * At the moment are state transitions only happen on start element and end element events.
   * Therefore we track element depth and by default when the depth becomes negative we switch
   * to the previous state.
   */
  private var depth: Int = 0

  protected def endElementStart(ns: String, localName: String, qName: String): Unit =
    xmlReceiver.endElement(ns, localName, qName)

  protected def getDepth: Int = depth

  protected def startElementStart(ns: String, localName: String, qName: String, atts: Attributes): State

  def characters(ch: Array[Char], start: Int, len: Int): State = {
    xmlReceiver.characters(ch, start, len)
    this
  }

  def endDocument: State = {
    xmlReceiver.endDocument()
    this
  }

  final def endElement(ns: String, localName: String, qName: String): State = {
    endElementStart(ns, localName, qName)
    depth -= 1
    if (depth == 0)
      previousState
    else {
      this
    }
  }

  def endPrefixMapping(pfx: String): State = {
    xmlReceiver.endPrefixMapping(pfx)
    this
  }

  def ignorableWhitespace(ch: Array[Char], start: Int, len: Int): State = {
    xmlReceiver.ignorableWhitespace(ch, start, len)
    this
  }

  def processingInstruction(target: String, dat: String): State = {
    xmlReceiver.processingInstruction(target, dat)
    this
  }

  def setDocumentLocator(locator: Locator): State = {
    xmlReceiver.setDocumentLocator(locator)
    this
  }

  def skippedEntity(name: String): State = {
    xmlReceiver.skippedEntity(name)
    this
  }

  def startDocument: State = {
    xmlReceiver.startDocument()
    this
  }

  final def startElement(ns: String, localName: String, qName: String, atts: Attributes): State = {
    val ret = startElementStart(ns, localName, qName, atts)
    if (ret eq this)
      depth += 1
    ret
  }

  def startPrefixMapping(pfx: String, uri: String): State = {
    xmlReceiver.startPrefixMapping(pfx, uri)
    this
  }

  def startDTD(name: String, publicId: String, systemId: String): State = {
    xmlReceiver.startDTD(name, publicId, systemId)
    this
  }

  def endDTD: State = {
    xmlReceiver.endDTD()
    this
  }

  def startEntity(name: String): State = {
    xmlReceiver.startEntity(name)
    this
  }

  def endEntity(name: String): State = {
    xmlReceiver.endEntity(name)
    this
  }

  def startCDATA: State = {
    xmlReceiver.startCDATA()
    this
  }

  def endCDATA: State = {
    xmlReceiver.endCDATA()
    this
  }

  def comment(ch: Array[Char], start: Int, length: Int): State = {
    xmlReceiver.comment(ch, start, length)
    this
  }
}