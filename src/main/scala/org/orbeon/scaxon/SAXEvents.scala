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

import org.xml.sax.{Attributes, Locator}
import javax.xml.namespace.QName
import org.orbeon.oxf.xml.XMLUtils
import scala.collection.mutable.ListBuffer

// Representation of all SAX events that are useful
// We skip: ignorableWhitespace, skippedEntity, startDTD/endDTD, startEntity/endEntity, and startCDATA/endCDATA.
object SAXEvents {

    sealed trait SAXEvent
    case class  DocumentLocator(locator: Locator) extends SAXEvent
    case object StartDocument extends SAXEvent
    case object EndDocument extends SAXEvent
    case class  StartElement(qName: QName, atts: Atts) extends SAXEvent
    case class  EndElement(qName: QName) extends SAXEvent
    case class  Characters(ch: Array[Char], start: Int, length: Int) extends SAXEvent
    case class  Comment(ch: Array[Char], start: Int, length: Int) extends SAXEvent
    case class  PI(target: String, data: String) extends SAXEvent
    case class  StartPrefixMapping(prefix: String, uri: String) extends SAXEvent
    case class  EndPrefixMapping(prefix: String) extends SAXEvent

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
