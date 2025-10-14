/**
 * Copyright (C) 2025 Orbeon, Inc.
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

import cats.implicits.catsSyntaxOptionId
import org.orbeon.oxf.controller.XmlNativeRoute
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.fr.*
import org.orbeon.oxf.fr.persistence.api.Diffs
import org.orbeon.oxf.fr.persistence.api.HistoryDiff.*
import org.orbeon.oxf.fr.persistence.relational.RelationalUtils
import org.orbeon.oxf.http.{HttpMethod, HttpStatusCodeException, StatusCode}
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.util.{CoreCrossPlatformSupport, CoreCrossPlatformSupportTrait, IndentedLogger}
import org.orbeon.oxf.xml.DeferredXMLReceiver
import org.orbeon.oxf.xml.XMLReceiverSupport.*

import scala.util.matching.Regex
import scala.util.{Failure, Success, Try}


object HistoryDiffRoute extends XmlNativeRoute {

  def process()(implicit pc: PipelineContext, ec: ExternalContext): Unit = {

    val httpRequest  = ec.getRequest
    val httpResponse = ec.getResponse

    implicit val indentedLogger: IndentedLogger = RelationalUtils.newIndentedLogger

    require(httpRequest.getMethod == HttpMethod.GET)

    try {
      httpRequest.getRequestPath match {
        case ServicePathRe(_, app, form, documentId) => process(httpRequest, AppForm(app, form), documentId)
        case _                                       => httpResponse.setStatus(StatusCode.NotFound)
      }
    } catch {
      case HttpStatusCodeWithDescription(statusCode, error) =>
        // TODO: the API should return detailed error messages in the body itself
        indentedLogger.logError("", s"Revision History API error: $error")
        httpResponse.setStatus(statusCode)

      case HttpStatusCodeException(statusCode, _, _) =>
        httpResponse.setStatus(statusCode)
    }
  }

  private val ServicePathRe: Regex = "/fr/service/([^/]+)/history/([^/]+)/([^/]+)/([^/^.]+)/diff".r

  private def process(
    httpRequest   : ExternalContext.Request,
    appForm       : AppForm,
    documentId    : String
  )(implicit
    ec            : ExternalContext,
    indentedLogger: IndentedLogger
  ): Unit = {

    def paramValue[T](paramName: String, parser: String => T, defaultOpt: => Option[T] = None): Try[T] =
      httpRequest.getFirstParamAsString(paramName) match {
        case Some(s) =>
          Try(parser(s)).recoverWith { case _ =>
            Failure(HttpStatusCodeWithDescription(StatusCode.BadRequest, s"Invalid `$paramName` parameter"))
          }

        case None    =>
          Try(defaultOpt) match {
            case Success(Some(default)) => Success(default)
            case Success(None)          => Failure(HttpStatusCodeWithDescription(StatusCode.BadRequest         , s"Missing `$paramName` parameter"))
            case Failure(_)             => Failure(HttpStatusCodeWithDescription(StatusCode.InternalServerError, s"Could not determine default value for `$paramName` parameter"))
          }
      }

    implicit val coreCrossPlatformSupport: CoreCrossPlatformSupportTrait = CoreCrossPlatformSupport

    (for {
      formVersion       <- paramValue("form-version", _.toInt)
      olderModifiedTime <- paramValue("older-modified-time", RelationalUtils.instantFromString)
      newerModifiedTime <- paramValue("newer-modified-time", RelationalUtils.instantFromString)
      truncationSizeOpt <- paramValue(TruncationSizeParam, _.toInt.some, Some(None))
      appFormVersion    = (appForm, formVersion)
      requestedLangOpt  = httpRequest.getFirstParamAsString(LanguageParam)
      formDefinition    <- formDefinition(appFormVersion, requestedLangOpt)
      diffsOpt          <- formDiffs(
        appFormVersion    = appFormVersion,
        documentId        = documentId,
        olderModifiedTime = olderModifiedTime,
        newerModifiedTime = newerModifiedTime,
        formDefinition    = formDefinition
      )
    } yield {
      implicit val receiver: DeferredXMLReceiver = getResponseXmlReceiverSetContentType

      withDocument {
        Diffs.serializeToSAX(
          diffsOpt,
          olderModifiedTime,
          newerModifiedTime.some,
          formDefinition,
          truncationSizeOpt
        )
      }
    }).get
  }
}
