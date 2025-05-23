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
package org.orbeon.oxf.fr.persistence.attachments

import org.orbeon.oxf.controller.NativeRoute
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.externalcontext.ExternalContext.{Request, Response}
import org.orbeon.oxf.fr.{AppForm, FormOrData, Version}
import org.orbeon.oxf.http.{HttpMethod, HttpRanges, HttpStatusCodeException, StatusCode}
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.RegexpMatcher.MatchResult
import org.orbeon.oxf.util.Logging.*
import org.orbeon.oxf.util.{IndentedLogger, LoggerFactory}

import scala.util.{Failure, Success}


object CRUDRoute extends NativeRoute {

  private val logger = LoggerFactory.createLogger(CRUDRoute.getClass)

  import CRUD.*

  def process(
    matchResult: MatchResult
  )(implicit
    pc         : PipelineContext,
    ec         : ExternalContext
  ): Unit = {

    implicit val httpRequest:  Request  = ec.getRequest
    implicit val httpResponse: Response = ec.getResponse
    implicit val indentedLogger: IndentedLogger = new IndentedLogger(logger)

    try {
      info(s"Attachments provider service: ${httpRequest.getMethod} ${httpRequest.getRequestPath}")

      val (provider, attachmentInformation) = providerAndAttachmentInformation(httpRequest)

      HttpRanges(httpRequest) match {
        case Success(ranges) =>
          httpRequest.getMethod match {
            case HttpMethod.HEAD   => provider.head  (attachmentInformation, ranges)
            case HttpMethod.GET    => provider.get   (attachmentInformation, ranges)
            case HttpMethod.PUT    => provider.put   (attachmentInformation)
            case HttpMethod.DELETE => provider.delete(attachmentInformation)
            case _                 => httpResponse.setStatus(StatusCode.MethodNotAllowed)
          }

        case Failure(throwable) =>
          error(s"Error while processing request ${httpRequest.getRequestPath}", throwable)
          httpResponse.setStatus(StatusCode.BadRequest)
      }
    } catch {
      case e: HttpStatusCodeException =>
        httpResponse.setStatus(e.code)
    }
  }
}

private[attachments] object CRUD {

  private val FormPath = "/fr/service/([^/]+)/crud/([^/]+)/([^/]+)/form/([^/]+)".r
  private val DataPath = "/fr/service/([^/]+)/crud/([^/]+)/([^/]+)/(data|draft)/([^/]+)/([^/]+)".r

  case class AttachmentInformation(
    appForm    : AppForm,
    formOrData : FormOrData,
    draft      : Boolean,
    documentId : Option[String],
    version    : Option[Int],
    filename   : String
  ) {
    // Fixed path to the attachment file (same for local filesystem and S3)
    lazy val pathSegments: List[String] =
      List(
        appForm.app,
        appForm.form,
        if (draft) "draft" else formOrData.entryName
      ) :++
        documentId :++
        version.map(_.toString) :+
        filename
  }

  def providerAndAttachmentInformation(httpRequest: Request): (Provider, AttachmentInformation) = {
    val versionFromHeaders = httpRequest.getFirstHeaderIgnoreCase(Version.OrbeonFormDefinitionVersion).map(_.toInt)

    httpRequest.getRequestPath match {
      case FormPath(providerName, app, form, filename) =>
        (Provider.withName(providerName), AttachmentInformation(
          appForm    = AppForm(app, form),
          formOrData = FormOrData.Form,
          draft      = false,
          documentId = None,
          version    = versionFromHeaders,
          filename   = filename
        ))

      case DataPath(providerName, app, form, dataOrDraft, documentId, filename) =>
        (Provider.withName(providerName), AttachmentInformation(
          appForm    = AppForm(app, form),
          formOrData = FormOrData.Data,
          draft      = dataOrDraft == "draft",
          documentId = Some(documentId),
          version    = versionFromHeaders,
          filename   = filename
        ))

      case _ =>
        throw HttpStatusCodeException(StatusCode.BadRequest)
    }
  }
}
