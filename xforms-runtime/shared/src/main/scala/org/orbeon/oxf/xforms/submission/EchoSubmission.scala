/**
 * Copyright (C) 2009 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.submission

import cats.syntax.option._
import org.orbeon.connection.{ConnectionResult, StreamedContent}
import org.orbeon.oxf.http.Headers
import org.orbeon.oxf.util.{Connection, CoreCrossPlatformSupport}
import org.orbeon.xforms.XFormsCrossPlatformSupport

import java.io.ByteArrayInputStream
import java.net.URI
import scala.concurrent.Future
import scala.util.Success


class EchoSubmission(submission: XFormsModelSubmission)
  extends BaseSubmission(submission) {

  val submissionType = "echo"

  def isMatch(
    submissionParameters   : SubmissionParameters,
    serializationParameters: SerializationParameters
  ): Boolean =
    submissionParameters.actionOrResource.startsWith("test:") || submissionParameters.actionOrResource.startsWith("echo:")

  def connect(
    submissionParameters   : SubmissionParameters,
    serializationParameters: SerializationParameters
  )(implicit
    refContext             : RefContext
  ): Option[ConnectResult Either Future[AsyncConnectResult]] = {

    serializationParameters.messageBody match {
      case None =>
        // Not sure when this can happen, but it can't be good
        // Q: Can't we use a `GET`?
        throw new XFormsSubmissionException(
          submission  = submission,
          message     = s"Action `${submissionParameters.actionOrResource}`: no message body.",
          description = "processing submission response"
        )
      case Some(messageBody) =>
        // Log message body for debugging purposes
        val indentedLogger = getDetailsLogger(submissionParameters)
        if (indentedLogger.debugEnabled && BaseSubmission.isLogBody)
          SubmissionUtils.logRequestBody(serializationParameters.actualRequestMediatype, messageBody)(indentedLogger)
    }

    val customHeaderNameValues = SubmissionUtils.evaluateHeaders(submission, submissionParameters.replaceType == ReplaceType.All)

    // Just a scheme is not a valid URI, so add a scheme-specific part if needed
    val url =
      URI.create(
        submissionParameters.actionOrResource match {
          case "echo:" | "test:" => "echo:/"
          case other             => other
        }
      )

    val headers = Connection.buildConnectionHeadersCapitalizedWithSOAPIfNeeded(
      url                      = url,
      method                   = submissionParameters.httpMethod,
      hasCredentials           = submissionParameters.credentialsOpt.isDefined,
      mediatypeOpt             = serializationParameters.actualRequestMediatype.some,
      encodingForSOAP          = submissionParameters.encoding,
      customHeaders            = customHeaderNameValues,
      headersToForward         = Connection.headersToForwardFromProperty,
      getHeader                = submission.containingDocument.headersGetter)(
      logger                   = getDetailsLogger(submissionParameters),
      externalContext          = XFormsCrossPlatformSupport.externalContext,
      coreCrossPlatformSupport = CoreCrossPlatformSupport
    )

    // Do as if we are receiving a regular XML response
    val cxr = ConnectionResult(
      url                 = submissionParameters.actionOrResource,
      statusCode          = 200,
      headers             = headers,
      content             = StreamedContent(
        inputStream     = new ByteArrayInputStream(serializationParameters.messageBody getOrElse Array.emptyByteArray),
        contentType     = Headers.firstItemIgnoreCase(headers, Headers.ContentType),
        contentLength   = Some(serializationParameters.messageBody map (_.length.toLong) getOrElse 0L),
        title           = None
      )
    )

    Left(
      ConnectResultT(
        submission.getEffectiveId,
        Success((submission.getReplacer(cxr, submissionParameters)(submission.getIndentedLogger), cxr)))
    ).some
  }
}