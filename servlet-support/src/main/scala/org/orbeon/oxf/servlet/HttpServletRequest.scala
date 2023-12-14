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
  def getAuthType: String
  def getContextPath: String
  def getCookies: Array[Cookie]
  def getDateHeader(name: String): Long
  def getHeader(name: String): String
  def getHeaderNames: ju.Enumeration[String]
  def getHeaders(name: String): ju.Enumeration[String]
  def getIntHeader(name: String): Int
  def getMethod: String
  def getPathInfo: String
  def getPathTranslated: String
  def getQueryString: String
  def getRemoteUser(): String
  def getRequestURI: String
  def getRequestURL: StringBuffer
  def getRequestedSessionId: String
  def getServletPath: String
  def getSession(create: Boolean): HttpSession
  def isRequestedSessionIdValid: Boolean
  def isUserInRole(role: String): Boolean
}

class JavaxHttpServletRequest(httpServletRequest: javax.servlet.http.HttpServletRequest) extends JavaxServletRequest(httpServletRequest) with HttpServletRequest {
  override def getNativeServletRequest: javax.servlet.http.HttpServletRequest = httpServletRequest

  override def getAuthType: String = httpServletRequest.getAuthType
  override def getContextPath: String = httpServletRequest.getContextPath
  override def getCookies: Array[Cookie] = httpServletRequest.getCookies.map(Cookie(_))
  override def getDateHeader(name: String): Long = httpServletRequest.getDateHeader(name)
  override def getHeader(name: String): String = httpServletRequest.getHeader(name)
  override def getHeaderNames: ju.Enumeration[String] = httpServletRequest.getHeaderNames
  override def getHeaders(name: String): ju.Enumeration[String] = httpServletRequest.getHeaders(name)
  override def getIntHeader(name: String): Int = httpServletRequest.getIntHeader(name)
  override def getMethod: String = httpServletRequest.getMethod
  override def getPathInfo: String = httpServletRequest.getPathInfo
  override def getPathTranslated: String = httpServletRequest.getPathTranslated
  override def getQueryString: String = httpServletRequest.getQueryString
  // Keep parentheses for UserRolesFacade
  override def getRemoteUser(): String = httpServletRequest.getRemoteUser
  override def getRequestURI: String = httpServletRequest.getRequestURI
  override def getRequestURL: StringBuffer = httpServletRequest.getRequestURL
  override def getRequestedSessionId: String = httpServletRequest.getRequestedSessionId
  override def getServletPath: String = httpServletRequest.getServletPath
  override def getSession(create: Boolean): HttpSession = HttpSession(httpServletRequest.getSession(create))
  override def isRequestedSessionIdValid: Boolean = httpServletRequest.isRequestedSessionIdValid
  override def isUserInRole(role: String): Boolean = httpServletRequest.isUserInRole(role)
}
class JakartaHttpServletRequest(val httpServletRequest: jakarta.servlet.http.HttpServletRequest) extends JakartaServletRequest(httpServletRequest) with HttpServletRequest {
  override def getNativeServletRequest: jakarta.servlet.http.HttpServletRequest = httpServletRequest

  override def getAuthType: String = httpServletRequest.getAuthType
  override def getContextPath: String = httpServletRequest.getContextPath
  override def getCookies: Array[Cookie] = httpServletRequest.getCookies.map(Cookie(_))
  override def getDateHeader(name: String): Long = httpServletRequest.getDateHeader(name)
  override def getHeader(name: String): String = httpServletRequest.getHeader(name)
  override def getHeaderNames: ju.Enumeration[String] = httpServletRequest.getHeaderNames
  override def getHeaders(name: String): ju.Enumeration[String] = httpServletRequest.getHeaders(name)
  override def getIntHeader(name: String): Int = httpServletRequest.getIntHeader(name)
  override def getMethod: String = httpServletRequest.getMethod
  override def getPathInfo: String = httpServletRequest.getPathInfo
  override def getPathTranslated: String = httpServletRequest.getPathTranslated
  override def getQueryString: String = httpServletRequest.getQueryString
  // Keep parentheses for UserRolesFacade
  override def getRemoteUser(): String = httpServletRequest.getRemoteUser
  override def getRequestURI: String = httpServletRequest.getRequestURI
  override def getRequestURL: StringBuffer = httpServletRequest.getRequestURL
  override def getRequestedSessionId: String = httpServletRequest.getRequestedSessionId
  override def getServletPath: String = httpServletRequest.getServletPath
  override def getSession(create: Boolean): HttpSession = HttpSession(httpServletRequest.getSession(create))
  override def isRequestedSessionIdValid: Boolean = httpServletRequest.isRequestedSessionIdValid
  override def isUserInRole(role: String): Boolean = httpServletRequest.isUserInRole(role)
}
