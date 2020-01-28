package org.orbeon.dom.io

import java.io._
import java.net.URL

import org.orbeon.dom.Document
import org.orbeon.dom.io.SAXReader._
import org.xml.sax._

class DocumentException(message: String, throwable: Throwable) extends Exception(message, throwable) {
  def this(message: String)      = this(message, null)
  def this(throwable: Throwable) = this(throwable.getMessage, throwable)
}

object SAXReader {

  private val SAXStringInterning   = "http://xml.org/sax/features/string-interning"
  private val SAXNamespacePrefixes = "http://xml.org/sax/features/namespace-prefixes"
  private val SAXNamespaces        = "http://xml.org/sax/features/namespaces"
  private val SAXValidation        = "http://xml.org/sax/features/validation"

  private val SAXLexicalHandler    = "http://xml.org/sax/properties/lexical-handler"

  // Should element & attribute names and namespace URIs be interned
  val SAXStringInternEnabled = true

  // Whether adjacent text nodes should be merged
  val MergeAdjacentText = false

  // Holds value of property stripWhitespaceText
  val StripWhitespaceText = false

  // Should we ignore comments
  val IgnoreComments = false
}

private class SAXEntityResolver(uriPrefix: String) extends EntityResolver {

    def resolveEntity(publicId: String, systemId: String): InputSource = {

      var _systemId = systemId

      if ((_systemId ne null) && ! _systemId.isEmpty) {
        if ((uriPrefix ne null) && (_systemId.indexOf(':') <= 0)) {
          _systemId = uriPrefix + _systemId
        }
      }
      new InputSource(_systemId)
    }
  }

/**
 * `SAXReader` creates a tree from SAX parsing events.
 */
class SAXReader(xmlReader: XMLReader) {

  def read(url: URL)         : Document = read(new InputSource(url.toExternalForm))
  def read(systemId: String) : Document = read(new InputSource(systemId))
  def read(in: InputStream)  : Document = read(new InputSource(in))
  def read(reader: Reader)   : Document = read(new InputSource(reader))

  def read(in: InputStream, systemId: String): Document = {
    val source = new InputSource(in)
    source.setSystemId(systemId)
    read(source)
  }

  def read(reader: Reader, systemId: String): Document = {
    val source = new InputSource(reader)
    source.setSystemId(systemId)
    read(source)
  }

  def read(inputSource: InputSource): Document =
    try {

      val contentHandler = new SAXContentHandler(
        systemIdOpt         = Option(inputSource.getSystemId),
        mergeAdjacentText   = MergeAdjacentText,
        stripWhitespaceText = StripWhitespaceText,
        ignoreComments      = IgnoreComments
      )

      xmlReader.setProperty(SAXLexicalHandler, contentHandler)

      xmlReader.setFeature (SAXNamespaces,        true)
      xmlReader.setFeature (SAXNamespacePrefixes, false)
      xmlReader.setFeature (SAXStringInterning,   SAXStringInternEnabled)
      xmlReader.setFeature (SAXValidation,        false)

      xmlReader.setContentHandler(contentHandler)
      xmlReader.setErrorHandler(contentHandler)
      xmlReader.setEntityResolver(createDefaultEntityResolver(inputSource.getSystemId))

      xmlReader.parse(inputSource)

      contentHandler.getDocument
    } catch {
      case e: SAXParseException =>

        val systemId = Option(e.getSystemId) getOrElse ""

        throw new DocumentException(
          s"Error on line ${e.getLineNumber} of document $systemId: ${e.getMessage}",
          e
        )
      case e: Exception =>
        throw new DocumentException(e.getMessage, e)
    }

  private def createDefaultEntityResolver(systemId: String): EntityResolver = {
    var prefix: String = null
    if ((systemId ne null) && ! systemId.isEmpty) {
      val idx = systemId.lastIndexOf('/')
      if (idx > 0) {
        prefix = systemId.substring(0, idx + 1)
      }
    }
    new SAXEntityResolver(prefix)
  }
}
