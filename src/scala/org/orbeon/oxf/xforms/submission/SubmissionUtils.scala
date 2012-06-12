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

import org.orbeon.oxf.resources.URLFactory
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.xforms.{XFormsModel, XFormsProperties}
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.oxf.util.{XPathCache, ConnectionResult, Connection, NetUtils}
import org.orbeon.saxon.om.DocumentInfo

// The plan is to move stuff from XFormsSubmissionUtils to here as needed
object SubmissionUtils {

    def readByteArray(model: XFormsModel, resolvedURL: String): Array[Byte] =
        processGETConnection(model, resolvedURL) { result ⇒
            NetUtils.inputStreamToByteArray(result.getResponseInputStream)
        }

    def readTinyTree(model: XFormsModel, resolvedURL: String, handleXInclude: Boolean): DocumentInfo =
        processGETConnection(model, resolvedURL) { result ⇒
            TransformerUtils.readTinyTree(
                XPathCache.getGlobalConfiguration,
                result.getResponseInputStream,
                result.resourceURI,
                handleXInclude,
                true)
        }

    def processGETConnection[T](model: XFormsModel, resolvedURL: String)(body: ConnectionResult ⇒ T): T =
        useAndClose(openGETConnection(model, resolvedURL)) { result ⇒
            if (NetUtils.isSuccessCode(result.statusCode))
                try body(result)
                catch { case e: Exception ⇒ throw new OXFException("Got exception while while reading URL: " + resolvedURL, e) }
            else
                throw new OXFException("Got invalid return code while reading URL: " + resolvedURL + ", " + result.statusCode)
        }

    def openGETConnection(model: XFormsModel, resolvedURL: String): ConnectionResult =
        (new Connection).open(
            NetUtils.getExternalContext,
            model.indentedLogger,
            BaseSubmission.isLogBody,
            Connection.Method.GET.name,
            URLFactory.createURL(resolvedURL),
            null, null, null, null,
            XFormsProperties.getForwardSubmissionHeaders(model.containingDocument))
}
