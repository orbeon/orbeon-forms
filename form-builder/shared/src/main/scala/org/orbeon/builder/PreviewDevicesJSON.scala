/**
 * Copyright (C) 2026 Orbeon, Inc.
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
package org.orbeon.builder

import enumeratum.EnumEntry.Lowercase
import enumeratum.{Enum, EnumEntry}
import io.circe.parser.parse
import io.circe.{Decoder, Encoder, Json}
import org.orbeon.oxf.util.StringUtils.*

import scala.collection.immutable


sealed trait PreviewDeviceCategory extends EnumEntry with Lowercase

object PreviewDeviceCategory extends Enum[PreviewDeviceCategory] {
  case object Mobile  extends PreviewDeviceCategory
  case object Tablet  extends PreviewDeviceCategory
  case object Desktop extends PreviewDeviceCategory

  val values: immutable.IndexedSeq[PreviewDeviceCategory] = findValues
}

case class PreviewDevice(
  id      : String,
  name    : String,
  width   : Int,
  height  : Int,
  category: PreviewDeviceCategory
)

object PreviewDevicesJSON {

  val MinViewportPx = 240
  val MaxViewportWidthPx  = 3840
  val MaxViewportHeightPx = 2160

  // Matches Form Runner responsive breakpoints in `grid.less` (`640px`) and `form-runner-responsive.less` (`980px`)
  val CompactBreakpointMaxPx = 640
  val MediumBreakpointMaxPx  = 980

  val DefaultDevices: List[PreviewDevice] = List(
    PreviewDevice("iphone-18-pro",       "iPhone 18 Pro",         402,  874,  PreviewDeviceCategory.Mobile),
    PreviewDevice("iphone-18-pro-max",   "iPhone 18 Pro Max",     440,  956,  PreviewDeviceCategory.Mobile),
    PreviewDevice("iphone-duo-folded",   "iPhone Duo (Folded)",   382,  618,  PreviewDeviceCategory.Mobile),
    PreviewDevice("pixel-11",            "Pixel 11",              412,  924,  PreviewDeviceCategory.Mobile),
    PreviewDevice("galaxy-s26",          "Galaxy S26",            360,  780,  PreviewDeviceCategory.Mobile),
    PreviewDevice("iphone-duo-unfolded", "iPhone Duo (Unfolded)", 669,  902,  PreviewDeviceCategory.Tablet),
    PreviewDevice("ipad-mini",           "iPad Mini",             744,  1133, PreviewDeviceCategory.Tablet),
    PreviewDevice("ipad-air",            "iPad Air",              820,  1180, PreviewDeviceCategory.Tablet),
    PreviewDevice("ipad-pro-13",         "iPad Pro 13\"",         1024, 1366, PreviewDeviceCategory.Tablet),
    PreviewDevice("laptop",              "Laptop",                1280, 800,  PreviewDeviceCategory.Desktop),
    PreviewDevice("desktop",             "Desktop",               1440, 900,  PreviewDeviceCategory.Desktop)
  )

  implicit val previewDeviceEncoder: Encoder[PreviewDevice] = Encoder.instance { device =>
    Json.obj(
      "id"       -> Json.fromString(device.id),
      "name"     -> Json.fromString(device.name),
      "width"    -> Json.fromInt(device.width),
      "height"   -> Json.fromInt(device.height),
      "category" -> Json.fromString(device.category.entryName)
    )
  }

  implicit val previewDeviceDecoder: Decoder[PreviewDevice] = Decoder.instance { c =>
    for {
      rawId       <- c.downField("id").as[String]
      rawName     <- c.downField("name").as[String]
      rawWidth    <- c.downField("width").as[Int]
      rawHeight   <- c.downField("height").as[Int]
      rawCategory <- c.downField("category").as[String]
      id          <- rawId.trimAllToOpt.toRight(io.circe.DecodingFailure("Blank device id", c.history))
      name        <- rawName.trimAllToOpt.toRight(io.circe.DecodingFailure("Blank device name", c.history))
      category    <- PreviewDeviceCategory.withNameInsensitiveOption(rawCategory.trimAllToEmpty)
                       .toRight(io.circe.DecodingFailure(s"Invalid category: $rawCategory", c.history))
      _           <- Either.cond(rawWidth > 0 && rawHeight > 0, (), io.circe.DecodingFailure("Non-positive dimensions", c.history))
    } yield {
      val (clampedW, clampedH) = clampDimensions(rawWidth, rawHeight)
      PreviewDevice(
        id       = id,
        name     = name,
        width    = clampedW,
        height   = clampedH,
        category = category
      )
    }
  }

  // Parse a JSON array of devices, skipping malformed entries and falling back to `DefaultDevices` if empty or invalid JSON
  def parseDevices(jsonString: String): List[PreviewDevice] = {
    val parsed =
      jsonString.trimAllToOpt.flatMap(parse(_).toOption).flatMap(_.asArray) match {
        case Some(items) =>
          items.toList.flatMap(_.as[PreviewDevice].toOption)
        case None =>
          Nil
      }

    if (parsed.nonEmpty) parsed else DefaultDevices
  }

  def clampDimensions(width: Int, height: Int): (Int, Int) = (
    math.max(MinViewportPx, math.min(MaxViewportWidthPx,  width)),
    math.max(MinViewportPx, math.min(MaxViewportHeightPx, height))
  )

  def rotateDimensions(width: Int, height: Int): (Int, Int) =
    clampDimensions(height, width)

  def computeFitScale(
    frameWidth : Int,
    frameHeight: Int,
    availWidth : Double,
    availHeight: Double
  ): Double =
    if (frameWidth <= 0 || frameHeight <= 0 || availWidth <= 0.0 || availHeight <= 0.0)
      1.0
    else {
      val scaleW = availWidth / frameWidth.toDouble
      val scaleH = availHeight / frameHeight.toDouble
      math.max(0.1, math.min(1.0, math.min(scaleW, scaleH)))
    }

  def responsiveBreakpointKey(width: Int): String =
    if (width <= CompactBreakpointMaxPx)
      "compact"
    else if (width <= MediumBreakpointMaxPx)
      "medium"
    else
      "wide"
}
