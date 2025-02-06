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

import org.apache.commons.io.IOUtils
import org.orbeon.connection.{StreamedContent, StreamedContentT}
import org.orbeon.io.IOUtils.*
import org.orbeon.oxf.externalcontext.ExternalContext.{Request, Session}
import org.orbeon.oxf.http.*
import org.orbeon.oxf.http.HttpMethod.{HttpMethodsWithRequestBody, POST}
import org.orbeon.oxf.util.*
import org.orbeon.oxf.util.CollectionUtils.*
import org.orbeon.oxf.util.PathUtils.*

import java.io.InputStream
import java.util as ju
import scala.collection.mutable
import scala.jdk.CollectionConverters.*


// Request used for local (within Orbeon Forms) requests.
//
// Used by:
//
// - InternalHttpClient
// - tests
//
class LocalRequest private (

  val getContainerType: String,
  val getContainerNamespace: String,

  val getPathInfo: String,
  val getContextPath: String,
  val getServletPath: String,

  val getAttributesMap: ju.Map[String, AnyRef],
  val getHeaderValuesMap: ju.Map[String, Array[String]],
  val getParameterMap: ju.Map[String, Array[AnyRef]],
  val incomingCookies: Iterable[(String, String)],

  val getContentLength: Int,
  val getContentType: String,
  val getInputStream: InputStream,

  val getProtocol: String,
  val getRemoteHost: String,
  val getRemoteAddr: String,
  val getScheme: String,
  val getMethod: HttpMethod,
  val getServerName: String,
  val getServerPort: Int,

  val isRequestedSessionIdValid: Boolean,
  val getRequestedSessionId: String,

  val getAuthType: String,
  val isSecure: Boolean,

  val credentials: Option[Credentials],

  val getLocale: ju.Locale,
  val getLocales: ju.Enumeration[ju.Locale],

  val getPathTranslated: String,
  val getQueryString: String,

  val getPortletMode: String,
  val getWindowState: String,
)(
  val _incomingRequestURL: String,
  val _sessionOpt: Option[Session],
) extends Request {

  def getCharacterEncoding: String = null // not used by our code
  def getNativeRequest: AnyRef     = null // should only be used for cookies forwarding

  def getSession(create: Boolean): Session = _sessionOpt.getOrElse {
    if (create)
      throw new UnsupportedOperationException("`getSession(true)` not supported in `LocalRequest` if session is absent")
    else
      null
  }

  def sessionInvalidate(): Unit =
    if (_sessionOpt.isDefined)
      throw new UnsupportedOperationException("`sessionInvalidate(true)` not supported in `LocalRequest` if session is present")

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

  // Must return the path including the context
  def getRequestURI: String =
    if (getContextPath == "/")
      getRequestPath
    else
      getContextPath + getRequestPath

  // 2014-09-10: Only used by `XHTMLToPDFProcessor`
  // Get absolute URL w/o query string e.g. `http://foo.com/a/b/c`
  // Resolving request URI against incoming absolute URL, e.g. `/d/e/f` -> `http://foo.com/d/e/f`
  def getRequestURL: String =
    NetUtils.resolveURI(getRequestURI, _incomingRequestURL)

  def isUserInRole(role: String): Boolean =
    credentials.exists(_.roles.exists(_.roleName == role))

  private lazy val platformClientContextPath: String =
    URLRewriterUtils.getClientContextPath(this, true)

  private lazy val applicationClientContextPath: String =
    URLRewriterUtils.getClientContextPath(this, false)

  def getClientContextPath(urlString: String): String =
    if (URLRewriterUtils.isPlatformPath(urlString))
      platformClientContextPath
    else
      applicationClientContextPath

  lazy val servicePrefix: String =
    URLRewriterImpl.rewriteServiceUrl(
      this,
      "/",
      UrlRewriteMode.Absolute,
      URLRewriterUtils.getServiceBaseURI
    )
}

object LocalRequest {

  def apply(
    externalContext        : ExternalContext,
    pathQuery              : String,
    method                 : HttpMethod,
    headersMaybeCapitalized: Map[String, List[String]],
    content                : Option[StreamedContent]
  ): LocalRequest =
    apply(
      safeRequestCtx          = SafeRequestContext(externalContext),
      pathQuery               = pathQuery,
      method                  = method,
      headersMaybeCapitalized = headersMaybeCapitalized,
      content                 = content
    )

  def apply(
    safeRequestCtx         : SafeRequestContext,
    pathQuery              : String,
    method                 : HttpMethod,
    headersMaybeCapitalized: Map[String, List[String]],
    content                : Option[StreamedContent]
  ): LocalRequest = {

    val (pathInfo, queryStringOpt) = splitQuery(pathQuery)

    val contentLengthOpt = content.flatMap(_.contentLength)
    val contentTypeOpt   = content.flatMap(_.contentType)

    val headersIncludingAuthBodyLowercase = {

      def requestHeadersIt =
        headersMaybeCapitalized.iterator map { case (k, v) => k.toLowerCase -> v.toArray }

      def credentialsHeadersIt =
        safeRequestCtx.credentials match {
          case Some(credentials) => CredentialsSerializer.toHeaders[Array](credentials).iterator
          case None              => Iterator.empty
        }

      def bodyHeadersIt =
        if (HttpMethodsWithRequestBody(method)) {
          (contentLengthOpt.iterator map (value => Headers.ContentLengthLower -> Array(value.toString))) ++
          (contentTypeOpt.iterator   map (value => Headers.ContentTypeLower   -> Array(value)))
        } else
          Iterator.empty

      def allHeadersLowercaseIt =
        requestHeadersIt     ++
        credentialsHeadersIt ++
        bodyHeadersIt

      allHeadersLowercaseIt.toMap.asJava
    }

    // See https://github.com/orbeon/orbeon-forms/issues/5081
    val attributesMap =
      ju.Collections.synchronizedMap(new ju.HashMap[String, AnyRef](safeRequestCtx.attributes.asJava))

    val queryAndBodyParameters = {
      // Query string
      // SRV.4.1: "Query string data is presented before post body data."
      def queryParameters = queryStringOpt.map(decodeSimpleQuery).getOrElse(Nil)

      // POST body form parameters
      // SRV.4.1.1 When Parameters Are Available
      // NOTE: Remember, the servlet container does not help us decoding the body: the "other side" will just end up here
      // when asking for parameters.
      def bodyParameters =
        if (method == POST)
          content collect {
            case StreamedContentT(is, Some("application/x-www-form-urlencoded"), _, _) =>
              useAndClose(is) { is =>
                decodeSimpleQuery(IOUtils.toString(is, ExternalContext.StandardFormCharacterEncoding))
              }
          }
        else
          None

      // Make sure to keep order
      (mutable.LinkedHashMap() ++ combineValues[String, AnyRef, Array](queryParameters ++ bodyParameters.getOrElse(Nil))).asJava
    }

    new LocalRequest(

      getContainerType          = safeRequestCtx.containerType,
      getContainerNamespace     = safeRequestCtx.containerNamespace,

      getPathInfo               = pathInfo,
      getContextPath            = safeRequestCtx.contextPath,
      getServletPath            = "",

      getAttributesMap          = attributesMap,
      getHeaderValuesMap        = headersIncludingAuthBodyLowercase,
      getParameterMap           = queryAndBodyParameters,
      incomingCookies           = safeRequestCtx.cookies,

      getContentLength          = contentLengthOpt map (_.toInt) getOrElse -1,
      getContentType            = contentTypeOpt.orNull,
      getInputStream            = content map (_.stream) getOrElse EmptyInputStream,

      // Client and server are preserved, assuming all those relate to knowledge about URL rewriting
      getProtocol               = safeRequestCtx.other.protocol,
      getRemoteHost             = safeRequestCtx.other.remoteHost,
      getRemoteAddr             = safeRequestCtx.other.remoteAddr,
      getScheme                 = safeRequestCtx.scheme,
      getMethod                 = method,
      getServerName             = safeRequestCtx.serverName,
      getServerPort             = safeRequestCtx.serverPort,

      isRequestedSessionIdValid = safeRequestCtx.other.requestedSessionIdValid,
      getRequestedSessionId     = safeRequestCtx.requestedSessionId.orNull,

      getAuthType               = safeRequestCtx.other.authType,
      isSecure                  = safeRequestCtx.other.secure,

      credentials               = safeRequestCtx.credentials,

      getLocale                 = safeRequestCtx.other.locale,
      getLocales                = safeRequestCtx.other.locales.iterator.asJavaEnumeration, // xxx should store collection, then iterate anytime getLocales is called

      getPathTranslated         = safeRequestCtx.other.pathTranslated, // should really not be used
      getQueryString            = queryStringOpt.orNull,

      getPortletMode            = safeRequestCtx.other.portletMode,
      getWindowState            = safeRequestCtx.other.windowState,
    )(
      _incomingRequestURL       = safeRequestCtx.other.requestURL,
      _sessionOpt               = safeRequestCtx.findSessionOrThrow(create = false)
    )
  }
}
