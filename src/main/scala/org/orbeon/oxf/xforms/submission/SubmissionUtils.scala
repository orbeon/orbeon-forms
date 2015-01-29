/**
 * Copyright (C) 2012 Orbeon, Inc.
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

import java.io.InputStream
import java.net.URI

import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.http
import org.orbeon.oxf.pipeline.api.ExternalContext
import org.orbeon.oxf.util._
import org.orbeon.oxf.xforms.{XFormsContainingDocument, XFormsModel}
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.saxon.om.{DocumentInfo, Navigator, NodeInfo}

// The plan is to move stuff from XFormsSubmissionUtils to here as needed
object SubmissionUtils {

    def dataNodeHash(node: NodeInfo) =
        SecureUtils.hmacString(Navigator.getPath(node), "hex")

    def readByteArray(model: XFormsModel, resolvedURL: String): Array[Byte] =
        processGETConnection(model, resolvedURL) { is ⇒
            NetUtils.inputStreamToByteArray(is)
        }

    def readTinyTree(model: XFormsModel, resolvedURL: String, handleXInclude: Boolean): DocumentInfo =
        processGETConnection(model, resolvedURL) { is ⇒
            TransformerUtils.readTinyTree(
                XPath.GlobalConfiguration,
                is,
                resolvedURL,
                handleXInclude,
                true
            )
        }

    def processGETConnection[T](model: XFormsModel, resolvedURL: String)(body: InputStream ⇒ T): T =
        ConnectionResult.withSuccessConnection(openGETConnection(model, resolvedURL), closeOnSuccess = true)(body)

    def openGETConnection(model: XFormsModel, resolvedURL: String) = {

        implicit val _logger = model.indentedLogger
        val url = new URI(resolvedURL)

        Connection(
            httpMethodUpper = "GET",
            url             = url,
            credentials     = None,
            content         = None,
            headers         = Connection.buildConnectionHeadersLowerIfNeeded(
                scheme           = url.getScheme,
                hasCredentials   = false,
                customHeaders    = Map(),
                headersToForward = Connection.headersToForwardFromProperty,
                cookiesToForward = Connection.cookiesToForwardFromProperty
            ) mapValues (_.toList),
            loadState       = true,
            logBody         = BaseSubmission.isLogBody
        ).connect(
            saveState = true
        )
    }

    def evaluateHeaders(submission: XFormsModelSubmission, forwardClientHeaders: Boolean): Map[String, List[String]] = {
        try {
            val headersToForward =
                clientHeadersToForward(submission.containingDocument.getRequestHeaders, forwardClientHeaders)

            SubmissionHeaders.evaluateHeaders(
                submission.container,
                submission.getModel.getContextStack,
                submission.getEffectiveId,
                submission.getSubmissionElement,
                headersToForward
            )

        } catch {
            case e: OXFException ⇒ throw new XFormsSubmissionException(submission, e, e.getMessage, "processing <header> elements")
        }
    }

    def clientHeadersToForward(allHeaders: Map[String, List[String]], forwardClientHeaders: Boolean) = {
        if (forwardClientHeaders) {
            // Forwarding the user agent and accept headers makes sense when dealing with resources that
            // typically would come from the client browser, including:
            //
            // - submission with replace="all"
            // - dynamic resources loaded by xf:output
            //
            // Also useful when the target URL renders XForms in noscript mode, where some browser sniffing takes
            // place for handling the <button> vs. <submit> element.
            val toForward =
                for {
                    name   ← List("user-agent", "accept")
                    values ← allHeaders.get(name)
                } yield
                    name → values

            // Give priority to explicit headers
            toForward.toMap
        } else
            Map.empty[String, List[String]]
    }

    def forwardResponseHeaders(cxr: ConnectionResult, response: ExternalContext.Response): Unit =
        for {
            (headerName, headerValues) ← http.Headers.proxyHeaders(cxr.headers, request = false)
            headerValue                ← headerValues
        } locally {
            response.addHeader(headerName, headerValue)
        }
}
