/**
 *  Copyright (C) 2007 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.scaxon

import org.xml.sax.{ContentHandler, Locator, Attributes}
import org.xml.sax.ext.LexicalHandler
import javax.xml.namespace.QName
import org.orbeon.oxf.xml.{NamespaceContext, XMLUtils} // TODO: remove dependency on this or move dependency to Scaxon
import scala.collection.mutable.ListBuffer

// FSM specifically handling SAX events
object SAXMachine {
    // All SAX events of interest
    sealed trait SAXEvent
    case class DocumentLocator(locator: Locator) extends SAXEvent
    case object StartDocument extends SAXEvent
    case object EndDocument extends SAXEvent
    case class StartElement(qName: QName, atts: Atts) extends SAXEvent
    case class EndElement(qName: QName) extends SAXEvent
    case class Characters(ch: Array[Char], start: Int, length: Int) extends SAXEvent
    case class Comment(ch: Array[Char], start: Int, length: Int) extends SAXEvent
    case class PI(target: String, data: String) extends SAXEvent
    case class StartPrefixMapping(prefix: String, uri: String) extends SAXEvent
    case class EndPrefixMapping(prefix: String) extends SAXEvent

    case class Atts(atts: Seq[(QName, String)]) extends Attributes {
        def getLength = atts.size

        def getURI(index: Int)       = if (inRange(index)) atts(index)._1.getNamespaceURI else null
        def getLocalName(index: Int) = if (inRange(index)) atts(index)._1.getLocalPart else null
        def getQName(index: Int)     = if (inRange(index)) XMLUtils.buildQName(getPrefix(index), getLocalName(index)) else null
        def getType(index: Int)      = if (inRange(index)) "CDATA" else null
        def getValue(index: Int)     = if (inRange(index)) atts(index)._2 else null

        def getIndex(uri: String, localName: String) =
            atts indexWhere { case (qName, _) ⇒ qName.getNamespaceURI == uri && qName.getLocalPart == localName }

        def getIndex(qName: String) = {
            val (prefix, localName) = XML.parseQName(qName)
            atts indexWhere { case (qName, _) ⇒ qName.getPrefix == prefix && qName.getLocalPart == localName }
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
        def apply(namespaceURI: String, localName: String, qName: String, atts: Attributes): StartElement =
            StartElement(new QName(namespaceURI, localName, XMLUtils.prefixFromQName(qName)), Atts(atts))
    }

    // Allow creating an EndElement with SAX-compatible parameters
    object EndElement {
        def apply(namespaceURI: String, localName: String, qName: String): EndElement =
            EndElement(new QName(namespaceURI, localName, XMLUtils.prefixFromQName(qName)))
    }

    object Atts {
        def apply(atts: Attributes): Atts = Atts(toSeq(atts))

        def toSeq(atts: Attributes) = {
            val length = atts.getLength
            val result = ListBuffer[(QName, String)]()
            var i = 0
            while (i < length) {
                result += new QName(atts.getURI(i), atts.getLocalName(i), XMLUtils.prefixFromQName(atts.getQName(i))) → atts.getValue(i)
                i += 1
            }
            result.toList
        }
    }
}

trait SAXMachine[S, D] extends FSM[S, SAXMachine.SAXEvent, D] with ContentHandler with LexicalHandler {

    import SAXMachine._
    
    // Get current element level
    private var _level = 0
    protected def depth = _level

    protected val namespaceContext: NamespaceContext = new NamespaceContext

    // Forward the given SAX event to the output
    final protected def forward(out: ContentHandler with LexicalHandler, saxEvent: SAXEvent) = saxEvent match {
        case StartDocument ⇒ out.startDocument()
        case EndDocument ⇒ out.endDocument()
        case StartElement(qName: QName, atts: Atts) ⇒ out.startElement(qName.getNamespaceURI, qName.getLocalPart, XMLUtils.buildQName(qName.getPrefix, qName.getLocalPart), atts)
        case EndElement(qName: QName) ⇒ out.endElement(qName.getNamespaceURI, qName.getLocalPart, XMLUtils.buildQName(qName.getPrefix, qName.getLocalPart))
        case Characters(ch: Array[Char], start: Int, length: Int) ⇒ out.characters(ch, start, length)
        case Comment(ch: Array[Char], start: Int, length: Int) ⇒ out.comment(ch, start, length)
        case PI(target: String, data: String) ⇒ out.processingInstruction(target, data)
        case StartPrefixMapping(prefix: String, uri: String) ⇒ out.startPrefixMapping(prefix, uri)
        case EndPrefixMapping(prefix: String) ⇒ out.endPrefixMapping(prefix)
        case DocumentLocator(locator) ⇒ out.setDocumentLocator(locator)
    }

    // Useful SAX events
    final override def setDocumentLocator(locator: Locator) = processEvent(DocumentLocator(locator))
    final override def startDocument() = processEvent(StartDocument)
    final override def endDocument() = processEvent(EndDocument)

    final override def startElement(namespaceURI: String, localName: String, qName: String, atts: Attributes) = {
        namespaceContext.startElement()
        _level += 1
        processEvent(StartElement(namespaceURI, localName, qName, atts))
    }

    final override def endElement(namespaceURI: String, localName: String, qName: String) = {
        processEvent(EndElement(namespaceURI, localName, qName))
        _level -= 1
        namespaceContext.endElement()
    }

    final override def characters(ch: Array[Char], start: Int, length: Int) = processEvent(Characters(ch, start, length))

    final override def comment(ch: Array[Char], start: Int, length: Int) = processEvent(Comment(ch, start, length))
    final override def processingInstruction(target: String, data: String) = processEvent(PI(target, data))
    
    final override def startPrefixMapping(prefix: String, uri: String) = {
        namespaceContext.startPrefixMapping(prefix, uri)
        processEvent(StartPrefixMapping(prefix, uri))
    }
    final override def endPrefixMapping(prefix: String) = processEvent(EndPrefixMapping(prefix))

    // Don't bother with these
    final override def ignorableWhitespace(ch: Array[Char], start: Int, length: Int) = ()
    final override def skippedEntity(name: String) = ()

    final override def startDTD(name: String, publicId: String, systemId: String) = ()
    final override def endDTD() = ()

    final override def startEntity(name: String) = ()
    final override def endEntity(name: String) = ()

    final override def startCDATA() = ()
    final override def endCDATA() = ()
}