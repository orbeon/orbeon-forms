package org.orbeon.oxf.util

import org.orbeon.datatypes.Mediatype.TypeOrSubtype.SpecificType
import org.orbeon.datatypes.MediatypeRange.SingletonMediatypeRange
import org.orbeon.datatypes.{Mediatype, MediatypeRange}
import org.orbeon.oxf.http.Headers
import org.orbeon.oxf.util.CollectionUtils.*
import org.orbeon.oxf.util.PathUtils.*


trait MediatypesTrait {

  protected case class Mapping(extension: String, mediatype: String, label: Option[String])

  protected def findMappings: Iterable[Mapping]

  private val (
    mappingsByMediatype   : Map[String, List[Mapping]],
    mappingsByExtension   : Map[String, List[Mapping]],
    mappingsBySpecificType: Map[SpecificType, List[Mapping]],
    labelsByMediatype     : Map[String, String]
  ) = {

    val allMappings = findMappings

    val mappingsByMediatype = combineValues[String, Mapping, List](allMappings map { case m @ Mapping(_, mediatype, _) => mediatype   -> m }).toMap
    val mappingsByExtension = combineValues[String, Mapping, List](allMappings map { case m @ Mapping(_, _, _)         => m.extension -> m }).toMap
    val labelsByMediatype   = allMappings.collect { case Mapping(_, mediatype, Some(label)) => mediatype -> label }.toMap

    val mappingsBySpecificType =
      allMappings
        .collect { case m @ Mapping(_, MediatypeRange(SingletonMediatypeRange(Mediatype(typ, _))), _) => typ -> m }
        .groupBy(_._1)
        .view
        .mapValues(_.map(_._2))
        .toMap

    (mappingsByMediatype, mappingsByExtension, mappingsBySpecificType, labelsByMediatype)
  }

  def findMediatypeForExtension(extension: String): Option[String] =
    for {
      mappings <- mappingsByExtension.get(extension.toLowerCase)
      mapping  <- mappings.headOption
    } yield
      mapping.mediatype

  def findMediatypeForPath(path: String): Option[String] =
    for {
      extension <- findExtension(path.toLowerCase)
      mediatype <- findMediatypeForExtension(extension)
    } yield
      mediatype

  def findMediatypeForPathJava(path: String): String =
    findMediatypeForPath(path).orNull

  def findExtensionForMediatype(mediatype: String): Option[String] =
    for {
      mappings  <- mappingsByMediatype.get(mediatype)
      mapping   <- mappings.headOption
    } yield
      mapping.extension

  def findExtensionsForMediatype(mediatype: String): List[String] =
    for {
      mappings  <- mappingsByMediatype.get(mediatype).toList
      mapping   <- mappings
    } yield
      mapping.extension

  def findExtensionsForSpecificType(typ: SpecificType): List[String] =
    for {
      mappings  <- mappingsBySpecificType.get(typ).toList
      mapping   <- mappings
    } yield
      mapping.extension

  def findLabelForMediatype(mediatype: String): Option[String] =
    labelsByMediatype.get(mediatype)

  def getExtensionForMediatypeOrThrow(mediatype: String): String =
    findExtensionForMediatype(mediatype).getOrElse(throw new IllegalArgumentException("mediatype"))

  def fromHeadersOrFilename(
    header  : String => Option[String],
    filename: => Option[String]
  ): Option[Mediatype] = {

    def fromHeaders =
      header(Headers.ContentType)            flatMap
        ContentTypes.getContentTypeMediaType filterNot
        (_ == ContentTypes.OctetStreamContentType)

    def fromFilename =
      filename flatMap findMediatypeForPath

    fromHeaders orElse fromFilename flatMap Mediatype.unapply
  }
}
