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
package org.orbeon.oxf.servlet

import org.orbeon.oxf.externalcontext._
import org.orbeon.oxf.http.{Headers, HttpMethod, StatusCode}
import org.orbeon.oxf.pipeline.InitUtils
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.properties.Properties
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util._
import org.orbeon.oxf.webapp.ServletSupport

import java.io._
import java.{util => ju}
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import scala.jdk.CollectionConverters._


/*
 * Servlet-specific implementation of ExternalContext.
 */
object ServletExternalContext {

  val Logger = LoggerFactory.createLogger(classOf[ServletExternalContext])

  private val HttpPageCacheHeadersDefault      = "Cache-Control: private, max-age=0; Pragma:"
  private val HttpPageCacheHeadersProperty     = "oxf.http.page.cache-headers"
  private val HttpResourceCacheHeadersDefault  = "Cache-Control: public; Pragma:"
  private val HttpResourceCacheHeadersProperty = "oxf.http.resource.cache-headers"
  private val HttpNocacheCacheHeadersDefault   = "Cache-Control: no-cache, no-store, must-revalidate; Pragma: no-cache; Expires: 0"
  private val HttpNocacheCacheHeadersProperty  = "oxf.http.nocache.cache-headers"

  lazy val pageCacheHeaders     = decodeCacheString(HttpPageCacheHeadersProperty,     HttpPageCacheHeadersDefault)
  lazy val resourceCacheHeaders = decodeCacheString(HttpResourceCacheHeadersProperty, HttpResourceCacheHeadersDefault)
  lazy val nocacheCacheHeaders  = decodeCacheString(HttpNocacheCacheHeadersProperty,  HttpNocacheCacheHeadersDefault)

  private def decodeCacheString(name: String, defaultValue: String): List[(String, String)] =
    for {
      header <- Properties.instance.getPropertySet.getString(name, defaultValue).splitTo[List](sep = ";")
      parts = header.splitTo[List](sep = ":")
      if parts.size == 2
      name :: value :: Nil = parts
    } yield
      name.trimAllToEmpty -> value.trimAllToEmpty
}

class ServletExternalContext(
  val pipelineContext : PipelineContext,
  val webAppContext   : WebAppContext,
  val nativeRequest   : HttpServletRequest,
  val nativeResponse  : HttpServletResponse
) extends ExternalContext {

  private class RequestImpl extends ExternalContext.Request with ServletPortletRequest {

    private var getParameterMapMultipartFormDataCalled = false
    private var getInputStreamCalled                   = false
    private var inputStreamCharsetOpt: Option[String]  = None

    // Delegate to underlying request
    def getPathInfo                = nativeRequest.getPathInfo
    def getRemoteAddr              = nativeRequest.getRemoteAddr
    def getAuthType                = nativeRequest.getAuthType
    def isSecure                   = nativeRequest.isSecure
    def getContentLength           = nativeRequest.getContentLength
    def getContentType             = nativeRequest.getContentType
    def getServerName              = nativeRequest.getServerName
    def getServerPort              = nativeRequest.getServerPort
    def getMethod                  = HttpMethod.withNameInsensitive(nativeRequest.getMethod)
    def getProtocol                = nativeRequest.getProtocol
    def getRemoteHost              = nativeRequest.getRemoteHost
    def getScheme                  = nativeRequest.getScheme
    def getPathTranslated          = nativeRequest.getPathTranslated
    def getRequestedSessionId      = nativeRequest.getRequestedSessionId
    def getServletPath             = nativeRequest.getServletPath
    def getLocale                  = nativeRequest.getLocale
    def getLocales                 = nativeRequest.getLocales
    def isRequestedSessionIdValid  = nativeRequest.isRequestedSessionIdValid

    def getContainerType           = "servlet"
    def getContainerNamespace      = getResponse.getNamespacePrefix

    private def servletIncludeAttributeOpt(name: String) =
      Option(nativeRequest.getAttribute(s"javax.servlet.include.$name").asInstanceOf[String])

    // NOTE: Servlet 2.4 spec says: "These attributes [javax.servlet.include.*] are accessible from the
    // included servlet via the getAttribute method on the request object and their values must be equal to
    // the request URI, context path, servlet path, path info, and query string of the included servlet,
    // respectively."
    // NOTE: This is very different from the similarly-named forward attributes, which reflect the values of the
    // first servlet in the chain!
    lazy val getContextPath: String =
      servletIncludeAttributeOpt("context_path") getOrElse nativeRequest.getContextPath

    // Use included / forwarded servlet's value
    // NOTE: Servlet 2.4 spec says: "These attributes [javax.servlet.include.*] are accessible from the
    // included servlet via the getAttribute method on the request object and their values must be equal to the
    // request URI, context path, servlet path, path info, and query string of the included servlet,
    // respectively."
    // NOTE: This is very different from the similarly-named forward attributes, which reflect the values of the
    // first servlet in the chain!
    lazy val getQueryString: String =
      servletIncludeAttributeOpt("query_string") getOrElse nativeRequest.getQueryString

    // Use included / forwarded servlet's value
    // NOTE: Servlet 2.4 spec says: "These attributes [javax.servlet.include.*] are accessible from the
    // included servlet via the getAttribute method on the request object and their values must be equal to the
    // request URI, context path, servlet path, path info, and query string of the included servlet,
    // respectively."
    // NOTE: This is very different from the similarly-named forward attributes, which reflect the values of the
    // first servlet in the chain!
    lazy val getRequestURI: String =
      servletIncludeAttributeOpt("request_uri") getOrElse nativeRequest.getRequestURI

    lazy val getRequestPath = ServletSupport.getRequestPathInfo(nativeRequest)

    lazy val getAttributesMap: ju.Map[String, AnyRef] = new InitUtils.RequestMap(nativeRequest)

    // NOTE: Normalize names to lowercase to ensure consistency between servlet containers
    protected[ServletExternalContext] lazy val headerValuesMap: Map[String, Array[String]] = (
      for (name <- nativeRequest.getHeaderNames.asScala)
        yield name.toLowerCase -> StringConversions.stringEnumerationToArray(nativeRequest.getHeaders(name))
    ).toMap

    def getHeaderValuesMap = headerValuesMap.asJava

    lazy val getParameterMap: ju.Map[String, Array[AnyRef]] = {

      // Only handle the `multipart/form-data"` case, as for `application/x-www-form-urlencoded` the servlet container
      // exposes parameters with getParameter*() methods (see SRV.4.1.1).
      val multipartParameterMap: Map[String, Array[AnyRef]] =
        if ((getContentType ne null) && getContentType.startsWith("multipart/form-data")) {
          if (getInputStreamCalled)
            throw new IllegalStateException(
              s"Cannot call `getParameterMap` after `getInputStream` when a form was posted with `multipart/form-data`"
            )
          // Decode the multipart data
          val result = Multipart.getParameterMapMultipartJava(pipelineContext, getRequest, ExternalContext.StandardHeaderCharacterEncoding)
          // Remember that we were called, so we can display a meaningful exception if getInputStream() is called after this
          getParameterMapMultipartFormDataCalled = true
          result.asScala.toMap
        } else {
          // Set the input character encoding before getting the stream as this can cause issues with Jetty
          // TODO: should we also do this in the `multipart/form-data` case?
          handleInputEncoding()
          Map.empty[String, Array[AnyRef]]
        }

      val nativeRequestParameterMap =
        nativeRequest.getParameterNames.asScala.map(name => {
          name -> nativeRequest.getParameterValues(name).asInstanceOf[Array[AnyRef]]
        }).toMap

      (nativeRequestParameterMap ++ multipartParameterMap).asJava
    }

    def getSession(create: Boolean): ExternalContext.Session =
      ServletExternalContext.this.getSession(create)

    def sessionInvalidate(): Unit = {
      val session = nativeRequest.getSession(false)
      if (session ne null)
        session.invalidate()
    }

    def getCharacterEncoding: String =
      inputStreamCharsetOpt getOrElse nativeRequest.getCharacterEncoding

    lazy val getRequestURL: String = {
      // NOTE: If this is included from a portlet, we may not have a request URL
      val requestUrl = nativeRequest.getRequestURL
      // TODO: check if we should return null or "" or sth else
      if (requestUrl ne null)
        requestUrl.toString
      else
        null
    }

    def getInputStream: InputStream = {
      if (getParameterMapMultipartFormDataCalled)
        throw new IllegalStateException(
          s"Cannot call `getInputStream` after `getParameterMap` when a form was posted with `multipart/form-data`"
        )

      // Set the input character encoding before getting the stream as this can cause issues with Jetty
      handleInputEncoding()

      // Remember that we were called, so we can display a meaningful exception if getParameterMap() is called after this
      getInputStreamCalled = true
      nativeRequest.getInputStream
    }

    def getPortletMode = null
    def getWindowState = null

    def getNativeRequest = nativeRequest

    private def handleInputEncoding(): Unit =
      if (! getInputStreamCalled)
        inputStreamCharsetOpt = Option(
          Option(nativeRequest.getCharacterEncoding) match {
            case Some(requestCharacterEncoding) =>
              requestCharacterEncoding
            case None =>
              nativeRequest.setCharacterEncoding(ExternalContext.StandardFormCharacterEncoding)
              ExternalContext.StandardFormCharacterEncoding
          }
        )
  }

  private class ResponseImpl(urlRewriter: URLRewriter) extends ExternalContext.Response with CachingResponseSupport {

    // Delegate to underlying response
    def getOutputStream                        = nativeResponse.getOutputStream
    def getWriter                              = nativeResponse.getWriter
    def isCommitted                            = nativeResponse.isCommitted
    def reset()                                = nativeResponse.reset()
    def setContentType(contentType: String)    = nativeResponse.setContentType(contentType)
    def setHeader(name: String, value: String) = nativeResponse.setHeader(name, value)
    def addHeader(name: String, value: String) = nativeResponse.addHeader(name, value)
    def setContentLength(len: Int)             = nativeResponse.setContentLength(len)
    def sendError(code: Int)                   = nativeResponse.sendError(code)

    // We assume below that `nativeResponse.getCharacterEncoding` reflects the encoding set with
    // `nativeResponse.setContentType` if any.
    def getCharacterEncoding: String =
      Option(nativeResponse.getCharacterEncoding) getOrElse
        ExternalContext.StandardCharacterEncoding

    def setStatus(status: Int): Unit = {
      // If anybody ever sets a non-success status code, we disable caching of the output. This covers the
      // following scenario:
      //
      // - request with If-Modified-Since arrives and causes PFC to run
      // - oxf:http-serializer runs and sees pipeline NOT cacheable so reads the input
      // - during execution of pipeline, HttpStatusCodeException is thrown
      // - PFC catches it and calls setStatus()
      // - error, not found, or unauthorized pipeline runs
      // - oxf:http-serializer runs and sees pipeline IS cacheable so sends a 403
      // - client sees wrong result!
      if (!StatusCode.isSuccessCode(status))
        responseCachingDisabled = true

      nativeResponse.setStatus(status)
    }

    def sendRedirect(location: String, isServerSide: Boolean, isExitPortal: Boolean): Unit =
      // Create URL
      if (isServerSide) {
        // Server-side redirect: do a forward
        val requestDispatcher = nativeRequest.getRequestDispatcher(location)
        // TODO: handle `isNoRewrite` like in XFormsSubmissionUtils.openOptimizedConnection(): absolute path can then
        // be used to redirect to other servlet context

        // Destroy the pipeline context before doing the forward. Nothing significant
        // should be allowed on "this side" of the forward after the forward return.
        pipelineContext.destroy(true)

        // Execute the forward
        val wrappedRequest = new ForwardServletRequestWrapper(nativeRequest, location)
        requestDispatcher.forward(wrappedRequest, nativeResponse)

      } else {
        // Client-side redirect: send the redirect to the client,
        // disabling caching as done for non-2xx status codes
        setResponseHeaders(ServletExternalContext.nocacheCacheHeaders)
        nativeResponse.sendRedirect(
          if (isEmbedded)
            PathUtils.recombineQuery(
              URLRewriterUtils.rewriteServiceURL(
                getRequest,
                location,
                UrlRewriteMode.AbsolutePath
              ),
              List(ExternalContext.EmbeddableParam -> "true")
            )
          else
            location
        )
      }

    def getNamespacePrefix: String =
      urlRewriter.getNamespacePrefix

    def setTitle(title: String) = ()

    def getNativeResponse: AnyRef = nativeResponse

    def rewriteActionURL(urlString: String): String =
      urlRewriter.rewriteActionURL(urlString)

    def rewriteRenderURL(urlString: String): String =
      urlRewriter.rewriteRenderURL(urlString)

    def rewriteActionURL(urlString: String, portletMode: String, windowState: String): String =
      urlRewriter.rewriteActionURL(urlString, portletMode, windowState)

    def rewriteRenderURL(urlString: String, portletMode: String, windowState: String): String =
      urlRewriter.rewriteRenderURL(urlString, portletMode, windowState)

    def rewriteResourceURL(urlString: String, rewriteMode: UrlRewriteMode): String =
      urlRewriter.rewriteResourceURL(urlString, rewriteMode)
  }

  def getWebAppContext: WebAppContext = webAppContext

  private lazy val requestImpl = new RequestImpl
  def getRequest: ExternalContext.Request = requestImpl

  // 2022-07-28: With `embeddedClientValueFromHeaders`, should we check that we have specifically the `portlet` token
  // to enable `WSRPURLRewriter` encoding? Also check uses of `isEmbedded`, which also causes  the `EmbeddableParam` to
  // be passed during a redirect. We should review this.
  // See also: https://github.com/orbeon/orbeon-forms/issues/5323
  private def isEmbedded: Boolean =
    Headers.isEmbeddedFromHeaders(requestImpl.headerValuesMap)

  // NOTE: This whole logic below could be used by ServletExternalContext and PortletExternalContext
  // Check if there is an override of container type. This is currently used by the proxy portlet and by
  // XHTMLToPDF, as both require a specific type of URL rewriting to take place. Using this header means that
  // using a global property is not required anymore.
  lazy val getResponse: ExternalContext.Response =
    new ResponseImpl(
      if (isEmbedded)
        // Always set wsrpEncodeResources to true if the client is a remote portlet
        new WSRPURLRewriter(URLRewriterUtils.getPathMatchersCallable, getRequest, wsrpEncodeResources = true)
      else
        new ServletURLRewriter(getRequest)
    )

  private var sessionImplOpt: Option[ExternalContext.Session] = None

  def getSession(create: Boolean): ExternalContext.Session =
    sessionImplOpt getOrElse {
      // Force creation if whoever forwarded to us did have a session
      // This is to work around a Tomcat issue whereby a session is newly created in the original servlet, but
      // somehow we can't know about it when the request is forwarded to us.
      val nativeSession = nativeRequest.getSession(
        create || getRequest.getAttributesMap.get(OrbeonXFormsFilter.RendererHasSessionAttributeName) == "true"
      )

      if (nativeSession ne null) {
        val newSessionImpl = new ServletSessionImpl(nativeSession)
        sessionImplOpt = Some(newSessionImpl)
        newSessionImpl
      } else
        null
    }

  def getStartLoggerString: String = getRequest.getRequestPath + " - Received request"
  def getEndLoggerString  : String = getRequest.getRequestPath
}