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
package org.orbeon.oxf.fr

import io.circe.*
import io.circe.parser.decode as decodeJson
import io.circe.syntax.*
import org.orbeon.io.CharsetNames
import org.orbeon.oxf.util.Base64
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.SimplePath.*

import scala.util.Try
import scala.xml.Elem


case class SummarySearchContext(
  freeText       : Option[String]                        = None,
  controlQueries : List[SummarySearchContext.ControlFilter]  = Nil,
  metadataQueries: List[SummarySearchContext.MetadataFilter] = Nil,
  sort           : Option[SummarySearchContext.SortSpec]     = None,
  drafts         : Option[SummarySearchContext.DraftsSpec]   = None,
  anyOfOperations: Option[String]                        = None,
  version        : Option[Int]                           = None,
  lang           : Option[String]                        = None
) {
  def searchVersion: SearchVersion =
    version match {
      case Some(v) => SearchVersion.Specific(v)
      case None    => SearchVersion.All
    }

  def toSearchElem(
    aroundDocOpt: Option[SummarySearchContext.AroundDoc],
    pageSize    : Int,
    pageNumber  : Int
  ): Elem = {

    val sortOpt = sort

    var sortAttached = false

    val controlQueryElems: List[Elem] =
      controlQueries.map { cq =>
        val sortAttr =
          sortOpt.filter(s => ! s.isMetadata && s.column == cq.path).map { s =>
            sortAttached = true
            s.direction
          }
        <query
          path={cq.path}
          match={cq.matchType.orNull}
          control={cq.control.orNull}
          sort={sortAttr.orNull}>{cq.value}</query>
      }

    val metadataQueryElems: List[Elem] =
      metadataQueries.map { mq =>
        val sortAttr =
          sortOpt.filter(s => s.isMetadata && s.column == mq.metadata && ! sortAttached).map { s =>
            sortAttached = true
            s.direction
          }
        <query
          metadata={mq.metadata}
          match={mq.matchType}
          sort={sortAttr.orNull}>{mq.value}</query>
      }

    val standaloneSortElemOpt: Option[Elem] =
      sortOpt.filter(_ => ! sortAttached).map { s =>
        if (s.isMetadata)
          <query metadata={s.column} sort={s.direction}/>
        else
          <query path={s.column} sort={s.direction}/>
      }

    val freeTextElemOpt: Option[Elem] =
      freeText.map(q => <query>{q}</query>)

    val draftsElemOpt: Option[Elem] =
      drafts.map { d =>
        <drafts
          for-never-saved-document={if (d.forNeverSavedDocument) "true" else null}
          for-document-id={d.forDocumentId.orNull}>{d.value}</drafts>
      }

    val operationsElemOpt: Option[Elem] =
      anyOfOperations.map(ops => <operations any-of={ops}/>)

    val aroundDocElemOpt: Option[Elem] =
      aroundDocOpt.map { ad =>
        <around-document
          id={ad.documentId}
          draft={ad.isDraft.toString}
          position={ad.fallbackPosition.map(_.toString).orNull}/>
      }

    val langElemOpt: Option[Elem] =
      lang.map(l => <lang>{l}</lang>)

    <search>{
      freeTextElemOpt.toList       :::
      controlQueryElems            :::
      metadataQueryElems           :::
      standaloneSortElemOpt.toList :::
      draftsElemOpt.toList         :::
      operationsElemOpt.toList     :::
      aroundDocElemOpt.toList      :::
      List(
        <page-size>{pageSize}</page-size>,
        <page-number>{pageNumber}</page-number>
      )                            :::
      langElemOpt.toList
    }</search>
  }

  def toSearchXmlBytes(
    aroundDocOpt: Option[SummarySearchContext.AroundDoc],
    pageSize    : Int,
    pageNumber  : Int
  ): Array[Byte] =
    toSearchElem(aroundDocOpt, pageSize, pageNumber).toString.getBytes(CharsetNames.Utf8)
}

object SummarySearchContext {

  val SearchParamName    = "fr-search"
  val SearchPosParamName = "fr-search-pos"

  case class ControlFilter(
    path     : String,
    matchType: Option[String],
    control  : Option[String],
    value    : String
  )

  case class MetadataFilter(
    metadata : String,
    matchType: String,
    value    : String
  )

  case class SortSpec(
    column    : String,
    isMetadata: Boolean,
    direction : String
  )

  case class DraftsSpec(
    value                : String,
    forNeverSavedDocument: Boolean        = false,
    forDocumentId        : Option[String] = None
  )

  case class AroundDoc(
    documentId      : String,
    isDraft         : Boolean,
    fallbackPosition: Option[Int]
  )

  private val DefaultSort = SortSpec("last-modified", isMetadata = true, "desc")

  implicit val controlFilterEncoder: Encoder[ControlFilter] = Encoder.instance { c =>
    Json.obj(
      List(
        Some("p" -> Json.fromString(c.path)),
        c.matchType.map("m" -> Json.fromString(_)),
        c.control.map("c" -> Json.fromString(_)),
        Some("v" -> Json.fromString(c.value))
      ).flatten*
    )
  }

  implicit val controlFilterDecoder: Decoder[ControlFilter] = Decoder.instance { c =>
    for {
      path      <- c.downField("p").as[String]
      matchType <- c.downField("m").as[Option[String]]
      control   <- c.downField("c").as[Option[String]]
      value     <- c.downField("v").as[String]
    } yield ControlFilter(path, matchType, control, value)
  }

  implicit val metadataFilterEncoder: Encoder[MetadataFilter] = Encoder.instance { m =>
    Json.obj(
      "k" -> Json.fromString(m.metadata),
      "m" -> Json.fromString(m.matchType),
      "v" -> Json.fromString(m.value)
    )
  }

  implicit val metadataFilterDecoder: Decoder[MetadataFilter] = Decoder.instance { c =>
    for {
      metadata  <- c.downField("k").as[String]
      matchType <- c.downField("m").as[String]
      value     <- c.downField("v").as[String]
    } yield MetadataFilter(metadata, matchType, value)
  }

  implicit val sortSpecEncoder: Encoder[SortSpec] = Encoder.instance { s =>
    Json.obj(
      List(
        Some("c" -> Json.fromString(s.column)),
        if (s.isMetadata) Some("m" -> Json.fromBoolean(true)) else None,
        Some("d" -> Json.fromString(s.direction))
      ).flatten*
    )
  }

  implicit val sortSpecDecoder: Decoder[SortSpec] = Decoder.instance { c =>
    for {
      column     <- c.downField("c").as[String]
      isMetadata <- c.downField("m").as[Option[Boolean]]
      direction  <- c.downField("d").as[String]
    } yield SortSpec(column, isMetadata.getOrElse(false), direction)
  }

  implicit val draftsSpecEncoder: Encoder[DraftsSpec] = Encoder.instance { d =>
    Json.obj(
      List(
        Some("v" -> Json.fromString(d.value)),
        if (d.forNeverSavedDocument) Some("n" -> Json.fromBoolean(true)) else None,
        d.forDocumentId.map("id" -> Json.fromString(_))
      ).flatten*
    )
  }

  implicit val draftsSpecDecoder: Decoder[DraftsSpec] = Decoder.instance { c =>
    for {
      value                 <- c.downField("v").as[String]
      forNeverSavedDocument <- c.downField("n").as[Option[Boolean]]
      forDocumentId         <- c.downField("id").as[Option[String]]
    } yield DraftsSpec(value, forNeverSavedDocument.getOrElse(false), forDocumentId)
  }

  implicit val summarySearchContextEncoder: Encoder[SummarySearchContext] = Encoder.instance { ctx =>
    val normalizedSort = ctx.sort.filterNot(_ == DefaultSort)
    Json.obj(
      List(
        ctx.freeText.map("q" -> Json.fromString(_)),
        if (ctx.controlQueries.nonEmpty)  Some("c" -> ctx.controlQueries.asJson)  else None,
        if (ctx.metadataQueries.nonEmpty) Some("m" -> ctx.metadataQueries.asJson) else None,
        normalizedSort.map("s" -> _.asJson),
        ctx.drafts.map("d" -> _.asJson),
        ctx.anyOfOperations.map("o" -> Json.fromString(_)),
        ctx.version.map("v" -> Json.fromInt(_)),
        ctx.lang.map("l" -> Json.fromString(_))
      ).flatten*
    )
  }

  implicit val summarySearchContextDecoder: Decoder[SummarySearchContext] = Decoder.instance { c =>
    for {
      freeText        <- c.downField("q").as[Option[String]]
      controlQueries  <- c.downField("c").as[Option[List[ControlFilter]]]
      metadataQueries <- c.downField("m").as[Option[List[MetadataFilter]]]
      sort            <- c.downField("s").as[Option[SortSpec]]
      drafts          <- c.downField("d").as[Option[DraftsSpec]]
      anyOfOperations <- c.downField("o").as[Option[String]]
      version         <- c.downField("v").as[Option[Int]]
      lang            <- c.downField("l").as[Option[String]]
    } yield SummarySearchContext(
      freeText        = freeText.flatMap(_.trimAllToOpt),
      controlQueries  = controlQueries.getOrElse(Nil),
      metadataQueries = metadataQueries.getOrElse(Nil),
      sort            = sort,
      drafts          = drafts,
      anyOfOperations = anyOfOperations.flatMap(_.trimAllToOpt),
      version         = version.filter(_ > 0),
      lang            = lang.flatMap(_.trimAllToOpt)
    )
  }

  def encode(ctx: SummarySearchContext): String = {
    val jsonBytes = ctx.asJson.noSpaces.getBytes(CharsetNames.Utf8)
    base64UrlEncode(jsonBytes)
  }

  def decode(encoded: String): Option[SummarySearchContext] =
    encoded.trimAllToOpt.flatMap { trimmed =>
      for {
        bytes <- base64UrlDecode(trimmed)
        json  <- Try(new String(bytes, CharsetNames.Utf8)).toOption
        ctx   <- decodeJson[SummarySearchContext](json).toOption
      } yield ctx
    }

  def fromSearchInstance(searchElem: NodeInfo, versionOrAll: String): SummarySearchContext = {
    val queryElems = searchElem.child("query").toList

    val freeTextOpt =
      queryElems
        .find(q => ! q.hasAtt("path") && ! q.hasAtt("metadata"))
        .flatMap(_.stringValue.trimAllToOpt)

    val controlQueries =
      queryElems
        .filter(_.hasAtt("path"))
        .flatMap { q =>
          q.stringValue.trimAllToOpt.map { v =>
            ControlFilter(
              path      = q.attValue("path"),
              matchType = q.attValueNonBlankOpt("match"),
              control   = q.attValueNonBlankOpt("control"),
              value     = v
            )
          }
        }

    val metadataQueries =
      queryElems
        .filter(_.hasAtt("metadata"))
        .flatMap { q =>
          for {
            v <- q.stringValue.trimAllToOpt
            m <- q.attValueNonBlankOpt("match")
          } yield MetadataFilter(
            metadata  = q.attValue("metadata"),
            matchType = m,
            value     = v
          )
        }

    val sortOpt =
      queryElems
        .find(_.attValueNonBlankOpt("sort").isDefined)
        .flatMap { q =>
          val dir = q.attValue("sort").trim.toLowerCase
          if (dir == "asc" || dir == "desc") {
            q.attValueNonBlankOpt("metadata")
              .map(m => SortSpec(m, isMetadata = true, dir))
              .orElse(q.attValueNonBlankOpt("path").map(p => SortSpec(p, isMetadata = false, dir)))
          } else {
            None
          }
        }
        .filterNot(_ == DefaultSort)

    val draftsOpt =
      searchElem.child("drafts").headOption.flatMap { d =>
        d.stringValue.trimAllToOpt.map { v =>
          DraftsSpec(
            value                 = v,
            forNeverSavedDocument = d.attValueOpt("for-never-saved-document").contains("true"),
            forDocumentId         = d.attValueNonBlankOpt("for-document-id")
          )
        }
      }

    val anyOfOperationsOpt =
      searchElem.child("operations").headOption.flatMap(_.attValueNonBlankOpt("any-of"))

    val versionOpt =
      versionOrAll.trimAllToOpt
        .filterNot(_ == "all")
        .flatMap(_.toIntOption)
        .filter(_ > 0)

    val langOpt =
      searchElem.elemValueOpt("lang").flatMap(_.trimAllToOpt)

    SummarySearchContext(
      freeText        = freeTextOpt,
      controlQueries  = controlQueries,
      metadataQueries = metadataQueries,
      sort            = sortOpt,
      drafts          = draftsOpt,
      anyOfOperations = anyOfOperationsOpt,
      version         = versionOpt,
      lang            = langOpt
    )
  }

  private def base64UrlEncode(bytes: Array[Byte]): String = {
    val standard = Base64.encode(bytes, useLineBreaks = false)
    val sb = new StringBuilder(standard.length)
    var i = 0
    while (i < standard.length) {
      standard.charAt(i) match {
        case '+' => sb.append('-')
        case '/' => sb.append('_')
        case '=' => // strip padding
        case c   => sb.append(c)
      }
      i += 1
    }
    sb.toString
  }

  private def base64UrlDecode(str: String): Option[Array[Byte]] = {
    val rem = str.length % 4
    if (rem == 1)
      None
    else {
      val padLen = if (rem == 0) 0 else 4 - rem
      val sb = new StringBuilder(str.length + padLen)
      var i = 0
      while (i < str.length) {
        str.charAt(i) match {
          case '-' => sb.append('+')
          case '_' => sb.append('/')
          case c   => sb.append(c)
        }
        i += 1
      }
      var p = 0
      while (p < padLen) {
        sb.append('=')
        p += 1
      }
      Try(Base64.decode(sb.toString)).toOption.filter(_ != null)
    }
  }
}
