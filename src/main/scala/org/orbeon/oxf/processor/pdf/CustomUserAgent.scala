/*
 * Copyright (c) 2004, 2005 Torbjoern Gannholm
 * Copyright (c) 2006 Wisconsin Court System
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2.1
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 */
package org.orbeon.oxf.processor.pdf

import java.awt.Color
import java.awt.geom.AffineTransform
import java.awt.image.{AffineTransformOp, BufferedImage}
import java.io.{BufferedInputStream, InputStream}
import java.net.URI

import com.lowagie.text.Image
import javax.imageio.ImageIO
import org.orbeon.io.IOUtils
import org.orbeon.oxf.externalcontext.{ExternalContext, URLRewriter}
import org.orbeon.oxf.http.{Headers, HttpMethod}
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.TryUtils._
import org.orbeon.oxf.util.{Connection, ConnectionResult, ImageMetadata, IndentedLogger, NetUtils, URLRewriterUtils}
import org.xhtmlrenderer.layout.SharedContext
import org.xhtmlrenderer.pdf.{ITextFSImage, ITextOutputDevice, PDFAsImage}
import org.xhtmlrenderer.resource.ImageResource
import org.xhtmlrenderer.swing.NaiveUserAgent
import org.xhtmlrenderer.util.{Configuration, ContentTypeDetectingInputStreamWrapper, ImageUtil, XRLog}

import scala.collection.mutable
import scala.util.Try
import scala.util.control.NonFatal

object CustomUserAgent {
  private val IMAGE_CACHE_CAPACITY = 32
}

class CustomUserAgent(
  pipelineContext : PipelineContext,
  outputDevice    : ITextOutputDevice,
  sharedContext   : SharedContext)(implicit
  externalContext : ExternalContext,
  indentedLogger  : IndentedLogger
) extends NaiveUserAgent(
    Configuration.valueAsInt(
      "xr.image.cache-capacity",
      CustomUserAgent.IMAGE_CACHE_CAPACITY
    )
  ) {

  import Private._

  private val requestOpt = Option(externalContext) flatMap (ctx => Option(ctx.getRequest))

  override def getImageResource(originalUriString: String): ImageResource = {

    def fromBase64(uriString: String): Option[Try[ImageResource]] =
      ImageUtil.isEmbeddedBase64Image(uriString) option
        Try(loadEmbeddedBase64ImageResource(uriString)) // always return an `ImageResource``

    // Not sure we even ever need to read a PDF "image"!
    def fromPdf(uriString: String): ImageResource = {

      val uri   = new URI(uriString)
      val rect  = outputDevice.getReader(uri).getPageSizeWithRotation(1)

      val image = new PDFAsImage(uri) |!>
        (_.setInitialWidth(rect.getWidth * outputDevice.getDotsPerPoint)) |!>
        (_.setInitialHeight(rect.getHeight * outputDevice.getDotsPerPoint))

      new ImageResource(uriString, image)
    }

    def fromImage(is: InputStream, uriString: String): ImageResource = {

      val (bis, orientationOpt) = findImageOrientation(is)
      val sourceImage           = ImageIO.read(bis)

      val rotatedImageOpt =
        orientationOpt flatMap
          (findTransformation(_, sourceImage.getWidth, sourceImage.getHeight)) map
          (transformImage(sourceImage, _))

      val iTextImage = Image.getInstance(rotatedImageOpt getOrElse sourceImage, null)
      scaleToOutputResolution(iTextImage)

      new ImageResource(uriString, new ITextFSImage(iTextImage))
    }

    def fromPdfOrImage(uriString: String): Try[ImageResource] =
      Try {
        IOUtils.useAndClose(resolveAndOpenStream(uriString)) { is =>
          val cis = new ContentTypeDetectingInputStreamWrapper(is)
          if (cis.isPdf)
            fromPdf(uriString)
          else
            fromImage(cis, uriString)
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
          XHTMLToPDFProcessor.logger
        )

      indentedLogger.logDebug("pdf", "getting image resource", "url", originalUriString, "local", localUri)

      fromBase64(localUri)       getOrElse
        fromPdfOrImage(localUri) map
        maybeClone               getOrElse
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

    val MaxExifSize: Int = 64 * 1024

    def findImageOrientation(is: InputStream): (BufferedInputStream, Option[Int]) = {

      val bis = new BufferedInputStream(is, MaxExifSize)
      bis.mark(MaxExifSize)

      val orientationOpt =
        ImageMetadata.findKnownMetadata(bis, ImageMetadata.MetadataType.Orientation) collect {
          case i: Int => i.intValue
        }

      bis.reset()

      (bis, orientationOpt)
    }

    def findTransformation(orientation: Int, width: Int, height: Int): Option[AffineTransform] =
      orientation match {
        case 2 =>
          // Horizontal flip
          Some(
            new AffineTransform            |!>
              (_.scale(-1.0, 1.0))         |!>
              (_.translate(-width, 0))
          )
        case 3 =>
          // 180 degree rotation
          Some(
            new AffineTransform            |!>
              (_.translate(width, height)) |!>
              (_.rotate(Math.PI))
          )
        case 4 =>
          // Vertical flip
          Some(
            new AffineTransform            |!>
              (_.scale(1.0, -1.0))         |!>
              (_.translate(0, -height))
          )
        case 5 =>
          Some(
            new AffineTransform            |!>
              (_.rotate(-Math.PI / 2))     |!>
              (_.scale(-1.0, 1.0))
          )
        case 6 =>
          // 90 degree rotation
          Some(
            new AffineTransform            |!>
              (_.translate(height, 0))     |!>
              (_.rotate(Math.PI / 2))
          )
        case 7 =>
          Some(
            new AffineTransform            |!>
              (_.scale(-1.0, 1.0))         |!>
              (_.translate(-height, 0))    |!>
              (_.translate(0, width))      |!>
              (_.rotate(  3 * Math.PI / 2))
          )
        case 8 =>
          // 270 degree rotation
          Some(
            new AffineTransform            |!>
              (_.translate(0, width))      |!>
              (_.rotate(  3 * Math.PI / 2))
          )
        case _ =>
          None
      }

    def transformImage(sourceImage: BufferedImage, transform: AffineTransform): BufferedImage = {

      val op = new AffineTransformOp(transform, AffineTransformOp.TYPE_BICUBIC)

      val destinationImage =
        op.createCompatibleDestImage(
          sourceImage,
          if (sourceImage.getType == BufferedImage.TYPE_BYTE_GRAY) sourceImage.getColorModel else null
        )

      // Not sure we need to clear
      val g = destinationImage.createGraphics
      g.setBackground(Color.WHITE)
      g.clearRect(0, 0, destinationImage.getWidth, destinationImage.getHeight)

      op.filter(sourceImage, destinationImage)
    }
  }
}
