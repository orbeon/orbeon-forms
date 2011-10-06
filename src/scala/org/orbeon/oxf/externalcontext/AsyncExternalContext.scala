/**
 * Copyright (C) 2011 Orbeon, Inc.
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
package org.orbeon.oxf.externalcontext

import org.orbeon.oxf.pipeline.api.ExternalContext
import java.lang.{Throwable, String}
import org.orbeon.oxf.pipeline.api.ExternalContext.{Response, Application}
import org.orbeon.oxf.pipeline.api.ExternalContext.Application.ApplicationListener
import org.orbeon.oxf.util.URLRewriterUtils

// This context copies all the values of the given request. It uses the original session.
class AsyncExternalContext(request: AsyncRequest, response: Response) extends ExternalContext {

    val getRequest = request
    val getResponse = response
    def getSession(create: Boolean) = getRequest.getSession(create)

    def getRealPath(path: String) = throw new UnsupportedOperationException

    def getStartLoggerString = getRequest.getRequestPath + " - Received request";
    def getEndLoggerString = getRequest.getRequestPath

    def rewriteServiceURL(urlString: String, rewriteMode: Int) =
        URLRewriterUtils.rewriteServiceURL(getRequest, urlString, rewriteMode)

    def log(msg: String) = throw new UnsupportedOperationException
    def log(message: String, throwable: Throwable) = throw new UnsupportedOperationException

    def getInitAttributesMap = throw new UnsupportedOperationException
    def getAttributesMap = throw new UnsupportedOperationException

    def getApplication = new Application {
        def removeListener(applicationListener: ApplicationListener) = throw new UnsupportedOperationException
        def addListener(applicationListener: ApplicationListener) = throw new UnsupportedOperationException
    }

    def getRequestDispatcher(path: String, isContextRelative: Boolean) = throw new UnsupportedOperationException
    def getNativeRequest = null
    def getNativeResponse = null
    def getNativeContext = throw new UnsupportedOperationException
}