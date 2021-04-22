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

import java.io.OutputStream

import com.lowagie.text.pdf.BaseFont
import org.orbeon.io.IOUtils
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.serializer.HttpSerializerBase
import org.orbeon.oxf.processor.serializer.legacy.HttpBinarySerializer
import org.orbeon.oxf.processor.{ProcessorImpl, ProcessorInput, ProcessorInputOutputInfo}
import org.orbeon.oxf.properties.Properties
import org.orbeon.oxf.resources.ResourceManagerWrapper
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util._
import org.xhtmlrenderer.pdf.ITextRenderer

import scala.util.Try
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
        case NonFatal(_) =>
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

    implicit val externalContext         : ExternalContext               = NetUtils.getExternalContext
    implicit val indentedLogger          : IndentedLogger                = new IndentedLogger(XHTMLToPDFProcessor.logger)
    implicit val coreCrossPlatformSupport: CoreCrossPlatformSupportTrait = CoreCrossPlatformSupport

    val requestOpt  = Option(externalContext) flatMap (ctx => Option(ctx.getRequest))
    val renderer    = new ITextRenderer(DefaultDotsPerPoint, DefaultDotsPerPixel)

    try {
      embedFontsConfiguredInProperties(renderer)


      // NOTE: Default compression level is 0.75:
      // https://docs.oracle.com/javase/8/docs/api/javax/imageio/plugins/jpeg/JPEGImageWriteParam.html#JPEGImageWriteParam-java.util.Locale-
      val jpegCompressionLevel =
        Properties.instance.getPropertySet.getNonBlankString("oxf.fr.pdf.jpeg.compression") flatMap
          (s => Try(java.lang.Float.parseFloat(s)).toOption) filter
          (f => f >= 0f && f <= 1.0f) getOrElse
          0.9f

      renderer.getSharedContext.setUserAgentCallback(
        new CustomUserAgent(
          jpegCompressionLevel,
          pipelineContext,
          renderer.getSharedContext
        )
      )
      // renderer.getSharedContext().setDPI(150);

      renderer.setDocument(
        readInputAsDOM(pipelineContext, input),
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