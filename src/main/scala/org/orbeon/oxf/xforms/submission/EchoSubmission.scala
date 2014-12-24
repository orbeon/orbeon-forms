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

import org.orbeon.oxf.http.{Headers, StreamedContent}
import org.orbeon.oxf.util.{Connection, ConnectionResult}
import org.orbeon.oxf.xml.XMLUtils

/**
 * Test submission which just echoes the incoming document.
 */
class EchoSubmission(submission: XFormsModelSubmission) extends BaseSubmission(submission) {

    def getType = "echo"

    // Match for replace="instance|none|all" and the submission resource starts with "test:" or "echo:"
    def isMatch(
        p : XFormsModelSubmission#SubmissionParameters,
        p2: XFormsModelSubmission#SecondPassParameters,
        sp: XFormsModelSubmission#SerializationParameters
    ) =
        (p.isReplaceInstance || p.isReplaceNone || p.isReplaceAll) &&
            (p2.actionOrResource.startsWith("test:") || p2.actionOrResource.startsWith("echo:"))

    def connect(
        p : XFormsModelSubmission#SubmissionParameters,
        p2: XFormsModelSubmission#SecondPassParameters,
        sp: XFormsModelSubmission#SerializationParameters
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

        val customHeaderNameValues = SubmissionUtils.evaluateHeaders(submission, p.isReplaceAll)
        val headersToForward       = containingDocument.getForwardSubmissionHeaders

        val headers = Connection.buildConnectionHeadersLowerWithSOAPIfNeeded(
            scheme            = "http",
            httpMethodUpper   = p.actualHttpMethod,
            credentialsOrNull = p2.credentials,
            mediatype         = sp.actualRequestMediatype,
            encodingForSOAP   = p2.encoding,
            customHeaders     = customHeaderNameValues,
            headersToForward  = headersToForward)(
            logger            = getDetailsLogger(p, p2)
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
        val replacer = submission.getReplacer(connectionResult, p.asInstanceOf[submission.SubmissionParameters])

        // Deserialize here so it can run in parallel
        replacer.deserialize(connectionResult, p, p2)

        new SubmissionResult(submission.getEffectiveId, replacer, connectionResult)
    }
}