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
package org.orbeon.oxf.util

import cats.syntax.option._
import net.coobird.thumbnailator.Thumbnails
import org.orbeon.datatypes.Mediatype
import org.orbeon.io.IOUtils.useAndClose
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.http.HttpMethod.GET
import org.orbeon.oxf.processor.generator.RequestGenerator
import org.orbeon.oxf.util.CollectionUtils._
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.ImageMetadata.AllMetadata

import java.awt.geom.AffineTransform
import java.awt.image.{AffineTransformOp, BufferedImage}
import java.io.{ByteArrayOutputStream, InputStream, OutputStream}
import java.net.URI
import javax.imageio.stream.MemoryCacheImageOutputStream
import javax.imageio.{IIOImage, ImageIO, ImageWriteParam}
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}


object ImageSupport {

  def findImageOrientation(is: InputStream): Option[Int] =
    ImageMetadata.findKnownMetadata(is, ImageMetadata.MetadataType.Orientation) collect {
      case i: Int => i.intValue
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
            (_.rotate(3 * Math.PI / 2))
        )
      case 8 =>
        // 270 degree rotation
        Some(
          new AffineTransform            |!>
            (_.translate(0, width))      |!>
            (_.rotate(3 * Math.PI / 2))
        )
      case _ =>
        None
    }

  def transformImage(sourceImage: BufferedImage, transform: AffineTransform): BufferedImage = {

    // Use `TYPE_NEAREST_NEIGHBOR` because we use transformations that shouldn't need more, and
    // if we uwe instead `TYPE_BICUBIC`, we end up with a color model that requires transparency,
    // and then `ImageIO.write()` cannot encode the image.
    val op = new AffineTransformOp(transform, AffineTransformOp.TYPE_NEAREST_NEIGHBOR)

    val destinationImage =
      op.createCompatibleDestImage(
        sourceImage,
        if (sourceImage.getType == BufferedImage.TYPE_BYTE_GRAY) sourceImage.getColorModel else null
      )

    op.filter(sourceImage, destinationImage)
  }

  def compressJpegImage(image: BufferedImage, compressionLevel: Float): Array[Byte] =
    ImageIO.getImageWritersByFormatName("jpg").asScala.nextOption() match {
      case Some(jpgWriter) =>

        val os = new ByteArrayOutputStream

        val jpgWriteParam =
          jpgWriter.getDefaultWriteParam |!>
          (_.setCompressionMode(ImageWriteParam.MODE_EXPLICIT)) |!>
          (_.setCompressionQuality(compressionLevel))

        jpgWriter.setOutput(new MemoryCacheImageOutputStream(os))
        jpgWriter.write(
          null,
          new IIOImage(image, null, null),
          jpgWriteParam
        )
        jpgWriter.dispose()

        os.toByteArray
      case None =>
        throw new IllegalArgumentException("can't find encoder for image")
    }

  // Implementation notes:
  //
  // - In this current implementation, this function may dereference and start reading the `URI` twice. This is ok in
  //   the main use case where the `URI` points to a temporary file on disk.
  // - In the future, we could separate the writing of the result from this function. Right now, it writes to a new
  //   temporary file and returns a `URI` pointing to it.
  def tryMaybeTransformImage(
    existingUri     : URI,
    maxWidthOpt     : Option[Int],
    maxHeightOpt    : Option[Int],
    mediatypeOpt    : Option[Mediatype],
    quality         : Float)(implicit
    logger          : IndentedLogger,
    externalContext : ExternalContext
  ): Try[Option[(URI, Long)]] = {

    def mustTransform(metadata: AllMetadata): Boolean =
      maxWidthOpt .exists(metadata.width  >) ||
      maxHeightOpt.exists(metadata.height >) ||
      mediatypeOpt.exists(metadata.mediatype !=)

    def tryReadAndTransformToOutputStream(metadata: AllMetadata, os: OutputStream): Try[Unit] =
      ConnectionResult.tryWithSuccessConnection(connectGet(existingUri), closeOnSuccess = true) { is =>

        var b =
          Thumbnails.of(is)
            .width (maxWidthOpt  filter (metadata.width >)  getOrElse metadata.width)
            .height(maxHeightOpt filter (metadata.height >) getOrElse metadata.height)

        mediatypeOpt map (_.toString) flatMap Mediatypes.findExtensionForMediatype foreach { format =>
          b = b.outputFormat(format)
        }

        b = b.outputQuality(quality)
        b.toOutputStream(os)
      }

    tryReadAllMetadata(existingUri) match {
      case Success(allMetadata) if mustTransform(allMetadata) =>

        val fileItem = FileItemSupport.prepareFileItem(NetUtils.SESSION_SCOPE, logger.logger.logger)

        useAndClose(fileItem.getOutputStream)(tryReadAndTransformToOutputStream(allMetadata, _)) map { _ =>
          (
            new URI(RequestGenerator.urlForFileItemCreateIfNeeded(fileItem, NetUtils.SESSION_SCOPE)),
            fileItem.getSize
          ).some
        }

      case Success(_) =>
        Success(None)
      case Failure(t) =>
        Failure(t)
    }
  }

  private def connectGet(
    existingUri     : URI)(implicit
    logger          : IndentedLogger,
    externalContext : ExternalContext
  ): ConnectionResult =
    Connection.connectNow(
      method          = GET,
      url             = existingUri,
      credentials     = None,
      content         = None,
      headers         = Map.empty,
      loadState       = false,
      saveState       = false,
      logBody         = false
    )

  // Open for tests
  private[util] def tryReadAllMetadata(
    existingUri     : URI)(implicit
    logger          : IndentedLogger,
    externalContext : ExternalContext
  ): Try[AllMetadata] =
    ConnectionResult.tryWithSuccessConnection(connectGet(existingUri), closeOnSuccess = true)(is =>
      ImageMetadata.findAllMetadata(is)
        .fold[Try[AllMetadata]](
          Failure(new IllegalArgumentException("metadata not found")))(
          Success.apply
        )
    ).flatten
}
