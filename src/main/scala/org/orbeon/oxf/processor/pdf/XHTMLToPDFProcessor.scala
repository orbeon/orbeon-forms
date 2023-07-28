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

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.{FSFontUseCase, FontStyle, PageSizeUnits}
import com.openhtmltopdf.pdfboxout.{CustomPdfRendererBuilder, PdfRendererBuilder}
import com.openhtmltopdf.util.XRLog
import org.orbeon.io.IOUtils
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.serializer.HttpSerializerBase
import org.orbeon.oxf.processor.serializer.legacy.HttpBinarySerializer
import org.orbeon.oxf.processor.{ProcessorImpl, ProcessorInput, ProcessorInputOutputInfo}
import org.orbeon.oxf.properties.{Properties, PropertySet}
import org.orbeon.oxf.resources.ResourceManagerWrapper
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util._

import java.io.{FileInputStream, OutputStream}
import scala.util.Try
import scala.util.control.NonFatal


// XHTML to PDF converter using the Open HTML To PDF library
private object XHTMLToPDFProcessor {

  val logger = LoggerFactory.createLogger(classOf[XHTMLToPDFProcessor])

  val DefaultFontFamily = "Inter"
  val DefaultFontPath   = "/apps/fr/print/Inter-Medium.ttf"
  val DefaultFontWeight = 500

  val PdfFontPropertyPrefix       = "oxf.fr.pdf.font."
  val PdfFontPathProperty         = PdfFontPropertyPrefix + "path"
  val PdfFontResourceProperty     = PdfFontPropertyPrefix + "resource"
  val PdfFontFamilyPropertyPrefix = PdfFontPropertyPrefix + "family."
  val PdfJpegCompressionProperty  = "oxf.fr.pdf.jpeg.compression"

  var DefaultContentType  = ContentTypes.PdfContentType
  val DefaultDotsPerPixel = 14 // default is 20, and makes things larger

  def embedFontsConfiguredInProperties(pdfRendererBuilder: CustomPdfRendererBuilder, propertySet: PropertySet): Unit =
    for {
      propName <- propertySet.propertiesStartsWith(PdfFontPathProperty) ++ propertySet.propertiesStartsWith(PdfFontResourceProperty)
      path     <- propertySet.getNonBlankString(propName)
      _ :: _ :: _ :: _ :: pathOrResource :: name :: Nil = propName.splitTo[List](".")
    } {
      try {
        pdfRendererBuilder.useFont(
          () => pathOrResource match {
            case "path"     => new FileInputStream(path)
            case "resource" => ResourceManagerWrapper.instance.getContentAsStream(path)
            case _          => throw new IllegalStateException
          },
          propertySet.getNonBlankString(s"$PdfFontFamilyPropertyPrefix$name").orNull,
          400,
          FontStyle.NORMAL,
          true, // `subset`
          java.util.EnumSet.of(FSFontUseCase.DOCUMENT)
        )
      } catch {
        case NonFatal(_) =>
          logger.warn(s"Failed to load font by path: `$path` specified with property `$propName`")
      }
    }

  def embedDefaultFont(pdfRendererBuilder: CustomPdfRendererBuilder): Unit =
    pdfRendererBuilder.useFont(
      () => ResourceManagerWrapper.instance.getContentAsStream(DefaultFontPath),
      DefaultFontFamily,
      DefaultFontWeight,
      FontStyle.NORMAL,
      true, // `subset`
      java.util.EnumSet.of(FSFontUseCase.FALLBACK_PRE)
    )

  // NOTE: Default compression level is 0.75:
  // https://docs.oracle.com/javase/8/docs/api/javax/imageio/plugins/jpeg/JPEGImageWriteParam.html#JPEGImageWriteParam-java.util.Locale-
  def jpegCompressionLevel: Float =
    Properties.instance.getPropertySet.getNonBlankString(PdfJpegCompressionProperty) flatMap
      (s => Try(java.lang.Float.parseFloat(s)).toOption) filter
      (f => f >= 0f && f <= 1.0f) getOrElse
      0.9f
}

class XHTMLToPDFProcessor extends HttpBinarySerializer {

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

    val requestOpt = Option(externalContext) flatMap (ctx => Option(ctx.getRequest))

    val pdfRendererBuilder = new CustomPdfRendererBuilder
    XRLog.listRegisteredLoggers.forEach(_ => XRLog.setLoggingEnabled(false)) // disable logging

    pdfRendererBuilder.useDefaultPageSize(8.5f, 11f, PageSizeUnits.INCHES)

    val propertySet = Properties.instance.getPropertySet

    pdfRendererBuilder.usePdfUaAccessbility(propertySet.getBoolean("oxf.fr.pdf.accessibility", default = false))

    pdfRendererBuilder.usePdfAConformance(
      propertySet.getString("oxf.fr.pdf.pdf/a", default = "none") match {
        case "none" => PdfRendererBuilder.PdfAConformance.NONE
        case "1a"   => PdfRendererBuilder.PdfAConformance.PDFA_1_A
        case "1b"   => PdfRendererBuilder.PdfAConformance.PDFA_1_B
        case "2a"   => PdfRendererBuilder.PdfAConformance.PDFA_2_A
        case "2b"   => PdfRendererBuilder.PdfAConformance.PDFA_2_B
        case "2u"   => PdfRendererBuilder.PdfAConformance.PDFA_2_U
        case "3a"   => PdfRendererBuilder.PdfAConformance.PDFA_3_A
        case "3b"   => PdfRendererBuilder.PdfAConformance.PDFA_3_B
        case "3u"   => PdfRendererBuilder.PdfAConformance.PDFA_3_U
        case other  => throw new IllegalArgumentException(s"Invalid PDF/A conformance: `$other`")
      }
    )

    embedFontsConfiguredInProperties(pdfRendererBuilder, propertySet)
    embedDefaultFont(pdfRendererBuilder)

    IOUtils.useAndClose(outputStream) { os =>

      pdfRendererBuilder.toStream(os)

      pdfRendererBuilder.withW3cDocument(
        readInputAsDOM(pipelineContext, input),
        requestOpt map (_.getRequestURL) orNull // no base URL if can't get request URL from context
      )

      IOUtils.useAndClose(
        pdfRendererBuilder.buildPdfRenderer(outputDevice =>
          new OrbeonPdfBoxUserAgent(jpegCompressionLevel, outputDevice, pipelineContext, DefaultDotsPerPixel)
        )
      ) { pdfBoxRenderer =>

        pdfBoxRenderer.getSharedContext.setDotsPerPixel(DefaultDotsPerPixel)
        pdfBoxRenderer.layout()

        // Page count might be zero!
        // Q: Log if no pages?
        val hasPages = Option(pdfBoxRenderer.getRootBox.getLayer.getPages) exists (_.size > 0)
        if (hasPages)
          pdfBoxRenderer.createPDF()
      }
    }
  }
}