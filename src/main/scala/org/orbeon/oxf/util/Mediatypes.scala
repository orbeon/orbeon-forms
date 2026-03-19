package org.orbeon.oxf.util

import org.orbeon.oxf.resources.URLFactory
import org.orbeon.oxf.util.PathUtils.*
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.xml.{ForwardingXMLReceiver, ParserConfiguration, XMLParsing}
import org.xml.sax.Attributes

import scala.collection.mutable.ListBuffer


object Mediatypes extends MediatypesTrait {

  import Private.*

  override protected def findMappings: Iterable[Mapping] = {
      val ch = new MimeTypesContentHandler
      val urlString = "oxf:/oxf/mime-types.xml"
      XMLParsing.inputStreamToSAX(
        inputStream         = URLFactory.createURL(urlString).openStream,
        urlString           = urlString,
        xmlReceiver         = ch,
        parserConfiguration = ParserConfiguration.Plain,
        handleLexical       = false,
        resolver            = null
      )
      ch.resultAsList
    }

  private object Private {

    class MimeTypesContentHandler extends ForwardingXMLReceiver {

      import MimeTypesContentHandler._

      private val builder = new java.lang.StringBuilder

      private var state: State = DefaultState
      private var name: String = null
      private var label: Option[String] = None

      private val buffer = ListBuffer[Mapping]()

      def resultAsList: List[Mapping] = {
        pprint.pprintln(buffer.result(), height = 10000)
        buffer.result()
      }

      private def extensionFromPattern(pattern: String) =
        pattern.toLowerCase.trimAllToOpt flatMap findExtension getOrElse ""

      override def startElement(uri: String, localname: String, qName: String, attributes: Attributes): Unit =
        localname match {
          case NameElement    => state = NameState
          case LabelElement   => state = LabelState
          case PatternElement => state = PatternState
          case _              => state = DefaultState
        }

      override def characters(chars: Array[Char], start: Int, length: Int): Unit =
        if (state == NameState || state == PatternState || state == LabelState)
          builder.append(chars, start, length)

      override def endElement(uri: String, localname: String, qName: String): Unit = {
        localname match {
          case NameElement     => name = builder.toString.trimAllToEmpty
          case LabelElement    => label = builder.toString.trimAllToOpt
          case PatternElement  => buffer += Mapping(extensionFromPattern(builder.toString), name.toLowerCase, label)
          case MimeTypeElement =>
            name = null
            label = None
          case _               =>
        }
        builder.setLength(0)
      }
    }

    object MimeTypesContentHandler {

      val MimeTypeElement = "mime-type"
      val NameElement     = "name"
      val PatternElement  = "pattern"
      val LabelElement    = "label"

      sealed trait State
      case object DefaultState extends State
      case object NameState    extends State
      case object PatternState extends State
      case object LabelState   extends State
    }
  }
}
