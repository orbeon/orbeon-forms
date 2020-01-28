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
import org.orbeon.oxf.xml.{XMLUtils, NamespaceContext}

// TODO: remove dependency on this or move dependency to Scaxon

// FSM specifically handling SAX events
trait SAXMachine[S, D] extends FSM[S, SAXEvents.SAXEvent, D] with ContentHandler with LexicalHandler {

  import SAXEvents._

  // Get current element level
  private var _level = 0
  protected def depth = _level

  protected val namespaceContext: NamespaceContext = new NamespaceContext

  // Forward the given SAX event to the output
  final protected def forward(out: ContentHandler with LexicalHandler, saxEvent: SAXEvent) = saxEvent match {
    case StartDocument                                   => out.startDocument()
    case EndDocument                                     => out.endDocument()
    case StartElement(qName: QName, atts: Atts)          => out.startElement(qName.getNamespaceURI, qName.getLocalPart, XMLUtils.buildQName(qName.getPrefix, qName.getLocalPart), atts)
    case EndElement(qName: QName)                        => out.endElement(qName.getNamespaceURI, qName.getLocalPart, XMLUtils.buildQName(qName.getPrefix, qName.getLocalPart))
    case Characters(text)                                => out.characters(text.toCharArray, 0, text.length)
    case Comment(text)                                   => out.comment(text.toCharArray, 0, text.length)
    case PI(target: String, data: String)                => out.processingInstruction(target, data)
    case StartPrefixMapping(prefix: String, uri: String) => out.startPrefixMapping(prefix, uri)
    case EndPrefixMapping(prefix: String)                => out.endPrefixMapping(prefix)
    case DocumentLocator(locator)                        => out.setDocumentLocator(locator)
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