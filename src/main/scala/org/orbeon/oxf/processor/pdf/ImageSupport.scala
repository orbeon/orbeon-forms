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

import java.awt.Color
import java.awt.geom.AffineTransform
import java.awt.image.{AffineTransformOp, BufferedImage}
import java.io.{BufferedInputStream, InputStream}

import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.ImageMetadata

object ImageSupport {

  private val MaxExifSize: Int = 64 * 1024

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
