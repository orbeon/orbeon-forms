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

import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.fr.AppForm
import org.orbeon.oxf.fr.permission.{Operation, PermissionsAuthorization}
import org.orbeon.oxf.fr.persistence.PersistenceMetadataSupport
import org.orbeon.oxf.fr.persistence.relational.RelationalCommon.getEffectiveFormVersionForSearchMaybeCallApi
import org.orbeon.oxf.fr.persistence.relational.RelationalUtils.Logger
import org.orbeon.oxf.fr.persistence.relational.index.Index
import org.orbeon.oxf.fr.persistence.relational.search.adt.Drafts._
import org.orbeon.oxf.fr.persistence.relational.search.adt.WhichDrafts._
import org.orbeon.oxf.fr.persistence.relational.search.adt._
import org.orbeon.oxf.fr.persistence.relational.{EncryptionAndIndexDetails, Provider}
import org.orbeon.oxf.util.NetUtils
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.saxon.om.DocumentInfo
import org.orbeon.scaxon.SimplePath._

import scala.util.{Failure, Success}


trait SearchRequestParser {

  val SearchPath = "/fr/service/([^/]+)/search/([^/]+)/([^/]+)".r

  def httpRequest: ExternalContext.Request = NetUtils.getExternalContext.getRequest

  def parseRequest(searchDocument: DocumentInfo, version: SearchVersion): SearchRequest = {

    if (Logger.debugEnabled)
      Logger.logDebug("search request", TransformerUtils.tinyTreeToString(searchDocument))

    httpRequest.getRequestPath match {
      case SearchPath(provider, app, form) =>

        val appForm             = AppForm(app, form)
        val searchElement       = searchDocument.rootElement
        val queryEls            = searchElement.child("query").toList
        val freeTextElOpt       = queryEls.find(! _.hasAtt("path"))
        val structuredSearchEls = queryEls.filter(_.hasAtt("path"))
        val draftsElOpt         = searchElement.child("drafts").headOption
        val credentials         = PermissionsAuthorization.findCurrentCredentialsFromSession
        val operationsElOpt     = searchElement.child("operations").headOption
        val allFields           = searchElement.attValueOpt("return-all-indexed-fields").contains(true.toString)

        val specificColumns =
          structuredSearchEls
            .map { c =>
              val filterOpt = trimAllToOpt(c.stringValue)
              Column(
                // Filter `[1]` predicates (see https://github.com/orbeon/orbeon-forms/issues/2922)
                path        = c.attValue("path").replace("[1]", ""),
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
                      c.attValueOpt("control") map Index.matchForControl map {
                        case "substring" => FilterType.Substring(filter)
                        case "token"     => FilterType.Token(filter.splitTo[List]())
                        case _           => FilterType.Exact(filter)
                      }

                    fromMatch     orElse
                      fromControl getOrElse
                      FilterType.Exact(filter)
                }
              )
            }

        val columns =
          if (allFields) {

            // Use specific columns to merge with explicit columns passed
            val specificColumnsByPath =
              specificColumns.map(c => c.path -> c).toMap

            PersistenceMetadataSupport.readPublishedFormEncryptionAndIndexDetails(appForm, getEffectiveFormVersionForSearchMaybeCallApi(appForm, version)) match {
              case Success(EncryptionAndIndexDetails(_, indexedControlsXPaths)) =>
                indexedControlsXPaths.value map { indexedControlXPath =>
                  Column(
                    indexedControlXPath,
                    specificColumnsByPath.get(indexedControlXPath).map(_.filterType).getOrElse(FilterType.None)
                  )
                }
              case Failure(_) =>
                // TODO: throw or log?
                //throw new IllegalArgumentException(s"Form not found: $appForm")
                Nil
            }
          } else
            specificColumns

        SearchRequest(
          provider       = Provider.withName(provider),
          appForm        = appForm,
          version        = version,
          credentials    = credentials,
          pageSize       = searchElement.firstChildOpt("page-size")  .get.stringValue.toInt,
          pageNumber     = searchElement.firstChildOpt("page-number").get.stringValue.toInt,
          freeTextSearch = freeTextElOpt.map(_.stringValue).flatMap(trimAllToOpt), // blank means no search
          columns        = columns,
          drafts         =
            credentials match {
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
          anyOfOperations =
            operationsElOpt.map(_.attValue("any-of").splitTo[Set]().map(Operation.withName))
        )
    }
  }
}
