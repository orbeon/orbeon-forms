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
import org.apache.fop.util.bitmap.JAIMonochromeBitmapConverter
import org.dom4j.{Element ⇒ DOM4JElement}
import org.icepdf.core.pobjects.{Document ⇒ ICEDocument, Page}
import org.icepdf.core.util.GraphicsRenderingHints
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.ProcessorImpl._
import org.orbeon.oxf.processor._
import org.orbeon.oxf.processor.serializer.BinaryTextXMLReceiver
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.util._
import org.orbeon.oxf.xml.XMLReceiver

import scala.collection.JavaConverters._

class PDFToImageProcessor extends ProcessorImpl with Logging {

    import PDFToImage._

    private val Logger = LoggerFactory.createLogger(classOf[PDFToImageProcessor])

    addInputInfo(new ProcessorInputOutputInfo("data"))
    addInputInfo(new ProcessorInputOutputInfo("config"))

    protected def getDefaultContentType = "image/tiff"

    override def createOutput(outputName: String): ProcessorOutput =
        new ProcessorOutputImpl(PDFToImageProcessor.this, outputName) {
            override protected def readImpl(pipelineContext: PipelineContext, xmlReceiver: XMLReceiver): Unit = {

                implicit val logger = new IndentedLogger(Logger)

                val config =
                    readCacheInputAsObject(pipelineContext, getInputByName(INPUT_CONFIG), new CacheableInputReader[Config] {
                        override def read(pipelineContext: PipelineContext, input: ProcessorInput): Config = {

                            val configElem = readInputAsDOM4J(pipelineContext, input).getRootElement

                            def elemValue(elem: DOM4JElement) =
                                Option(elem) map (_.getStringValue) flatMap nonEmptyOrNone

                            def floatValue(elem: DOM4JElement) =
                                elemValue(elem) map (_.toFloat)

                            val scale  = floatValue(configElem.element("scale")) getOrElse 1f
                            val format = elemValue(configElem.element("format")) getOrElse "tiff"

                            val compression = Option(configElem.element("compression")) map { compressionElem ⇒
                                val typ     = elemValue(compressionElem.element("type"))
                                val quality = floatValue(compressionElem.element("quality"))

                                Compression(typ, quality)
                            }

                            Config(scale, format, compression)
                        }
                    })

                val outputStream = new ContentHandlerOutputStream(xmlReceiver, true)
                outputStream.setContentType(s"image/${config.format}")

                val fileItem = NetUtils.prepareFileItem(NetUtils.REQUEST_SCOPE, Logger)
                try {
                    readInputAsSAX(pipelineContext, "data", new BinaryTextXMLReceiver(fileItem.getOutputStream))
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
    // ImageIO.getWriterFormatNames returns:
    //
    // JPG, jpg, tiff, pcx, PCX, bmp, BMP, gif, GIF, WBMP, png, PNG, raw, RAW, JPEG, pnm, PNM, tif, TIF, TIFF,
    // wbmp, jpeg
    //
    // ImageWriteParam.getCompressionTypes for "tiff" returns:
    //
    // CCITT RLE, CCITT T.4, CCITT T.6, LZW, JPEG, ZLib, PackBits, Deflate, EXIF JPEG

    case class Compression(typ: Option[String], quality: Option[Float])
    case class Config(scale: Float, format: String, compression: Option[Compression])

    val KnownBlackAndWhiteCompressions = Set("CCITT RLE", "CCITT T.4", "CCITT T.6")

    // NOTE: Checked experimentally that invocations of getImageWritersByFormatName return separate instances.
    def findNewImageWriterForFormat(format: String) =
        ImageIO.getImageWritersByFormatName(format).asScala.nextOption()

    // Convert a PDF to an image, given the specified configuration
    def convert(config: Config, file: File, outputStream: OutputStream) = {

        val iceDocument = new ICEDocument
        iceDocument.setFile(file.getAbsolutePath)

        try {
            val imageWriter = findNewImageWriterForFormat(config.format).getOrElse(
                throw new OXFException(s"No appropriate writer found for image format ${config.format}.")
            )

            try {
                val imageOutput = ImageIO.createImageOutputStream(outputStream)

                imageWriter.setOutput(imageOutput)

                val params = {
                    val newParams = imageWriter.getDefaultWriteParam
                    config.compression match {
                        case Some(Compression(None, None)) ⇒
                            newParams.setCompressionMode(ImageWriteParam.MODE_DEFAULT)
                        case Some(Compression(typ, quality)) ⇒
                            newParams.setCompressionMode(ImageWriteParam.MODE_EXPLICIT)
                            typ foreach newParams.setCompressionType
                            quality foreach newParams.setCompressionQuality
                        case None ⇒
                            newParams.setCompressionMode(ImageWriteParam.MODE_DISABLED)
                    }
                    newParams
                }

                val hasBlackAndWhiteCompressionType =
                    config.compression flatMap (_.typ) exists KnownBlackAndWhiteCompressions

                // If the writer doesn't support writing sequences, we only write one page at most, and we use a
                // different API to write an individual image
                val canWriteSequence = imageWriter.canWriteSequence

                val numberOfPagesToWrite =
                    if (canWriteSequence)
                        iceDocument.getNumberOfPages
                    else
                        iceDocument.getNumberOfPages min 1

                if (canWriteSequence)
                    imageWriter.prepareWriteSequence(null)

                for (pageIndex ← 0 until numberOfPagesToWrite) {

                    val bufferedImage =
                        iceDocument.getPageImage(
                            pageIndex,
                            GraphicsRenderingHints.PRINT,
                            Page.BOUNDARY_CROPBOX,
                            0f,
                            config.scale
                        ).asInstanceOf[BufferedImage]

                    val imageToWrite =
                        if (hasBlackAndWhiteCompressionType) {
                            // This calls code from Apache FOP. If we don't want the entire FOP dependency we could
                            // easily take over just a couple of files from Apache FOP.
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
                        case img: BufferedImage ⇒ img.flush() // unclear whether needed
                        case _ ⇒
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