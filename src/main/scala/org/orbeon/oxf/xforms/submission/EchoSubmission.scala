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

import org.orbeon.oxf.http.{Headers, HttpMethod, StreamedContent}
import org.orbeon.oxf.util.{Connection, ConnectionResult}

/**
 * Test submission which just echoes the incoming document.
 */
class EchoSubmission(submission: XFormsModelSubmission) extends BaseSubmission(submission) {

  def getType = "echo"

  def isMatch(
    p : SubmissionParameters,
    p2: SecondPassParameters,
    sp: SerializationParameters
  ) = {
    p2.actionOrResource.startsWith("test:") || p2.actionOrResource.startsWith("echo:")
  }

  def connect(
    p : SubmissionParameters,
    p2: SecondPassParameters,
    sp: SerializationParameters
  ) = {

    if (sp.messageBody == null) {
      // Not sure when this can happen, but it can't be good
      throw new XFormsSubmissionException(submission, "Action 'test:': no message body.", "processing submission response")
    }  else {
      // Log message body for debugging purposes
      val indentedLogger = getDetailsLogger(p, p2)
      if (indentedLogger.isDebugEnabled && BaseSubmission.isLogBody)
        Connection.logRequestBody(sp.actualRequestMediatype, sp.messageBody)(indentedLogger)
    }

    val customHeaderNameValues = SubmissionUtils.evaluateHeaders(submission, p.replaceType == ReplaceType.All)

    val headers = Connection.buildConnectionHeadersCapitalizedWithSOAPIfNeeded(
      scheme           = "http",
      method           = p.httpMethod,
      hasCredentials   = p2.credentialsOpt.isDefined,
      mediatype        = sp.actualRequestMediatype,
      encodingForSOAP  = p2.encoding,
      customHeaders    = customHeaderNameValues,
      headersToForward = Connection.headersToForwardFromProperty,
      getHeader        = containingDocument.headersGetter)(
      logger           = getDetailsLogger(p, p2)
    )

    // Do as if we are receiving a regular XML response
    val connectionResult = ConnectionResult(
      url                 = p2.actionOrResource,
      statusCode          = 200,
      headers             = headers,
      content             = StreamedContent(
        inputStream     = new ByteArrayInputStream(sp.messageBody),
        contentType     = Headers.firstHeaderIgnoreCase(headers, Headers.ContentType),
        contentLength   = Some(sp.messageBody.length),
        title           = None
      )
    )
    val replacer = submission.getReplacer(connectionResult, p)

    // Deserialize here so it can run in parallel
    replacer.deserialize(connectionResult, p, p2)

    new SubmissionResult(submission.getEffectiveId, replacer, connectionResult)
  }
}