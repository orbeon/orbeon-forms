/**
 * Copyright (C) 2020 Orbeon, Inc.
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

import java.io.{ByteArrayInputStream, InputStream}
import java.net.URI

import com.lowagie.text.Image
import javax.imageio.ImageIO
import org.orbeon.io.IOUtils
import org.orbeon.oxf.externalcontext.{ExternalContext, URLRewriter}
import org.orbeon.oxf.http.{Headers, HttpMethod}
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.pdf.ImageSupport._
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.TryUtils._
import org.orbeon.oxf.util.{Connection, ConnectionResult, IndentedLogger, NetUtils, URLRewriterUtils}
import org.xhtmlrenderer.layout.SharedContext
import org.xhtmlrenderer.pdf.ITextFSImage
import org.xhtmlrenderer.resource.ImageResource
import org.xhtmlrenderer.swing.NaiveUserAgent
import org.xhtmlrenderer.util.{ImageUtil, XRLog}

import scala.collection.mutable
import scala.util.Try
import scala.util.control.NonFatal


class CustomUserAgent(
  jpegCompressionLevel : Float,
  pipelineContext      : PipelineContext,
  sharedContext        : SharedContext)(implicit
  externalContext      : ExternalContext,
  indentedLogger       : IndentedLogger
) extends NaiveUserAgent {

  import Private._

  override def getImageResource(originalUriString: String): ImageResource = {

    def fromBase64(uriString: String): Option[Try[ImageResource]] =
      ImageUtil.isEmbeddedBase64Image(uriString) option
        Try(loadEmbeddedBase64ImageResource(uriString)) // always return an `ImageResource``

    def fromImage(uriString: String): Try[ImageResource] =
      Try {
        IOUtils.useAndClose(resolveAndOpenStream(uriString)) { is =>

          val sourceStreamBytes = NetUtils.inputStreamToByteArray(is)

          val imageBytes =
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

          new ImageResource(
            uriString,
            new ITextFSImage(Image.getInstance(imageBytes) |!> scaleToOutputResolution)
          )
        }
      }

    // Not sure why this cloning is needed (was from original code)
    def maybeClone(resource: ImageResource): ImageResource =
      resource.getImage match {
        case iTextFsImage: ITextFSImage => new ImageResource(resource.getImageUri, iTextFsImage.clone().asInstanceOf[ITextFSImage])
        case _                          => resource
      }

    def createImageResource(originalUriString: String, resolvedUriString: String): ImageResource = {

      val localUri =
        NetUtils.inputStreamToAnyURI(
          resolveAndOpenStream(resolvedUriString),
          NetUtils.REQUEST_SCOPE,
          XHTMLToPDFProcessor.logger.logger
        )

      indentedLogger.logDebug("pdf", "getting image resource", "url", originalUriString, "local", localUri)

      fromBase64(localUri)  getOrElse
        fromImage(localUri) map
        maybeClone          getOrElse
        new ImageResource(originalUriString, null) // for some reason, we return a "null" image resource
    }

    val resolvedUriString = resolveURI(originalUriString)
    localImageCache.getOrElseUpdate(
      resolvedUriString,
      createImageResource(originalUriString, resolvedUriString)
    )
  }

  // Called by:
  // - getCSSResource
  // - getImageResource below
  // - getBinaryResource (not sure when called)
  // - getXMLResource (not sure when called)
  override protected def resolveAndOpenStream(uri: String): InputStream = {

    val resolvedURI = resolveURI(uri)

    // TODO: Use xf:submission code instead
    // Tell callee we are loading that we are a servlet environment, as in effect we act like
    // a browser retrieving resources directly, not like a portlet. This is the case also if we are
    // called by the proxy portlet or if we are directly within a portlet.

    val url = new URI(resolvedURI)
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
      Connection(
        method          = HttpMethod.GET,
        url             = url,
        credentials     = None,
        content         = None,
        headers         = headers,
        loadState       = true,
        logBody         = false)(
        logger          = indentedLogger,
        externalContext = externalContext
      ).connect(
        saveState = true
      )

    ConnectionResult.tryWithSuccessConnection(cxr, closeOnSuccess = false)(identity) doEitherWay {
      pipelineContext.addContextListener((_: Boolean) => cxr.close())
    } get
  }

  // Called for:
  //
  // - CSS URLs
  // - image URLs
  // - link clicked / form submission (not relevant for our usage)
  // - resolveAndOpenStream below
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
    indentedLogger.logDebug("pdf", "before resolving URL", "url", uri)

    val resolved =
      URLRewriterUtils.rewriteServiceURL(
        requestOpt.orNull,
        uri,
        URLRewriter.REWRITE_MODE_ABSOLUTE_NO_CONTEXT
      )

    indentedLogger.logDebug("pdf", "after resolving URL", "url", resolved)
    resolved
  }

  private object Private {

    val requestOpt = Option(externalContext) flatMap (ctx => Option(ctx.getRequest))

    // See https://github.com/orbeon/orbeon-forms/issues/1996
    // Use our own local cache (`NaiveUserAgent` has one too) so that we can cache against the absolute URL
    // yet pass a local URL to `super.getImageResource()`. This doesn't live beyond the production of this
    // PDF as the user agent instance is created each time.
    val localImageCache: mutable.Map[String, ImageResource] = mutable.HashMap()

    def loadEmbeddedBase64ImageResource(uri: String): ImageResource =
      try {
        val buffer = ImageUtil.getEmbeddedBase64Image(uri)
        val image = Image.getInstance(buffer)
        scaleToOutputResolution(image)
        new ImageResource(null, new ITextFSImage(image))
      } catch {
        case NonFatal(t) =>
          XRLog.exception(s"Can't read XHTML embedded image.", t)
          new ImageResource(null, null)
      }

    def scaleToOutputResolution(image: com.lowagie.text.Image): Unit = {
      val factor = sharedContext.getDotsPerPixel
      if (factor != 1.0f)
        image.scaleAbsolute(image.getPlainWidth * factor, image.getPlainHeight * factor)
    }
  }
}
