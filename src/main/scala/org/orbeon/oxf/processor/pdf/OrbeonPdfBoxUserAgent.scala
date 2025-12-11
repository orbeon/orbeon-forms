/**
 * Copyright (C) 2022 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.processor.pdf

import com.openhtmltopdf.pdfboxout.{PdfBoxImage, PdfBoxOutputDevice, PdfBoxUserAgent}
import com.openhtmltopdf.resource.ImageResource
import com.openhtmltopdf.swing.NaiveUserAgent
import com.openhtmltopdf.util.{LogMessageId, XRLog}
import org.orbeon.connection.ConnectionResult
import org.orbeon.css.CSSParsing.CSSCache
import org.orbeon.css.{CSSParsing, MediaQuery, Selector, VariableDefinitions}
import org.orbeon.io.IOUtils
import org.orbeon.oxf.externalcontext.{ExternalContext, SafeRequestContext, UrlRewriteMode}
import org.orbeon.oxf.http.{Headers, HttpMethod}
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.util.ImageSupport.{compressJpegImage, findImageOrientation, findTransformation, transformImage}
import org.orbeon.oxf.util.Logging.*
import org.orbeon.oxf.util.TryUtils.*
import org.orbeon.oxf.util.{Connection, ImageMetadata, IndentedLogger, ResourceResolver, URLRewriterUtils}

import java.io.*
import java.net.{URI, URL}
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.logging.Level
import javax.imageio.ImageIO


class OrbeonPdfBoxUserAgent(
  jpegCompressionLevel                 : Float,
  outputDevice                         : PdfBoxOutputDevice,
  override val pipelineContext         : PipelineContext,
  dotsPerPixel                         : Int,
  variableDefinitions                  : VariableDefinitions,
  lookupSelectors                      : List[Selector],
  cssCache                             : CSSCache
)(override implicit val externalContext: ExternalContext,
  override implicit val indentedLogger : IndentedLogger
) extends PdfBoxUserAgent(outputDevice)
  with URIResolver {

  override def getImageResource(uriStr: String): ImageResource = {
    @throws[IOException]
    def readStream(is: InputStream) = {
      val out = new ByteArrayOutputStream(is.available)
      val buf = new Array[Byte](10240)
      var i = 0

      while ( {i = is.read(buf); i != -1} ) {
        out.write(buf, 0, i)
      }
      out.close()
      out.toByteArray
    }

    def scaleToOutputResolution(image: PdfBoxImage): Unit =
      if (dotsPerPixel != 1.0f)
        image.scale(image.getWidth * dotsPerPixel, image.getHeight * dotsPerPixel)

    val uriResolved = resolveURI(uriStr)
    if (uriResolved == null) {
      XRLog.log(Level.INFO, LogMessageId.LogMessageId2Param.LOAD_URI_RESOLVER_REJECTED_LOADING_AT_URI, "image", uriStr)
      return new ImageResource(uriStr, null)
    }
    var resource = _imageCache.get(uriResolved)
    if (resource != null && resource.getImage.isInstanceOf[PdfBoxImage]) { // Make copy of PdfBoxImage so we don't stuff up the cache.
      val original = resource.getImage.asInstanceOf[PdfBoxImage]
      val copy = new PdfBoxImage(original.getBytes, original.getUri, original.getWidth.toFloat, original.getHeight.toFloat, original.getXObject)
      return new ImageResource(resource.getImageUri, copy)
    }

    try {
      IOUtils.useAndClose(openStream(uriResolved)) { is =>
        if (uriStr.toLowerCase(Locale.US).endsWith(".pdf")) {
          // TODO: Implement PDF AS IMAGE
          // PdfReader reader = _outputDevice.getReader(uri);
          // PDFAsImage image = new PDFAsImage(uri);
          // Rectangle rect = reader.getPageSizeWithRotation(1);
          // image.setInitialWidth(rect.getWidth() *
          // _outputDevice.getDotsPerPoint());
          // image.setInitialHeight(rect.getHeight() *
          // resource = new ImageResource(uriStr, image);
        }
        else {
          val sourceBytes = readStream(is)
          val isWebP      = ImageMetadata.findImageMediatype(new ByteArrayInputStream(sourceBytes)).contains("image/webp")
          val imgBytes    =
            if (isWebP) {
              // Convert WebP to JPG, as PDFBox does not support WebP natively
              Option(ImageIO.read(new ByteArrayInputStream(sourceBytes)))
                .map(compressJpegImage(_, jpegCompressionLevel))
                .getOrElse(sourceBytes)
            } else {
              // Handle non-WebP images with existing orientation logic
              findImageOrientation(new ByteArrayInputStream(sourceBytes)) match {
                case Some(orientation) if orientation >= 2 && orientation <= 8 =>

                  val sourceImage = ImageIO.read(new ByteArrayInputStream(sourceBytes))

                  val rotatedImage =
                    transformImage(
                      sourceImage,
                      findTransformation(orientation, sourceImage.getWidth, sourceImage.getHeight)
                        .getOrElse(throw new IllegalStateException)
                    )

                  // https://github.com/orbeon/orbeon-forms/issues/4593
                  compressJpegImage(rotatedImage, jpegCompressionLevel)
                case _ =>
                  sourceBytes
              }
            }
          val fsImage = new PdfBoxImage(imgBytes, uriStr)
          scaleToOutputResolution(fsImage)
          outputDevice.realizeImage(fsImage)
          resource = new ImageResource(uriResolved, fsImage)
        }
        _imageCache.put(uriResolved, resource)

      }
    } catch {
      case e: Exception =>
        XRLog.log(Level.WARNING, LogMessageId.LogMessageId1Param.EXCEPTION_CANT_READ_IMAGE_FILE_FOR_URI, uriStr, e)
    }

    if (resource != null) resource = new ImageResource(resource.getImageUri, resource.getImage)
    else resource = new ImageResource(uriStr, null)

    resource
  }

  override protected def openReader(uri: String): Reader = {
    debug(s"openReader($uri)")

    lazy val reader = new InputStreamReader(openStream(uri), StandardCharsets.UTF_8)

    if (uri.contains(".css")) {

      lazy val originalCss = IOUtils.readStreamAsStringAndClose(reader)

      // Retrieve parsed CSS from cache or parse from reader
      val cascadingStyleSheetOpt = cssCache.get(new URL(resolveURI(uri)), CSSParsing.parsedCss(originalCss))

      val modifiedCss = cascadingStyleSheetOpt match {
        case None =>
          error(s"Could not parse CSS for variable injection from $uri")
          originalCss

        case Some(cascadingStyleSheet) =>
          CSSParsing.injectVariablesIntoCss(
            cascadingStyleSheet = cascadingStyleSheet,
            variableDefinitions = variableDefinitions,
            lookupMediaQuery    = MediaQuery.PrintMediaQuery,
            lookupSelectors     = lookupSelectors
          )
      }

      new StringReader(modifiedCss)
    } else {
      reader
    }
  }
}

trait URIResolver extends NaiveUserAgent {

  val pipelineContext         : PipelineContext
  implicit val externalContext: ExternalContext
  implicit val indentedLogger : IndentedLogger

  // Unneeded for JVM platform
  private implicit val resourceResolver: Option[ResourceResolver] = None

  lazy val requestOpt: Option[ExternalContext.Request] =
    Option(externalContext).flatMap(ctx => Option(ctx.getRequest))

  // All resources we care about here are resource URLs. The PDF pipeline makes sure that the servlet
  // URL rewriter processes the XHTML output to rewrite resource URLs to absolute paths, including
  // the servlet context and version number if needed. In addition, CSS resources must either use
  // relative paths when pointing to other CSS files or images, or go through the XForms CSS rewriter,
  // which also generates absolute paths.
  // So all we need to do here is rewrite the resulting path to an absolute URL.
  // NOTE: We used to call rewriteResourceURL() here as the PDF pipeline did not do URL rewriting.
  // However this caused issues, for example resources like background images referred by CSS files
  // could be rewritten twice: once by the XForms resource rewriter, and a second time here.
  override def resolveURI(uri: String): String =
    withDebug(s"before resolving URL `$uri`") {
      URLRewriterUtils.rewriteServiceURL(
        requestOpt.orNull,
        uri,
        UrlRewriteMode.AbsoluteNoContext
      )
    }

  // Called by:
  // - getCSSResource
  // - getImageResource below
  // - getBinaryResource (not sure when called)
  // - getXMLResource (not sure when called)
  override protected def openStream(uri: String): InputStream = {

    debug(s"openStream($uri)")

    val resolvedURI = resolveURI(uri)

    // TODO: Use xf:submission code instead
    // Tell callee we are loading that we are a servlet environment, as in effect we act like
    // a browser retrieving resources directly, not like a portlet. This is the case also if we are
    // called by the proxy portlet or if we are directly within a portlet.

    implicit val safeRequestCtx: SafeRequestContext = SafeRequestContext(externalContext)

    val url = URI.create(resolvedURI)
    val headers =
      Connection.buildConnectionHeadersCapitalizedIfNeeded(
        url              = url,
        hasCredentials   = false,
        customHeaders    = Map(Headers.OrbeonClient -> List("servlet")),
        headersToForward = Connection.headersToForwardFromProperty,
        cookiesToForward = Connection.cookiesToForwardFromProperty,
        getHeader        = name => requestOpt flatMap (r => Connection.getHeaderFromRequest(r)(name))
      )

    val cxr =
      Connection.connectNow(
        method           = HttpMethod.GET,
        url              = url,
        credentials      = None,
        content          = None,
        headers          = headers,
        loadState        = true,
        saveState        = true,
        logBody          = false
      )

    ConnectionResult.tryWithSuccessConnection(cxr, closeOnSuccess = false)(identity) doEitherWay {
      pipelineContext.addContextListener((_: Boolean) => cxr.close())
    } get
  }
}