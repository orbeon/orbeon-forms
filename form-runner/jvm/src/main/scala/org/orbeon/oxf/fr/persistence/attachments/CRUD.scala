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

import org.orbeon.oxf.externalcontext.ExternalContext.{Request, Response}
import org.orbeon.oxf.fr.persistence.relational.Version
import org.orbeon.oxf.fr.{AppForm, FormOrData}
import org.orbeon.oxf.http.{HttpMethod, HttpStatusCodeException, Ranges, StatusCode}
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.ProcessorImpl
import org.orbeon.oxf.util.{LoggerFactory, NetUtils}

import scala.util.{Failure, Success}

class CRUD extends ProcessorImpl {
  private val logger = LoggerFactory.createLogger(classOf[CRUD])

  override def start(pipelineContext: PipelineContext): Unit = {
    implicit val httpRequest:  Request  = NetUtils.getExternalContext.getRequest
    implicit val httpResponse: Response = NetUtils.getExternalContext.getResponse

    try {
      logger.info(s"Attachments provider service: ${httpRequest.getMethod} ${httpRequest.getRequestPath}")

      val (provider, attachmentInformation) = CRUD.providerAndAttachmentInformation(httpRequest)

      Ranges(httpRequest) match {
        case Success(ranges) =>
          // Only support single HTTP ranges for now
          httpRequest.getMethod match {
            case HttpMethod.HEAD   => provider.head  (attachmentInformation, ranges.singleRange)
            case HttpMethod.GET    => provider.get   (attachmentInformation, ranges.singleRange)
            case HttpMethod.PUT    => provider.put   (attachmentInformation)
            case HttpMethod.DELETE => provider.delete(attachmentInformation)
            case _                 => httpResponse.setStatus(StatusCode.MethodNotAllowed)
          }

        case Failure(throwable) =>
          logger.error(throwable)(s"Error while processing request ${httpRequest.getRequestPath}")
          httpResponse.setStatus(StatusCode.BadRequest)
      }


    } catch {
      case e: HttpStatusCodeException =>
        httpResponse.setStatus(e.code)
    }
  }
}

object CRUD {
  private val FormPath = "/fr/service/([^/]+)/crud/([^/]+)/([^/]+)/form/([^/]+)".r
  private val DataPath = "/fr/service/([^/]+)/crud/([^/]+)/([^/]+)/data/([^/]+)/([^/]+)".r

  case class AttachmentInformation(
    appForm    : AppForm,
    formOrData : FormOrData,
    documentId : Option[String],
    version    : Option[Int],
    filename   : String
  )

  private def providerAndAttachmentInformation(httpRequest: Request): (Provider, AttachmentInformation) = {
    val versionFromHeaders = httpRequest.getFirstHeaderIgnoreCase(Version.OrbeonFormDefinitionVersion).map(_.toInt)

    httpRequest.getRequestPath match {
      case FormPath(providerName, app, form, filename) =>
        (Provider.withName(providerName), AttachmentInformation(
          appForm    = AppForm(app, form),
          formOrData = FormOrData.Form,
          documentId = None,
          version    = versionFromHeaders,
          filename   = filename
        ))

      case DataPath(providerName, app, form, documentId, filename) =>
        (Provider.withName(providerName), AttachmentInformation(
          appForm    = AppForm(app, form),
          formOrData = FormOrData.Data,
          documentId = Some(documentId),
          version    = versionFromHeaders,
          filename   = filename
        ))

      case _ =>
        throw HttpStatusCodeException(StatusCode.BadRequest)
    }
  }
}
