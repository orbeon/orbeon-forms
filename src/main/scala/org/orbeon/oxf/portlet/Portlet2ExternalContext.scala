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
package org.orbeon.oxf.portlet

import java.io._
import java.security.Principal
import java.util.Locale
import java.{util ⇒ ju}
import javax.portlet._

import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.externalcontext.{PortletToExternalContextRequestDispatcherWrapper, WSRPURLRewriter}
import org.orbeon.oxf.http.Headers
import org.orbeon.oxf.pipeline.api.{ExternalContext, PipelineContext}
import org.orbeon.oxf.util.CollectionUtils._
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util._
import org.orbeon.oxf.webapp.{HttpStatusCodeException, SessionListeners, WebAppContext, WebAppRequest}

import scala.collection.JavaConverters._

/*
 * Portlet-specific implementation of ExternalContext.
 */
object Portlet2ExternalContext {

  class BufferedResponseImpl(request: ExternalContext.Request)
    extends ExternalContext.Response {

    private val urlRewriter = new WSRPURLRewriter(
      getPathMatchers     = URLRewriterUtils.getPathMatchersCallable,
      request             = request,
      wsrpEncodeResources = URLRewriterUtils.isWSRPEncodeResources
    )

    // Response state
    private var contentType          : String  = null
    private var redirectLocation     : String  = null
    private var redirectIsExitPortal : Boolean = false
    private var title                : String  = null

    private lazy val streams = {
      val stringBuilderWriter = new StringBuilderWriter
      val printWriter         = new PrintWriter(stringBuilderWriter)
      val outputStream        = new ByteArrayOutputStream

      (stringBuilderWriter, printWriter, outputStream)
    }

    // Not handled right now or because it doesn't make sense
    def setContentLength(len: Int)                            = ()
    def checkIfModifiedSince(lastModified: Long)              = true
    def sendError(code: Int)                                  = throw new OXFException(s"Error while processing request: $code")
    def setPageCaching(lastModified: Long)                    = ()
    def setResourceCaching(lastModified: Long, expires: Long) = ()
    def setHeader(name: String, value: String)                = ()
    def addHeader(name: String, value: String)                = ()
    def reset()                                               = () // NOTE: We could implement this if needed.

    // We are always buffering
    def isCommitted                                           = false

    def setStatus(status: Int): Unit = {
      // Test error
      if (status == ExternalContext.SC_NOT_FOUND) {
        throw HttpStatusCodeException(ExternalContext.SC_NOT_FOUND)
      } else if (status >= 400) {
        // Ignore
      }
      // TODO: How to handle NOT_MODIFIED?
    }

    def rewriteActionURL(urlString: String): String =
      urlRewriter.rewriteActionURL(urlString)

    def rewriteRenderURL(urlString: String): String =
      urlRewriter.rewriteRenderURL(urlString)

    def rewriteActionURL(urlString: String, portletMode: String, windowState: String): String =
      urlRewriter.rewriteActionURL(urlString, portletMode, windowState)

    def rewriteRenderURL(urlString: String, portletMode: String, windowState: String): String =
      urlRewriter.rewriteRenderURL(urlString, portletMode, windowState)

    def rewriteResourceURL(urlString: String, rewriteMode: Int): String =
      urlRewriter.rewriteResourceURL(urlString, rewriteMode)

    def getNamespacePrefix: String = urlRewriter.getNamespacePrefix
    def getNativeResponse: AnyRef  = throw new NotImplementedError

    def setContentType(contentType: String) = this.contentType = NetUtils.getContentTypeMediaType(contentType)
    def setTitle(title: String)             = this.title = title

    def getWriter: PrintWriter         = streams._2
    def getOutputStream: OutputStream  = streams._3

    def sendRedirect(
      location     : String,
      isServerSide : Boolean, // ignored for portlets
      isExitPortal : Boolean  // if this is true, the redirect will exit the portal
    ): Unit = {
      this.redirectLocation     = location
      this.redirectIsExitPortal = isExitPortal
    }

    // NOTE: This is used only by ResultStoreWriter as of 8/12/03,
    // and used only to compute the size of the resulting byte
    // stream, size which is then set but not used when working with
    // portlets.
    def getCharacterEncoding: String = ExternalContext.DefaultHeaderEncoding

    def getContentType         : String  = contentType
    def getRedirectLocation    : String  = redirectLocation
    def isRedirectIsExitPortal : Boolean = redirectIsExitPortal
    def isRedirect             : Boolean = redirectLocation ne null
    def getTitle               : String  = title

    def getBytes: Array[Byte] =
      if (streams._1.getBuilder.length > 0)
        streams._1.toString.getBytes(getContentType) // TODO: what if content type not specified?
      else if (streams._3.size > 0)
        streams._3.toByteArray
      else
        Array.empty[Byte]
  }

  // Present a view of the HttpServletRequest properties as a Map.
  class RequestMap(val portletRequest: PortletRequest) extends AttributesToMap[AnyRef](
    new AttributesToMap.Attributeable[AnyRef] {
      def getAttribute(s: String): AnyRef           = portletRequest.getAttribute(s)
      def getAttributeNames: ju.Enumeration[String] = portletRequest.getAttributeNames
      def removeAttribute(s: String)                = portletRequest.removeAttribute(s)
      def setAttribute(s: String, o: AnyRef)        = portletRequest.setAttribute(s, o)
    }
  )

  // Present a view of the HttpSession properties as a Map.
  class SessionMap(val portletSession: PortletSession, val scope: Int) extends AttributesToMap[AnyRef](
    new AttributesToMap.Attributeable[AnyRef]() {
      def getAttribute(s: String): AnyRef           = portletSession.getAttribute(s, scope)
      def getAttributeNames: ju.Enumeration[String] = portletSession.getAttributeNames(scope)
      def removeAttribute(s: String)                = portletSession.removeAttribute(s, scope)
      def setAttribute(s: String, o: AnyRef)        = portletSession.setAttribute(s, o, scope)
    }
  )
}

class Portlet2ExternalContext(
  val pipelineContext : PipelineContext,
  val webAppContext   : WebAppContext,
  var portletRequest  : PortletRequest,
  val amendRequest    : Boolean
) extends ExternalContext {

  import Portlet2ExternalContext._

  private val clientDataRequestOpt = collectByErasedType[ClientDataRequest](portletRequest)

  def getWebAppContext: WebAppContext = webAppContext

  private class RequestImpl extends ExternalContext.Request with WebAppRequest {

    def getContainerType          : String            = "portlet"
    def getContainerNamespace     : String            = getResponse.getNamespacePrefix
    def getRequestPath            : String            = getPathInfo

    // Delegate to underlying request
    def getContextPath            : String            = portletRequest.getContextPath
    def getAuthType               : String            = portletRequest.getAuthType
    def isSecure                  : Boolean           = portletRequest.isSecure
    def getRequestedSessionId     : String            = portletRequest.getRequestedSessionId
    def getScheme                 : String            = portletRequest.getScheme
    def getServerName             : String            = portletRequest.getServerName
    def getServerPort             : Int               = portletRequest.getServerPort
    def getLocale                 : Locale            = portletRequest.getLocale
    def getLocales                : ju.Enumeration[_] = portletRequest.getLocales
    def isRequestedSessionIdValid : Boolean           = portletRequest.isRequestedSessionIdValid
    def getUserPrincipal          : Principal         = portletRequest.getUserPrincipal
    def getNativeRequest          : AnyRef            = portletRequest
    def getPortletMode            : String            = portletRequest.getPortletMode.toString
    def getWindowState            : String            = portletRequest.getWindowState.toString

    // Delegate to underlying request with data
    def getCharacterEncoding      : String            = clientDataRequestOpt map (_.getCharacterEncoding)  orNull
    def getContentLength          : Int               = clientDataRequestOpt map (_.getContentLength)      getOrElse -1
    def getContentType            : String            = clientDataRequestOpt map (_.getContentType)        orNull
    def getMethod                 : String            = clientDataRequestOpt map (_.getMethod)             getOrElse "GET"
    def getReader                 : Reader            = clientDataRequestOpt map (_.getReader)             orNull
    def getInputStream            : InputStream       = clientDataRequestOpt map (_.getPortletInputStream) orNull

    // Not available or not implemented
    def getRemoteAddr             : String            = null
    def getPathTranslated         : String            = null
    def getProtocol               : String            = null
    def getQueryString            : String            = null
    def getRequestURI             : String            = null
    def getRequestURL             : String            = null
    def getServletPath            : String            = null
    def getRemoteHost             : String            = null

    lazy val getPathInfo: String = {
      // Use the resource id if we are a ResourceRequest
      // In that case, remove the query string part of the resource id, that's handled by getParameterMap()
      val rawResult =
        portletRequest match {
          case rr: ResourceRequest ⇒ PathUtils.splitQuery(rr.getResourceID)._1
          case _                   ⇒ portletRequest.getParameter(WSRPURLRewriter.PathParameterName)
        }

      PathUtils.appendStartingSlash(rawResult.trimAllToEmpty)
    }

    protected lazy val headerValuesMap = {

      // NOTE: Not sure we should even pass these properties as "headers"
      // Example of property: javax.portlet.markup.head.element.support = true

      val propertiesIt =
        for (name ← portletRequest.getPropertyNames.asScala)
          yield name.toLowerCase → StringConversions.stringEnumerationToArray(portletRequest.getProperties(name))

      // PLT.11.1.5 Request Properties: "client request HTTP headers may not be always available. Portlets
      // should not rely on the presence of headers to function properly. The PortletRequest interface
      // provides specific methods to access information normally available as HTTP headers: content-length,
      // content-type, accept-language."
      // NOTE: It seems like while Liferay 5 was making headers available, Liferay 6 doesn't anymore.

      val headersIt =
        clientDataRequestOpt.iterator flatMap { cdr ⇒
          ((cdr.getContentType ne null) iterator (Headers.ContentTypeLower   → Array(cdr.getContentType))) ++
          ((cdr.getContentLength != -1) iterator (Headers.ContentLengthLower → Array(cdr.getContentLength.toString)))
        }

      propertiesIt ++ headersIt toMap
    }

    def getHeaderValuesMap: ju.Map[String, Array[String]] = headerValuesMap.asJava

    def getSession(create: Boolean): ExternalContext.Session =
      Portlet2ExternalContext.this.getSession(create)

    def sessionInvalidate(): Unit = {
      val session = portletRequest.getPortletSession(false)
      if (session ne null)
        session.invalidate()
    }

    lazy val getParameterMap: ju.Map[String, Array[AnyRef]] =
      if ((getContentType ne null) && getContentType.startsWith("multipart/form-data")) {
        Multipart.jGetParameterMapMultipart(pipelineContext, getRequest, ExternalContext.DefaultFormCharsetDefault)
      } else {
        portletRequest match {
          case rr: ResourceRequest ⇒
            // We encoded query parameters directly into the resource id in this case
            val (_, queryString) = PathUtils.splitQueryDecodeParams(rr.getResourceID)
            CollectionUtils.combineValues[String, AnyRef, Array](queryString).toMap.asJava
          case _ ⇒
            // Use native request parameters
            // Filter out `PathParameterName`, make values `Array[AnyRef]` (not great), and make immutable `Map`.
            val filteredParams =
              portletRequest.getParameterMap.asScala collect {
                case pair @ (k, v) if k != WSRPURLRewriter.PathParameterName ⇒ (k, v.toArray[AnyRef])
              }
            filteredParams.asJava
        }
      }

    lazy val getAttributesMap: ju.Map[String, AnyRef] =
      new RequestMap(portletRequest)
  }

  private class SessionImpl(portletSession: PortletSession)
    extends ExternalContext.Session {

    import PortletSession._

    import SessionListeners._

    // Delegate
    def getCreationTime                       = portletSession.getCreationTime
    def getId                                 = portletSession.getId
    def getLastAccessedTime                   = portletSession.getLastAccessedTime
    def getMaxInactiveInterval                = portletSession.getMaxInactiveInterval
    def invalidate()                          = portletSession.invalidate()
    def isNew                                 = portletSession.isNew
    def setMaxInactiveInterval(interval: Int) = portletSession.setMaxInactiveInterval(interval)

    lazy val applicationAttributesMap = new SessionMap(portletSession, APPLICATION_SCOPE)
    lazy val portletAttributesMap     = new SessionMap(portletSession, PORTLET_SCOPE)

    def getAttributesMap: ju.Map[String, AnyRef] = portletAttributesMap

    def getAttributesMap(scope: Int): ju.Map[String, AnyRef] = scope match {
      case APPLICATION_SCOPE ⇒ applicationAttributesMap
      case PORTLET_SCOPE     ⇒ portletAttributesMap
      case _                 ⇒ throw new IllegalArgumentException
    }

    def addListener(sessionListener: ExternalContext.Session.SessionListener): Unit =
      portletSession.getAttribute(SessionListenersKey, APPLICATION_SCOPE) match {
        case listeners: SessionListeners ⇒ listeners.addListener(sessionListener)
        case _ ⇒
          throw new IllegalStateException(
            "`SessionListeners` object not found in session. `OrbeonSessionListener` might be missing from web.xml."
          )
      }

    def removeListener(sessionListener: ExternalContext.Session.SessionListener): Unit =
      portletSession.getAttribute(SessionListenersKey, APPLICATION_SCOPE) match {
        case listeners: SessionListeners ⇒ listeners.removeListener(sessionListener)
        case _ ⇒
      }
  }

  lazy val getRequest  : ExternalContext.Request  = new RequestImpl
  lazy val getResponse : ExternalContext.Response = new BufferedResponseImpl(getRequest)

  private var sessionImplOpt: Option[SessionImpl] = None

  def getSession(create: Boolean): ExternalContext.Session =
    sessionImplOpt getOrElse {
      val nativeSession = portletRequest.getPortletSession(create)

      if (nativeSession ne null) {
        val newSessionImpl = new SessionImpl(nativeSession)
        sessionImplOpt = Some(newSessionImpl)
        newSessionImpl
      } else
        null
    }

  def getStartLoggerString: String = getRequest.getRequestPath + " - Received request"
  def getEndLoggerString  : String = getRequest.getRequestPath

  def getRequestDispatcher(path: String, isContextRelative: Boolean): ExternalContext.RequestDispatcher =
    new PortletToExternalContextRequestDispatcherWrapper(
      webAppContext.getNativeContext.asInstanceOf[PortletContext].getRequestDispatcher(path)
    )
}