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
import org.orbeon.css.CSSParsing
import org.orbeon.io.IOUtils
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.serializer.HttpSerializerBase
import org.orbeon.oxf.processor.serializer.legacy.HttpBinarySerializer
import org.orbeon.oxf.processor.{ProcessorImpl, ProcessorInput, ProcessorInputOutputInfo}
import org.orbeon.oxf.properties.{Properties, PropertySet}
import org.orbeon.oxf.resources.ResourceManagerWrapper
import org.orbeon.oxf.util.*
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.xml.{ForwardingXMLReceiver, TransformerUtils, XMLParsing, XMLReceiver}
import org.w3c.dom.Document
import org.xml.sax.Attributes
import org.xml.sax.helpers.AttributesImpl

import java.io.{FileInputStream, OutputStream}
import java.net.{URI, URL}
import java.text.Normalizer
import javax.xml.transform.dom.DOMResult
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

  private def embedFontsConfiguredInProperties(pdfRendererBuilder: CustomPdfRendererBuilder, propertySet: PropertySet): Unit =
    for {
      propName <- propertySet.propertiesStartsWith(PdfFontPathProperty) ++ propertySet.propertiesStartsWith(PdfFontResourceProperty)
      path     <- propertySet.getNonBlankString(propName)
      _ :: _ :: _ :: _ :: pathOrResource :: name :: Nil = propName.splitTo[List](".")
    } locally {
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
        case NonFatal(t) =>
          logger.warn(t)(s"Failed to load font with property `$propName` of value `$path`")
      }
    }

  private def embedDefaultFont(pdfRendererBuilder: CustomPdfRendererBuilder): Unit =
    pdfRendererBuilder.useFont(
      () => ResourceManagerWrapper.instance.getContentAsStream(DefaultFontPath),
      DefaultFontFamily,
      DefaultFontWeight,
      FontStyle.NORMAL,
      true, // `subset`
      java.util.EnumSet.of(FSFontUseCase.FALLBACK_FINAL)
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

  import XHTMLToPDFProcessor.*

  addInputInfo(new ProcessorInputOutputInfo(ProcessorImpl.INPUT_DATA))

  //  protected
  def getDefaultContentType: String = XHTMLToPDFProcessor.DefaultContentType

  //  protected
  def readInput(
    pipelineContext : PipelineContext,
    input           : ProcessorInput,
    config          : HttpSerializerBase.Config,
    outputStream    : OutputStream
  ): Unit = {

    implicit val externalContext: ExternalContext = NetUtils.getExternalContext
    implicit val indentedLogger : IndentedLogger  = new IndentedLogger(XHTMLToPDFProcessor.logger)

    val requestOpt = Option(externalContext).flatMap(ec => Option(ec.getRequest))

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

      // We are trying to avoid `getRequestURL`. Most likely, we do not rely on this to resolve resources in
      // `OrbeonPdfBoxUserAgent`.
      val baseUri = requestOpt.map(_.getRequestURI)

      val w3cDocument =
        readInputAsDOM(
          pipelineContext,
          input,
          rcv => new ForwardingXMLReceiver(rcv) {

            // Ensure characters are normalized to NFC in order to avoid issues with incorrect rendering of diacritics
            // by the PDF renderer. It should be very rare to have non-normalized attribute values that actually matter
            // in the PDF output, but we check for this anyway.
            // https://github.com/orbeon/orbeon-forms/issues/7214

            override def characters(chars: Array[Char], start: Int, length: Int): Unit = {
              val cs = java.nio.CharBuffer.wrap(chars, start, length)
              if (Normalizer.isNormalized(cs, Normalizer.Form.NFC)) {
                super.characters(chars, start, length)
              } else {
                val normalized = Normalizer.normalize(cs, Normalizer.Form.NFC)
                super.characters(normalized.toCharArray, 0, normalized.length)
              }
            }

            override def startElement(uri: String, localname: String, qName: String, attributes: Attributes): Unit =
              if ((0 until attributes.getLength)
                .forall(i => Normalizer.isNormalized(attributes.getValue(i), Normalizer.Form.NFC))) {
                super.startElement(uri, localname, qName, attributes)
              } else {
                val newAttributes = new AttributesImpl(attributes)
                (0 until attributes.getLength).foreach { i =>
                  val value = attributes.getValue(i)
                  if (! Normalizer.isNormalized(value, Normalizer.Form.NFC)) {
                    val normalized = Normalizer.normalize(value, Normalizer.Form.NFC)
                    newAttributes.setValue(i, normalized)
                  }
                }
                super.startElement(uri, localname, qName, newAttributes)
              }
          }
        )

      val (outerPipelineContext, outerExternalContext, outerIndentedLogger) =
        (pipelineContext, externalContext, indentedLogger)

      val uriResolver = new URIResolver {
        override val pipelineContext         : PipelineContext = outerPipelineContext
        override implicit val externalContext: ExternalContext = outerExternalContext
        override implicit val indentedLogger : IndentedLogger  = outerIndentedLogger
      }

      val variableDefinitions =
        CSSParsing.variableDefinitions(
          // Retrieve CSS resources from the document (link and style elements)
          resources          = CSSParsing.cssResources(w3cDocument),
          inputStreamFromURI = (uri: URI) => new URL(uriResolver.resolveURI(uri.toString)).openStream()
        )

      pdfRendererBuilder.withW3cDocument(
        w3cDocument,
        baseUri.orNull // no base URL if can't get request URL from context
      )

      IOUtils.useAndClose(
        pdfRendererBuilder.buildPdfRenderer(outputDevice =>
          new OrbeonPdfBoxUserAgent(
            jpegCompressionLevel,
            outputDevice,
            pipelineContext,
            DefaultDotsPerPixel,
            variableDefinitions
          )
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

  def readInputAsDOM(context: PipelineContext, input: ProcessorInput, filter: XMLReceiver => XMLReceiver): Document = {
    val identity = TransformerUtils.getIdentityTransformerHandler
    val domResult = new DOMResult(XMLParsing.createDocument)
    identity.setResult(domResult)
    ProcessorImpl.readInputAsSAX(context, input, filter(identity))
    domResult.getNode.asInstanceOf[Document]
  }
}