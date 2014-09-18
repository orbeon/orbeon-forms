/**
 * Copyright (C) 2010 Orbeon, Inc.
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
package org.orbeon.oxf.util

import org.orbeon.oxf.xml.{ForwardingXMLReceiver, XMLParsing}
import org.xml.sax.Attributes

import scala.collection.mutable.ListBuffer

/**
 * Serve resources to the response.
 */
object Mediatypes {

    // Only read mime types config once
    private lazy val MimeTypes = {
        val ch = new MimeTypesContentHandler
        XMLParsing.urlToSAX("oxf:/oxf/mime-types.xml", ch, XMLParsing.ParserConfiguration.PLAIN, false)
        ch.mimeTypeConfig
    }

    def getMimeType(path: String) = {
        val lowercasePath = path.toLowerCase
        MimeTypes collectFirst { case mapping if mapping.matches(lowercasePath) ⇒ mapping.mimeType }
    }

    def getMimeTypeJava(path: String) =
        getMimeType(path).orNull

    private class MimeTypesContentHandler extends ForwardingXMLReceiver {

        import MimeTypesContentHandler._

        private val builder = new java.lang.StringBuilder

        private var state: State = DefaultState
        private var name: String = null

        private var buffer = ListBuffer[Mapping]()

        def mimeTypeConfig = buffer.result()

        override def startElement(uri: String, localname: String, qName: String, attributes: Attributes): Unit =
            localname match {
                case NameElement    ⇒ state = NameState
                case PatternElement ⇒ state = PatternState
                case _              ⇒ state = DefaultState
            }

        override def characters(chars: Array[Char], start: Int, length: Int): Unit =
            if (state == NameState || state == PatternState)
                builder.append(chars, start, length)

        override def endElement(uri: String, localname: String, qName: String): Unit = {
            localname match {
                case NameElement     ⇒ name = builder.toString.trim
                case PatternElement  ⇒ buffer += Mapping(builder.toString.trim.toLowerCase, name.toLowerCase)
                case MimeTypeElement ⇒ name = null
                case _               ⇒
            }
            builder.setLength(0)
        }
    }

    private object MimeTypesContentHandler {

        val MimeTypeElement = "mime-type"
        val NameElement     = "name"
        val PatternElement  = "pattern"

        sealed trait State
        case object DefaultState extends State
        case object NameState    extends State
        case object PatternState extends State
    }

    private case class Mapping(pattern: String, mimeType: String) {
        def matches(path: String) =
            if (pattern == "*") {
                true
            }  else if (pattern.startsWith("*") && pattern.endsWith("*")) {
                val middle = pattern.substring(1, pattern.length - 1)
                path.contains(middle)
            } else if (pattern.startsWith("*")) {
                path.endsWith(pattern.substring(1))
            } else if (pattern.endsWith("*")) {
                path.startsWith(pattern.substring(0, pattern.length - 1))
            } else {
                path == pattern
            }
    }
}
