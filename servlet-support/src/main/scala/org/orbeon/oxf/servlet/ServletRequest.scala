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
  def getNativeServletRequest: javax.servlet.ServletRequest = servletRequest

  def getAttribute(name: String): AnyRef = servletRequest.getAttribute(name)
  def getAttributeNames: ju.Enumeration[String] = servletRequest.getAttributeNames
  def getCharacterEncoding: String = servletRequest.getCharacterEncoding
  def getContentLength: Int = servletRequest.getContentLength
  def getContentType: String = servletRequest.getContentType
  def getInputStream: InputStream = servletRequest.getInputStream
  def getLocalName: String = servletRequest.getLocalName
  def getLocale: ju.Locale = servletRequest.getLocale
  def getLocales: ju.Enumeration[ju.Locale] = servletRequest.getLocales
  def getParameter(name: String): String = servletRequest.getParameter(name)
  def getParameterMap: ju.Map[String, Array[String]] = servletRequest.getParameterMap
  def getParameterNames: ju.Enumeration[String] = servletRequest.getParameterNames
  def getParameterValues(name: String): Array[String] = servletRequest.getParameterValues(name)
  def getProtocol: String = servletRequest.getProtocol
  def getReader: BufferedReader = servletRequest.getReader
  def getRemoteAddr: String = servletRequest.getRemoteAddr
  def getRemoteHost: String = servletRequest.getRemoteHost
  def getRequestDispatcher(path: String): RequestDispatcher = RequestDispatcher(servletRequest.getRequestDispatcher(path))
  def getScheme: String = servletRequest.getScheme
  def getServerName: String = servletRequest.getServerName
  def getServerPort: Int = servletRequest.getServerPort
  def getServletContext: ServletContext = ServletContext(servletRequest.getServletContext)
  def isSecure: Boolean = servletRequest.isSecure
  def removeAttribute(name: String): Unit = servletRequest.removeAttribute(name)
  def setAttribute(name: String, o: AnyRef): Unit = servletRequest.setAttribute(name, o)
  def setCharacterEncoding(env: String): Unit = servletRequest.setCharacterEncoding(env)
}

class JakartaServletRequest(servletRequest: jakarta.servlet.ServletRequest) extends ServletRequest {
  def getNativeServletRequest: jakarta.servlet.ServletRequest = servletRequest

  def getAttribute(name: String): AnyRef = servletRequest.getAttribute(name)
  def getAttributeNames: ju.Enumeration[String] = servletRequest.getAttributeNames
  def getCharacterEncoding: String = servletRequest.getCharacterEncoding
  def getContentLength: Int = servletRequest.getContentLength
  def getContentType: String = servletRequest.getContentType
  def getInputStream: InputStream = servletRequest.getInputStream
  def getLocalName: String = servletRequest.getLocalName
  def getLocale: ju.Locale = servletRequest.getLocale
  def getLocales: ju.Enumeration[ju.Locale] = servletRequest.getLocales
  def getParameter(name: String): String = servletRequest.getParameter(name)
  def getParameterMap: ju.Map[String, Array[String]] = servletRequest.getParameterMap
  def getParameterNames: ju.Enumeration[String] = servletRequest.getParameterNames
  def getParameterValues(name: String): Array[String] = servletRequest.getParameterValues(name)
  def getProtocol: String = servletRequest.getProtocol
  def getReader: BufferedReader = servletRequest.getReader
  def getRemoteAddr: String = servletRequest.getRemoteAddr
  def getRemoteHost: String = servletRequest.getRemoteHost
  def getRequestDispatcher(path: String): RequestDispatcher = RequestDispatcher(servletRequest.getRequestDispatcher(path))
  def getScheme: String = servletRequest.getScheme
  def getServerName: String = servletRequest.getServerName
  def getServerPort: Int = servletRequest.getServerPort
  def getServletContext: ServletContext = ServletContext(servletRequest.getServletContext)
  def isSecure: Boolean = servletRequest.isSecure
  def removeAttribute(name: String): Unit = servletRequest.removeAttribute(name)
  def setAttribute(name: String, o: AnyRef): Unit = servletRequest.setAttribute(name, o)
  def setCharacterEncoding(env: String): Unit = servletRequest.setCharacterEncoding(env)
}