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

import java.io.ByteArrayInputStream
import java.net.URI

import cats.syntax.option._
import org.orbeon.oxf.http.{Headers, StreamedContent}
import org.orbeon.oxf.util.{Connection, ConnectionResult, CoreCrossPlatformSupport}
import org.orbeon.xforms.XFormsCrossPlatformSupport

import scala.util.Success

/**
 * Test submission which just echoes the incoming document.
 */
class EchoSubmission(submission: XFormsModelSubmission) extends BaseSubmission(submission) {

  def getType = "echo"

  def isMatch(
    p : SubmissionParameters,
    p2: SecondPassParameters,
    sp: SerializationParameters
  ): Boolean =
    p2.actionOrResource.startsWith("test:") || p2.actionOrResource.startsWith("echo:")

  def connect(
    p : SubmissionParameters,
    p2: SecondPassParameters,
    sp: SerializationParameters
  ): Option[ConnectResult] = {

    sp.messageBody match {
      case None =>
        // Not sure when this can happen, but it can't be good
        throw new XFormsSubmissionException(
          submission  = submission,
          message     = "Action 'test:': no message body.",
          description = "processing submission response"
        )
      case Some(messageBody) =>
        // Log message body for debugging purposes
        val indentedLogger = getDetailsLogger(p, p2)
        if (indentedLogger.debugEnabled && BaseSubmission.isLogBody)
          SubmissionUtils.logRequestBody(sp.actualRequestMediatype, messageBody)(indentedLogger)
    }

    val customHeaderNameValues = SubmissionUtils.evaluateHeaders(submission, p.replaceType == ReplaceType.All)

    // Just a scheme is not a valid URI, so add a scheme-specific part if needed
    val url =
      new URI(
        p2.actionOrResource match {
          case "echo:" | "test:" => "echo:/"
          case other             => other
        }
      )

    val headers = Connection.buildConnectionHeadersCapitalizedWithSOAPIfNeeded(
      url                      = url,
      method                   = p.httpMethod,
      hasCredentials           = p2.credentialsOpt.isDefined,
      mediatypeOpt             = sp.actualRequestMediatype.some,
      encodingForSOAP          = p2.encoding,
      customHeaders            = customHeaderNameValues,
      headersToForward         = Connection.headersToForwardFromProperty,
      getHeader                = containingDocument.headersGetter)(
      logger                   = getDetailsLogger(p, p2),
      externalContext          = XFormsCrossPlatformSupport.externalContext,
      coreCrossPlatformSupport = CoreCrossPlatformSupport
    )

    // Do as if we are receiving a regular XML response
    val connectionResult = ConnectionResult(
      url                 = p2.actionOrResource,
      statusCode          = 200,
      headers             = headers,
      content             = StreamedContent(
        inputStream     = new ByteArrayInputStream(sp.messageBody getOrElse Array.emptyByteArray),
        contentType     = Headers.firstItemIgnoreCase(headers, Headers.ContentType),
        contentLength   = Some(sp.messageBody map (_.length.toLong) getOrElse 0L),
        title           = None
      )
    )
    val replacer = submission.getReplacer(connectionResult, p)(submission.getIndentedLogger)

    // Deserialize here so it can run in parallel
    replacer.deserialize(connectionResult, p, p2)

    ConnectResult(submission.getEffectiveId, Success((replacer, connectionResult))).some
  }
}