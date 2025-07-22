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

import cats.syntax.option.*
import org.orbeon
import org.orbeon.apache.xerces.parsers.{NonValidatingConfiguration, SAXParser}
import org.orbeon.apache.xerces.util.SymbolTable
import org.orbeon.apache.xerces.xni.parser.{XMLErrorHandler, XMLInputSource, XMLParseException}
import org.orbeon.dom.io.{SAXContentHandler, SAXReader}
import org.orbeon.oxf.externalcontext.{ExternalContext, URLRewriterImpl, UrlRewriteMode}
import org.orbeon.oxf.http.HttpMethod
import org.orbeon.oxf.util.*
import org.orbeon.oxf.util.StaticXPath.*
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.control.XFormsValueControl
import org.orbeon.oxf.xml.XMLReceiver
import org.orbeon.saxon.jaxp.SaxonTransformerFactory
import org.scalajs.dom

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
  def resolveServiceURL(containingDocument: XFormsContainingDocument, element: orbeon.dom.Element, url: String, rewriteMode: UrlRewriteMode): String =
    url match {
      case "input:instance" => url
      case _                => url
    }

  def resolveResourceURL(containingDocument: XFormsContainingDocument, element: orbeon.dom.Element, url: String, rewriteMode: UrlRewriteMode): String =
    if (url.startsWith("data:") || url.startsWith("blob:") || url.startsWith("javascript:"))
      url
    else
      throw new NotImplementedError(s"resolveResourceURL for `$url`")

  def resolveRenderURL(
    containingDocument : XFormsContainingDocument,
    currentElement     : orbeon.dom.Element,
    url                : String,
    skipRewrite        : Boolean
  ): String =
    throw new NotImplementedError("resolveRenderURL")

  def rewriteURL(request: ExternalContext.Request, urlString: String, rewriteMode: UrlRewriteMode): String =
    URLRewriterImpl.rewriteURL(request, urlString, rewriteMode)

  def resolveActionURL(containingDocument: XFormsContainingDocument, currentElement: orbeon.dom.Element, url: String): String =
    throw new NotImplementedError("resolveActionURL")

  // In the JavaScript environment, we currently don't require a way to handle dynamic URLs, so we
  // proxy resources as `data:` or `blob:` URLs.
  // Used by `XFormsOutputControl` for download, image, video. Scenarios:
  // - `data:` | `blob:` URLs go unchanged to the browser
  // - `upload`: if found, we return a `blob:` URL; if not found, we return a `javascript:void(0)` URL
  // - other URLs go through `Connection.connectNow()`
  //     - `fromResourceResolver()`
  //     - `fromSubmissionProviderSync()`
  def proxyURI(
    urlString      : String,
    forEffectiveId : String,
    forDownload    : Boolean,
    filename       : Option[String],
    contentType    : Option[String],
    lastModified   : Long,
    customHeaders  : Map[String, List[String]],
    getHeader      : String => Option[List[String]],
    fromCacheOrElse: (URI, () => URI) => URI
  )(implicit
    logger          : IndentedLogger,
    resourceResolver: Option[ResourceResolver]
  ): Option[URI] = {

    val uri = URI.create(urlString)

    uri.getScheme match {
      case "data" | "blob" =>
        uri.some
      case scheme =>
        JsFileSupport.findObjectUrl(uri) match {
          case some @ Some(_) =>
            some
          case None if scheme == JsFileSupport.UploadUriScheme =>
            // Can this happen?
            URI.create("javascript:void(0)").some
          case None if ! forDownload =>

            // Try to load the resource as soon as possible
            // NOTE: We could also always return `None` and let RPC logic request the `blob:` URL later. Doing it here
            // has the benefit of using the URI cache, and faster loading of the image. Doing it later is more
            // consistent.
            resourceResolver.flatMap(_.resolve(HttpMethod.GET, uri, None, Map.empty)) match {
              case Some(cxr) =>

                // Resource was found from the resource resolver, so we can create a `blob:` URL from it.

                // TODO: Handle unsuccessful connection result.
                val isSuccess =
                  cxr.isSuccessResponse

                def createBlobUrl(): URI =
                  URI.create(
                    dom.URL.createObjectURL(
                      new dom.Blob(
                        blobParts = js.Array(new Uint8Array(Connection.inputStreamIterable(cxr.content.stream)).buffer),
                        options   = new dom.BlobPropertyBag { `type` = cxr.mediatype.orUndefined }
                      )
                    )
                  )

                fromCacheOrElse(uri, createBlobUrl).some // UriUtils.removeQueryAndFragment ? or just fragment if any?

              case None =>
                // Return `None` so that the caller can handle the case where the resource is not available yet and
                // use a default/dummy URL.
                None
            }

          case None =>
            // TODO: handle download, see https://github.com/orbeon/orbeon-forms/issues/7149

            def setDownloadSourceBlob(blob: dom.Blob): Boolean =
              Option(dom.document.querySelector(s"#$forEffectiveId .xforms-output-appearance-xxforms-download a[href]").asInstanceOf[dom.html.Anchor])
                .map { anchor =>
                  anchor.href = dom.URL.createObjectURL(blob)
                  // TODO: blob won't be revoked; it can be downloaded multiple times; but should be revoked eventually;
                  //  Should be: 1. When the control becomes non-relevant, 2. When the form is destroyed.
                }
                .nonEmpty

            None
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

  def readOrbeonDom(xmlString: String): orbeon.dom.Document = {

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
  ): orbeon.dom.Document = {

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

  def writeMultipartFormData(document: orbeon.dom.Document, os: OutputStream): String = throw new NotImplementedError("writeMultipartFormData")

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

  private def newDomReceiver: (XMLReceiver, () => orbeon.dom.Document) = {
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
