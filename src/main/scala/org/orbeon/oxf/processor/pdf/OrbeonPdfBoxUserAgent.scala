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

import com.openhtmltopdf.layout.SharedContext
import com.openhtmltopdf.pdfboxout.{PdfBoxImage, PdfBoxOutputDevice, PdfBoxUserAgent}
import com.openhtmltopdf.resource.ImageResource
import com.openhtmltopdf.util.{LogMessageId, XRLog}
import org.orbeon.io.IOUtils
import org.orbeon.oxf.externalcontext.{ExternalContext, UrlRewriteMode}
import org.orbeon.oxf.http.{Headers, HttpMethod}
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.util.ImageSupport.{compressJpegImage, findImageOrientation, findTransformation, transformImage}
import org.orbeon.oxf.util.Logging._
import org.orbeon.oxf.util.TryUtils.TryOps
import org.orbeon.oxf.util.{Connection, ConnectionResult, CoreCrossPlatformSupportTrait, IndentedLogger, URLRewriterUtils}

import java.io._
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.logging.Level
import javax.imageio.ImageIO


class OrbeonPdfBoxUserAgent(
  jpegCompressionLevel     : Float,
  outputDevice             : PdfBoxOutputDevice,
  pipelineContext          : PipelineContext,
  dotsPerPixel             : Int)(implicit
  externalContext          : ExternalContext,
  indentedLogger           : IndentedLogger,
  coreCrossPlatformSupport : CoreCrossPlatformSupportTrait
) extends PdfBoxUserAgent(outputDevice) {
  import Private._

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
        image.scale((image.getWidth * dotsPerPixel), (image.getHeight * dotsPerPixel))

    val uriResolved = resolveURI(uriStr)
    if (uriResolved == null) {
      XRLog.log(Level.INFO, LogMessageId.LogMessageId2Param.LOAD_URI_RESOLVER_REJECTED_LOADING_AT_URI, "image", uriStr)
      return new ImageResource(uriStr, null)
    }
    var resource = _imageCache.get(uriResolved)
    if (resource != null && resource.getImage.isInstanceOf[PdfBoxImage]) { // Make copy of PdfBoxImage so we don't stuff up the cache.
      val original = resource.getImage.asInstanceOf[PdfBoxImage]
      val copy = new PdfBoxImage(original.getBytes, original.getUri, original.getWidth, original.getHeight, original.getXObject)
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
          val sourceStreamBytes = readStream(is)
          val imgBytes =
            findImageOrientation(new ByteArrayInputStream(sourceStreamBytes)) match {
              case Some(orientation) if orientation >= 2 && orientation <= 8 =>

                val sourceImage = ImageIO.read(new ByteArrayInputStream(sourceStreamBytes))

                val rotatedImage =
                  transformImage(
                    sourceImage,
                    findTransformation(orientation, sourceImage.getWidth, sourceImage.getHeight)
                      .getOrElse(throw new IllegalStateException)
                  )

                // https://github.com/orbeon/orbeon-forms/issues/4593
                compressJpegImage(rotatedImage, jpegCompressionLevel)
              case _ =>
                sourceStreamBytes
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

  override def resolveURI(uri: String): String = {

    // All resources we care about here are resource URLs. The PDF pipeline makes sure that the servlet
    // URL rewriter processes the XHTML output to rewrite resource URLs to absolute paths, including
    // the servlet context and version number if needed. In addition, CSS resources must either use
    // relative paths when pointing to other CSS files or images, or go through the XForms CSS rewriter,
    // which also generates absolute paths.
    // So all we need to do here is rewrite the resulting path to an absolute URL.
    // NOTE: We used to call rewriteResourceURL() here as the PDF pipeline did not do URL rewriting.
    // However this caused issues, for example resources like background images referred by CSS files
    // could be rewritten twice: once by the XForms resource rewriter, and a second time here.
    withDebug(s"before resolving URL `$uri`") {
      URLRewriterUtils.rewriteServiceURL(
        requestOpt.orNull,
        uri,
        UrlRewriteMode.AbsoluteNoContext
      )
    }
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
        method          = HttpMethod.GET,
        url             = url,
        credentials     = None,
        content         = None,
        headers         = headers,
        loadState       = true,
        saveState       = true,
        logBody         = false)(
        logger          = indentedLogger,
        externalContext = externalContext
      )

    ConnectionResult.tryWithSuccessConnection(cxr, closeOnSuccess = false)(identity) doEitherWay {
      pipelineContext.addContextListener((_: Boolean) => cxr.close())
    } get
  }

  override protected def openReader(uri: String): Reader = {
    debug(s"openReader($uri)")
    new InputStreamReader(openStream(uri), StandardCharsets.UTF_8)
  }

  private object Private {
    val requestOpt = Option(externalContext) flatMap (ctx => Option(ctx.getRequest))
  }
}