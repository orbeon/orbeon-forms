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
package org.orbeon.oxf.xml

import java.io.{InputStream, Reader, StringReader}
import java.{util => ju}

import javax.xml.parsers._
import org.orbeon.apache.xerces.impl.{Constants, XMLEntityManager, XMLErrorReporter}
import org.orbeon.apache.xerces.xni.parser.XMLInputSource
import org.orbeon.oxf.common.{OXFException, ValidationException}
import org.orbeon.oxf.processor.transformer.TransformerURIResolver
import org.orbeon.oxf.resources.URLFactory
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.{LoggerFactory, SequenceReader, StringUtils}
import org.orbeon.oxf.xml.dom.XmlLocationData
import org.orbeon.oxf.xml.xerces.XercesSAXParserFactoryImpl
import org.w3c.dom.Document
import org.xml.sax._


// 2020-10-13: Some of the methods here should be moved out. There is legacy processor support, as well as
// conversion functions.
object XMLParsing {

  import Private._

  val EntityResolver = new XMLParsing.EntityResolver
  val ErrorHandler   = new XMLParsing.ErrorHandler

  // 2020-10-13: 1 external call in `MSVValidationProcessor`
  // FIXME: This can have `handleXInclude = true` and use Xerces XInclude.
  def createSAXParserFactory(parserConfiguration: ParserConfiguration): XercesSAXParserFactoryImpl =
    new XercesSAXParserFactoryImpl(parserConfiguration)

  // 2020-10-13: 2 external calls in `XFormsModelSchemaValidator`
  // FIXME: This can have `handleXInclude = true` and use Xerces XInclude.
  def getSAXParserFactory(parserConfiguration: ParserConfiguration): SAXParserFactory =
    synchronized {
      val key = parserConfiguration.getKey
      val existingFactory = parserFactories.get(key)
      if (existingFactory != null)
        return existingFactory
      val newFactory = createSAXParserFactory(parserConfiguration)
      parserFactories.put(key, newFactory)
      newFactory
    }

  // 2020-10-13: 1 external call in `XPath`
  // NOTE: Does NOT pass `handleXInclude = true`.
  def newSAXParser(parserConfiguration: ParserConfiguration): SAXParser =
    getSAXParserFactory(parserConfiguration).newSAXParser

  // 2020-10-13: 7 external calls
  // FIXME: May have `handleXInclude = true` which can use Xerces XInclude.
  def newXMLReader(parserConfiguration: ParserConfiguration): XMLReader = {
    val saxParser = newSAXParser(parserConfiguration)
    val xmlReader = saxParser.getXMLReader
    xmlReader.setEntityResolver(XMLParsing.EntityResolver)
    xmlReader.setErrorHandler(XMLParsing.ErrorHandler)
    xmlReader
  }

  // Given an input stream, return a reader. This performs encoding detection as per the XML spec. Caller must close
  // the resulting Reader when done.
  //
  // @return Reader initialized with the proper encoding
  //
  // 2020-10-13: 1 external call in `SubmissionUtils`
  def getReaderFromXMLInputStream(inputStream: InputStream): Reader = {
    // Create a Xerces XMLInputSource
    val inputSource = new XMLInputSource(null, null, null, inputStream, null)
    // Obtain encoding from Xerces
    val entityManager = new XMLEntityManager
    entityManager.setProperty(Constants.XERCES_PROPERTY_PREFIX + Constants.ERROR_REPORTER_PROPERTY, new XMLErrorReporter) // prevent NPE by providing this
    entityManager.setupCurrentEntity("[xml]", inputSource, false, true) // the result is the encoding, but we don't use it directly
    entityManager.getCurrentEntity.reader
  }

  // 2020-10-13: 2 external callers
  def createDocument: Document =
    getThreadDocumentBuilder.newDocument

  // 2020-10-13: 1 external caller
  def stringToDOM(xml: String): Document =
    getThreadDocumentBuilder.parse(new InputSource(new StringReader(xml)))

  /**
   * Parse a string into SAX events. If the string is empty or only contains white space, output an empty document.
   *
   * @param xml                 XML string
   * @param urlString           URL of the document, or null
   * @param xmlReceiver         receiver to output to
   * @param parserConfiguration parser configuration
   * @param handleLexical       whether the XML parser must output SAX LexicalHandler events, including comments
   */
  // 2020-10-13: 2 external callers
  // NOTE: Do NOT pass `handleXInclude = true`.
  def stringToSAX(
    xml                 : String,
    urlString           : String,
    xmlReceiver         : XMLReceiver,
    parserConfiguration : ParserConfiguration,
    handleLexical       : Boolean
  ): Unit =
    if (StringUtils.trimAllToEmpty(xml) == "") {
      xmlReceiver.startDocument()
      xmlReceiver.endDocument()
    } else
      readerToSAX(new StringReader(xml), urlString, xmlReceiver, parserConfiguration, handleLexical, null)

  // 2020-10-13: 9 external callers
  // NOTE: This will use our own XInclude implementation.
  // TODO: Only tests use XInclude.
  def inputStreamToSAX(
    inputStream         : InputStream,
    urlString           : String,
    xmlReceiver         : XMLReceiver,
    parserConfiguration : ParserConfiguration,
    handleLexical       : Boolean,
    resolver            : TransformerURIResolver // must be closed by caller if needed.
  ): Unit = {
    val inputSource = new InputSource(inputStream)
    inputSource.setSystemId(urlString)
    inputSourceToSAX(inputSource, xmlReceiver, parserConfiguration, handleLexical, resolver)
  }

  // 2020-10-13: 2 external calls in `URLGenerator`
  // NOTE: This will use our own XInclude implementation.
  // NOTE: Internal caller does NOT pass `handleXInclude = true`.
  // TODO: URLGenerator may pass `handleXInclude = true`.
  def readerToSAX(
    reader              : Reader,
    urlString           : String,
    xmlReceiver         : XMLReceiver,
    parserConfiguration : ParserConfiguration,
    handleLexical       : Boolean,
    resolver            : TransformerURIResolver // must be closed by caller if needed.
  ): Unit = {
    val inputSource = new InputSource(reader)
    inputSource.setSystemId(urlString)
    inputSourceToSAX(inputSource, xmlReceiver, parserConfiguration, handleLexical, resolver)
  }

  // Return whether the given string contains well-formed XML.
  //
  // 2020-10-13: 1 external caller
  def isWellFormedXML(xmlString: String): Boolean = {
    // Empty string is never well-formed XML
    if (xmlString.trimAllToOpt.isEmpty)
      return false
    try {
      val xmlReader = newSAXParser(ParserConfiguration.Plain).getXMLReader
      xmlReader.setContentHandler(NullContentHandler)
      xmlReader.setEntityResolver(EntityResolver)
      xmlReader.setErrorHandler(
        new org.xml.sax.ErrorHandler {
          def error     (exception: SAXParseException): Unit = throw exception
          def fatalError(exception: SAXParseException): Unit = throw exception
          def warning   (exception: SAXParseException): Unit = ()
        }
      )
      xmlReader.parse(new InputSource(new StringReader(xmlString)))
      true
    } catch {
      case _: Exception =>
        false
    }
  }

  // 2020-10-13: 1 external caller in processors
  def parseDocumentFragment(reader: Reader, xmlReceiver: XMLReceiver) {
    val xmlReader = newSAXParser(ParserConfiguration.Plain).getXMLReader
    xmlReader.setContentHandler(new XMLFragmentReceiver(xmlReceiver))
    val readers = new ju.ArrayList[Reader](3)
    readers.add(new StringReader("<root>"))
    readers.add(reader)
    readers.add(new StringReader("</root>"))
    xmlReader.parse(new InputSource(new SequenceReader(readers.iterator)))
  }

  // 2020-10-13: 2 external callers in processors
  def parseDocumentFragment(fragment: String, xmlReceiver: XMLReceiver): Unit =
    if (fragment.contains("<") || fragment.contains("&")) {
        val xmlReader = newSAXParser(ParserConfiguration.Plain).getXMLReader
        xmlReader.setContentHandler(new XMLFragmentReceiver(xmlReceiver))
        xmlReader.parse(new InputSource(new StringReader("<root>" + fragment + "</root>")))
    } else {
      // Optimization when fragment looks like text
      xmlReceiver.characters(fragment.toCharArray, 0, fragment.length)
    }

  class EntityResolver extends org.xml.sax.EntityResolver {
    def resolveEntity(publicId: String, systemId: String): InputSource = {
      // 2020-10-13: Thought we might just not support external entities at all. But
      // some of our tests fail if we just remove this.
      // TODO: Remove dependency on `URLFactory`?
      val is = new InputSource
      is.setSystemId(systemId)
      is.setPublicId(publicId)
      val url = URLFactory.createURL(systemId)
      // Would be nice to support XML Catalogs or similar here. See:
      // http://xerces.apache.org/xerces2-j/faq-xcatalogs.html
      if (url.getProtocol == "http")
        logger.warn("XML entity resolver for public id: " + publicId + " is accessing external entity via HTTP: " + url.toExternalForm)
      is.setByteStream(url.openStream)
      is
    }
  }

  class ErrorHandler extends org.xml.sax.ErrorHandler {

    // NOTE: We used to throw here, but we probably shouldn't.
    def error(exception: SAXParseException): Unit =
      logger.info("Error: " + exception)

    def fatalError(exception: SAXParseException): Unit =
      throw new ValidationException("Fatal error: " + exception.getMessage, XmlLocationData(exception))

    def warning(exception: SAXParseException): Unit =
      logger.info("Warning: " + exception)
  }

  object Private {

    val logger = LoggerFactory.createLoggerJava(XMLParsing.getClass)
    val NullContentHandler = new XMLReceiverAdapter

    val documentBuilderFactory: DocumentBuilderFactory =
      Class.forName("org.orbeon.apache.xerces.jaxp.DocumentBuilderFactoryImpl")
        .newInstance
        .asInstanceOf[DocumentBuilderFactory] |!>
        (_.setNamespaceAware(true))

    var documentBuilders: ju.Map[Thread, DocumentBuilder] = null
    val parserFactories = new ju.HashMap[String, SAXParserFactory]

    def inputSourceToSAX(
      inputSource         : InputSource,
      xmlReceiver         : XMLReceiver,
      parserConfiguration : ParserConfiguration,
      handleLexical       : Boolean,
      resolver            : TransformerURIResolver
    ): Unit = {

      val (newXmlReceiver, newParserConfiguration) =
        if (parserConfiguration.handleXInclude && (resolver ne null))
          (
            new XIncludeReceiver(null, xmlReceiver, parserConfiguration.uriReferences, resolver),
            parserConfiguration.copy(handleXInclude = false)
          )
        else
          (
            xmlReceiver,
            parserConfiguration
          )

      try {
        val xmlReader = newSAXParser(newParserConfiguration).getXMLReader
        xmlReader.setContentHandler(newXmlReceiver)
        if (handleLexical)
          xmlReader.setProperty(XMLConstants.SAX_LEXICAL_HANDLER, newXmlReceiver)
        xmlReader.setEntityResolver(EntityResolver)
        xmlReader.setErrorHandler(ErrorHandler)
        xmlReader.parse(inputSource)
      } catch {
        case e: SAXParseException =>
          throw new ValidationException(e.getMessage, XmlLocationData(e))
        case e: Exception =>
          throw new OXFException(e)
      }
    }

    //
    // Associate one `DocumentBuilder` per thread. This is so we avoid synchronizing (`parse()` for
    // example may take a lot of time on a `DocumentBuilder`) or creating `DocumentBuilder` instances
    // all the time. Since typically in an app server we work with a thread pool, not too many
    // instances of `DocumentBuilder` should be created.
    //
    // 2020-10-13: Not sure it's costly to create `DocumentBuilder` instances! If not, this might be
    // entirely unnecessary!
    //
    def getThreadDocumentBuilder: DocumentBuilder = {
      val thread = Thread.currentThread
      var documentBuilder =
        if (documentBuilders == null)
          null
        else
          documentBuilders.get(thread)
      // Try a first test outside the synchronized block
      if (documentBuilder == null)
        documentBuilderFactory.synchronized {
          // Redo the test within the synchronized block
          documentBuilder =
            if (documentBuilders == null)
              null
            else
              documentBuilders.get(thread)
          if (documentBuilder == null) {
            if (documentBuilders == null)
              documentBuilders = new ju.HashMap[Thread, DocumentBuilder]
            documentBuilder = documentBuilderFactory.newDocumentBuilder
            documentBuilders.put(thread, documentBuilder)
          }
        }
      documentBuilder
    }

    class XMLFragmentReceiver(val xmlReceiver: XMLReceiver) extends ForwardingXMLReceiver(xmlReceiver) {

      private var elementCount = 0

      override def startDocument(): Unit = ()
      override def endDocument(): Unit = ()

      override def startElement(uri: String, localname: String, qName: String, attributes: Attributes): Unit = {
        elementCount += 1
        if (elementCount > 1)
          super.startElement(uri, localname, qName, attributes)
      }

      override def endElement(uri: String, localname: String, qName: String): Unit = {
        elementCount -= 1
        if (elementCount > 0)
          super.endElement(uri, localname, qName)
      }
    }
  }
}
