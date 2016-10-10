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
  *//**
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

import java.io._
import java.{util ⇒ ju}
import javax.servlet.http.{HttpServletRequest, HttpServletResponse, HttpSession}
import javax.servlet.{ServletContext, ServletException}

import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.externalcontext.{ServletToExternalContextRequestDispatcherWrapper, ServletURLRewriter, URLRewriter, WSRPURLRewriter}
import org.orbeon.oxf.fr.UserRole
import org.orbeon.oxf.http.Headers
import org.orbeon.oxf.pipeline.InitUtils
import org.orbeon.oxf.pipeline.api.ExternalContext.Session
import org.orbeon.oxf.pipeline.api.{ExternalContext, PipelineContext}
import org.orbeon.oxf.properties.Properties
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util._
import org.orbeon.oxf.webapp.{SessionListeners, WebAppContext}

import scala.collection.JavaConverters._

/*
 * Servlet-specific implementation of ExternalContext.
 */
object ServletExternalContext {

  val Logger = LoggerFactory.createLogger(classOf[ServletExternalContext])

  val DefaultHeaderEncoding                    = "utf-8"
  val DefaultFormCharsetDefault                = "utf-8"

  private val DefaultFormCharsetProperty       = "oxf.servlet.default-form-charset"
  private val HttpPageCacheHeadersDefault      = "Cache-Control: private, max-age=0; Pragma:"
  private val HttpPageCacheHeadersProperty     = "oxf.http.page.cache-headers"
  private val HttpResourceCacheHeadersDefault  = "Cache-Control: public; Pragma:"
  private val HttpResourceCacheHeadersProperty = "oxf.http.resource.cache-headers"
  private val HttpNocacheCacheHeadersDefault   = "Cache-Control: no-cache, no-store, must-revalidate; Pragma: no-cache; Expires: 0"
  private val HttpNocacheCacheHeadersProperty  = "oxf.http.nocache.cache-headers"

  lazy val defaultFormCharset           = Properties.instance.getPropertySet.getString(DefaultFormCharsetProperty, DefaultFormCharsetDefault)

  private lazy val pageCacheHeaders     = decodeCacheString(HttpPageCacheHeadersProperty,     HttpPageCacheHeadersDefault)
  private lazy val resourceCacheHeaders = decodeCacheString(HttpResourceCacheHeadersProperty, HttpResourceCacheHeadersDefault)
  private lazy val nocacheCacheHeaders  = decodeCacheString(HttpNocacheCacheHeadersProperty,  HttpNocacheCacheHeadersDefault)

  private def decodeCacheString(name: String, defaultValue: String): List[(String, String)] =
    for {
      header ← Properties.instance.getPropertySet.getString(name, defaultValue).splitTo[List](sep = ";")
      parts = header.splitTo[List](":")
      if parts.size == 2
      name :: value :: Nil = parts
    } yield
      name.trimAllToEmpty → value.trimAllToEmpty

  private def setResponseHeaders(nativeResponse: HttpServletResponse, headers: List[(String, String)]): Unit =
    for ((key, value) ← headers)
      nativeResponse.setHeader(key, value)
}

class ServletExternalContext(
  val pipelineContext : PipelineContext,
  val webAppContext   : WebAppContext,
  val nativeRequest   : HttpServletRequest,
  val nativeResponse  : HttpServletResponse
) extends ExternalContext {

  import ServletExternalContext._

  private class RequestImpl extends ExternalContext.Request {

    private var getParameterMapMultipartFormDataCalled = false
    private var getInputStreamCalled                   = false
    private var inputStreamCharsetOpt: Option[String]     = None

    // Delegate to underlying request
    def getPathInfo                = nativeRequest.getPathInfo
    def getRemoteAddr              = nativeRequest.getRemoteAddr
    def getAuthType                = nativeRequest.getAuthType
    def isSecure                   = nativeRequest.isSecure
    def getContentLength           = nativeRequest.getContentLength
    def getContentType             = nativeRequest.getContentType
    def getServerName              = nativeRequest.getServerName
    def getServerPort              = nativeRequest.getServerPort
    def getMethod                  = nativeRequest.getMethod
    def getProtocol                = nativeRequest.getProtocol
    def getRemoteHost              = nativeRequest.getRemoteHost
    def getScheme                  = nativeRequest.getScheme
    def getPathTranslated          = nativeRequest.getPathTranslated
    def getRequestedSessionId      = nativeRequest.getRequestedSessionId
    def getServletPath             = nativeRequest.getServletPath
    def getLocale                  = nativeRequest.getLocale
    def getLocales                 = nativeRequest.getLocales
    def isRequestedSessionIdValid  = nativeRequest.isRequestedSessionIdValid
    def getUserPrincipal           = nativeRequest.getUserPrincipal
    def getReader                  = nativeRequest.getReader

    def getContainerType           = "servlet"
    def getContainerNamespace      = getResponse.getNamespacePrefix

    lazy val getContextPath: String = {
      // NOTE: Servlet 2.4 spec says: "These attributes [javax.servlet.include.*] are accessible from the
      // included servlet via the getAttribute method on the request object and their values must be equal to
      // the request URI, context path, servlet path, path info, and query string of the included servlet,
      // respectively."
      // NOTE: This is very different from the similarly-named forward attributes, which reflect the values of the
      // first servlet in the chain!
      val dispatcherContext = nativeRequest.getAttribute("javax.servlet.include.context_path").asInstanceOf[String]

      if (dispatcherContext ne null)
        dispatcherContext            // this ensures we return the included / forwarded servlet's value
      else
        nativeRequest.getContextPath // use regular context
    }

    lazy val getAttributesMap: ju.Map[String, AnyRef] = new InitUtils.RequestMap(nativeRequest)

    // NOTE: Normalize names to lowercase to ensure consistency between servlet containers
    private lazy val headerValuesMap: Map[String, Array[String]] = (
        for (name ← nativeRequest.getHeaderNames.asInstanceOf[ju.Enumeration[String]].asScala)
          yield name.toLowerCase → StringConversions.stringEnumerationToArray(nativeRequest.getHeaders(name))
      ).toMap

    def getHeaderValuesMap = headerValuesMap.asJava

    lazy val getParameterMap: ju.Map[String, Array[AnyRef]] = {
      // Two conditions: file upload ("multipart/form-data") or not
      // NOTE: Regular form POST uses application/x-www-form-urlencoded. In this case, the servlet container
      // exposes parameters with getParameter*() methods (see SRV.4.1.1).
      if ((getContentType ne null) && getContentType.startsWith("multipart/form-data")) {
        // Special handling for multipart/form-data
        if (getInputStreamCalled)
          throw new OXFException(s"Cannot call `getParameterMap` after `getInputStream` when a form was posted with `multipart/form-data`")

        // Decode the multipart data
        val result = Multipart.jGetParameterMapMultipart(pipelineContext, getRequest, DefaultHeaderEncoding)
        // Remember that we were called, so we can display a meaningful exception if getInputStream() is called after this
        getParameterMapMultipartFormDataCalled = true
        result
      } else {
        // Set the input character encoding before getting the stream as this can cause issues with Jetty
        try {
          handleInputEncoding()
        } catch {
          case e: UnsupportedEncodingException ⇒
            throw new OXFException(e)
        }
        // Just use native request parameters
        val result = new ju.HashMap[String, Array[AnyRef]]
        val e = nativeRequest.getParameterNames.asInstanceOf[ju.Enumeration[String]]
        while (e.hasMoreElements) {
          val name = e.nextElement()
          result.put(name, nativeRequest.getParameterValues(name).asInstanceOf[Array[AnyRef]])
        }
        result
      }
    }

    def getUsername: String  = Headers.firstHeaderIgnoreCase(headerValuesMap, Headers.OrbeonUsername).orNull
    def getUserGroup: String = Headers.firstHeaderIgnoreCase(headerValuesMap, Headers.OrbeonGroup).orNull
    def getUserOrganization = null

    lazy val getUserRoles: Array[UserRole] =
      Headers.nonEmptyHeaderIgnoreCase(headerValuesMap, Headers.OrbeonRoles) match {
        case Some(headers) ⇒ headers map UserRole.parse
        case None          ⇒ Array.empty[UserRole]
      }

    def isUserInRole(role: String): Boolean =
      getUserRoles exists (_.roleName == role)

    def getSession(create: Boolean): ExternalContext.Session =
      ServletExternalContext.this.getSession(create)

    def sessionInvalidate(): Unit = {
      val session = nativeRequest.getSession(false)
      if (session ne null)
        session.invalidate()
    }

    def getCharacterEncoding: String =
      inputStreamCharsetOpt getOrElse nativeRequest.getCharacterEncoding

    def getQueryString: String = {
      // Use included / forwarded servlet's value
      // NOTE: Servlet 2.4 spec says: "These attributes [javax.servlet.include.*] are accessible from the
      // included servlet via the getAttribute method on the request object and their values must be equal to the
      // request URI, context path, servlet path, path info, and query string of the included servlet,
      // respectively."
      // NOTE: This is very different from the similarly-named forward attributes, which reflect the values of the
      // first servlet in the chain!
      val dispatcherQueryString = nativeRequest.getAttribute("javax.servlet.include.query_string").asInstanceOf[String]
      if (dispatcherQueryString ne null)
        dispatcherQueryString
      else
        nativeRequest.getQueryString
    }

    def getRequestPath = NetUtils.getRequestPathInfo(nativeRequest)

    def getRequestURI: String = {
      // Use included / forwarded servlet's value
      // NOTE: Servlet 2.4 spec says: "These attributes [javax.servlet.include.*] are accessible from the
      // included servlet via the getAttribute method on the request object and their values must be equal to the
      // request URI, context path, servlet path, path info, and query string of the included servlet,
      // respectively."
      // NOTE: This is very different from the similarly-named forward attributes, which reflect the values of the
      // first servlet in the chain!
      val dispatcherRequestURI = nativeRequest.getAttribute("javax.servlet.include.request_uri").asInstanceOf[String]
      if (dispatcherRequestURI ne null)
        dispatcherRequestURI
      else
        nativeRequest.getRequestURI
    }

    def getRequestURL: String = {
      // NOTE: If this is included from a portlet, we may not have a request URL
      val requestUrl = nativeRequest.getRequestURL
      // TODO: check if we should return null or "" or sth else
      if (requestUrl ne null)
        requestUrl.toString
      else
        null
    }

    def getClientContextPath(urlString: String): String = {
      // Return depending on whether passed URL is a platform URL or not
      if (URLRewriterUtils.isPlatformPath(urlString))
        platformClientContextPath
      else
        applicationClientContextPath
    }

    private lazy val platformClientContextPath: String =
      URLRewriterUtils.getClientContextPath(this, true)

    private lazy val applicationClientContextPath: String =
      URLRewriterUtils.getClientContextPath(this, false)

    def getInputStream: InputStream = {
      if (getParameterMapMultipartFormDataCalled)
        throw new OXFException(s"Cannot call `getInputStream` after `getParameterMap` when a form was posted with `multipart/form-data`")

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
            case Some(requestCharacterEncoding) ⇒
              requestCharacterEncoding
            case None ⇒
              nativeRequest.setCharacterEncoding(defaultFormCharset)
              defaultFormCharset
          }
        )
  }

  private class ResponseImpl(urlRewriter: URLRewriter) extends ExternalContext.Response {

    private var responseCachingDisabled: Boolean = false

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
    def getCharacterEncoding: String           = nativeResponse.getCharacterEncoding

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
      if (! NetUtils.isSuccessCode(status))
        responseCachingDisabled = true

      nativeResponse.setStatus(status)
    }

    def sendRedirect(location: String, isServerSide: Boolean, isExitPortal: Boolean): Unit =
      // Create URL
      if (isServerSide) {
        // Server-side redirect: do a forward
        val requestDispatcher = nativeRequest.getRequestDispatcher(location)
        // TODO: handle isNoRewrite like in XFormsSubmissionUtils.openOptimizedConnection(): absolute path can then be used to redirect to other servlet context
        try {
          // Destroy the pipeline context before doing the forward. Nothing significant
          // should be allowed on "this side" of the forward after the forward return.
          pipelineContext.destroy(true)
          // Execute the forward
          val wrappedRequest = new ForwardServletRequestWrapper(nativeRequest, location)
          requestDispatcher.forward(wrappedRequest, nativeResponse)
        }catch {
          case e: ServletException ⇒
            throw new OXFException(e)
        }
      } else {
        // Client-side redirect: send the redirect to the client
        nativeResponse.sendRedirect(
          if (isEmbedded)
            NetUtils.appendQueryString(
              URLRewriterUtils.rewriteServiceURL(
                getRequest,
                location,
                URLRewriter.REWRITE_MODE_ABSOLUTE_PATH)
              ,
              "orbeon-embeddable=true"
            )
          else
            location
        )
      }

    def setPageCaching(lastModified: Long): Unit =
      if (responseCachingDisabled) {
        setResponseHeaders(nativeResponse, nocacheCacheHeaders)
      } else {
        // Get current time and adjust lastModified
        val now = System.currentTimeMillis
        var _lastModified = lastModified
        if (_lastModified <= 0)
          _lastModified = now
        // Set last-modified
        nativeResponse.setDateHeader(Headers.LastModified, _lastModified)
        // Make sure the client does not load from cache without revalidation
        nativeResponse.setDateHeader("Expires", now)
        setResponseHeaders(nativeResponse, pageCacheHeaders)
      }

    def setResourceCaching(lastModified: Long, expires: Long): Unit =
      if (responseCachingDisabled) {
        setResponseHeaders(nativeResponse, nocacheCacheHeaders)
      } else {
        // Get current time and adjust parameters
        val now = System.currentTimeMillis
        var _lastModified = lastModified
        var _expires = expires
        if (_lastModified <= 0) {
          _lastModified = now
          _expires = now
        } else if (_expires <= 0) {
          // Regular expiration strategy. We use the HTTP spec heuristic to calculate the "Expires" header value
          // (10% of the difference between the current time and the last modified time)
          _expires = now + (now - _lastModified) / 10
        }
        // Set caching headers
        nativeResponse.setDateHeader(Headers.LastModified, _lastModified)
        nativeResponse.setDateHeader("Expires", _expires)
        setResponseHeaders(nativeResponse, resourceCacheHeaders)
      }

    def checkIfModifiedSince(lastModified: Long): Boolean =
      responseCachingDisabled || NetUtils.checkIfModifiedSince(getRequest , lastModified, Logger)

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

    def rewriteResourceURL(urlString: String, generateAbsoluteURL: Boolean): String =
      rewriteResourceURL(
        urlString, if (generateAbsoluteURL)
          URLRewriter.REWRITE_MODE_ABSOLUTE
        else
          URLRewriter.REWRITE_MODE_ABSOLUTE_PATH_OR_RELATIVE
      )

    def rewriteResourceURL(urlString: String, rewriteMode: Int): String =
      urlRewriter.rewriteResourceURL(urlString, rewriteMode)
  }

  private class SessionImpl(var httpSession: HttpSession) extends ExternalContext.Session {

    // Delegate to underlying session
    def getCreationTime                       = httpSession.getCreationTime
    def getId                                 = httpSession.getId
    def getLastAccessedTime                   = httpSession.getLastAccessedTime
    def getMaxInactiveInterval                = httpSession.getMaxInactiveInterval
    def invalidate()                          = httpSession.invalidate()
    def isNew                                 = httpSession.isNew
    def setMaxInactiveInterval(interval: Int) = httpSession.setMaxInactiveInterval(interval)

    lazy val getAttributesMap: ju.Map[String, AnyRef] =
      new InitUtils.SessionMap(httpSession)

    def getAttributesMap(scope: Int): ju.Map[String, AnyRef] = {
      if (scope != Session.APPLICATION_SCOPE)
        throw new IllegalArgumentException("Only the application scope is allowed in a servlet environment")

      getAttributesMap
    }

    def addListener(sessionListener: ExternalContext.Session.SessionListener): Unit = {
      val listeners = httpSession.getAttribute(SessionListeners.SessionListenersKey).asInstanceOf[SessionListeners]
      if (listeners eq null)
        throw new IllegalStateException("SessionListeners object not found in session. OrbeonSessionListener might be missing from web.xml.")

      listeners.addListener(sessionListener)
    }

    def removeListener(sessionListener: ExternalContext.Session.SessionListener): Unit = {
      val listeners = httpSession.getAttribute(SessionListeners.SessionListenersKey).asInstanceOf[SessionListeners]

      if (listeners ne null)
        listeners.removeListener(sessionListener)
    }
  }

  def getWebAppContext: WebAppContext = webAppContext

  lazy val getRequest: ExternalContext.Request = new RequestImpl

  private def isEmbedded: Boolean = {
    // NOTE: use request.getHeaderValuesMap() which normalizes header names to lowercase. This is important if
    // the headers map is generated internally as in that case it might be lowercase already.
    val doOverride = NetUtils.getFirstHeaderOrNull(getRequest.getHeaderValuesMap, Headers.OrbeonClientLower)
    Headers.EmbeddedClientValues.contains(doOverride)
  }

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

  private var sessionImplOpt: Option[SessionImpl] = None

  def getSession(create: Boolean): ExternalContext.Session =
    sessionImplOpt getOrElse {
      // Force creation if whoever forwarded to us did have a session
      // This is to work around a Tomcat issue whereby a session is newly created in the original servlet, but
      // somehow we can't know about it when the request is forwarded to us.
      val nativeSession = nativeRequest.getSession(
        create || getRequest.getAttributesMap.get(OrbeonXFormsFilter.RendererHasSessionAttributeName) == "true"
      )

      if (nativeSession ne null) {
        val newSessionImpl = new SessionImpl(nativeSession)
        sessionImplOpt = Some(newSessionImpl)
        newSessionImpl
      } else
        null
    }

  def getStartLoggerString: String =
    getRequest.getRequestPath + " - Received request"

  def getEndLoggerString: String =
    getRequest.getRequestPath

  def getRequestDispatcher(path: String, isContextRelative: Boolean): ExternalContext.RequestDispatcher = {
    val servletContext = webAppContext.getNativeContext.asInstanceOf[ServletContext]
    if (isContextRelative) {
      // Path is relative to the current context root
      val slashServletContext = servletContext.getContext("/")
      new ServletToExternalContextRequestDispatcherWrapper(servletContext.getRequestDispatcher(path), slashServletContext eq servletContext)
    } else {
      // Path is relative to the server document root
      val otherServletContext = servletContext.getContext(path)
      if (otherServletContext eq null)
        return null

      val slashServletContext = servletContext.getContext("/")

      val (modifiedPath, isDefaultContext) =
        if (slashServletContext ne otherServletContext) {
          // Remove first path element
          val newPath = NetUtils.removeFirstPathElement(path)
          if (newPath eq null)
            return null
          newPath → false
        }  else {
          // No need to remove first path element because the servlet context is ""
          path → true
        }

      new ServletToExternalContextRequestDispatcherWrapper(otherServletContext.getRequestDispatcher(modifiedPath), isDefaultContext)
    }
  }
}