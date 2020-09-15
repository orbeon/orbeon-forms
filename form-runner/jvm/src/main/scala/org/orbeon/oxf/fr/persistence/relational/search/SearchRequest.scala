/**
 * Copyright (C) 2016 Orbeon, Inc.
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
package org.orbeon.oxf.fr.persistence.relational.search

import org.orbeon.oxf.fr.permission.Operation
import org.orbeon.oxf.fr.persistence.relational.RelationalUtils.Logger
import org.orbeon.oxf.fr.persistence.relational.search.adt.Drafts._
import org.orbeon.oxf.fr.persistence.relational.search.adt.WhichDrafts._
import org.orbeon.oxf.fr.persistence.relational.search.adt._
import org.orbeon.oxf.fr.persistence.relational.{Provider, Version}
import org.orbeon.oxf.util.NetUtils
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.saxon.om.DocumentInfo
import org.orbeon.scaxon.SimplePath._

trait SearchRequest {

  val SearchPath = "/fr/service/([^/]+)/search/([^/]+)/([^/]+)".r

  def httpRequest = NetUtils.getExternalContext.getRequest

  def parseRequest(searchDocument: DocumentInfo, version: Version): Request = {

    if (Logger.debugEnabled)
      Logger.logDebug("search request", TransformerUtils.tinyTreeToString(searchDocument))

    httpRequest.getRequestPath match {
      case SearchPath(provider, app, form) =>

        val searchElement   = searchDocument.rootElement
        val queryEls        = searchElement.child("query").toList
        val draftsElOpt     = searchElement.child("drafts").headOption
        val username        = httpRequest.credentials map     (_.username)
        val group           = httpRequest.credentials flatMap (_.group)
        val operationsElOpt = searchElement.child("operations").headOption

        Request(
          provider       = Provider.withName(provider),
          app            = app,
          form           = form,
          version        = version,
          username       = username,
          group          = group,
          pageSize       = searchElement.firstChildOpt("page-size")  .get.stringValue.toInt,
          pageNumber     = searchElement.firstChildOpt("page-number").get.stringValue.toInt,
          freeTextSearch =
            queryEls
              // Free text is in the first <query>
              .headOption.map(_.stringValue)
              // Empty means no search
              .flatMap(trimAllToOpt),
          columns        =
            queryEls
              // First is for free text search
              .drop(1)
              .map { c =>
                val filterOpt = trimAllToOpt(c.stringValue)
                Column(
                  // Filter `[1]` predicates (see https://github.com/orbeon/orbeon-forms/issues/2922)
                  path       = c.attValue("path").replaceAllLiterally("[1]", ""),
                  filterType  = filterOpt match {
                    case None => FilterType.None
                    case Some(filter) =>

                      def fromMatch: Option[FilterType] =
                        c.attValueOpt("match") map {
                          case "substring" => FilterType.Substring(filter)
                          case "token"     => FilterType.Token(filter.splitTo[List]())
                          case "exact"     => FilterType.Exact(filter)
                          case other       => throw new IllegalArgumentException(other)
                        }

                      def fromControl: Option[FilterType] =
                        c.attValueOpt("control") map {
                          case "input" | "textarea" => FilterType.Substring(filter)
                          case "select"             => FilterType.Token(filter.splitTo[List]())
                          case _                    => FilterType.Exact(filter)
                        }

                      fromMatch     orElse
                        fromControl getOrElse
                        FilterType.Exact(filter)
                  }
                )
              },
          drafts         =
            username match {
              case None =>
                ExcludeDrafts
              case Some(_) =>
                draftsElOpt match {
                  case None =>
                    IncludeDrafts
                  case Some(draftsEl) =>
                    draftsEl.stringValue match {
                      case "exclude" => ExcludeDrafts
                      case "include" => IncludeDrafts
                      case "only"    => OnlyDrafts(
                        draftsEl.attValueOpt("for-document-id") match {
                          case Some(documentId) => DraftsForDocumentId(documentId)
                          case None =>
                            draftsEl.attValueOpt("for-never-saved-document") match {
                              case Some(_) => DraftsForNeverSavedDocs
                              case None    => AllDrafts
                            }
                        }
                      )
                    }
                }
            },
          anyOfOperations = operationsElOpt.map { operationsEl =>
            val operations: List[String] = operationsEl.attValue("any-of").splitTo[List]()
            operations.map(Operation.withName)
          }
        )
    }
  }
}
