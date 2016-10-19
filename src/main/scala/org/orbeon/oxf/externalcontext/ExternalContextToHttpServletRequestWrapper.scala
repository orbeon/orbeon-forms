/**
 * Copyright (C) 2016 Orbeon, Inc.
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

import java.io._
import java.security.Principal
import java.{util ⇒ ju}
import javax.servlet.http.{Cookie, HttpServletRequest, HttpServletRequestWrapper, HttpSession}
import javax.servlet.{RequestDispatcher, ServletInputStream}

import org.orbeon.oxf.util.{DateUtils, NetUtils, StringConversions}
import org.orbeon.oxf.webapp.ExternalContext

/**
 * Wrap an ExternalContext.Request into an HttpServletRequest.
 *
 * This is intended to "simulate" request information upon servlet forward/include. Information supported is:
 *
 * - request method
 * - request path and query string
 * - request parameters
 * - request body
 * - request attributes
 * - request headers
 *
 * Often, an HttpServletRequestWrapper is used to forward within the same application, and in this case developers have
 * good control over all the aspects of the application.
 *
 * Here, we need to make it look to the recipient that the request is as close as possible as a new incoming HTTP
 * request. The difficulty is to decide what to delegate to the servlet container, and what to get from the incoming
 * request object passed to the constructor.
 */
class ExternalContextToHttpServletRequestWrapper(
  request   : ExternalContext.Request,
  isForward : Boolean
) extends HttpServletRequestWrapper(
  request.getNativeRequest.asInstanceOf[HttpServletRequest]
) {

  private var servletInputStream: ServletInputStream = null

  /*
   * SUPPORTED: request method
   *
   * An obvious one: we want the recipient to see a GET or a POST, for example.
   */

  override def getMethod: String = request.getMethod

  /*
   * SPECIAL: derived path information
   *
   * Upon forward, Tomcat in particular constructs contextPath, pathInfo, servletPath, and requestURI based on the
   * path of the resource to which the request is forwarded. So we don't need to rebuild them.
   *
   * It makes sense that the servlet container fixes them up, otherwise there would be no need for the properties
   * listed in SRV.8.3.1 and SRV.8.4.2.
   *
   * HOWEVER, this is NOT the case for includes. Tomcat does not have the same code for includes, and the spec does
   * not mention path adjustments for includes.
   *
   * NOTE: We handle the query string separately.
   */

  override def getContextPath: String = if (isForward) super.getContextPath else request.getContextPath
  override def getPathInfo   : String = if (isForward) super.getPathInfo    else request.getPathInfo
  override def getServletPath: String = if (isForward) super.getServletPath else request.getServletPath
  override def getRequestURI : String = if (isForward) super.getRequestURI  else request.getRequestURI

  /*
   * SUPPORTED: request path and query string
   *
   * Paths are automatically computed by container (see below), except maybe for getRequestURL().
   */

  override def getRequestURL: StringBuffer = {
    // NOTE: By default, super will likely return the original request URL. Here we simulate a new one.

    // Get absolute URL w/o query string e.g. http://foo.com/a/b/c
    val incomingRequestURL = super.getRequestURL
    // Resolving request URI against incoming absolute URL, e.g. /d/e/f -> http://foo.com/d/e/f
    new StringBuffer(NetUtils.resolveURI(getRequestURI, incomingRequestURL.toString))
  }

  /* SUPPORTED: query string and request parameters */

  override def getQueryString: String =
    NetUtils.encodeQueryString(request.getParameterMap)

  override def getParameter(name: String): String = {
    val values = getParameterValues(name)
    if (values eq null) null else values(0)
  }

  override def getParameterMap: ju.Map[_, _] =
    request.getParameterMap

  override def getParameterNames: ju.Enumeration[_] =
    ju.Collections.enumeration(request.getParameterMap.keySet)

  // Convert as parameters MAY contain FileItem values
  override def getParameterValues(name: String): Array[String] =
    StringConversions.objectArrayToStringArray(request.getParameterMap.get(name))

  /* SUPPORTED: request body */

  override def getCharacterEncoding: String = request.getCharacterEncoding
  override def getContentLength    : Int    = request.getContentLength
  override def getContentType      : String = request.getContentType

  @throws[IOException]
  override def getInputStream: ServletInputStream = {
    if (servletInputStream eq null) {
      val is = request.getInputStream
      servletInputStream = new ServletInputStream() {
        @throws[IOException]
        def read = is.read
      }
    }
    servletInputStream
  }

  @throws[IOException]
  override def getReader: BufferedReader =
    // NOTE: Not sure why reader can be null, but a user reported that it can happen so returning null if that's the case
    request.getReader match {
      case reader: BufferedReader ⇒ reader
      case reader: Reader         ⇒ new BufferedReader(reader)
      case _                      ⇒ null
    }

  @throws[UnsupportedEncodingException]
  override def setCharacterEncoding(encoding: String): Unit = {
    // TODO: Request does not support setCharacterEncoding()
    //super.setCharacterEncoding(encoding);
  }

  /* SUPPORTED: request attributes */

  override def getAttribute(name: String)           : AnyRef            = request.getAttributesMap.get(name)
  override def getAttributeNames                    : ju.Enumeration[_] = ju.Collections.enumeration(request.getAttributesMap.keySet)
  override def removeAttribute(name: String)        : Unit              = request.getAttributesMap.remove(name)
  override def setAttribute(name: String, o: AnyRef): Unit              = request.getAttributesMap.put(name, o)

  /* SUPPORTED: request headers */

  override def getDateHeader(name: String): Long = {
    val value = getHeader(name)
    if (value eq null)
      return -1L

    // Attempt to convert the date header in a variety of formats
    val result = DateUtils.parseRFC1123(value)
    if (result != -1L)
      return result

    throw new IllegalArgumentException(value)
  }

  override def getHeader(name: String): String = {
    val values = request.getHeaderValuesMap.get(name)
    if (values eq null) null else values(0)
  }

  override def getHeaderNames: ju.Enumeration[_] =
    ju.Collections.enumeration(request.getHeaderValuesMap.keySet)

  override def getHeaders(name: String): ju.Enumeration[_] = {
    val values = request.getHeaderValuesMap.get(name)
    ju.Collections.enumeration(
      if (values ne null) ju.Arrays.asList(values) else ju.Collections.emptyList[Any]
    )
  }

  override def getIntHeader(name: String): Int = {
    val value = getHeader(name)
    if (value eq null) -1 else value.toInt
  }

  /*
   * DELEGATED: other path information
   */

  override def getRealPath(path: String)     : String            = super.getRealPath(path)
  override def getPathTranslated             : String            = super.getPathTranslated

  /* DELEGATED: authentication methods */

  override def getAuthType                   : String            = super.getAuthType
  override def getRemoteUser                 : String            = super.getRemoteUser
  override def getUserPrincipal              : Principal         = super.getUserPrincipal
  override def isUserInRole(role: String)    : Boolean           = super.isUserInRole(role)

  /*
   * DELEGATED: session handling
   *
   * We know for a fact that session handling fails with Tomcat with cross-context forwards if we don't use the
   * superclass's session methods.
   */

  override def getRequestedSessionId         : String            = super.getRequestedSessionId
  override def getSession                    : HttpSession       = super.getSession
  override def getSession(b: Boolean)        : HttpSession       = super.getSession(b)
  override def isRequestedSessionIdFromCookie: Boolean           = super.isRequestedSessionIdFromCookie
  override def isRequestedSessionIdFromURL   : Boolean           = super.isRequestedSessionIdFromURL
  override def isRequestedSessionIdFromUrl   : Boolean           = super.isRequestedSessionIdFromURL
  override def isRequestedSessionIdValid     : Boolean           = super.isRequestedSessionIdValid

  /*
   * DELEGATED: other client information
   */

  override def getLocale                     : ju.Locale         = super.getLocale
  override def getLocales                    : ju.Enumeration[_] = super.getLocales
  override def getCookies                    : Array[Cookie]     = super.getCookies

  /*
   * DELEGATED: remote host
   *
   * NOTE: Could also somehow use the local host's information, probably not worth it
   */

  override def getRemoteAddr                 : String            = super.getRemoteAddr
  override def getRemoteHost                 : String            = super.getRemoteHost

  /*
   * DELEGATED: other protocol-level information
   *
   * NOTE: Could also somehow use the local host's information, probably not worth it
   */

  override def getProtocol                   : String            = super.getProtocol
  override def isSecure                      : Boolean           = super.isSecure
  override def getScheme                     : String            = super.getScheme

  /*
   * DELEGATED: local server information
   */

  override def getServerName                 : String            = super.getServerName
  override def getServerPort                 : Int               = super.getServerPort

  /* DELEGATED: request dispatcher */

  override def getRequestDispatcher(path: String): RequestDispatcher = super.getRequestDispatcher(path)
}