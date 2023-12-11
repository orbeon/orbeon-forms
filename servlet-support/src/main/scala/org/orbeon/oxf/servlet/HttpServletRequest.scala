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

import java.io.BufferedReader
import java.{util => ju}

object HttpServletRequest {
  def fromAnyRef(httpServletRequest: AnyRef): HttpServletRequest =
    httpServletRequest match {
      case httpServletRequest: javax.servlet.http.HttpServletRequest   =>
        HttpServletRequest(httpServletRequest)
      case httpServletRequest: jakarta.servlet.http.HttpServletRequest =>
        HttpServletRequest(httpServletRequest)
      case _ =>
        throw new IllegalArgumentException(s"Unsupported HTTP servlet request: ${httpServletRequest.getClass.getName}")
    }

  def apply(httpServletRequest: javax.servlet.http.HttpServletRequest): JavaxHttpServletRequest     = new JavaxHttpServletRequest(httpServletRequest)
  def apply(httpServletRequest: jakarta.servlet.http.HttpServletRequest): JakartaHttpServletRequest = new JakartaHttpServletRequest(httpServletRequest)
}

trait HttpServletRequest extends ServletRequest {
  def getAttribute(name: String): AnyRef
  def getAttributeNames: ju.Enumeration[String]
  def getAuthType: String
  def getCharacterEncoding: String
  def getContentLength: Int
  def getContentType: String
  def getContextPath: String
  def getCookies: Array[Cookie]
  def getDateHeader(name: String): Long
  def getHeader(name: String): String
  def getHeaderNames: ju.Enumeration[String]
  def getHeaders(name: String): ju.Enumeration[String]
  def getInputStream: ServletInputStream
  def getIntHeader(name: String): Int
  def getLocalName: String
  def getLocale: ju.Locale
  def getLocales: ju.Enumeration[ju.Locale]
  def getMethod: String
  def getParameter(name: String): String
  def getParameterNames: ju.Enumeration[String]
  def getParameterMap: ju.Map[String, Array[String]]
  def getParameterValues(name: String): Array[String]
  def getPathInfo: String
  def getPathTranslated: String
  def getProtocol: String
  def getQueryString: String
  def getReader: BufferedReader
  def getRealPath(path: String): String
  def getRemoteAddr: String
  def getRemoteHost: String
  def getRemoteUser(): String
  def getRequestDispatcher(path: String): RequestDispatcher
  def getRequestURI: String
  def getRequestURL: StringBuffer
  def getRequestedSessionId: String
  def getScheme: String
  def getServerName: String
  def getServerPort: Int
  def getServletContext: ServletContext
  def getServletPath: String
  def getSession(create: Boolean): HttpSession
  def isRequestedSessionIdValid: Boolean
  def isSecure: Boolean
  def isUserInRole(role: String): Boolean
  def removeAttribute(name: String): Unit
  def setAttribute(name: String, o: AnyRef): Unit
  def setCharacterEncoding(env: String): Unit
}

class JavaxHttpServletRequest(httpServletRequest: javax.servlet.http.HttpServletRequest) extends HttpServletRequest {
  override def getNativeServletRequest: javax.servlet.http.HttpServletRequest = httpServletRequest

  override def getAttribute(name: String): AnyRef = httpServletRequest.getAttribute(name)
  override def getAttributeNames: ju.Enumeration[String] = httpServletRequest.getAttributeNames
  override def getAuthType: String = httpServletRequest.getAuthType
  override def getCharacterEncoding: String = httpServletRequest.getCharacterEncoding
  override def getContentLength: Int = httpServletRequest.getContentLength
  override def getContentType: String = httpServletRequest.getContentType
  override def getContextPath: String = httpServletRequest.getContextPath
  override def getCookies: Array[Cookie] = httpServletRequest.getCookies.map(Cookie(_))
  override def getDateHeader(name: String): Long = httpServletRequest.getDateHeader(name)
  override def getHeader(name: String): String = httpServletRequest.getHeader(name)
  override def getHeaderNames: ju.Enumeration[String] = httpServletRequest.getHeaderNames
  override def getHeaders(name: String): ju.Enumeration[String] = httpServletRequest.getHeaders(name)
  override def getInputStream: ServletInputStream = ServletInputStream(httpServletRequest.getInputStream)
  override def getIntHeader(name: String): Int = httpServletRequest.getIntHeader(name)
  override def getLocalName: String = httpServletRequest.getLocalName
  override def getLocale: ju.Locale = httpServletRequest.getLocale
  override def getLocales: ju.Enumeration[ju.Locale] = httpServletRequest.getLocales
  override def getMethod: String = httpServletRequest.getMethod
  override def getParameter(name: String): String = httpServletRequest.getParameter(name)
  override def getParameterNames: ju.Enumeration[String] = httpServletRequest.getParameterNames
  override def getParameterMap: ju.Map[String, Array[String]] = httpServletRequest.getParameterMap
  override def getParameterValues(name: String): Array[String] = httpServletRequest.getParameterValues(name)
  override def getPathInfo: String = httpServletRequest.getPathInfo
  override def getPathTranslated: String = httpServletRequest.getPathTranslated
  override def getProtocol: String = httpServletRequest.getProtocol
  override def getQueryString: String = httpServletRequest.getQueryString
  override def getReader: BufferedReader = httpServletRequest.getReader
  override def getRealPath(path: String): String = httpServletRequest.getRealPath(path)
  override def getRemoteAddr: String = httpServletRequest.getRemoteAddr
  override def getRemoteHost: String = httpServletRequest.getRemoteHost
  override def getRemoteUser(): String = httpServletRequest.getRemoteUser
  override def getRequestDispatcher(path: String): RequestDispatcher = RequestDispatcher(httpServletRequest.getRequestDispatcher(path))
  override def getRequestURI: String = httpServletRequest.getRequestURI
  override def getRequestURL: StringBuffer = httpServletRequest.getRequestURL
  override def getRequestedSessionId: String = httpServletRequest.getRequestedSessionId
  override def getScheme: String = httpServletRequest.getScheme
  override def getServerName: String = httpServletRequest.getServerName
  override def getServerPort: Int = httpServletRequest.getServerPort
  override def getServletContext: ServletContext = ServletContext(httpServletRequest.getServletContext)
  override def getServletPath: String = httpServletRequest.getServletPath
  override def getSession(create: Boolean): HttpSession = HttpSession(httpServletRequest.getSession(create))
  override def isRequestedSessionIdValid: Boolean = httpServletRequest.isRequestedSessionIdValid
  override def isSecure: Boolean = httpServletRequest.isSecure
  override def isUserInRole(role: String): Boolean = httpServletRequest.isUserInRole(role)
  override def removeAttribute(name: String): Unit = httpServletRequest.removeAttribute(name)
  override def setAttribute(name: String, o: AnyRef): Unit = httpServletRequest.setAttribute(name, o)
  override def setCharacterEncoding(env: String): Unit = httpServletRequest.setCharacterEncoding(env)
}
class JakartaHttpServletRequest(val httpServletRequest: jakarta.servlet.http.HttpServletRequest) extends HttpServletRequest {
  override def getNativeServletRequest: jakarta.servlet.http.HttpServletRequest = httpServletRequest

  override def getAttribute(name: String): AnyRef = httpServletRequest.getAttribute(name)
  override def getAttributeNames: ju.Enumeration[String] = httpServletRequest.getAttributeNames
  override def getAuthType: String = httpServletRequest.getAuthType
  override def getCharacterEncoding: String = httpServletRequest.getCharacterEncoding
  override def getContentLength: Int = httpServletRequest.getContentLength
  override def getContentType: String = httpServletRequest.getContentType
  override def getContextPath: String = httpServletRequest.getContextPath
  override def getCookies: Array[Cookie] = httpServletRequest.getCookies.map(Cookie(_))
  override def getDateHeader(name: String): Long = httpServletRequest.getDateHeader(name)
  override def getHeader(name: String): String = httpServletRequest.getHeader(name)
  override def getHeaderNames: ju.Enumeration[String] = httpServletRequest.getHeaderNames
  override def getHeaders(name: String): ju.Enumeration[String] = httpServletRequest.getHeaders(name)
  override def getInputStream: ServletInputStream = ServletInputStream(httpServletRequest.getInputStream)
  override def getIntHeader(name: String): Int = httpServletRequest.getIntHeader(name)
  override def getLocalName: String = httpServletRequest.getLocalName
  override def getLocale: ju.Locale = httpServletRequest.getLocale
  override def getLocales: ju.Enumeration[ju.Locale] = httpServletRequest.getLocales
  override def getMethod: String = httpServletRequest.getMethod
  override def getParameter(name: String): String = httpServletRequest.getParameter(name)
  override def getParameterNames: ju.Enumeration[String] = httpServletRequest.getParameterNames
  override def getParameterMap: ju.Map[String, Array[String]] = httpServletRequest.getParameterMap
  override def getParameterValues(name: String): Array[String] = httpServletRequest.getParameterValues(name)
  override def getPathInfo: String = httpServletRequest.getPathInfo
  override def getPathTranslated: String = httpServletRequest.getPathTranslated
  override def getProtocol: String = httpServletRequest.getProtocol
  override def getQueryString: String = httpServletRequest.getQueryString
  override def getReader: BufferedReader = httpServletRequest.getReader
  override def getRealPath(path: String): String = httpServletRequest.getRealPath(path)
  override def getRemoteAddr: String = httpServletRequest.getRemoteAddr
  override def getRemoteHost: String = httpServletRequest.getRemoteHost
  override def getRemoteUser(): String = httpServletRequest.getRemoteUser
  override def getRequestDispatcher(path: String): RequestDispatcher = RequestDispatcher(httpServletRequest.getRequestDispatcher(path))
  override def getRequestURI: String = httpServletRequest.getRequestURI
  override def getRequestURL: StringBuffer = httpServletRequest.getRequestURL
  override def getRequestedSessionId: String = httpServletRequest.getRequestedSessionId
  override def getScheme: String = httpServletRequest.getScheme
  override def getServerName: String = httpServletRequest.getServerName
  override def getServerPort: Int = httpServletRequest.getServerPort
  override def getServletContext: ServletContext = ServletContext(httpServletRequest.getServletContext)
  override def getServletPath: String = httpServletRequest.getServletPath
  override def getSession(create: Boolean): HttpSession = HttpSession(httpServletRequest.getSession(create))
  override def isRequestedSessionIdValid: Boolean = httpServletRequest.isRequestedSessionIdValid
  override def isSecure: Boolean = httpServletRequest.isSecure
  override def isUserInRole(role: String): Boolean = httpServletRequest.isUserInRole(role)
  override def removeAttribute(name: String): Unit = httpServletRequest.removeAttribute(name)
  override def setAttribute(name: String, o: AnyRef): Unit = httpServletRequest.setAttribute(name, o)
  override def setCharacterEncoding(env: String): Unit = httpServletRequest.setCharacterEncoding(env)
}
