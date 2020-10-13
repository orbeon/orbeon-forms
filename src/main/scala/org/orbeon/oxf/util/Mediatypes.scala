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

import org.orbeon.datatypes.Mediatype
import org.orbeon.oxf.http.Headers
import org.orbeon.oxf.util.CollectionUtils._
import org.orbeon.oxf.util.PathUtils._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.xml.{ForwardingXMLReceiver, ParserConfiguration, XMLParsing}
import org.xml.sax.Attributes

import scala.collection.mutable.ListBuffer

object Mediatypes {

  import Private._

  private val (mappingsByMediatype, mappingsByExtension) = {

    val list = {
      val ch = new MimeTypesContentHandler
      XMLParsing.urlToSAX("oxf:/oxf/mime-types.xml", ch, ParserConfiguration.Plain, handleLexical = false)
      ch.resultAsList
    }

    (
      combineValues[String, Mapping, List](list map { case m @ Mapping(_, mediatype) => mediatype   -> m }).toMap,
      combineValues[String, Mapping, List](list map { case m @ Mapping(_, _)         => m.extension -> m }).toMap
    )
  }

  def findMediatypeForPath(path: String): Option[String] =
    for {
      extension <- findExtension(path.toLowerCase)
      mappings  <- mappingsByExtension.get(extension)
      mapping   <- mappings.headOption
    } yield
      mapping.mediatype

  def findMediatypeForPathJava(path: String): String =
    findMediatypeForPath(path).orNull

  def findExtensionForMediatype(mediatype: String): Option[String] =
    for {
      mappings  <- mappingsByMediatype.get(mediatype)
      mapping   <- mappings.headOption
    } yield
      mapping.extension

  def fromHeadersOrFilename(
    header  : String => Option[String],
    filename: => Option[String]
  ): Option[Mediatype] = {

    def fromHeaders =
      header(Headers.ContentType)            flatMap
        ContentTypes.getContentTypeMediaType filterNot
        (_ == ContentTypes.OctetStreamContentType)

    def fromFilename =
      filename flatMap findMediatypeForPath

    fromHeaders orElse fromFilename flatMap Mediatype.unapply
  }

  private object Private {

    class MimeTypesContentHandler extends ForwardingXMLReceiver {

      import MimeTypesContentHandler._

      private val builder = new java.lang.StringBuilder

      private var state: State = DefaultState
      private var name: String = null

      private var buffer = ListBuffer[Mapping]()

      def resultAsList: List[Mapping] = buffer.result()

      private def extensionFromPattern(pattern: String) =
        pattern.toLowerCase.trimAllToOpt flatMap findExtension getOrElse ""

      override def startElement(uri: String, localname: String, qName: String, attributes: Attributes): Unit =
        localname match {
          case NameElement    => state = NameState
          case PatternElement => state = PatternState
          case _              => state = DefaultState
        }

      override def characters(chars: Array[Char], start: Int, length: Int): Unit =
        if (state == NameState || state == PatternState)
          builder.append(chars, start, length)

      override def endElement(uri: String, localname: String, qName: String): Unit = {
        localname match {
          case NameElement     => name = builder.toString.trimAllToEmpty
          case PatternElement  => buffer += Mapping(extensionFromPattern(builder.toString), name.toLowerCase)
          case MimeTypeElement => name = null
          case _               =>
        }
        builder.setLength(0)
      }
    }

    object MimeTypesContentHandler {

      val MimeTypeElement = "mime-type"
      val NameElement     = "name"
      val PatternElement  = "pattern"

      sealed trait State
      case object DefaultState extends State
      case object NameState    extends State
      case object PatternState extends State
    }

    case class Mapping(extension: String, mediatype: String)
  }
}
