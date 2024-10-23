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
import org.orbeon.oxf.externalcontext.{ExternalContext, URLRewriterImpl, UrlRewriteMode}
import org.orbeon.oxf.http.HttpMethod
import org.orbeon.oxf.util.StaticXPath.*
import org.orbeon.oxf.util.*
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.control.XFormsValueControl
import org.orbeon.oxf.xml.XMLReceiver
import org.orbeon.oxf.xml.XMLReceiverSupport.*
import org.orbeon.saxon.jaxp.SaxonTransformerFactory
import org.scalajs.dom.DOMParser
import org.scalajs.dom.ext.*
import org.scalajs.dom.raw.{Blob, BlobPropertyBag, HTMLDocument}

import java.io.*
import java.net.URI
import javax.xml.transform.Transformer
import javax.xml.transform.sax.TransformerHandler
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*
import scala.scalajs.js.typedarray.Uint8Array


object XFormsCrossPlatformSupport extends XFormsCrossPlatformSupportTrait {

  def externalContext: ExternalContext = CoreCrossPlatformSupport.externalContext

  // We have no notion of upload progress in the JavaScript environment as we just pass a `File` object.
  def getUploadProgress(
    request   : ExternalContext.Request,
    uuid      : String,
    fieldName : String
  ): Option[UploadProgress[Unit]] =
    None

  // We have no notion of upload progress in the JavaScript environment as we just pass a `File` object.
  def removeUploadProgress(request: ExternalContext.Request, control: XFormsValueControl): Unit = ()

  // TODO, although this is probably not called in the JavaScript environment, as that is only called
  // upon `xxforms-state-restored`.
  def attachmentFileExists(holderValue: String): Boolean =
    throw new NotImplementedError("attachmentFileExists")

  // Form Runner: called with `input:instance`
  def resolveServiceURL(containingDocument: XFormsContainingDocument, element: dom.Element, url: String, rewriteMode: UrlRewriteMode): String =
    url match {
      case "input:instance" => url
      case _                => url
    }

  def resolveResourceURL(containingDocument: XFormsContainingDocument, element: dom.Element, url: String, rewriteMode: UrlRewriteMode): String =
    if (url.startsWith("data:") || url.startsWith("blob:") || url.startsWith("javascript:"))
      url
    else
      throw new NotImplementedError(s"resolveResourceURL for `$url`")

  def resolveRenderURL(
    containingDocument : XFormsContainingDocument,
    currentElement     : dom.Element,
    url                : String,
    skipRewrite        : Boolean
  ): String =
    throw new NotImplementedError("resolveRenderURL")

  def rewriteURL(request: ExternalContext.Request, urlString: String, rewriteMode: UrlRewriteMode): String =
    URLRewriterImpl.rewriteURL(request, urlString, rewriteMode)

  def resolveActionURL(containingDocument: XFormsContainingDocument, currentElement: dom.Element, url: String): String =
    throw new NotImplementedError("resolveActionURL")

  def htmlStringToDocumentTagSoup(value: String, locationData: LocationData): org.w3c.dom.Document =
    throw new NotImplementedError("htmlStringToDocumentTagSoup")

  def streamHTMLFragment(
    value        : String,
    locationData : LocationData,
    xhtmlPrefix  : String
  )(implicit
    xmlReceiver  : XMLReceiver
  ): Unit = {

    // The Scala.js implementation uses the HTML environment's `DOMParser`, unlike on the JVM where
    // we have to use a library like `TagSoup`.
    val parser = new DOMParser()
    val doc    = parser.parseFromString(value, org.scalajs.dom.MIMEType.`text/html`).asInstanceOf[HTMLDocument]

    def outputFragment(nodes: org.scalajs.dom.NodeList[org.scalajs.dom.Node]): Unit = {
      nodes.toList.foreach {
        case v: org.scalajs.dom.Element =>
          withElement(
            v.tagName,
            atts = v.attributes.toList map { case (name, att) => name -> att.value }
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
  // proxy resources as `data:` or `blob:` URLs.
  // Used by `XFormsOutputControl` for download, image, video. Scenarios:
  // - `data:` | `blob:` URLs go unchanged to the browser
  // - `upload`: if found, we return a `blob:` URL; if not found, we return a `javascript:void(0)` URL
  // - other URLs go through `Connection.connectNow()`
  //     - `fromResourceResolver()`
  //     - `fromSubmissionProviderSync()`
  def proxyURI(
    urlString       : String,
    filename        : Option[String],
    contentType     : Option[String],
    lastModified    : Long,
    customHeaders   : Map[String, List[String]],
    getHeader       : String => Option[List[String]],
    fromCacheOrElse : (URI, () => URI) => URI
  )(implicit
    logger           : IndentedLogger,
    resourceResolver: Option[ResourceResolver]
  ): URI = {

    implicit val ec = externalContext

    val uri = URI.create(urlString)

    uri.getScheme match {
      case "data" | "blob" =>
        uri
      case scheme =>
        JsFileSupport.findObjectUrl(uri) match {
          case Some(objectUrl) =>
            objectUrl
          case None if scheme == JsFileSupport.UploadUriScheme =>
            // Can this happen?
            URI.create("javascript:void(0)")
          case None            =>

            def fromZip =
              resourceResolver.flatMap(_.resolve(HttpMethod.GET, uri, None, Map.empty)).map(_ -> true)

            // TODO: Ideally, this would use HTTP caching, so we could do a conditional `GET`, for example.
            def fromConnection =
              Connection.connectNow(
                method          = HttpMethod.GET,
                url             = uri,
                credentials     = None,
                content         = None,
                headers         = Map.empty,
                loadState       = false,
                saveState       = false,
                logBody         = false
              ) -> false

            val (cxr, isSourceFromZip) =
              fromZip.getOrElse(fromConnection)

            // TODO: Handle unsuccessful connection result.
            val isSuccess =
              cxr.isSuccessResponse

            def createBlobUrl(): URI =
              URI.create(
                js.Dynamic.global.window.URL.createObjectURL(
                  new Blob(
                    js.Array(new Uint8Array(Connection.inputStreamIterable(cxr.content.stream)).buffer),
                    BlobPropertyBag(`type` = cxr.mediatype.orUndefined)
                  )
                ).asInstanceOf[String]
              )

            if (isSourceFromZip)
              fromCacheOrElse(uri, createBlobUrl) // UriUtils.removeQueryAndFragment ? or just fragment if any?
            else
              createBlobUrl()
        }
    }
  }

  // TODO: could use `blob:`?
  // In the JavaScript environment, we currently don't require a way to handle dynamic URLs, so we
  // proxy resources as `data:` URLs.
  def proxyBase64Binary(
    value            : String,
    filename         : Option[String],
    mediatype        : Option[String],
    evaluatedHeaders : Map[String, List[String]],
    getHeader        : String => Option[List[String]]
  )(implicit
    logger           : IndentedLogger,
    resourceResolver: Option[ResourceResolver]
  ): URI =
    URI.create("data:" + mediatype.getOrElse("") + ";base64," + value)

  def mapSavedUri(
    beforeUri         : String,
    afterUri          : String
  ): Unit =
    JsFileSupport.mapSavedUri(URI.create(beforeUri), URI.create(afterUri))

  def renameAndExpireWithSession(
    existingFileURI  : URI
  )(implicit
    logger           : IndentedLogger
  ): URI = {
    existingFileURI
  }

  def inputStreamToRequestUri(
    inputStream      : InputStream
  )(implicit
    logger           : IndentedLogger
  ): Option[URI] =
    throw new NotImplementedError("inputStreamToRequestUri")

  def inputStreamToSessionUri(
    inputStream      : InputStream
  )(implicit
    logger           : IndentedLogger
  ): Option[URI] =
    throw new NotImplementedError("inputStreamToSessionUri")

  // TODO: This is used by `XFormsOutputControl` when proxying resources.
  def getLastModifiedIfFast(absoluteURL: String): Long = 0

  // Must not be called, see comment in trait
  def readTinyTreeFromUrl(url: URI): DocumentNodeInfoType =
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

  def readOrbeonDom(xmlString: String): dom.Document = {

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

  def readOrbeonDom(
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

  // For `SubmissionUtils.dataNodeHash()` only
  def hmacStringToHexShort(text: String): String = throw new NotImplementedError("hmacStringToHexShort")

  // This is unnecessary in the JavaScript environment
  def hmacStringForUpload(text: String, encoding: ByteEncoding): String = ""

  // Used by `CacheableSubmission` for `requestBodyHash`
  def digestBytes(bytes: Array[Byte], encoding: ByteEncoding): String = throw new NotImplementedError("digestBytes")

  def openUrlStream(url: URI): InputStream = throw new NotImplementedError("openUrlStream")

  def writeMultipartFormData(document: dom.Document, os: OutputStream): String = throw new NotImplementedError("writeMultipartFormData")

  def getRootThrowable(t : Throwable): Throwable = Exceptions.getRootThrowable(t).orNull
  def causesIterator(t : Throwable): Iterator[Throwable] = Exceptions.causesIterator(t)

  def tempFileSize(filePath: String): Long = throw new NotImplementedError("tempFileSize")

  def deleteFileIfPossible(urlString: String): Unit = {
    // TODO
  }

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
    handleXInclude : Boolean, // unused
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

    parser.parse(source)
  }
}
