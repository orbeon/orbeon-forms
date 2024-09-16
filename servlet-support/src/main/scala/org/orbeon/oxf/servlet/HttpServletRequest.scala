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

import org.orbeon.oxf.util.PathUtils._
import cats.data.NonEmptyList

import java.io.BufferedReader
import java.net.URI
import java.{util => ju}
import scala.jdk.CollectionConverters._


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

  // Prefer higher-level methods further below
  def getHeaderNames: ju.Enumeration[String]
  def getHeader(name: String): String
  def getHeaders(name: String): ju.Enumeration[String]
  def getIntHeader(name: String): Int
  def getDateHeader(name: String): Long

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

  lazy val headerNamesWithValues: List[(String, NonEmptyList[String])] =
    getHeaderNames.asScala
      .map { headerName => headerName -> NonEmptyList.fromList(getHeaders(headerName).asScala.toList) }
      .collect { case (name, Some(valueNel)) => name -> valueNel } // value list *should* be non-empty in the first place
      .toList

  def headerFirstValueOpt(name: String): Option[String] = headerValuesNel(name).map(_.head)
  def headerValuesList(name: String): List[String] = headerValuesNel(name).map(_.toList).getOrElse(Nil)

  // `getHeaders()` is supposed to be case-insensitive, but it might not be the case in some setups that use faulty
  // custom filters. Here we try to be case-insensitive by first finding the header name ignoring case, and then
  // getting the headers for that name using the exact case of the header name found.
  def headerValuesNel(name: String): Option[NonEmptyList[String]] =
    getHeaderNames.asScala
      .collectFirst { case headerName if headerName.equalsIgnoreCase(name) => NonEmptyList.fromList(getHeaders(headerName).asScala.toList) }
      .collect { case Some(valueNel) => valueNel }

  def headersAsString: String = {
    headerNamesWithValues.flatMap { case (headerName, headerValues) =>
      headerValues.toList.map { value =>
        s"$headerName: $value"
      }
    } mkString "\n"
  }

  // javax/jakarta.servlet.http.HttpServletRequestWrapper
  def wrappedWith(wrapper: HttpServletRequestWrapper): AnyRef

  /**
   * Return a request path info that looks like what one would expect. The path starts with a "/", relative to the
   * servlet context. If the servlet was included or forwarded to, return the path by which the *current* servlet was
   * invoked, NOT the path of the calling servlet.
   *
   * Request path = servlet path + path info.
   *
   * @param request servlet HTTP request
   * @return path
   */
  def getRequestPathInfo: String = {

    // NOTE: Servlet 2.4 spec says: "These attributes [javax.servlet.include.*] are accessible from the included
    // servlet via the getAttribute method on the request object and their values must be equal to the request URI,
    // context path, servlet path, path info, and query string of the included servlet, respectively."
    // NOTE: This is very different from the similarly-named forward attributes, which reflect the values of the
    // first servlet in the chain!

    val servletPath =
      Option(getAttribute("javax.servlet.include.servlet_path").asInstanceOf[String])
        .orElse(Option(getServletPath))
        .getOrElse("")

    val pathInfo =
      Option(getAttribute("javax.servlet.include.path_info").asInstanceOf[String])
        .orElse(Option(getPathInfo))
        .getOrElse("")

    val requestPath =
      if (servletPath.endsWith("/") && pathInfo.startsWith("/"))
        servletPath + pathInfo.dropStartingSlash
      else
        servletPath + pathInfo

    requestPath.prependSlash
  }

  def isOptions: Boolean   = Option(getMethod).exists(_.toUpperCase == "OPTIONS")
  def isFont: Boolean      = hasFileExtension(Set("otf", "ttf", "woff", "woff2"))
  def isSourceMap: Boolean = hasFileExtension(Set("map"))

  private def hasFileExtension(extensions: Set[String]): Boolean =
    (for {
      url  <- Option(getRequestURL)
      path <- Option(URI.create(url.toString).getPath)
    } yield extensions.exists(ext => path.endsWith(s".$ext"))).getOrElse(false)
}

class JavaxHttpServletRequest(httpServletRequest: javax.servlet.http.HttpServletRequest) extends JavaxServletRequest(httpServletRequest) with HttpServletRequest {
  override def getNativeServletRequest: javax.servlet.http.HttpServletRequest = httpServletRequest

  override def getAuthType: String = httpServletRequest.getAuthType
  override def getContextPath: String = httpServletRequest.getContextPath
  override def getCookies: Array[Cookie] =
    Option(httpServletRequest.getCookies)
      .map(_.map(c => Cookie(c): Cookie))
      .getOrElse(Array.empty[Cookie])
  override def getHeaderNames: ju.Enumeration[String] = httpServletRequest.getHeaderNames
  override def getHeader(name: String): String = httpServletRequest.getHeader(name)
  override def getHeaders(name: String): ju.Enumeration[String] = httpServletRequest.getHeaders(name)
  override def getIntHeader(name: String): Int = httpServletRequest.getIntHeader(name)
  override def getDateHeader(name: String): Long = httpServletRequest.getDateHeader(name)
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
  // #6019: make sure we return null if the native session is null
  override def getSession(create: Boolean): HttpSession = Option(httpServletRequest.getSession(create)).map(HttpSession.apply).orNull
  override def isRequestedSessionIdValid: Boolean = httpServletRequest.isRequestedSessionIdValid
  override def isUserInRole(role: String): Boolean = httpServletRequest.isUserInRole(role)

  override def wrappedWith(wrapper: HttpServletRequestWrapper): javax.servlet.http.HttpServletRequestWrapper =
    new javax.servlet.http.HttpServletRequestWrapper(httpServletRequest) {
      // Handle mutable behavior
      override def setRequest(servletRequest: javax.servlet.ServletRequest): Unit = {
        super.setRequest(servletRequest)
        servletRequest match {
          case httpServletRequest: javax.servlet.http.HttpServletRequest =>
            wrapper.request = HttpServletRequest(httpServletRequest)
        }
      }

      override def getAttribute(name: String): AnyRef = wrapper.getAttribute(name)
      override def getAttributeNames: ju.Enumeration[String] = wrapper.getAttributeNames
      override def getAuthType: String = wrapper.getAuthType
      override def getCharacterEncoding: String = wrapper.getCharacterEncoding
      override def getContentLength: Int = wrapper.getContentLength
      override def getContentType: String = wrapper.getContentType
      override def getContextPath: String = wrapper.getContextPath
      override def getCookies: Array[javax.servlet.http.Cookie] =
        Option(wrapper.getCookies)
          .map(_.map(c => c.getNativeCookie.asInstanceOf[javax.servlet.http.Cookie]))
          .getOrElse(Array.empty[javax.servlet.http.Cookie])
      override def getHeaderNames: ju.Enumeration[String] = wrapper.getHeaderNames
      override def getHeader(name: String): String = wrapper.getHeader(name)
      override def getHeaders(name: String): ju.Enumeration[String] = wrapper.getHeaders(name)
      override def getIntHeader(name: String): Int = wrapper.getIntHeader(name)
      override def getDateHeader(name: String): Long = wrapper.getDateHeader(name)
      // See comment in ServletRequest trait
      override def getInputStream: javax.servlet.ServletInputStream = wrapper.getInputStream.asInstanceOf[javax.servlet.ServletInputStream]
      override def getLocalName: String = wrapper.getLocalName
      override def getLocale: ju.Locale = wrapper.getLocale
      override def getLocales: ju.Enumeration[ju.Locale] = wrapper.getLocales
      override def getMethod: String = wrapper.getMethod
      override def getParameter(name: String): String = wrapper.getParameter(name)
      override def getParameterNames: ju.Enumeration[String] = wrapper.getParameterNames
      override def getParameterMap: ju.Map[String, Array[String]] = wrapper.getParameterMap
      override def getParameterValues(name: String): Array[String] = wrapper.getParameterValues(name)
      override def getPathInfo: String = wrapper.getPathInfo
      override def getPathTranslated: String = wrapper.getPathTranslated
      override def getProtocol: String = wrapper.getProtocol
      override def getQueryString: String = wrapper.getQueryString
      override def getReader: BufferedReader = wrapper.getReader
      override def getRemoteAddr: String = wrapper.getRemoteAddr
      override def getRemoteHost: String = wrapper.getRemoteHost
      override def getRemoteUser: String = wrapper.getRemoteUser()
      override def getRequestDispatcher(path: String): javax.servlet.RequestDispatcher = wrapper.getRequestDispatcher(path).getNativeRequestDispatcher.asInstanceOf[javax.servlet.RequestDispatcher]
      override def getRequestURI: String = wrapper.getRequestURI
      override def getRequestURL: StringBuffer = wrapper.getRequestURL
      override def getRequestedSessionId: String = wrapper.getRequestedSessionId
      override def getScheme: String = wrapper.getScheme
      override def getServerName: String = wrapper.getServerName
      override def getServerPort: Int = wrapper.getServerPort
      override def getServletContext: javax.servlet.ServletContext = wrapper.getServletContext.getNativeServletContext.asInstanceOf[javax.servlet.ServletContext]
      override def getServletPath: String = wrapper.getServletPath
      override def getSession(create: Boolean): javax.servlet.http.HttpSession = wrapper.getSession(create).getNativeHttpSession.asInstanceOf[javax.servlet.http.HttpSession]
      override def isRequestedSessionIdValid: Boolean = wrapper.isRequestedSessionIdValid
      override def isSecure: Boolean = wrapper.isSecure
      override def isUserInRole(role: String): Boolean = wrapper.isUserInRole(role)
      override def removeAttribute(name: String): Unit = wrapper.removeAttribute(name)
      override def setAttribute(name: String, o: AnyRef): Unit = wrapper.setAttribute(name, o)
      override def setCharacterEncoding(env: String): Unit = wrapper.setCharacterEncoding(env)
    }
}
class JakartaHttpServletRequest(httpServletRequest: jakarta.servlet.http.HttpServletRequest) extends JakartaServletRequest(httpServletRequest) with HttpServletRequest {
  override def getNativeServletRequest: jakarta.servlet.http.HttpServletRequest = httpServletRequest

  override def getAuthType: String = httpServletRequest.getAuthType
  override def getContextPath: String = httpServletRequest.getContextPath
  override def getCookies: Array[Cookie] =
    Option(httpServletRequest.getCookies)
      .map(_.map(c => Cookie(c): Cookie))
      .getOrElse(Array.empty[Cookie])
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
  // #6019: make sure we return null if the native session is null
  override def getSession(create: Boolean): HttpSession = Option(httpServletRequest.getSession(create)).map(HttpSession.apply).orNull
  override def isRequestedSessionIdValid: Boolean = httpServletRequest.isRequestedSessionIdValid
  override def isUserInRole(role: String): Boolean = httpServletRequest.isUserInRole(role)

  override def wrappedWith(wrapper: HttpServletRequestWrapper): jakarta.servlet.http.HttpServletRequestWrapper =
    new jakarta.servlet.http.HttpServletRequestWrapper(httpServletRequest) {
      // Handle mutable behavior
      override def setRequest(servletRequest: jakarta.servlet.ServletRequest): Unit = {
        super.setRequest(servletRequest)
        servletRequest match {
          case httpServletRequest: jakarta.servlet.http.HttpServletRequest =>
            wrapper.request = HttpServletRequest(httpServletRequest)
        }
      }

      override def getAttribute(name: String): AnyRef = wrapper.getAttribute(name)
      override def getAttributeNames: ju.Enumeration[String] = wrapper.getAttributeNames
      override def getAuthType: String = wrapper.getAuthType
      override def getCharacterEncoding: String = wrapper.getCharacterEncoding
      override def getContentLength: Int = wrapper.getContentLength
      override def getContentType: String = wrapper.getContentType
      override def getContextPath: String = wrapper.getContextPath
      override def getCookies: Array[jakarta.servlet.http.Cookie] =
        Option(wrapper.getCookies)
          .map(_.map(_.getNativeCookie.asInstanceOf[jakarta.servlet.http.Cookie]))
          .getOrElse(Array.empty[jakarta.servlet.http.Cookie])
      override def getDateHeader(name: String): Long = wrapper.getDateHeader(name)
      override def getHeader(name: String): String = wrapper.getHeader(name)
      override def getHeaderNames: ju.Enumeration[String] = wrapper.getHeaderNames
      override def getHeaders(name: String): ju.Enumeration[String] = wrapper.getHeaders(name)
      // See comment in ServletRequest trait
      override def getInputStream: jakarta.servlet.ServletInputStream = wrapper.getInputStream.asInstanceOf[jakarta.servlet.ServletInputStream]
      override def getIntHeader(name: String): Int = wrapper.getIntHeader(name)
      override def getLocalName: String = wrapper.getLocalName
      override def getLocale: ju.Locale = wrapper.getLocale
      override def getLocales: ju.Enumeration[ju.Locale] = wrapper.getLocales
      override def getMethod: String = wrapper.getMethod
      override def getParameter(name: String): String = wrapper.getParameter(name)
      override def getParameterNames: ju.Enumeration[String] = wrapper.getParameterNames
      override def getParameterMap: ju.Map[String, Array[String]] = wrapper.getParameterMap
      override def getParameterValues(name: String): Array[String] = wrapper.getParameterValues(name)
      override def getPathInfo: String = wrapper.getPathInfo
      override def getPathTranslated: String = wrapper.getPathTranslated
      override def getProtocol: String = wrapper.getProtocol
      override def getQueryString: String = wrapper.getQueryString
      override def getReader: BufferedReader = wrapper.getReader
      override def getRemoteAddr: String = wrapper.getRemoteAddr
      override def getRemoteHost: String = wrapper.getRemoteHost
      override def getRemoteUser: String = wrapper.getRemoteUser()
      override def getRequestDispatcher(path: String): jakarta.servlet.RequestDispatcher = wrapper.getRequestDispatcher(path).getNativeRequestDispatcher.asInstanceOf[jakarta.servlet.RequestDispatcher]
      override def getRequestURI: String = wrapper.getRequestURI
      override def getRequestURL: StringBuffer = wrapper.getRequestURL
      override def getRequestedSessionId: String = wrapper.getRequestedSessionId
      override def getScheme: String = wrapper.getScheme
      override def getServerName: String = wrapper.getServerName
      override def getServerPort: Int = wrapper.getServerPort
      override def getServletContext: jakarta.servlet.ServletContext = wrapper.getServletContext.getNativeServletContext.asInstanceOf[jakarta.servlet.ServletContext]
      override def getServletPath: String = wrapper.getServletPath
      override def getSession(create: Boolean): jakarta.servlet.http.HttpSession = wrapper.getSession(create).getNativeHttpSession.asInstanceOf[jakarta.servlet.http.HttpSession]
      override def isRequestedSessionIdValid: Boolean = wrapper.isRequestedSessionIdValid
      override def isSecure: Boolean = wrapper.isSecure
      override def isUserInRole(role: String): Boolean = wrapper.isUserInRole(role)
      override def removeAttribute(name: String): Unit = wrapper.removeAttribute(name)
      override def setAttribute(name: String, o: AnyRef): Unit = wrapper.setAttribute(name, o)
      override def setCharacterEncoding(env: String): Unit = wrapper.setCharacterEncoding(env)
    }
}
