/**
 * Copyright (C) 2026 Orbeon, Inc.
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

import org.scalajs.dom
import org.scalajs.dom.{HTMLCanvasElement, document}

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.scalajs.js
import scala.scalajs.js.Dynamic.literal


object JsImageSupport {

  case class TransformResult(
    newFile  : dom.File,
    width    : Int,
    height   : Int,
    mediatype: String,
    size     : Long
  )

  def transformImage(
    file        : dom.File,
    newFilename : String,
    maxWidthOpt : Option[Int],
    maxHeightOpt: Option[Int],
    targetMime  : String,
    quality     : Float
  )(implicit
    ec          : ExecutionContext
  ): Future[TransformResult] = {

    val options = literal(imageOrientation = "from-image")
    val promise = dom.window
      .asInstanceOf[js.Dynamic]
      .createImageBitmap(file, options)
      .asInstanceOf[js.Promise[js.Dynamic]]

    promise.toFuture.flatMap { bitmap =>

      val srcWidth  = bitmap.width.asInstanceOf[Int]
      val srcHeight = bitmap.height.asInstanceOf[Int]

      val scaleX = maxWidthOpt.filter(_ < srcWidth).map(_.toDouble / srcWidth)
      val scaleY = maxHeightOpt.filter(_ < srcHeight).map(_.toDouble / srcHeight)
      val scale  = List(scaleX, scaleY).flatten match {
        case Nil  => 1.0
        case list => list.min
      }

      val dstWidth  = Math.max(1, Math.round(srcWidth * scale).toInt)
      val dstHeight = Math.max(1, Math.round(srcHeight * scale).toInt)

      val canvas = document.createElement("canvas").asInstanceOf[HTMLCanvasElement]
      canvas.width  = dstWidth
      canvas.height = dstHeight

      val ctx = canvas.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]
      if (targetMime == "image/jpeg") {
        ctx.fillStyle = "#FFFFFF"
        ctx.fillRect(0, 0, dstWidth, dstHeight)
      }
      ctx.asInstanceOf[js.Dynamic].drawImage(bitmap, 0, 0, dstWidth, dstHeight)

      if (js.typeOf(bitmap.close) == "function") {
        bitmap.close()
      }

      val p = Promise[TransformResult]()
      canvas.asInstanceOf[js.Dynamic].toBlob(
        (blob: dom.Blob) => {
          if (blob != null) {
            val newFile = new dom.File(
              js.Array(blob),
              newFilename,
              new dom.FilePropertyBag { `type` = targetMime }
            )
            p.success(TransformResult(newFile, dstWidth, dstHeight, targetMime, blob.size.toLong))
          } else {
            p.failure(new RuntimeException("Canvas toBlob failed"))
          }
        },
        targetMime,
        quality
      )
      p.future
    }
  }
}
