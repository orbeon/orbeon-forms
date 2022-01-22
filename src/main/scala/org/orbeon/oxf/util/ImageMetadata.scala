/**
 * Copyright (C) 2013 Orbeon, Inc.
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
package org.orbeon.oxf.util

import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.Metadata
import com.drew.metadata.bmp.BmpHeaderDirectory
import com.drew.metadata.gif.GifHeaderDirectory
import com.drew.metadata.jpeg.JpegDirectory
import com.drew.metadata.png.PngDirectory
import enumeratum.EnumEntry.Lowercase
import enumeratum._
import org.orbeon.datatypes.Mediatype

import java.io.InputStream
import scala.jdk.CollectionConverters._


// Functions to extract image metadata from a stream
object ImageMetadata {

  case class AllMetadata(mediatype: Mediatype, width: Int, height: Int, orientation: Option[Int])

  sealed trait MetadataType extends EnumEntry with Lowercase

  object MetadataType extends Enum[MetadataType] {

    val values = findValues

    case object Width       extends MetadataType
    case object Height      extends MetadataType
    case object Orientation extends MetadataType
  }

  // Given a name in KnownNames, try to extract its value and return it as a Java object
  // 2020-03-25: Currently return java.lang.Integer
  def findKnownMetadata(content: InputStream, name: MetadataType): Option[Any] =
    KnownNamesToMetadataExtractorNames.get(name) flatMap (findMetadata(content, _))

  // Try to find the type of the image
  def findImageMediatype(content: InputStream): Option[String] =
    findImageMediatypeFromMetadata(ImageMetadataReader.readMetadata(content))

  def findAllMetadata(content: InputStream): Option[AllMetadata] = {

    val metadata = ImageMetadataReader.readMetadata(content)

    val mediatypeOpt =
      findImageMediatypeFromMetadata(metadata) collect {
        case Mediatype(mediatype) => mediatype
      }

    for {
      mediatype   <- mediatypeOpt
      width       <- findMetadataFromMetadataAsInt(metadata, MetadataType.Width)
      height      <- findMetadataFromMetadataAsInt(metadata, MetadataType.Height)
    } yield
      AllMetadata(mediatype, width, height, findMetadataFromMetadataAsInt(metadata, MetadataType.Orientation))
  }

  private def findImageMediatypeFromMetadata(metadata: Metadata): Option[String] = {
    // Support formats thar are supported by web browsers only
    // http://en.wikipedia.org/wiki/Comparison_of_web_browsers#Image_format_support
    metadata.getDirectories.asScala.iterator collectFirst {
      case _: JpegDirectory      => "image/jpeg"
      case _: PngDirectory       => "image/png"
      case _: GifHeaderDirectory => "image/gif"
      case _: BmpHeaderDirectory => "image/bmp"
    }
  }

  private def findMetadataFromMetadataAsInt(metadata: Metadata, metadataType: MetadataType): Option[Int] =
    KnownNamesToMetadataExtractorNames.get(metadataType) flatMap (findMetadataFromMetadata(metadata, _)) collect {
      case i: Int => i.intValue
      case other =>
        println(other)
        42
    }

  private def findMetadataFromMetadata(metadata: Metadata, name: String): Option[Any] = {

    val directoryIterator =
      for {
        directory <- metadata.getDirectories.asScala.iterator
        tags      = for (tag <- directory.getTags.asScala)
                    yield
                      tag.getTagName -> tag.getTagType
      } yield
        directory -> tags.toMap

    directoryIterator collectFirst {
      case (directory, map) if map.contains(name) => directory.getObject(map(name))
    }
  }

  // Try to extract the value of the given metadata item
  private def findMetadata(content: InputStream, name: String): Option[Any] =
    findMetadataFromMetadata(ImageMetadataReader.readMetadata(content), name)

  private val KnownNamesToMetadataExtractorNames = Map[MetadataType, String](
    MetadataType.Width       -> "Image Width",
    MetadataType.Height      -> "Image Height",
    MetadataType.Orientation -> "Orientation"
  )
}
