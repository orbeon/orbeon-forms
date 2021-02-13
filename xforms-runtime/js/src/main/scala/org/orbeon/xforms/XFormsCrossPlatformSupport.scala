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
import org.orbeon.dom.io.{SAXContentHandler, SAXReader}
import org.orbeon.io.IOUtils
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.http.HttpMethod.GET
import org.orbeon.oxf.util.StaticXPath._
import org.orbeon.oxf.util.{Base64, Connection, CoreCrossPlatformSupport, Exceptions, IndentedLogger, StaticXPath, UploadProgress}
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.control.XFormsValueControl
import org.orbeon.oxf.xml.XMLReceiver
import org.orbeon.oxf.xml.XMLReceiverSupport._
import org.orbeon.saxon.jaxp.SaxonTransformerFactory
import org.scalajs.dom.DOMParser
import org.scalajs.dom.ext._
import org.scalajs.dom.raw.HTMLDocument

import java.io._
import java.net.URI
import javax.xml.transform.Transformer
import javax.xml.transform.sax.TransformerHandler


object XFormsCrossPlatformSupport extends XFormsCrossPlatformSupportTrait {

  def externalContext: ExternalContext = CoreCrossPlatformSupport.externalContext

  // TODO
  def getUploadProgress(
    request   : ExternalContext.Request,
    uuid      : String,
    fieldName : String
  ): Option[UploadProgress[Unit]] =
    None

  def removeUploadProgress(request: ExternalContext.Request, control: XFormsValueControl): Unit =
    throw new NotImplementedError("removeUploadProgress")

  def attachmentFileExists(holderValue: String): Boolean =
    throw new NotImplementedError("attachmentFileExists")

  // Form Runner: called with `input:instance`
  def resolveServiceURL(containingDocument: XFormsContainingDocument, element: dom.Element, url: String, rewriteMode: Int): String =
    url match {
      case "input:instance" => url
      case _                => url
    }

  def resolveResourceURL(containingDocument: XFormsContainingDocument, element: dom.Element, url: String, rewriteMode: Int): String =
    throw new NotImplementedError("resolveResourceURL")

  def resolveRenderURL(
    containingDocument : XFormsContainingDocument,
    currentElement     : dom.Element,
    url                : String,
    skipRewrite        : Boolean
  ): String = throw new NotImplementedError("resolveRenderURL")

  def rewriteURL(request: ExternalContext.Request, urlString: String, rewriteMode: Int): String =
    throw new NotImplementedError("rewriteURL")

  def resolveActionURL(containingDocument: XFormsContainingDocument, currentElement: dom.Element, url: String): String =
    throw new NotImplementedError("resolveActionURL")

  def htmlStringToDocumentTagSoup(value: String, locationData: LocationData): org.w3c.dom.Document =
    throw new NotImplementedError("htmlStringToDocumentTagSoup")

  def streamHTMLFragment(
    value        : String,
    locationData : LocationData,
    xhtmlPrefix  : String)(implicit
    xmlReceiver  : XMLReceiver
  ): Unit = {

    // The Scala.js implementation uses the HTML environment's `DOMParser`, unlike on the JVM where
    // we have to use a library like `TagSoup`.
    val parser = new DOMParser()
    val doc    = parser.parseFromString(value, "text/html").asInstanceOf[HTMLDocument]

    def outputFragment(nodes: org.scalajs.dom.NodeList): Unit = {
      nodes foreach {
        case v: org.scalajs.dom.Element =>
          withElement(
            v.tagName,
            atts = v.attributes.toIterable map { case (name, att) => name -> att.value }
          ) {
            outputFragment(v.childNodes)
          }
        case v: org.scalajs.dom.Text    =>
          val s = v.nodeValue
          xmlReceiver.characters(s.toCharArray, 0, s.length)
        case _ =>
      }
    }

    outputFragment(doc.body.childNodes)
  }

  // In the JavaScript environment, we currently don't require a way to handle dynamic URLs, so we
  // proxy resources as `data:` URLs.
  def proxyURI(
    uri              : String,
    filename         : Option[String],
    contentType      : Option[String],
    lastModified     : Long,
    customHeaders    : Map[String, List[String]],
    getHeader        : String => Option[List[String]])(implicit
    logger           : IndentedLogger
  ): String = {

    implicit val ec = externalContext

    val cxr =
      Connection.connectNow(
        method          = GET,
        url             = new URI(uri),
        credentials     = None,
        content         = None,
        headers         = Map.empty,
        loadState       = false,
        saveState       = false,
        logBody         = false
      )

    val baos = new ByteArrayOutputStream
    IOUtils.copyStreamAndClose(cxr.content.inputStream, baos)
    "data:" + contentType.orElse(cxr.mediatype).getOrElse("") + ";base64," + Base64.encode(baos.toByteArray, useLineBreaks = false)
  }

  // In the JavaScript environment, we currently don't require a way to handle dynamic URLs, so we
  // proxy resources as `data:` URLs.
  def proxyBase64Binary(
    value            : String,
    filename         : Option[String],
    mediatype        : Option[String],
    evaluatedHeaders : Map[String, List[String]],
    getHeader        : String => Option[List[String]])(implicit
    logger           : IndentedLogger
  ): String =
    "data:" + mediatype.getOrElse("") + ";base64," + value

  def renameAndExpireWithSession(
    existingFileURI  : String)(implicit
    logger           : IndentedLogger
  ): URI =
    throw new NotImplementedError("renameAndExpireWithSession")

  def inputStreamToRequestUri(
    inputStream      : InputStream)(implicit
    logger           : IndentedLogger
  ): Option[String] =
    throw new NotImplementedError("inputStreamToRequestUri")

  def inputStreamToSessionUri(
    inputStream      : InputStream)(implicit
    logger           : IndentedLogger
  ): Option[String] =
    throw new NotImplementedError("inputStreamToSessionUri")

  // TODO: This is used by `XFormsOutputControl` when proxying resources.
  def getLastModifiedIfFast(absoluteURL: String): Long = 0

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

    parseToReceiver(
      Left(inputStream),
      systemId,
      handleXInclude,
      handleLexical,
      receiver
    )

    result()
  }

  def stringToTinyTree(
    configuration  : SaxonConfiguration,
    xmlString      : String,
    handleXInclude : Boolean,
    handleLexical  : Boolean
  ): DocumentNodeInfoType = {

    val (receiver, result) = StaticXPath.newTinyTreeReceiver

    parseToReceiver(
      Right(new StringReader(xmlString)),
      systemId       = null,
      handleXInclude = handleXInclude,
      handleLexical  = handleLexical,
      receiver       = receiver
    )

    result()
  }

  def readDom4j(xmlString: String): dom.Document = {

    val (receiver, result) = newDomReceiver

    parseToReceiver(
      Right(new StringReader(xmlString)),
      systemId       = null,
      handleXInclude = false,
      handleLexical  = true,
      receiver       = receiver
    )

    result()
  }

  def readDom4j(
    inputStream    : InputStream,
    systemId       : String,
    handleXInclude : Boolean,
    handleLexical  : Boolean
  ): dom.Document = {

    val (receiver, result) = newDomReceiver

    parseToReceiver(
      Left(inputStream),
      systemId,
      handleXInclude,
      handleLexical,
      receiver
    )

    result()
  }

  def hmacString(text: String, encoding: String): String = throw new NotImplementedError("hmacString")
  def digestBytes(bytes: Array[Byte], encoding: String): String = throw new NotImplementedError("digestBytes")

  def openUrlStream(urlString: String): InputStream = throw new NotImplementedError("openUrlStream")

  def writeMultipartFormData(document: dom.Document, os: OutputStream): String = throw new NotImplementedError("writeMultipartFormData")

  def getRootThrowable(t : Throwable) : Throwable = Exceptions.getRootThrowable(t).orNull
  def causesIterator(t : Throwable) : Iterator[Throwable] = Exceptions.causesIterator(t)

  def tempFileSize(filePath: String): Long = throw new NotImplementedError("tempFileSize")

  def deleteFileIfPossible(urlString: String): Unit = throw new NotImplementedError("deleteFileIfPossible")

  protected def getIdentityTransformer: Transformer =
    new SaxonTransformerFactory(GlobalConfiguration).newTransformer

  protected def getIdentityTransformerHandler: TransformerHandler =
    new SaxonTransformerFactory(GlobalConfiguration).newTransformerHandler

  private def newDomReceiver: (XMLReceiver, () => dom.Document) = {
    val receiver =
      new SAXContentHandler(
        systemIdOpt         = None,
        mergeAdjacentText   = SAXReader.MergeAdjacentText,
        stripWhitespaceText = SAXReader.StripWhitespaceText,
        ignoreComments      = SAXReader.IgnoreComments
      ) with XMLReceiver

    (
      receiver,
      () => receiver.getDocument
    )
  }

  private def parseToReceiver(
    stream         : InputStream Either Reader,
    systemId       : String,
    handleXInclude : Boolean,
    handleLexical  : Boolean,
    receiver       : XMLReceiver
  ): Unit = {

    val source = {
      stream match {
        case Left(is) => new XMLInputSource(publicId = null, systemId = systemId, baseSystemId = null, byteStream = is, encoding = null)
        case Right(r) => new XMLInputSource(publicId = null, systemId = systemId, baseSystemId = null, charStream = r,  encoding = null)
      }
    }

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

    println(s"xxxx parsing $systemId")
    val t1 = System.currentTimeMillis()
    parser.parse(source)
    val t2 = System.currentTimeMillis()
    println(s"xxxx time = ${t2 - t1} ms")
  }
}
