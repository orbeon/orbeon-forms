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

import org.orbeon.oxf.controller.Authorizer
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.fr.AppForm
import org.orbeon.oxf.fr.FormRunnerPersistence.{DataXml, FormXhtml}
import org.orbeon.oxf.fr.persistence.relational.rest.SqlSupport.Logger
import org.orbeon.oxf.fr.persistence.relational.{Provider, StageHeader, Version}
import org.orbeon.oxf.http.{Headers, HttpMethod, HttpStatusCodeException, StatusCode}
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.ProcessorImpl
import org.orbeon.oxf.util.NetUtils
import org.orbeon.oxf.util.StringUtils._

import java.time.Instant


class CRUD
    extends ProcessorImpl
    with Read
    with CreateUpdateDelete
    with LockUnlock {

  import CRUD._

  override def start(pipelineContext: PipelineContext): Unit = {

    implicit val httpRequest : ExternalContext.Request = NetUtils.getExternalContext.getRequest
    implicit val httpResponse: ExternalContext.Response = NetUtils.getExternalContext.getResponse

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
}

private object CRUD {

  val CrudFormPath = "/fr/service/([^/]+)/crud/([^/]+)/([^/]+)/form/([^/]+)".r
  val CrudDataPath = "/fr/service/([^/]+)/crud/([^/]+)/([^/]+)/(data|draft)/([^/]+)/([^/]+)".r

  def headerValueIgnoreCase(name: String)(implicit httpRequest: ExternalContext.Request): Option[String] =
    httpRequest.getFirstHeaderIgnoreCase(name)

  def requestWorkflowStage(implicit httpRequest: ExternalContext.Request): Option[String] =
    headerValueIgnoreCase(StageHeader.HeaderName)

  def getLockUnlockRequest(requestPath: String)(implicit httpRequest: ExternalContext.Request): LockUnlockRequest =
    requestPath match {
      case CrudDataPath(provider, _, _, dataOrDraft, documentId, _) =>
        val dataPart = DataPart(dataOrDraft == "draft", documentId, stage = requestWorkflowStage, forceDelete = false)
        LockUnlockRequest(Provider.withName(provider), dataPart)
      case _ =>
        throw HttpStatusCodeException(StatusCode.BadRequest)
    }

  def getCrudRequest(requestPath: String)(implicit httpRequest: ExternalContext.Request): CrudRequest = {

    if (Logger.debugEnabled)
      Logger.logDebug("CRUD", s"receiving request: method = ${httpRequest.getMethod}, path = $requestPath")

    // The persistence proxy must pass a specific version when needed. It is not always needed. For example a `GET` of
    // data doesn't require a version, but a `GET` or a form definition does, and a `PUT` of data does as well as the
    // version is stored.
    val incomingVersion = headerValueIgnoreCase(Version.OrbeonFormDefinitionVersion).map(_.toInt)
    val requestUsername = headerValueIgnoreCase(Headers.OrbeonUsername)
    val requestGroup    = headerValueIgnoreCase(Headers.OrbeonGroup)
    val requestFlatView = headerValueIgnoreCase("orbeon-create-flat-view").contains("true")

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
          requestWorkflowStage
        )
      case CrudDataPath(provider, app, form, dataOrDraft, documentId, filename) =>

        val filenameOpt     = if (filename == DataXml) None else Some(filename)
        val lastModifiedOpt = httpRequest.getFirstParamAsString("last-modified-time").flatMap(_.trimAllToOpt).map(Instant.parse) // xxx TODO constant

        val forceDelete =
          httpRequest.getMethod == HttpMethod.DELETE &&
            httpRequest.getFirstParamAsString("force-delete").flatMap(_.trimAllToOpt).contains(true.toString)

        if (forceDelete && (
          ! Authorizer.authorizedWithToken(NetUtils.getExternalContext) || // force `DELETE` from internal callers only
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
          requestWorkflowStage
        )
      case _ =>
        throw HttpStatusCodeException(StatusCode.BadRequest)
    }
  }
}