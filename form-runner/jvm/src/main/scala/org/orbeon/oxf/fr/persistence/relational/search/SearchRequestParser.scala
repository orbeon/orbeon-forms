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

import org.orbeon.oxf.externalcontext.Credentials
import org.orbeon.oxf.fr.permission.PermissionsAuthorization
import org.orbeon.oxf.fr.persistence.PersistenceMetadataSupport
import org.orbeon.oxf.fr.persistence.relational.RelationalUtils.parsePositiveIntParamOrThrow
import org.orbeon.oxf.fr.persistence.relational.index.Index
import org.orbeon.oxf.fr.persistence.relational.search.adt.*
import org.orbeon.oxf.fr.persistence.relational.search.adt.Drafts.*
import org.orbeon.oxf.fr.persistence.relational.search.adt.Metadata.*
import org.orbeon.oxf.fr.persistence.relational.search.adt.WhichDrafts.*
import org.orbeon.oxf.fr.persistence.relational.{FormStorageDetails, Provider, RelationalUtils}
import org.orbeon.oxf.fr.{AppForm, FormDefinitionVersion}
import org.orbeon.oxf.properties.PropertySet
import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.util.Logging.*
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.saxon.om.{DocumentInfo, NodeInfo}
import org.orbeon.scaxon.SimplePath.*

import scala.util.{Failure, Success}


object SearchRequestParser {

  def parseRequest(
    provider           : Provider,
    appForm            : AppForm,
    isInternalAdminUser: Boolean,
    searchDocument     : DocumentInfo,
    version            : FormDefinitionVersion
  )(implicit
    indentedLogger     : IndentedLogger,
    propertySet        : PropertySet
  ): SearchRequest = {

    debug(s"search request: ${TransformerUtils.tinyTreeToString(searchDocument)}")

    val searchElement    = searchDocument.rootElement
    val queryEls         = searchElement.child("query").toList
    val freeTextElOpt    = queryEls.find(nodeInfo => ! nodeInfo.hasAtt("path") && ! nodeInfo.hasAtt("metadata"))
    val controlQueryEls  = queryEls.filter(_.hasAtt("path"))
    val metadataQueryEls = queryEls.filter(_.hasAtt("metadata"))
    val draftsElOpt      = searchElement.child("drafts").headOption
    val credentials      = PermissionsAuthorization.findCurrentCredentialsFromSession
    val allControls      = searchElement.attValueOpt("return-all-indexed-fields").contains(true.toString)

    SearchRequest(
      provider            = provider,
      appForm             = appForm,
      version             = version,
      credentials         = credentials,
      isInternalAdminUser = isInternalAdminUser,
      pageSize            = parsePositiveIntParamOrThrow(searchElement.elemValueOpt("page-size"),  10),
      pageNumber          = parsePositiveIntParamOrThrow(searchElement.elemValueOpt("page-number"), 1),
      queries             = controlQueries(appForm, version, controlQueryEls, allControls) ::: metadataQueries(metadataQueryEls),
      drafts              = drafts(draftsElOpt, credentials),
      freeTextSearch      = freeTextElOpt.map(_.stringValue).flatMap(trimAllToOpt), // Blank means no search
      anyOfOperations     = SearchLogic.anyOfOperations(searchElement)
    )
  }

  private def drafts(draftsElOpt: Option[NodeInfo], credentials: Option[Credentials]): Drafts = credentials match {
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
  }

  private def orderDirection(queryEl: NodeInfo): Option[OrderDirection] =
    queryEl.attValueOpt("sort").map(OrderDirection.apply)

  private def controlQueries(
    appForm        : AppForm,
    version        : FormDefinitionVersion,
    controlQueryEls: List[NodeInfo],
    allControls    : Boolean
  )(implicit
    indentedLogger : IndentedLogger,
    propertySet    : PropertySet
  ): List[ControlQuery] = {
    val specificControls =
      controlQueryEls
        .map { controlQueryEl =>
          val filterOpt = trimAllToOpt(controlQueryEl.stringValue)
          ControlQuery(
            // Filter `[1]` predicates (see https://github.com/orbeon/orbeon-forms/issues/2922)
            path        = controlQueryEl.attValue("path").replace("[1]", ""),
            filterType  = filterOpt map { filter =>

              def fromMatch: Option[ControlFilterType] =
                controlQueryEl.attValueOpt("match") map {
                  case "substring" => ControlFilterType.Substring(filter)
                  case "token"     => ControlFilterType.Token    (filter.splitTo[List]())
                  case "exact"     => ControlFilterType.Exact    (filter)
                  case other       => throw new IllegalArgumentException(other)
                }

              def fromControl: Option[ControlFilterType] =
                controlQueryEl.attValueOpt("control") map Index.matchForControl map {
                  case "substring" => ControlFilterType.Substring(filter)
                  case "token"     => ControlFilterType.Token    (filter.splitTo[List]())
                  case _           => ControlFilterType.Exact    (filter)
                }

              fromMatch     orElse
                fromControl getOrElse
                ControlFilterType.Exact(filter)
            },
            orderDirection = orderDirection(controlQueryEl)
          )
        }

    if (allControls) {

      // Use specific controls to merge with explicit controls passed
      val specificControlsByPath =
        specificControls.map(c => c.path -> c).toMap

      PersistenceMetadataSupport.readPublishedFormStorageDetails(appForm, version) match {
        case Success(FormStorageDetails(_, indexedControlsXPaths, _)) =>
          indexedControlsXPaths.value map { indexedControlXPath =>
            ControlQuery(
              indexedControlXPath,
              specificControlsByPath.get(indexedControlXPath).flatMap(_.filterType),
              orderDirection = None
            )
          }
        case Failure(_) =>
          // TODO: throw or log?
          //throw new IllegalArgumentException(s"Form not found: $appForm")
          Nil
      }
    } else
      specificControls
  }

  private def metadataQueries(metadataQueryEls: List[NodeInfo]): List[MetadataQuery] =
    metadataQueryEls
      .map { metadataQueryEl =>
        val metadata   = Metadata.apply(metadataQueryEl.attValue("metadata"))
        val filterType = trimAllToOpt(metadataQueryEl.stringValue) map { filter =>
          metadataQueryEl.attValue("match") match {
            case "gte"   => MetadataFilterType.GreaterThanOrEqual(RelationalUtils.instantFromString(filter))
            case "lt"    => MetadataFilterType.LowerThan         (RelationalUtils.instantFromString(filter))
            case "exact" => MetadataFilterType.Exact             (filter)
            case other   => throw new IllegalArgumentException(other)
          }
        }

        // Check metadata vs match operator compatibility
        filterType.map {
          case _: MetadataFilterType.StringFilterType  => Set[Metadata](CreatedBy, LastModifiedBy, WorkflowStage)
          case _: MetadataFilterType.InstantFilterType => Set[Metadata](Created, LastModified)
        }.foreach {
          allowedMetadata =>
            if (! allowedMetadata.contains(metadata)) {
              throw new IllegalArgumentException(s"Invalid match type `${filterType.get}` for metadata `$metadata`")
            }
        }

        MetadataQuery(
          metadata       = metadata,
          filterType     = filterType,
          orderDirection = orderDirection(metadataQueryEl)
        )
      }
}
