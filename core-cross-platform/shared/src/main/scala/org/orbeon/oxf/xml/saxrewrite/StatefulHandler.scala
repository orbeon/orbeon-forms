package org.orbeon.oxf.xml.saxrewrite

import org.orbeon.oxf.xml.XMLReceiver
import org.xml.sax.{Attributes, Locator}


/**
 * Driver for a state machine that response to SAX events. Just forwards SAX events to a State which in turn returns
 * the next State.
 */
final class StatefulHandler(var state: State) extends XMLReceiver {

  def characters(ch: Array[Char], strt: Int, len: Int): Unit =
    state = state.characters(ch, strt, len)

  def endDocument(): Unit =
    state = state.endDocument

  def endElement(ns: String, lnam: String, qnam: String): Unit =
    state = state.endElement(ns, lnam, qnam)

  def endPrefixMapping(pfx: String): Unit =
    state = state.endPrefixMapping(pfx)

  def ignorableWhitespace(ch: Array[Char], strt: Int, len: Int): Unit =
    state = state.ignorableWhitespace(ch, strt, len)

  def processingInstruction(trgt: String, dat: String): Unit =
    state = state.processingInstruction(trgt, dat)

  def setDocumentLocator(loc: Locator): Unit =
    state = state.setDocumentLocator(loc)

  def skippedEntity(nam: String): Unit =
    state = state.skippedEntity(nam)

  def startDocument(): Unit =
    state = state.startDocument

  def startElement(ns: String, lnam: String, qnam: String, atts: Attributes): Unit =
    state = state.startElement(ns, lnam, qnam, atts)

  def startPrefixMapping(pfx: String, uri: String): Unit =
    state = state.startPrefixMapping(pfx, uri)

  def startDTD(name: String, publicId: String, systemId: String): Unit =
    state = state.startDTD(name, publicId, systemId)

  def endDTD(): Unit =
    state = state.endDTD

  def startEntity(name: String): Unit =
    state = state.startEntity(name)

  def endEntity(name: String): Unit =
    state = state.endEntity(name)

  def startCDATA(): Unit =
    state = state.startCDATA

  def endCDATA(): Unit =
    state = state.endCDATA

  def comment(ch: Array[Char], start: Int, length: Int): Unit =
    state = state.comment(ch, start, length)
}