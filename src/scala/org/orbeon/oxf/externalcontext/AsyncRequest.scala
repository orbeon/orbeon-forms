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

import java.lang.String
import org.orbeon.oxf.pipeline.api.ExternalContext.Request
import org.orbeon.oxf.pipeline.api.ExternalContext
import org.orbeon.oxf.util.URLRewriterUtils
import java.util.{Collections, HashMap}

// This request copies all values of the given request
class AsyncRequest(req: Request) extends ExternalContext.Request {

    private val session = req.getSession(true) // assume it's ok to create a session
    private lazy val platformClientContextPath = URLRewriterUtils.getClientContextPath(this, true)
    private lazy val applicationClientContextPath = URLRewriterUtils.getClientContextPath(this, false)

    def getSession(create: Boolean) = session
    def sessionInvalidate() = session.invalidate()

    // TODO: We can't support this w/o knowing a list of roles in advance
    def isUserInRole(role: String) = throw new UnsupportedOperationException

    // TODO
    val getReader = null
    val getInputStream = null

    val getAttributesMap = new HashMap[String, Object](req.getAttributesMap)
    val getHeaderValuesMap = Collections.unmodifiableMap[String, Array[String]](new HashMap[String, Array[String]](req.getHeaderValuesMap))
    val getParameterMap = Collections.unmodifiableMap[String, Array[Object]](new HashMap[String, Array[Object]](req.getParameterMap))

    def getClientContextPath(urlString: String) =
        if (URLRewriterUtils.isPlatformPath(urlString)) platformClientContextPath else applicationClientContextPath

    def getNativeRequest = throw new UnsupportedOperationException

    // Copy all other values right away
    val getWindowState = req.getWindowState
    val getPortletMode = req.getPortletMode
    val getRequestURL = req.getRequestURL
    val getRequestURI = req.getRequestURI
    val getQueryString = req.getQueryString
    val getPathTranslated = req.getPathTranslated
    val getLocales = req.getLocales
    val getLocale = req.getLocale
    val getUserPrincipal = req.getUserPrincipal

    val getRemoteUser = req.getRemoteUser
    val isSecure = req.isSecure
    val getAuthType = req.getAuthType
    val getRequestedSessionId = req.getRequestedSessionId
    val isRequestedSessionIdValid = req.isRequestedSessionIdValid

    val getServerPort = req.getServerPort
    val getServerName = req.getServerName
    val getMethod = req.getMethod
    val getScheme = req.getScheme
    val getRemoteAddr = req.getRemoteAddr
    val getRemoteHost = req.getRemoteHost
    val getProtocol = req.getProtocol

    val getContentType = req.getContentType
    val getContentLength = req.getContentLength
    val getCharacterEncoding = req.getCharacterEncoding

    val getServletPath = req.getServletPath
    val getContextPath = req.getContextPath
    val getRequestPath = req.getRequestPath
    val getPathInfo = req.getPathInfo
    val getContainerNamespace = req.getContainerNamespace
    val getContainerType = req.getContainerType
}