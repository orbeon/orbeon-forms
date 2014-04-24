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

import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.resources.URLFactory
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.util._
import org.orbeon.oxf.xforms.{XFormsContainingDocument, XFormsModel}
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.saxon.om.DocumentInfo
import scala.util.control.NonFatal

// The plan is to move stuff from XFormsSubmissionUtils to here as needed
object SubmissionUtils {

    def readByteArray(model: XFormsModel, resolvedURL: String): Array[Byte] =
        processGETConnection(model, resolvedURL) { result ⇒
            NetUtils.inputStreamToByteArray(result.getResponseInputStream)
        }

    def readTinyTree(model: XFormsModel, resolvedURL: String, handleXInclude: Boolean): DocumentInfo =
        processGETConnection(model, resolvedURL) { result ⇒
            TransformerUtils.readTinyTree(
                XPath.GlobalConfiguration,
                result.getResponseInputStream,
                result.resourceURI,
                handleXInclude,
                true)
        }

    def processGETConnection[T](model: XFormsModel, resolvedURL: String)(body: ConnectionResult ⇒ T): T =
        useAndClose(openGETConnection(model, resolvedURL)) { result ⇒
            if (NetUtils.isSuccessCode(result.statusCode))
                try body(result)
                catch { case NonFatal(t) ⇒ throw new OXFException("Got exception while while reading URL: " + resolvedURL, t) }
            else
                throw new OXFException("Got invalid return code while reading URL: " + resolvedURL + ", " + result.statusCode)
        }

    def openGETConnection(model: XFormsModel, resolvedURL: String) =
        Connection(
            "GET",
            URLFactory.createURL(resolvedURL),
            None,
            None,
            Connection.buildConnectionHeaders(None, Map(), getHeadersToForward(model.containingDocument))(model.indentedLogger),
            loadState = true,
            logBody = BaseSubmission.isLogBody)(model.indentedLogger).connect(saveState = true)

    private def getHeadersToForward(containingDocument: XFormsContainingDocument) =
        Option(containingDocument.getForwardSubmissionHeaders)

    def evaluateHeaders(submission: XFormsModelSubmission, forwardClientHeaders: Boolean): Map[String, Array[String]] = {
        try {
            val headersToForward =
                clientHeadersToForward(submission.containingDocument.getRequestHeaders, forwardClientHeaders)

            Headers.evaluateHeaders(
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

    def clientHeadersToForward(allHeaders: Map[String, Array[String]], forwardClientHeaders: Boolean) = {
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
            Map.empty[String, Array[String]]
    }
}
