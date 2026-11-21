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

import org.orbeon.oxf.fr.permission.{AnyOperation, Operation, Operations}
import org.orbeon.oxf.fr.persistence.api.PersistenceApi
import org.orbeon.oxf.fr.persistence.api.PersistenceApi.{SearchNeighbor, SearchNeighbors}
import org.orbeon.oxf.util.*
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.NodeConversions
import org.orbeon.scaxon.SimplePath.*

import scala.util.Try


object SummarySearchNavigation {

  sealed trait Direction
  object Direction {
    case object First    extends Direction
    case object Previous extends Direction
    case object Next     extends Direction
    case object Last     extends Direction
  }

  def resolveNeighbors(
    appForm            : AppForm,
    documentId         : String,
    isDraft            : Boolean,
    encodedSearch      : String,
    fallbackPositionOpt: Option[Int],
    fetchLast          : Boolean = false
  )(implicit
    logger             : IndentedLogger
  ): Option[SearchNeighbors] =
    for {
      searchCtx <- SummarySearchContext.decode(encodedSearch)
      neighbors <- PersistenceApi.searchAround(
        appForm       = appForm,
        searchContext = searchCtx,
        around        = SummarySearchContext.AroundDoc(
          documentId       = documentId,
          isDraft          = isDraft,
          fallbackPosition = fallbackPositionOpt
        ),
        fetchLast     = fetchLast
      )(logger, CoreCrossPlatformSupport).toOption
    } yield neighbors

  def buildNeighborHref(
    appForm      : AppForm,
    currentMode  : String,
    neighbor     : SearchNeighbor,
    encodedSearch: String
  ): Option[String] = {
    val ops = neighbor.operations.getOrElse(AnyOperation)

    val targetModeOpt: Option[String] =
      if (currentMode == "view") {
        if (Operations.allows(ops, Operation.Read)) Some("view")
        else None
      } else {
        if (Operations.allows(ops, Operation.Update)) Some("edit")
        else if (Operations.allows(ops, Operation.Read)) Some("view")
        else None
      }

    targetModeOpt.map { targetMode =>
      val basePath = s"/fr/${appForm.app}/${appForm.form}/$targetMode/${neighbor.documentId}"
      val params =
        (if (neighbor.isDraft) List("draft" -> "true") else Nil) :::
        List(
          SummarySearchContext.SearchParamName    -> encodedSearch,
          SummarySearchContext.SearchPosParamName -> neighbor.position.toString
        )
      PathUtils.recombineQuery(basePath, params)
    }
  }

  def toNavigationXml(
    appForm            : AppForm,
    currentMode        : String,
    encodedSearch      : String,
    fallbackPositionOpt: Option[Int],
    neighborsOpt       : Option[SearchNeighbors]
  ): NodeInfo = {
    val elem =
      neighborsOpt match {
        case Some(neighbors) =>
          val firstHrefOpt    = neighbors.first   .flatMap(buildNeighborHref(appForm, currentMode, _, encodedSearch))
          val prevHrefOpt     = neighbors.previous.flatMap(buildNeighborHref(appForm, currentMode, _, encodedSearch))
          val nextHrefOpt     = neighbors.next    .flatMap(buildNeighborHref(appForm, currentMode, _, encodedSearch))
          val lastHrefOpt     = neighbors.last    .flatMap(buildNeighborHref(appForm, currentMode, _, encodedSearch))
          val hasFirst        = firstHrefOpt.isDefined || prevHrefOpt.isDefined
          val hasLast         = lastHrefOpt.isDefined  || nextHrefOpt.isDefined
          val effectivePosOpt = neighbors.currentPosition.orElse(fallbackPositionOpt)
          <search-navigation>
            <search-param>{encodedSearch}</search-param>
            <position>{neighbors.currentPosition.map(_.toString).getOrElse("")}</position>
            <fallback-position>{effectivePosOpt.map(_.toString).getOrElse("")}</fallback-position>
            <total>{neighbors.total}</total>
            <has-first>{hasFirst}</has-first>
            <has-previous>{prevHrefOpt.isDefined}</has-previous>
            <has-next>{nextHrefOpt.isDefined}</has-next>
            <has-last>{hasLast}</has-last>
          </search-navigation>
        case None =>
          <search-navigation>
            <search-param/>
            <position/>
            <fallback-position/>
            <total>0</total>
            <has-first>false</has-first>
            <has-previous>false</has-previous>
            <has-next>false</has-next>
            <has-last>false</has-last>
          </search-navigation>
      }

    NodeConversions.elemToDocumentInfo(elem).rootElement
  }

  def parsePositionOpt(posStr: String): Option[Int] =
    Option(posStr).flatMap(_.trimAllToOpt).flatMap(s => Try(s.toInt).toOption).filter(_ > 0)
}
