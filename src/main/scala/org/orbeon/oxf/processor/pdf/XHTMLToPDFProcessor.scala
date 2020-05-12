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
package org.orbeon.oxf.processor.pdf

import java.io.{InputStream, OutputStream}
import java.net.URI

import com.lowagie.text.pdf.BaseFont
import org.orbeon.io.{IOUtils, UriScheme}
import org.orbeon.oxf.externalcontext.URLRewriter
import org.orbeon.oxf.http.{Headers, HttpMethod}
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.serializer.HttpSerializerBase
import org.orbeon.oxf.processor.serializer.legacy.HttpBinarySerializer
import org.orbeon.oxf.processor.{ProcessorImpl, ProcessorInput, ProcessorInputOutputInfo}
import org.orbeon.oxf.properties.Properties
import org.orbeon.oxf.resources.ResourceManagerWrapper
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.TryUtils._
import org.orbeon.oxf.util._
import org.xhtmlrenderer.pdf.{ITextRenderer, ITextUserAgent}
import org.xhtmlrenderer.resource.ImageResource

import scala.collection.mutable
import scala.util.control.NonFatal

// XHTML to PDF converter using the Flying Saucer library.
private object XHTMLToPDFProcessor {

  val logger = LoggerFactory.createLogger(classOf[XHTMLToPDFProcessor])

  var DefaultContentType  = "application/pdf"
  val DefaultDotsPerPoint = 20f * 4f / 3f
  val DefaultDotsPerPixel = 14

  def embedFontsConfiguredInProperties(renderer: ITextRenderer): Unit = {

    val props = Properties.instance.getPropertySet

    for {
      propName  <- props.propertiesStartsWith("oxf.fr.pdf.font.path") ++ props.propertiesStartsWith("oxf.fr.pdf.font.resource")
      path      <- props.getNonBlankString(propName)
      _ :: _ :: _ :: _ :: pathOrResource :: name :: Nil = propName.splitTo[List](".")
    } locally {
      try {

        val familyOpt = props.getNonBlankString(s"oxf.fr.pdf.font.family.$name")

        val absolutePath =
          pathOrResource match {
            case "path"     => path
            case "resource" => ResourceManagerWrapper.instance.getRealPath(path)
            case _          => throw new IllegalStateException
          }

        renderer.getFontResolver.addFont(absolutePath, familyOpt.orNull, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, null)
      } catch {
        case NonFatal(e) =>
          logger.warn(s"Failed to load font by path: `$path` specified with property `$propName`")
      }
    }
  }
}

class XHTMLToPDFProcessor() extends HttpBinarySerializer {

  import XHTMLToPDFProcessor._

  addInputInfo(new ProcessorInputOutputInfo(ProcessorImpl.INPUT_DATA))

  protected def getDefaultContentType: String = XHTMLToPDFProcessor.DefaultContentType

  protected def readInput(
     pipelineContext : PipelineContext,
     input           : ProcessorInput,
     config          : HttpSerializerBase.Config,
     outputStream    : OutputStream
  ): Unit = {

    implicit val externalContext = NetUtils.getExternalContext

    val domDocument = readInputAsDOM(pipelineContext, input)
    val requestOpt  = Option(externalContext) flatMap (ctx => Option(ctx.getRequest))
    val renderer    = new ITextRenderer(DefaultDotsPerPoint, DefaultDotsPerPixel)

    try {
      embedFontsConfiguredInProperties(renderer)

      val callback = new ITextUserAgent(renderer.getOutputDevice) {

        implicit val indentedLogger = new IndentedLogger(XHTMLToPDFProcessor.logger)

        // See https://github.com/orbeon/orbeon-forms/issues/1996
        // Use our own local cache (`NaiveUserAgent` has one too) so that we can cache against the absolute URL
        // yet pass a local URL to `super.getImageResource()`.
        // This doesn't live beyond the production of this PDF as the `ITextUserAgent` is created each time.
        val localImageCache = mutable.HashMap[String, ImageResource]()

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
              URLRewriter.REWRITE_MODE_ABSOLUTE | URLRewriter.REWRITE_MODE_ABSOLUTE_PATH_NO_CONTEXT
            )

          indentedLogger.logDebug("pdf", "after resolving URL", "url", resolved)
          resolved
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

        override def getImageResource(uri: String): ImageResource = {
          val resolvedURI = resolveURI(uri)
          localImageCache.getOrElseUpdate(
            resolvedURI,
            {
              val is = resolveAndOpenStream(resolvedURI)
              val localURI = NetUtils.inputStreamToAnyURI(is, NetUtils.REQUEST_SCOPE, XHTMLToPDFProcessor.logger)
              indentedLogger.logDebug("pdf", "getting image resource", "url", uri, "local", localURI)
              super.getImageResource(localURI)
            }
          )
        }
      }

      callback.setSharedContext(renderer.getSharedContext)
      renderer.getSharedContext.setUserAgentCallback(callback)
      // renderer.getSharedContext().setDPI(150);

      renderer.setDocument(
        domDocument,
        requestOpt map (_.getRequestURL) orNull // no base URL if can't get request URL from context
      )

      renderer.layout()

      IOUtils.useAndClose(outputStream) { os =>
        // Page count might be zero!
        // Q: Log if no pages?
        val hasPages = Option(renderer.getRootBox.getLayer.getPages) exists (_.size > 0)
        if (hasPages)
          renderer.createPDF(os)
      }
    } finally {
      // Free resources associated with the rendering context
      renderer.getSharedContext.reset()
    }
  }
}