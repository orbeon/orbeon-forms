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

import java.io._
import java.{util ⇒ ju}

import org.orbeon.oxf.externalcontext.ExternalContext.SessionScope.Application
import org.orbeon.oxf.externalcontext.ExternalContext._
import org.orbeon.oxf.util.{LoggerFactory, SecureUtils, StringBuilderWriter}

import scala.collection.{immutable ⇒ i, mutable ⇒ m}

/**
  * Simple implementation of the ExternalContext and related interfaces.
  *
  * Used by CommandLineExternalContext.
  */
object SimpleExternalContext {
  private val Logger = LoggerFactory.createLogger(classOf[SimpleExternalContext])
}

abstract class SimpleExternalContext extends ExternalContext {

  private   val webAppContext = new TestWebAppContext(SimpleExternalContext.Logger, m.LinkedHashMap[String, AnyRef]())
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
    def getInputStream: InputStream                                                   = null
    def getLocale: ju.Locale                                                          = null
    def getLocales: ju.Enumeration[_]                                                 = null
    def isRequestedSessionIdValid                                                     = false
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
    def checkIfModifiedSince(request: Request, lastModified: Long)                    = true
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

    protected var sessionAtts = i.HashMap[String, AnyRef]()

    val getCreationTime                                                               = System.currentTimeMillis
    val getId                                                                         = SecureUtils.randomHexId
    val getLastAccessedTime                                                           = 0L
    val getMaxInactiveInterval                                                        = 0
    def invalidate(): Unit                                                            = sessionAtts = i.HashMap[String, AnyRef]()
    val isNew                                                                         = false
    def setMaxInactiveInterval(interval: Int)                                         = ()

    def addListener(sessionListener: SessionListener)                                 = ()
    def removeListener(sessionListener: SessionListener)                              = ()

    def getAttribute(name: String, scope: SessionScope): Option[AnyRef]               = sessionAtts.get(name)
    def setAttribute(name: String, value: AnyRef, scope: SessionScope): Unit          = sessionAtts += name → value
    def removeAttribute(name: String, scope: SessionScope): Unit                      = sessionAtts -= name
  }

  def getWebAppContext: WebAppContext                                                 = webAppContext
  def getRequest: Request                                                             = request
  def getResponse: Response                                                           = response
  def getSession(create: Boolean): Session                                            = session

  def getStartLoggerString                                                            = "Running processor"
  def getEndLoggerString                                                              = "Done running processor"

  def getRequestDispatcher(path: String, isContextRelative: Boolean): RequestDispatcher = null
}