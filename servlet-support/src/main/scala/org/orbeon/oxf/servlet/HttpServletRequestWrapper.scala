/**
 * Copyright (C) 2023 Orbeon, Inc.
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
package org.orbeon.oxf.servlet

import java.io.{BufferedReader, InputStream}
import java.{util => ju}

class HttpServletRequestWrapper(request: HttpServletRequest) extends HttpServletRequest {
  override def getNativeServletRequest: AnyRef = request.getNativeServletRequest
  override def getAttribute(name: String): AnyRef = request.getAttribute(name)
  override def getAttributeNames: ju.Enumeration[String] = request.getAttributeNames
  override def getAuthType: String = request.getAuthType
  override def getCharacterEncoding: String = request.getCharacterEncoding
  override def getContentLength: Int = request.getContentLength
  override def getContentType: String = request.getContentType
  override def getContextPath: String = request.getContextPath
  override def getCookies: Array[Cookie] = request.getCookies
  override def getDateHeader(name: String): Long = request.getDateHeader(name)
  override def getHeader(name: String): String = request.getHeader(name)
  override def getHeaderNames: ju.Enumeration[String] = request.getHeaderNames
  override def getHeaders(name: String): ju.Enumeration[String] = request.getHeaders(name)
  override def getInputStream: InputStream = request.getInputStream
  override def getIntHeader(name: String): Int = request.getIntHeader(name)
  override def getLocalName: String = request.getLocalName
  override def getLocale: ju.Locale = request.getLocale
  override def getLocales: ju.Enumeration[ju.Locale] = request.getLocales
  override def getMethod: String = request.getMethod
  override def getParameter(name: String): String = request.getParameter(name)
  override def getParameterNames: ju.Enumeration[String] = request.getParameterNames
  override def getParameterMap: ju.Map[String, Array[String]] = request.getParameterMap
  override def getParameterValues(name: String): Array[String] = request.getParameterValues(name)
  override def getPathInfo: String = request.getPathInfo
  override def getPathTranslated: String = request.getPathTranslated
  override def getProtocol: String = request.getProtocol
  override def getQueryString: String = request.getQueryString
  override def getReader: BufferedReader = request.getReader
  override def getRemoteAddr: String = request.getRemoteAddr
  override def getRemoteHost: String = request.getRemoteHost
  override def getRemoteUser: String = request.getRemoteUser
  override def getRequestDispatcher(path: String): RequestDispatcher = request.getRequestDispatcher(path)
  override def getRequestURI: String = request.getRequestURI
  override def getRequestURL: StringBuffer = request.getRequestURL
  override def getRequestedSessionId: String = request.getRequestedSessionId
  override def getScheme: String = request.getScheme
  override def getServerName: String = request.getServerName
  override def getServerPort: Int = request.getServerPort
  override def getServletContext: ServletContext = request.getServletContext
  override def getServletPath: String = request.getServletPath
  override def getSession(create: Boolean): HttpSession = request.getSession(create)
  override def isRequestedSessionIdValid: Boolean = request.isRequestedSessionIdValid
  override def isSecure: Boolean = request.isSecure
  override def isUserInRole(role: String): Boolean = request.isUserInRole(role)
  override def removeAttribute(name: String): Unit = request.removeAttribute(name)
  override def setAttribute(name: String, o: AnyRef): Unit = request.setAttribute(name, o)
  override def setCharacterEncoding(env: String): Unit = request.setCharacterEncoding(env)
}
