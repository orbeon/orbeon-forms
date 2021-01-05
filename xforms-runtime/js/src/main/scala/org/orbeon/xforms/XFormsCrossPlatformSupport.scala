/**
 * Copyright (C) 2020 Orbeon, Inc.
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
package org.orbeon.xforms

import org.orbeon.apache.xerces.parsers.{NonValidatingConfiguration, SAXParser}
import org.orbeon.apache.xerces.util.SymbolTable
import org.orbeon.apache.xerces.xni.parser.{XMLErrorHandler, XMLInputSource, XMLParseException}
import org.orbeon.datatypes.LocationData
import org.orbeon.dom
import org.orbeon.dom.io.DocumentSource
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.util.CoreUtils.BooleanOps
import org.orbeon.oxf.util.StaticXPath._
import org.orbeon.oxf.util.StringUtils.StringOps
import org.orbeon.oxf.util.{CoreCrossPlatformSupport, IndentedLogger, StaticXPath, UploadProgress}
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.control.XFormsValueControl
import org.orbeon.oxf.xml.XMLReceiver
import org.orbeon.saxon.jaxp.SaxonTransformerFactory
import org.xml.sax.InputSource

import java.io.{ByteArrayOutputStream, InputStream, OutputStream, Writer}
import java.net.URI
import javax.xml.transform.stream.StreamResult
import javax.xml.transform.{OutputKeys, Transformer}


object XFormsCrossPlatformSupport extends XFormsCrossPlatformSupportTrait {

  def externalContext: ExternalContext = CoreCrossPlatformSupport.externalContext

  def getUploadProgress(request: ExternalContext.Request, uuid: String, fieldName: String): Option[UploadProgress[Unit]] = ???

  def removeUploadProgress(request: ExternalContext.Request, control: XFormsValueControl): Unit = ???

  def attachmentFileExists(holderValue: String): Boolean = ???

  // Form Runner: called with `input:instance`
  def resolveServiceURL(containingDocument: XFormsContainingDocument, element: dom.Element, url: String, rewriteMode: Int): String =
    url match {
      case "input:instance" => url
      case _ =>
        println(s"xxx resolveServiceURL: `$url`")
        url
    }

  def resolveResourceURL(containingDocument: XFormsContainingDocument, element: dom.Element, url: String, rewriteMode: Int): String = ???

  def resolveRenderURL(
    containingDocument : XFormsContainingDocument,
    currentElement     : dom.Element,
    url                : String,
    skipRewrite        : Boolean
  ): String = ???

  def rewriteURL(request: ExternalContext.Request, urlString: String, rewriteMode: Int): String = ???

  def resolveActionURL(containingDocument: XFormsContainingDocument, currentElement: dom.Element, url: String): String = ???

  def htmlStringToDocumentTagSoup(value: String, locationData: LocationData): org.w3c.dom.Document = ???

  // XXX TODO: implement
  def streamHTMLFragment(xmlReceiver: XMLReceiver, value: String, locationData: LocationData, xhtmlPrefix: String): Unit = {
    val s = s"""[TODO: streamHTMLFragment] $value"""
    xmlReceiver.characters(s.toCharArray, 0, s.length)
  }

  def createHTMLFragmentXmlReceiver(writer: Writer, skipRootElement: Boolean): XMLReceiver = {
    ???
  }

  def serializeToByteArray(
    document           : dom.Document,
    method             : String,
    encoding           : String,
    versionOpt         : Option[String],
    indent             : Boolean,
    omitXmlDeclaration : Boolean,
    standaloneOpt      : Option[Boolean],
  ): Array[Byte] = {

    val identity = new SaxonTransformerFactory(GlobalConfiguration).newTransformer

    applyOutputProperties(
      identity,
      method             = method,
      encoding           = encoding,
      indentAmountOpt    = indent option 4,
      omitXmlDeclaration = omitXmlDeclaration,
      versionOpt         = versionOpt,
      standaloneOpt      = standaloneOpt
    )

    val os = new ByteArrayOutputStream
    identity.transform(new DocumentSource(document), new StreamResult(os))
    os.toByteArray
  }

  def proxyURI(
    uri              : String,
    filename         : Option[String],
    contentType      : Option[String],
    lastModified     : Long,
    customHeaders    : Map[String, List[String]],
    getHeader        : String => Option[List[String]])(implicit
    logger           : IndentedLogger
  ): String =
    ???

  def proxyBase64Binary(
    value            : String,
    filename         : Option[String],
    mediatype        : Option[String],
    evaluatedHeaders : Map[String, List[String]],
    getHeader        : String => Option[List[String]])(implicit
    logger           : IndentedLogger
  ): String =
    ???

  def renameAndExpireWithSession(
    existingFileURI  : String)(implicit
    logger           : IndentedLogger
  ): URI =
    ???

  def inputStreamToRequestUri(
    inputStream      : InputStream)(implicit
    logger           : IndentedLogger
  ): Option[String] =
    ???

  def inputStreamToSessionUri(
    inputStream      : InputStream)(implicit
    logger           : IndentedLogger
  ): Option[String] =
    ???

  def getLastModifiedIfFast(absoluteURL: String): Long = ???

  // Must not be called, see comment in trait
  def readTinyTreeFromUrl(urlString: String): DocumentNodeInfoType =
    throw new UnsupportedOperationException

//  object ErrorHandler extends org.xml.sax.ErrorHandler {
//
//    // NOTE: We used to throw here, but we probably shouldn't.
//    def error(exception: SAXParseException): Unit =
//      logger.info("Error: " + exception)
//
//    def fatalError(exception: SAXParseException): Unit =
//      throw new ValidationException("Fatal error: " + exception.getMessage, XmlLocationData(exception))
//
//    def warning(exception: SAXParseException): Unit =
//      logger.info("Warning: " + exception)
//  }

  def readTinyTree(
    configuration  : SaxonConfiguration,
    inputStream    : InputStream,
    systemId       : String,
    handleXInclude : Boolean, // TODO: MAYBE: XInclude support.
    handleLexical  : Boolean
  ): DocumentNodeInfoType = {

    val (receiver, result) = StaticXPath.newTinyTreeReceiver

    val inputSource = new InputSource(inputStream)
    inputSource.setSystemId(systemId)

    locally {

      val source = new XMLInputSource(publicId = null, systemId = systemId, baseSystemId = null, byteStream = inputStream, encoding = null)
      val config = new NonValidatingConfiguration(new SymbolTable)
      val parser = new SAXParser(config)

      parser.setContentHandler(receiver)
      if (handleLexical)
        parser.setLexicalHandler(receiver)

      config.setErrorHandler(new XMLErrorHandler {
        def warning(domain: String, key: String, exception: XMLParseException): Unit =
          println("Warning: " + exception.getMessage)
        def error(domain: String, key: String, exception: XMLParseException): Unit =
          println("Error: " + exception.getMessage)
        def fatalError(domain: String, key: String, exception: XMLParseException): Unit =
          println("Fatal: " + exception.getMessage)
      })
      parser.parse(source)
    }

    result()
  }

  def stringToTinyTree(
    configuration  : SaxonConfiguration,
    string         : String,
    handleXInclude : Boolean,
    handleLexical  : Boolean
  ): DocumentNodeInfoType = ???

  def readDom4j(xmlString: String): dom.Document = ???

  def readDom4j(
    inputStream    : InputStream,
    systemId       : String,
    handleXInclude : Boolean,
    handleLexical  : Boolean
  ): dom.Document = ???

  def hmacString(text: String, encoding: String): String = ???
  def digestBytes(bytes: Array[Byte], encoding: String): String = ???

  def openUrlStream(urlString: String): InputStream = ???

  def writeMultipartFormData(document: dom.Document, os: OutputStream): String = ???

  def getRootThrowable(t : Throwable) : Throwable = t // XXX TODO
  def causesIterator(t : Throwable) : Iterator[Throwable] = Iterator(t) // XXX TODO

  def tempFileSize(filePath: String): Long = ???

  def deleteFileIfPossible(urlString: String): Unit = ???
}
