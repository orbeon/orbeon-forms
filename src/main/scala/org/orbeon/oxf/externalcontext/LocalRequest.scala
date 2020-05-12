/**
 * Copyright (C) 2010 Orbeon, Inc.
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

import java.{util => ju}

import org.apache.commons.io.IOUtils
import org.orbeon.oxf.externalcontext.ExternalContext.Request
import org.orbeon.oxf.http.HttpMethod.POST
import org.orbeon.oxf.http._
import org.orbeon.oxf.util.CollectionUtils._
import org.orbeon.io.IOUtils._
import org.orbeon.oxf.util.PathUtils._
import org.orbeon.oxf.util._

import scala.collection.JavaConverters._
import scala.collection.mutable

// Request used for local (within Orbeon Forms) requests.
//
// Used by:
//
// - InternalHttpClient
// - RequestDispatcherSubmission
//
class LocalRequest(
  incomingRequest         : Request,
  contextPath             : String,
  pathQuery               : String,
  method                  : HttpMethod,
  headersMaybeCapitalized : Map[String, List[String]],
  content                 : Option[StreamedContent]
) extends Request {

  private val _contentLengthOpt = content flatMap (_.contentLength)
  private val _contentTypeOpt   = content flatMap (_.contentType)

  private val _headersIncludingAuthBodyLowercase = {

    def requestHeadersIt =
      headersMaybeCapitalized.iterator map { case (k, v) => k.toLowerCase -> v.toArray }

    def credentialsHeadersIt =
      incomingRequest.credentials match {
        case Some(credentials) => Credentials.toHeaders(credentials).iterator
        case None              => Iterator.empty
      }

    def bodyHeadersIt =
      if (Connection.requiresRequestBody(method)) {
        (_contentLengthOpt.iterator map (value => Headers.ContentLengthLower -> Array(value.toString))) ++
        (_contentTypeOpt.iterator   map (value => Headers.ContentTypeLower   -> Array(value)))
      } else
        Iterator.empty

    def allHeadersLowercaseIt =
      requestHeadersIt     ++
      credentialsHeadersIt ++
      bodyHeadersIt

    allHeadersLowercaseIt.toMap.asJava
  }

  private val (_pathInfo, _queryString) =
    splitQuery(pathQuery)

  private lazy val _queryAndBodyParameters = {
    // Query string
    // SRV.4.1: "Query string data is presented before post body data."
    def queryParameters = Option(getQueryString) map decodeSimpleQuery getOrElse Seq()

    // POST body form parameters
    // SRV.4.1.1 When Parameters Are Available
    // NOTE: Remember, the servlet container does not help us decoding the body: the "other side" will just end up here
    // when asking for parameters.
    def bodyParameters =
      if (method == POST)
        content collect {
          case StreamedContent(is, Some("application/x-www-form-urlencoded"), _, _) =>
            useAndClose(is) { is =>
              decodeSimpleQuery(IOUtils.toString(is, ExternalContext.StandardFormCharacterEncoding))
            }
        }
      else
        None

    // Make sure to keep order
    mutable.LinkedHashMap() ++ combineValues[String, AnyRef, Array](queryParameters ++ bodyParameters.getOrElse(Nil)) asJava
  }

  /* SUPPORTED: methods called by ExternalContextToHttpServletRequestWrapper */

  def getMethod            = method
  def getParameterMap      = _queryAndBodyParameters
  def getQueryString       = _queryString.orNull
  def getCharacterEncoding = null;//TODO? // not used by our code
  def getContentLength     = _contentLengthOpt map (_.toInt) getOrElse -1
  def getContentType       = _contentTypeOpt.orNull
  def getInputStream       = content map (_.inputStream) getOrElse EmptyInputStream
  def getHeaderValuesMap   = _headersIncludingAuthBodyLowercase

  // 2013-09-10: We should start with a fresh attributes map:
  //
  //     ju.Collections.synchronizedMap(new ju.HashMap[String, AnyRef])
  //
  // However, upon forwarding via RequestDispatcher, this doesn't work. It's unclear why, as Tomcat stores special
  // attributes in its own ApplicationHttpRequest.specialAttributes. So for now we keep the forward, which was in
  // place before the 2013-09-10 refactor anyway.
  //
  // 2016-06-09: If we remove support for RequestDispatcherSubmission with #2809, we can probably fix this.
  //
  lazy val getAttributesMap = {

    val newMap = new ju.HashMap[String, AnyRef]

    newMap.asScala ++= incomingRequest.getAttributesMap.asScala filter {
      case (k, _) => k.startsWith("javax.servlet.")
    }

    ju.Collections.synchronizedMap(newMap)
  }

  /*
  * NOTE: All the path methods are handled by the request dispatcher implementation in the servlet container upon
  * forward, but upon include we must provide them.
  *
  * NOTE: Checked 2009-02-12 that none of the methods below are called when forwarding through
  * spring/JSP/filter/Orbeon in Tomcat 5.5.27. HOWEVER they are called when including.
  *
  * NOTE: 2014-09-22: Checked that getServletPath and getPathInfo are called by JspServlet in tomcat-7.0.47 at least.
  */
  def getPathInfo = _pathInfo
  def getServletPath = ""
  def getContextPath = contextPath // return the context path passed to this wrapper

  def getRequestPath: String = {
    // Get servlet path and path info
    val servletPath = Option(getServletPath) getOrElse ""
    val pathInfo    = Option(getPathInfo)    getOrElse ""

    // Concatenate servlet path and path info, avoiding a double slash
    val requestPath =
      if (servletPath.endsWith("/") && pathInfo.startsWith("/"))
        servletPath + pathInfo.substring(1)
      else
        servletPath + pathInfo

    // Add starting slash if missing
    requestPath.prependSlash
  }

  def getRequestURI: String = {
    // Must return the path including the context
    val contextPath = getContextPath
    if (contextPath == "/")
      getRequestPath
    else
      getContextPath + getRequestPath
  }

  // 2014-09-10: Only used by XHTMLToPDFProcessor
  def getRequestURL: String = {
    // Get absolute URL w/o query string e.g. http://foo.com/a/b/c
    val incomingRequestURL = incomingRequest.getRequestURL
    // Resolving request URI against incoming absolute URL, e.g. /d/e/f -> http://foo.com/d/e/f
    NetUtils.resolveURI(getRequestURI, incomingRequestURL)
  }

  // ==== Properties which are delegated =============================================================================

  // TODO: Check against ExternalContextToHttpServletRequestWrapper

  // Container is preserved
  def getContainerType                        = incomingRequest.getContainerType
  def getContainerNamespace                   = incomingRequest.getContainerNamespace

  def getPortletMode                          = incomingRequest.getPortletMode    // submission does not change portlet mode
  def getWindowState                          = incomingRequest.getWindowState    // submission does not change window state

  def getNativeRequest                        = incomingRequest.getNativeRequest  // should not have mainstream uses; see RequestDispatcherSubmission, and cookies forwarding
  def getPathTranslated                       = incomingRequest.getPathTranslated // should really not be called

  // Client and server are preserved, assuming all those relate to knowledge about URL rewriting
  def getProtocol                             = incomingRequest.getProtocol
  def getServerPort                           = incomingRequest.getServerPort
  def getScheme                               = incomingRequest.getScheme
  def getRemoteHost                           = incomingRequest.getRemoteHost
  def getRemoteAddr                           = incomingRequest.getRemoteAddr
  def isSecure                                = incomingRequest.isSecure
  def getLocale                               = incomingRequest.getLocale
  def getLocales                              = incomingRequest.getLocales

  def getServerName                           = incomingRequest.getServerName
  def getClientContextPath(urlString: String) = incomingRequest.getClientContextPath(urlString)

  // Session information is preserved
  def isRequestedSessionIdValid               = incomingRequest.isRequestedSessionIdValid
  def sessionInvalidate()                     = incomingRequest.sessionInvalidate()
  def getSession(create: Boolean)             = incomingRequest.getSession(create)
  def getRequestedSessionId                   = incomingRequest.getRequestedSessionId

  // User information is preserved
  def credentials                             = incomingRequest.credentials
  def isUserInRole(role: String)              = incomingRequest.isUserInRole(role)

  def getAuthType                             = incomingRequest.getAuthType
}
