/**
 * Copyright (C) 2015 Orbeon, Inc.
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

import java.awt.image._
import java.io.{File, OutputStream}
import javax.imageio.{IIOImage, ImageIO, ImageTypeSpecifier, ImageWriteParam}

import org.apache.commons.fileupload.disk.DiskFileItem
import org.icepdf.core.pobjects.{Page, Document => ICEDocument}
import org.icepdf.core.util.GraphicsRenderingHints
import org.orbeon.dom.{Element => DOM4JElement}
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.ProcessorImpl._
import org.orbeon.oxf.processor._
import org.orbeon.oxf.processor.pdf.fop.JAIMonochromeBitmapConverter
import org.orbeon.oxf.processor.serializer.BinaryTextXMLReceiver
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.CollectionUtils._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util._
import org.orbeon.oxf.xml.XMLReceiver

import scala.collection.JavaConverters._
// This processor converts a PDF, provided as a binary document on its `data` input, into an image (possibly a
// multi-page TIFF image) on its `data` output. It is configurable via its `config` input.
//
// The implementation relies on:
//
// - ICEpdf for the PDF-to-image conversion proper
// - ImageIO for  writing the image (with the Java Advanced Imaging library jai-imageio-core for TIFF support)
// - some FOP code for dithering as a shortcut (which in fact uses Java Advanced Imaging library jai-core)
//
// The main use case is to use TIFF as an output format, as that supports multi-page and a PDF file is often
// multi-page. If the input is a single-page PDF, then other output formats make sense too.
//
// In the future one could imagine the processor producing a sequence of images as well, which could then be
// combined into a ZIP file for example.

class PDFToImageProcessor extends ProcessorImpl with Logging {

  import PDFToImage._

  private val Logger = LoggerFactory.createLogger(classOf[PDFToImageProcessor])

  addInputInfo(new ProcessorInputOutputInfo("data"))
  addInputInfo(new ProcessorInputOutputInfo("config"))

  protected def getDefaultContentType = "image/tiff"

  override def createOutput(outputName: String): ProcessorOutput =
    new ProcessorOutputImpl(PDFToImageProcessor.this, outputName) {
      override protected def readImpl(pc: PipelineContext, xmlReceiver: XMLReceiver): Unit = {

        implicit val logger = new IndentedLogger(Logger)

        val config =
          readCacheInputAsObject(pc, getInputByName(INPUT_CONFIG), new CacheableInputReader[Config] {
            override def read(pipelineContext: PipelineContext, input: ProcessorInput): Config = {

              val configElem = readInputAsOrbeonDom(pipelineContext, input).getRootElement

              def elemValue(elem: DOM4JElement) =
                Option(elem) map (_.getStringValue) flatMap trimAllToOpt

              def floatValue(elem: DOM4JElement) =
                elemValue(elem) map (_.toFloat)

              val scale  = floatValue(configElem.element("scale")) getOrElse 1f
              val format = elemValue(configElem.element("format")) getOrElse (throw new OXFException(s"No image format specified."))

              val compression = Option(configElem.element("compression")) map { compressionElem =>
                val tpe     = elemValue(compressionElem.element("type"))
                val quality = floatValue(compressionElem.element("quality"))

                Compression(tpe, quality)
              }

              Config(scale, format, compression)
            }
          })

        val outputStream = new ContentHandlerOutputStream(xmlReceiver, true)
        outputStream.setContentType(s"image/${config.format}")

        val fileItem = NetUtils.prepareFileItem(NetUtils.REQUEST_SCOPE, Logger)
        try {
          readInputAsSAX(pc, "data", new BinaryTextXMLReceiver(fileItem.getOutputStream))
          convert(config, fileItem.asInstanceOf[DiskFileItem].getStoreLocation, outputStream)
        } finally {
          fileItem.delete()
        }

        outputStream.close()
      }
    }
}

object PDFToImage {

  // Reference
  //
  // ImageIO.getWriterFormatNames returns, when jai-imageio-core is present:
  //
  // JPG, jpg, tiff, pcx, PCX, bmp, BMP, gif, GIF, WBMP, png, PNG, raw, RAW, JPEG, pnm, PNM, tif, TIF, TIFF,
  // wbmp, jpeg
  //
  // ImageWriteParam.getCompressionTypes for "tiff" returns:
  //
  // CCITT RLE, CCITT T.4, CCITT T.6, LZW, JPEG, ZLib, PackBits, Deflate, EXIF JPEG

  case class Compression(tpe: Option[String], quality: Option[Float])
  case class Config(scale: Float, format: String, compression: Option[Compression])

  // The ImageIO API is supposed to allow discovery, but the results are inconsistent. We list below the formats we
  // want to support explicitly based on experimentation.
  val SupportedFormatCompressions = Map(
    "gif"  -> Set("LZW"),        // doesn't support disabling compression
    "png"  -> Set.empty[String], // uses DEFLATE, but API doesn't support setting it (`canWriteCompressed == false`)
    "jpeg" -> Set("JPEG"),       // doesn't support disabling compression
    "tiff" -> Set("CCITT RLE", "CCITT T.4", "CCITT T.6", "LZW", "JPEG", "ZLib", "PackBits", "Deflate", "EXIF JPEG") // supports disabling compression
  )

  val SupportedFormats = SupportedFormatCompressions.keySet

  val KnownBlackAndWhiteTIFFCompressions = Set("CCITT RLE", "CCITT T.4", "CCITT T.6")

  // NOTE: Checked experimentally that invocations of getImageWritersByFormatName return separate instances.
  def findNewImageWriterForFormat(format: String) =
    ImageIO.getImageWritersByFormatName(format).asScala.nextOption()

  // Convert a PDF to an image, given the specified configuration
  def convert(config: Config, file: File, outputStream: OutputStream) = {

    val iceDocument = new ICEDocument
    iceDocument.setFile(file.getAbsolutePath)

    try {
      if (! SupportedFormats(config.format))
        throw new OXFException(s"Unsupported image format ${config.format}.")

      val imageWriter = findNewImageWriterForFormat(config.format) getOrElse
        (throw new OXFException(s"No image writer found for image format ${config.format}."))

      try {
        val imageOutput = ImageIO.createImageOutputStream(outputStream)

        imageWriter.setOutput(imageOutput)

        val params = {
          val newParams = imageWriter.getDefaultWriteParam

          // We can only call `setCompressionMode` if `canWriteCompressed == true`
          if (newParams.canWriteCompressed) {

            // GIF *requires* setting a compression
            def defaultCompressionIfAny = {
              val supportedCompressions = SupportedFormatCompressions(config.format)
              if (supportedCompressions.size == 1) {
                newParams.setCompressionMode(ImageWriteParam.MODE_EXPLICIT)
                Some(supportedCompressions.head)
              }  else
                None
            }

            config.compression match {
              case None | Some(Compression(None, None)) =>
                defaultCompressionIfAny match {
                  case Some(default) =>
                    newParams.setCompressionMode(ImageWriteParam.MODE_EXPLICIT)
                    newParams.setCompressionType(default)
                  case None =>
                    newParams.setCompressionMode(ImageWriteParam.MODE_DEFAULT)
                }
              case Some(Compression(Some("none"), _)) =>
                newParams.setCompressionMode(ImageWriteParam.MODE_DISABLED)
              case Some(Compression(tpe, quality)) =>
                newParams.setCompressionMode(ImageWriteParam.MODE_EXPLICIT)

                tpe orElse defaultCompressionIfAny foreach newParams.setCompressionType

                quality foreach newParams.setCompressionQuality
            }
          }
          newParams
        }

        val hasBlackAndWhiteTIFFCompressionType =
          config.compression flatMap (_.tpe) exists KnownBlackAndWhiteTIFFCompressions

        // If the writer doesn't support writing sequences, we only write one page at most, and we use a
        // different API to write an individual image.
        val canWriteSequence = imageWriter.canWriteSequence

        val numberOfPagesToWrite =
          if (canWriteSequence)
            iceDocument.getNumberOfPages
          else
            iceDocument.getNumberOfPages min 1

        if (canWriteSequence)
          imageWriter.prepareWriteSequence(null)

        for (pageIndex <- 0 until numberOfPagesToWrite) {

          val bufferedImage =
            iceDocument.getPageImage(
              pageIndex,
              GraphicsRenderingHints.PRINT,
              Page.BOUNDARY_CROPBOX,
              0f,
              config.scale
            ).asInstanceOf[BufferedImage]

          val imageToWrite =
            if (hasBlackAndWhiteTIFFCompressionType) {
              // JAIMonochromeBitmapConverter is copied over from Apache FOP
              val converter = new JAIMonochromeBitmapConverter |!> (_.setHint("quality", "true"))
              val newImage = converter.convertToMonochrome(bufferedImage)
              bufferedImage.flush() // unclear whether needed
              newImage
            } else {
              bufferedImage
            }

          val spec = ImageTypeSpecifier.createFromRenderedImage(imageToWrite)
          val metadata = imageWriter.getDefaultImageMetadata(spec, params)

          if (canWriteSequence)
            imageWriter.writeToSequence(new IIOImage(imageToWrite, null, metadata), params)
          else
            imageWriter.write(new IIOImage(imageToWrite, null, metadata))

          imageToWrite match {
            case img: BufferedImage => img.flush() // unclear whether needed
            case _ =>
          }
        }

        if (canWriteSequence)
          imageWriter.endWriteSequence() // implementation just checks/set a flag

        imageOutput.flush()                // probably not needed, based on experimentation
        imageOutput.close()                // necessary, based on experimentation
      } finally {
        imageWriter.dispose()              // implementation just resets writer's fields, probably unneeded
      }
    } finally {
      iceDocument.dispose()
    }
  }
}