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
import java.util as ju


object ServletRequest {
  def apply(servletRequest: javax.servlet.ServletRequest): ServletRequest =
    servletRequest match {
      case httpServletRequest: javax.servlet.http.HttpServletRequest =>
        new JavaxHttpServletRequest(httpServletRequest)
      case _ =>
        new JavaxServletRequest(servletRequest)
    }

  def apply(servletRequest: jakarta.servlet.ServletRequest): ServletRequest =
    servletRequest match {
      case httpServletRequest: jakarta.servlet.http.HttpServletRequest =>
        new JakartaHttpServletRequest(httpServletRequest)
      case _ =>
        new JakartaServletRequest(servletRequest)
    }
}

trait ServletRequest {
  // javax/jakarta.servlet.ServletRequest
  def getNativeServletRequest: AnyRef

  val ForwardContextPath: String
  val IncludeServletPath: String
  val IncludePathInfo: String
  val IncludeContextPath: String
  val IncludeQueryString: String
  val IncludeRequestUri: String
  def getAttribute(name: String): AnyRef
  def getAttributeNames: ju.Enumeration[String]
  def getCharacterEncoding: String
  def getContentLength: Int
  def getContentType: String
  // Return an InputStream here instead of a ServletInputStream wrapper to avoid runtime problems with structural types.
  // We've had weird problems with IOUtils.useAndClose (which uses { def close(): Unit }) trying to call the wrong class
  // in the context of WildFly (but not Tomcat), i.e. looking for a javax.* class when it should be looking for a
  // jakarta.* class and vice versa.
  def getInputStream: InputStream
  def getLocalName: String
  def getLocale: ju.Locale
  def getLocales: ju.Enumeration[ju.Locale]
  def getParameter(name: String): String
  def getParameterMap: ju.Map[String, Array[String]]
  def getParameterNames: ju.Enumeration[String]
  def getParameterValues(name: String): Array[String]
  def getProtocol: String
  def getReader: BufferedReader
  def getRemoteAddr: String
  def getRemoteHost: String
  def getRequestDispatcher(path: String): RequestDispatcher
  def getScheme: String
  def getServerName: String
  def getServerPort: Int
  def getServletContext: ServletContext
  def isSecure: Boolean
  def removeAttribute(name: String): Unit
  def setAttribute(name: String, o: AnyRef): Unit
  def setCharacterEncoding(env: String): Unit
}

class JavaxServletRequest(servletRequest: javax.servlet.ServletRequest) extends ServletRequest {
  override def getNativeServletRequest: javax.servlet.ServletRequest = servletRequest

  override val ForwardContextPath: String = javax.servlet.RequestDispatcher.FORWARD_CONTEXT_PATH
  override val IncludeServletPath: String = javax.servlet.RequestDispatcher.INCLUDE_SERVLET_PATH
  override val IncludePathInfo: String = javax.servlet.RequestDispatcher.INCLUDE_PATH_INFO
  override val IncludeContextPath: String = javax.servlet.RequestDispatcher.INCLUDE_CONTEXT_PATH
  override val IncludeQueryString: String = javax.servlet.RequestDispatcher.INCLUDE_QUERY_STRING
  override val IncludeRequestUri: String = javax.servlet.RequestDispatcher.INCLUDE_REQUEST_URI
  override def getAttribute(name: String): AnyRef = servletRequest.getAttribute(name)
  override def getAttributeNames: ju.Enumeration[String] = servletRequest.getAttributeNames
  override def getCharacterEncoding: String = servletRequest.getCharacterEncoding
  override def getContentLength: Int = servletRequest.getContentLength
  override def getContentType: String = servletRequest.getContentType
  override def getInputStream: InputStream = servletRequest.getInputStream
  override def getLocalName: String = servletRequest.getLocalName
  override def getLocale: ju.Locale = servletRequest.getLocale
  override def getLocales: ju.Enumeration[ju.Locale] = servletRequest.getLocales
  override def getParameter(name: String): String = servletRequest.getParameter(name)
  override def getParameterMap: ju.Map[String, Array[String]] = servletRequest.getParameterMap
  override def getParameterNames: ju.Enumeration[String] = servletRequest.getParameterNames
  override def getParameterValues(name: String): Array[String] = servletRequest.getParameterValues(name)
  override def getProtocol: String = servletRequest.getProtocol
  override def getReader: BufferedReader = servletRequest.getReader
  override def getRemoteAddr: String = servletRequest.getRemoteAddr
  override def getRemoteHost: String = servletRequest.getRemoteHost
  override def getRequestDispatcher(path: String): RequestDispatcher = RequestDispatcher(servletRequest.getRequestDispatcher(path))
  override def getScheme: String = servletRequest.getScheme
  override def getServerName: String = servletRequest.getServerName
  override def getServerPort: Int = servletRequest.getServerPort
  override def getServletContext: ServletContext = ServletContext(servletRequest.getServletContext)
  override def isSecure: Boolean = servletRequest.isSecure
  override def removeAttribute(name: String): Unit = servletRequest.removeAttribute(name)
  override def setAttribute(name: String, o: AnyRef): Unit = servletRequest.setAttribute(name, o)
  override def setCharacterEncoding(env: String): Unit = servletRequest.setCharacterEncoding(env)
}

class JakartaServletRequest(servletRequest: jakarta.servlet.ServletRequest) extends ServletRequest {
  override def getNativeServletRequest: jakarta.servlet.ServletRequest = servletRequest

  override val ForwardContextPath: String = jakarta.servlet.RequestDispatcher.FORWARD_CONTEXT_PATH
  override val IncludeServletPath: String = jakarta.servlet.RequestDispatcher.INCLUDE_SERVLET_PATH
  override val IncludePathInfo: String = jakarta.servlet.RequestDispatcher.INCLUDE_PATH_INFO
  override val IncludeContextPath: String = jakarta.servlet.RequestDispatcher.INCLUDE_CONTEXT_PATH
  override val IncludeQueryString: String = jakarta.servlet.RequestDispatcher.INCLUDE_QUERY_STRING
  override val IncludeRequestUri: String = jakarta.servlet.RequestDispatcher.INCLUDE_REQUEST_URI
  override def getAttribute(name: String): AnyRef = servletRequest.getAttribute(name)
  override def getAttributeNames: ju.Enumeration[String] = servletRequest.getAttributeNames
  override def getCharacterEncoding: String = servletRequest.getCharacterEncoding
  override def getContentLength: Int = servletRequest.getContentLength
  override def getContentType: String = servletRequest.getContentType
  override def getInputStream: InputStream = servletRequest.getInputStream
  override def getLocalName: String = servletRequest.getLocalName
  override def getLocale: ju.Locale = servletRequest.getLocale
  override def getLocales: ju.Enumeration[ju.Locale] = servletRequest.getLocales
  override def getParameter(name: String): String = servletRequest.getParameter(name)
  override def getParameterMap: ju.Map[String, Array[String]] = servletRequest.getParameterMap
  override def getParameterNames: ju.Enumeration[String] = servletRequest.getParameterNames
  override def getParameterValues(name: String): Array[String] = servletRequest.getParameterValues(name)
  override def getProtocol: String = servletRequest.getProtocol
  override def getReader: BufferedReader = servletRequest.getReader
  override def getRemoteAddr: String = servletRequest.getRemoteAddr
  override def getRemoteHost: String = servletRequest.getRemoteHost
  override def getRequestDispatcher(path: String): RequestDispatcher = RequestDispatcher(servletRequest.getRequestDispatcher(path))
  override def getScheme: String = servletRequest.getScheme
  override def getServerName: String = servletRequest.getServerName
  override def getServerPort: Int = servletRequest.getServerPort
  override def getServletContext: ServletContext = ServletContext(servletRequest.getServletContext)
  override def isSecure: Boolean = servletRequest.isSecure
  override def removeAttribute(name: String): Unit = servletRequest.removeAttribute(name)
  override def setAttribute(name: String, o: AnyRef): Unit = servletRequest.setAttribute(name, o)
  override def setCharacterEncoding(env: String): Unit = servletRequest.setCharacterEncoding(env)
}