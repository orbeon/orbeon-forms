/**
 * Copyright (C) 2012 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.scaxon


import javax.xml.namespace.QName

import org.orbeon.oxf.xml.SaxonUtils.parseQName
import org.orbeon.oxf.xml.{XMLReceiverAdapter, XMLUtils}
import org.xml.sax.{Attributes, Locator}

import scala.collection.mutable.ListBuffer

// Representation of all SAX events that are useful
// We skip: ignorableWhitespace, skippedEntity, startDTD/endDTD, startEntity/endEntity, and startCDATA/endCDATA.
object SAXEvents {

  sealed trait SAXEvent

  case class  DocumentLocator    (locator: Locator)             extends SAXEvent
  case object StartDocument                                     extends SAXEvent
  case object EndDocument                                       extends SAXEvent
  case class  StartElement       (qName: QName, atts: Atts)     extends SAXEvent
  case class  EndElement         (qName: QName)                 extends SAXEvent
  case class  Characters         (text: String)                 extends SAXEvent
  case class  Comment            (text: String)                 extends SAXEvent
  case class  PI                 (target: String, data: String) extends SAXEvent
  case class  StartPrefixMapping (prefix: String, uri: String)  extends SAXEvent
  case class  EndPrefixMapping   (prefix: String)               extends SAXEvent

 case class Atts(atts: List[(QName, String)]) extends Attributes {
    def getLength = atts.size

    def getURI(index: Int)       = if (inRange(index)) atts(index)._1.getNamespaceURI else null
    def getLocalName(index: Int) = if (inRange(index)) atts(index)._1.getLocalPart else null
    def getQName(index: Int)     = if (inRange(index)) XMLUtils.buildQName(getPrefix(index), getLocalName(index)) else null
    def getType(index: Int)      = if (inRange(index)) "CDATA" else null
    def getValue(index: Int)     = if (inRange(index)) atts(index)._2 else null

    def getIndex(uri: String, localName: String) =
      atts indexWhere { case (qName, _) => qName.getNamespaceURI == uri && qName.getLocalPart == localName }

    def getIndex(qName: String) = {
      val (prefix, localName) = parseQName(qName)
      atts indexWhere { case (qName, _) => qName.getPrefix == prefix && qName.getLocalPart == localName }
    }

    def getValue(uri: String, localName: String) = getValue(getIndex(uri, localName))
    def getValue(qName: String) = getValue(getIndex(qName))

    def getType(uri: String, localName: String) = getType(getIndex(uri, localName))
    def getType(qName: String) = getType(getIndex(qName))

    private def getPrefix(index: Int) = if (inRange(index)) atts(index)._1.getPrefix else null
    private def inRange(index: Int) = index >= 0 && index < atts.size

  }

  // Allow creating a StartElement with SAX-compatible parameters
  object StartElement {
    def apply(uri: String, localName: String, qName: String, atts: Attributes): StartElement =
      StartElement(new QName(uri, localName, XMLUtils.prefixFromQName(qName)), Atts(atts))
  }

  // Allow creating an EndElement with SAX-compatible parameters
  object EndElement {
    def apply(uri: String, localName: String, qName: String): EndElement =
      EndElement(new QName(uri, localName, XMLUtils.prefixFromQName(qName)))
  }

  object Atts {
    def apply(atts: Attributes): Atts = Atts(toSeq(atts))

    def toSeq(atts: Attributes) = {
      val length = atts.getLength
      val result = ListBuffer[(QName, String)]()
      var i = 0
      while (i < length) {
        result += new QName(atts.getURI(i), atts.getLocalName(i), XMLUtils.prefixFromQName(atts.getQName(i))) -> atts.getValue(i)
        i += 1
      }
      result.toList
    }

    implicit def tuplesToAtts1(atts: List[(QName, String)]): Atts = Atts(atts map { case (qName, value) => qName -> value })
    implicit def tuplesToAtts2(atts: List[(String, String)]): Atts = Atts(atts map { case (name, value) => new QName("", name, name) -> value })
  }

  object Characters {
    def apply(ch: Array[Char], start: Int, length: Int): Characters = Characters(new String(ch, start, length))
  }

  object Comment {
    def apply(ch: Array[Char], start: Int, length: Int): Characters = Characters(new String(ch, start, length))
  }
}

class DocumentAndElementsCollector extends XMLReceiverAdapter {

  import org.orbeon.scaxon.SAXEvents._

  private var _events = ListBuffer[SAXEvent]()
  def events: List[SAXEvent] = _events.result()

  override def startDocument()                                                               : Unit = _events += StartDocument
  override def endDocument()                                                                 : Unit = _events += EndDocument
  override def startPrefixMapping(prefix: String, uri: String)                               : Unit = _events += StartPrefixMapping(prefix, uri)
  override def endPrefixMapping(prefix: String)                                              : Unit = _events += EndPrefixMapping(prefix)
  override def startElement(uri: String, localName: String, qName: String, atts: Attributes) : Unit = _events += StartElement(uri, localName, qName, atts)
  override def endElement(uri: String, localName: String, qName: String)                     : Unit = _events += EndElement(uri, localName, qName)
}

class AllCollector extends XMLReceiverAdapter {

  import org.orbeon.scaxon.SAXEvents._

  private var _events = ListBuffer[SAXEvent]()
  def events: List[SAXEvent] = _events.result()

  override def setDocumentLocator(locator: Locator)                                          : Unit = _events += DocumentLocator(locator)
  override def startDocument()                                                               : Unit = _events += StartDocument
  override def endDocument()                                                                 : Unit = _events += EndDocument
  override def startPrefixMapping(prefix: String, uri: String)                               : Unit = _events += StartPrefixMapping(prefix, uri)
  override def endPrefixMapping(prefix: String)                                              : Unit = _events += EndPrefixMapping(prefix)
  override def startElement(uri: String, localName: String, qName: String, atts: Attributes) : Unit = _events += StartElement(uri, localName, qName, atts)
  override def endElement(uri: String, localName: String, qName: String)                     : Unit = _events += EndElement(uri, localName, qName)
  override def characters(ch: Array[Char], start: Int, length: Int)                          : Unit = _events += Characters(ch, start, length)
  override def comment(ch: Array[Char], start: Int, length: Int)                             : Unit = _events += Comment(ch, start, length)
  override def processingInstruction(target: String, data: String)                           : Unit = _events += PI(target, data)
}
