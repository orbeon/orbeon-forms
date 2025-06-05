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
package org.orbeon.oxf.fr.persistence.relational.rest

import org.orbeon.oxf.controller.{Authorizer, NativeRoute}
import org.orbeon.oxf.externalcontext.{ExternalContext, UserAndGroup}
import org.orbeon.oxf.fr.FormRunnerPersistence.{DataXml, FormXhtml, OrbeonHashAlogrithm, OrbeonHashValue}
import org.orbeon.oxf.fr.persistence.api.PersistenceApi
import org.orbeon.oxf.fr.persistence.relational.{Provider, StageHeader}
import org.orbeon.oxf.fr.{AppForm, Version}
import org.orbeon.oxf.http.*
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.RegexpMatcher.MatchResult
import org.orbeon.oxf.util.Logging.*
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.util.{IndentedLogger, LoggerFactory, NetUtils}

import java.time.Instant
import scala.util.{Failure, Success}


object CRUDRoute
  extends NativeRoute
  with    Read
  with    CreateUpdateDelete
  with    LockUnlock {

  private val logger = LoggerFactory.createLogger(CRUDRoute.getClass)

  def process(
    matchResult: MatchResult
  )(implicit
    pc         : PipelineContext,
    ec         : ExternalContext
  ): Unit = {

    implicit val indentedLogger : IndentedLogger  = new IndentedLogger(logger)

    implicit val httpRequest: ExternalContext.Request  = ec.getRequest
    val httpResponse  : ExternalContext.Response = ec.getResponse

    try {

      val requestPath  = httpRequest.getRequestPath

      httpRequest.getMethod match {
        case HttpMethod.GET | HttpMethod.HEAD => getOrHead(getCrudRequest      (requestPath), httpRequest.getMethod)
        case HttpMethod.PUT                   => change   (getCrudRequest      (requestPath), delete = false)
        case HttpMethod.DELETE                => change   (getCrudRequest      (requestPath), delete = true)
        case HttpMethod.LOCK                  => lock     (getLockUnlockRequest(requestPath))
        case HttpMethod.UNLOCK                => unlock   (getLockUnlockRequest(requestPath))
        case _                                => httpResponse.setStatus(StatusCode.MethodNotAllowed)
      }
    } catch {
      case e: HttpStatusCodeException =>
        httpResponse.setStatus(e.code)
    }
  }

  private val CrudFormPath = "/fr/service/([^/]+)/crud/([^/]+)/([^/]+)/form/([^/]+)".r
  private val CrudDataPath = "/fr/service/([^/]+)/crud/([^/]+)/([^/]+)/(data|draft)/([^/]+)/([^/]+)".r

  private def headerValueIgnoreCase(name: String)(implicit httpRequest: ExternalContext.Request): Option[String] =
    httpRequest.getFirstHeaderIgnoreCase(name)

  private def requestWorkflowStage(implicit httpRequest: ExternalContext.Request): Option[String] =
    headerValueIgnoreCase(StageHeader.HeaderName)

  private def getLockUnlockRequest(requestPath: String)(implicit httpRequest: ExternalContext.Request): LockUnlockRequest =
    requestPath match {
      case CrudDataPath(provider, _, _, dataOrDraft, documentId, _) =>
        val dataPart = DataPart(dataOrDraft == "draft", documentId, stage = requestWorkflowStage, forceDelete = false)
        LockUnlockRequest(Provider.withName(provider), dataPart)
      case _ =>
        throw HttpStatusCodeException(StatusCode.BadRequest)
    }

  private def getCrudRequest(
    requestPath: String
  )(implicit
    httpRequest   : ExternalContext.Request,
    indentedLogger: IndentedLogger
  ): CrudRequest = {

    debug(s"receiving request: method = ${httpRequest.getMethod}, path = $requestPath")

    // The persistence proxy must pass a specific version when needed. It is not always needed. For example a `GET` of
    // data doesn't require a version, but a `GET` or a form definition does, and a `PUT` of data does as well as the
    // version is stored.
    val incomingVersion = headerValueIgnoreCase(Version.OrbeonFormDefinitionVersion).map(_.toInt)
    val requestUsername = headerValueIgnoreCase(Headers.OrbeonUsername)
    val requestGroup    = headerValueIgnoreCase(Headers.OrbeonGroup)
    val requestFlatView = headerValueIgnoreCase(Headers.OrbeonCreateFlatView).contains("true")
    val singleton       = headerValueIgnoreCase(Headers.OrbeonSingleton).map(_.toBoolean)
    val hashAlgorithm   = headerValueIgnoreCase(OrbeonHashAlogrithm)
    val hashValue       = headerValueIgnoreCase(OrbeonHashValue)

    val ranges = HttpRanges(httpRequest) match {
      case Success(ranges) => ranges
      case Failure(t)      => throw HttpStatusCodeException(StatusCode.BadRequest, throwable = Some(t))
    }

    val existingRow = {

      def headerValueIgnoreCaseExisting(name: String): Option[String] = headerValueIgnoreCase(s"$name-Existing")

      headerValueIgnoreCaseExisting(Headers.OrbeonCreated).map(Instant.parse).map {
        createdTime =>
          ExistingRow(
            createdTime  = createdTime,
            createdBy    = headerValueIgnoreCaseExisting(Headers.OrbeonUsername).map { username =>
              UserAndGroup(
                username  = username,
                groupname = headerValueIgnoreCaseExisting(Headers.OrbeonGroup)
              )
            },
            organization = None, // Some(Headers.allItemsIgnoreCase(headers, Headers.OrbeonOrganization).toList).filter(_.nonEmpty)
          )
      }
    }

    requestPath match {
      case CrudFormPath(provider, app, form, filename) =>
        val filenameOpt = if (filename == FormXhtml) None else Some(filename)
        CrudRequest(
          Provider.withName(provider),
          AppForm(app, form),
          incomingVersion,
          filenameOpt,
          None,
          None,
          requestUsername,
          requestGroup,
          requestFlatView,
          httpRequest.credentials,
          requestWorkflowStage,
          ranges,
          existingRow,
          singleton,
          hashAlgorithm,
          hashValue
        )
      case CrudDataPath(provider, app, form, dataOrDraft, documentId, filename) =>

        val filenameOpt     = if (filename == DataXml) None else Some(filename)
        val lastModifiedOpt = httpRequest.getFirstParamAsString(PersistenceApi.LastModifiedTimeParam).flatMap(_.trimAllToOpt).map(Instant.parse)

        val forceDelete =
          (httpRequest.getMethod == HttpMethod.DELETE || httpRequest.getMethod == HttpMethod.HEAD) &&
            httpRequest.getFirstParamAsString(PersistenceApi.ForceDeleteParam).flatMap(_.trimAllToOpt).contains(true.toString)

        if (forceDelete && (
          ! Authorizer.authorizedWithToken(NetUtils.getExternalContext) || // force `DELETE` from internal callers only (for now, as a safeguard)
          filenameOpt.isEmpty && lastModifiedOpt.isEmpty                || // and only for historical data
          filenameOpt.isDefined && lastModifiedOpt.isDefined               // or for attachments
          )
        ) {
          throw HttpStatusCodeException(throw HttpStatusCodeException(StatusCode.BadRequest))
        }

        CrudRequest(
          Provider.withName(provider),
          AppForm(app, form),
          incomingVersion,
          filenameOpt,
          Some(DataPart(dataOrDraft == "draft", documentId, stage = requestWorkflowStage, forceDelete = forceDelete)),
          lastModifiedOpt,
          requestUsername,
          requestGroup,
          requestFlatView,
          httpRequest.credentials,
          requestWorkflowStage,
          ranges,
          existingRow,
          singleton,
          hashAlgorithm,
          hashValue
        )
      case _ =>
        throw HttpStatusCodeException(StatusCode.BadRequest)
    }
  }
}