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
package org.orbeon.oxf.pipeline

import java.io._
import java.security.Principal
import java.{util ⇒ ju}

import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.fr.Credentials
import org.orbeon.oxf.util.{LoggerFactory, StringBuilderWriter}
import org.orbeon.oxf.webapp.ExternalContext._
import org.orbeon.oxf.webapp.{ExternalContext, TestWebAppContext, WebAppContext}

import scala.collection.mutable

/**
  * Simple implementation of the ExternalContext and related interfaces.
  *
  * Used by CommandLineExternalContext.
  */
object SimpleExternalContext {
  private val Logger = LoggerFactory.createLogger(classOf[SimpleExternalContext])
}

abstract class SimpleExternalContext extends ExternalContext {

  private   val webAppContext = new TestWebAppContext(SimpleExternalContext.Logger, mutable.LinkedHashMap[String, AnyRef]())
  protected var request       = new RequestImpl
  protected var response      = new ResponseImpl
  protected val session       = new SessionImpl

  protected class RequestImpl extends Request {

    protected var attributesMap = new ju.HashMap[String, AnyRef]

    def getContainerType                                                              = "simple"
    def getContainerNamespace                                                         = ""
    def getContextPath                                                                = ""
    def getPathInfo                                                                   = ""
    def getRemoteAddr                                                                 = "127.0.0.1"
    def getAttributesMap: ju.Map[String, AnyRef]                                      = attributesMap
    def getHeaderValuesMap: ju.Map[String, Array[String]]                             = ju.Collections.emptyMap[String, Array[String]]
    def getParameterMap: ju.Map[String, Array[AnyRef]]                                = ju.Collections.emptyMap[String, Array[AnyRef]]
    def getAuthType                                                                   = "basic"
    def isSecure                                                                      = false
    def credentials: Option[Credentials]                                              = None
    def isUserInRole(role: String)                                                    = false
    def getSession(create: Boolean): Session                                          = session
    def sessionInvalidate()                                                           = ()
    def getCharacterEncoding                                                          = "utf-8"
    def getContentLength                                                              = 0
    def getContentType                                                                = ""
    def getServerName                                                                 = ""
    def getServerPort                                                                 = 0
    def getMethod                                                                     = "GET"
    def getProtocol                                                                   = "http"
    def getRemoteHost                                                                 = ""
    def getScheme                                                                     = ""
    def getPathTranslated                                                             = ""
    def getQueryString                                                                = ""
    def getRequestedSessionId                                                         = ""
    def getRequestPath                                                                = ""
    def getRequestURI                                                                 = ""
    def getRequestURL                                                                 = ""
    def getServletPath                                                                = ""
    def getClientContextPath(urlString: String)                                       = getContextPath
    def getReader: Reader                                                             = null
    def getInputStream: InputStream                                                   = null
    def getLocale: ju.Locale                                                          = null
    def getLocales: ju.Enumeration[_]                                                 = null
    def isRequestedSessionIdValid                                                     = false
    def getUserPrincipal: Principal                                                   = null
    def getNativeRequest: Any                                                         = null
    def getPortletMode: String                                                        = null
    def getWindowState: String                                                        = null
  }


  protected class ResponseImpl extends Response {

    protected var outputStream                                                        = new ByteArrayOutputStream
    protected var writer                                                              = new StringBuilderWriter
    protected var contentType: String                                                 = null
    protected var status                                                              = 0
    protected var headers                                                             = new ju.HashMap[String, String]

    def getOutputStream: OutputStream                                                 = outputStream
    def getWriter: PrintWriter                                                        = new PrintWriter(writer)
    def isCommitted: Boolean                                                          = false

    def reset(): Unit = {
      outputStream.reset()
      writer.getBuilder.delete(0, writer.getBuilder.length)
    }

    def setContentType(contentType: String)                                           = this.contentType = contentType
    def setStatus(status: Int)                                                        = this.status = status
    def setHeader(name: String, value: String)                                        = headers.put(name, value)
    def addHeader(name: String, value: String)                                        = headers.put(name, value)
    def sendRedirect(location: String, isServerSide: Boolean, isExitPortal: Boolean)  = ()
    def setContentLength(len: Int)                                                    = ()
    def sendError(code: Int)                                                          = ()
    def getCharacterEncoding                                                          = StandardCharacterEncoding

    def setPageCaching(lastModified: Long)                                            = ()
    def setResourceCaching(lastModified: Long, expires: Long)                         = ()
    def checkIfModifiedSince(lastModified: Long)                                      = true
    def rewriteActionURL(urlString: String)                                           = ""
    def rewriteRenderURL(urlString: String)                                           = ""
    def rewriteActionURL(urlString: String, portletMode: String, windowState: String) = ""
    def rewriteRenderURL(urlString: String, portletMode: String, windowState: String) = ""
    def rewriteResourceURL(urlString: String)                                         = ""
    def rewriteResourceURL(urlString: String, generateAbsoluteURL: Boolean)           = ""
    def rewriteResourceURL(urlString: String, rewriteMode: Int)                       = ""
    def getNamespacePrefix                                                            = ""
    def setTitle(title: String)                                                       = ()

    def getNativeResponse: AnyRef                                                     = null
  }

  protected class SessionImpl extends Session {

    protected var sessionAttributesMap = new ju.HashMap[String, AnyRef]

    def getCreationTime                                                               = 0L
    def getId                                                                         = sessionAttributesMap.hashCode.toString
    def getLastAccessedTime                                                           = 0L
    def getMaxInactiveInterval                                                        = 0
    def invalidate(): Unit                                                            = sessionAttributesMap = new ju.HashMap[String, AnyRef]
    def isNew                                                                         = false
    def setMaxInactiveInterval(interval: Int)                                         = ()

    def getAttributesMap: ju.Map[String, AnyRef]                                      = sessionAttributesMap

    def getAttributesMap(scope: SessionScope): ju.Map[String, AnyRef] = {
      if (scope != ApplicationSessionScope)
        throw new OXFException("Invalid session scope scope: only the application scope is allowed.")
      getAttributesMap
    }

    def addListener(sessionListener: SessionListener)                                 = ()
    def removeListener(sessionListener: SessionListener)                              = ()
  }

  def getWebAppContext: WebAppContext                                                 = webAppContext
  def getRequest: Request                                                             = request
  def getResponse: Response                                                           = response
  def getSession(create: Boolean): Session                                            = session

  def getStartLoggerString                                                            = "Running processor"
  def getEndLoggerString                                                              = "Done running processor"

  def getRequestDispatcher(path: String, isContextRelative: Boolean): RequestDispatcher = null
}