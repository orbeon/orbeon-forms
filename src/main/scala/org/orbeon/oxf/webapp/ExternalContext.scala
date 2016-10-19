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
package org.orbeon.oxf.webapp

import java.io._
import java.security.Principal
import java.{util ⇒ ju}

import org.orbeon.oxf.externalcontext.URLRewriter
import org.orbeon.oxf.fr.UserRole

/**
  * ExternalContext abstracts context, request and response information so that compile-time dependencies on the
  * Servlet API or Portlet API can be removed.
  *
  * It is also possible to use ExternalContext to embed Orbeon Forms and to provide a web-like request/response
  * interface.
  */
object ExternalContext {

  val StandardCharacterEncoding       : String = "utf-8"
  val StandardHeaderCharacterEncoding : String = StandardCharacterEncoding
  val StandardFormCharacterEncoding   : String = StandardCharacterEncoding

  // TODO: Should be ADT.
  val APPLICATION_SCOPE = 1
  val PORTLET_SCOPE     = 2

  trait Request {

    def getContainerType: String
    def getContainerNamespace: String

    def getPathInfo: String
    def getRequestPath: String
    def getContextPath: String
    def getServletPath: String
    def getClientContextPath(urlString: String): String

    def getAttributesMap: ju.Map[String, AnyRef]
    def getHeaderValuesMap: ju.Map[String, Array[String]]
    def getParameterMap: ju.Map[String, Array[AnyRef]]

    def getCharacterEncoding: String
    def getContentLength: Int
    def getContentType: String
    def getInputStream: InputStream
    def getReader: Reader // TODO: remove uses of this

    def getProtocol: String
    def getRemoteHost: String
    def getRemoteAddr: String
    def getScheme: String
    def getMethod: String
    def getServerName: String
    def getServerPort: Int

    def getSession(create: Boolean): Session
    def sessionInvalidate()
    def isRequestedSessionIdValid: Boolean
    def getRequestedSessionId: String

    def getAuthType: String
    def isSecure: Boolean

    // TODO: Consider using a class such as `HttpRequest.Credentials`
    def getUsername: String
    def getUserGroup: String
    def getUserRoles: Array[UserRole] // TODO: Don't use `Array`
    def getUserOrganization: Array[String]
    def isUserInRole(role: String): Boolean
    def getUserPrincipal: Principal // TODO: any use of this?

    def getLocale: ju.Locale
    def getLocales: ju.Enumeration[_]

    def getPathTranslated: String
    def getQueryString: String
    def getRequestURI: String
    def getRequestURL: String

    def getPortletMode: String
    def getWindowState: String

    def getNativeRequest: Any
  }

  trait Rewriter extends URLRewriter {
    def rewriteActionURL(urlString: String): String
    def rewriteRenderURL(urlString: String): String
    def rewriteActionURL(urlString: String, portletMode: String, windowState: String): String
    def rewriteRenderURL(urlString: String, portletMode: String, windowState: String): String
    def rewriteResourceURL(urlString: String, rewriteMode: Int): String
    def getNamespacePrefix: String
  }

  trait Response extends Rewriter {
    def getWriter: PrintWriter // TODO: remove uses of this
    def getOutputStream: OutputStream

    def isCommitted: Boolean
    def reset()
    def setContentType(contentType: String)
    def setStatus(status: Int)
    def setContentLength(len: Int)
    def setHeader(name: String, value: String)
    def addHeader(name: String, value: String)

    def sendError(code: Int)

    def getCharacterEncoding: String

    def sendRedirect(location: String, isServerSide: Boolean, isExitPortal: Boolean)

    def setPageCaching(lastModified: Long)

    /**
      * Set expiration headers for resources.
      *
      * - If lastModified is > 0, Last-Modified is set to that value
      * - If lastModified is <= 0, Last-Modified and Expires are set to the time of the response
      * - If expires is > 0 and lastModified is > 0, Expires is set to that value
      * - If expires is <= 0 , Expires is set using the default policy: 1/10 of the age of the resource
      *
      * @param lastModified last modification date of resource, or <= 0 if unknown
      * @param expires      requested expiration, or <=0 if unknown or to trigger default policy
      */
    def setResourceCaching(lastModified: Long, expires: Long)

    def checkIfModifiedSince(lastModified: Long): Boolean

    def setTitle(title: String)

    def getNativeResponse: AnyRef
  }

  object Session {
    val APPLICATION_SCOPE: Int = 1
    val PORTLET_SCOPE: Int = 2
  }

  trait SessionListener {
    def sessionDestroyed()
  }

  trait Session {
    def getCreationTime: Long
    def getId: String
    def getLastAccessedTime: Long
    def getMaxInactiveInterval: Int
    def invalidate()
    def isNew: Boolean
    def setMaxInactiveInterval(interval: Int)

    def getAttributesMap: ju.Map[String, AnyRef]
    def getAttributesMap(scope: Int): ju.Map[String, AnyRef]

    def addListener(sessionListener: SessionListener)
    def removeListener(sessionListener: SessionListener)
  }

  trait RequestDispatcher {
    def forward(request: Request, response: Response)
    def include(request: Request, response: Response)
    def isDefaultContext: Boolean
  }

}

trait ExternalContext {

  import ExternalContext._

  def getWebAppContext: WebAppContext

  // NOTE: The only reason the session is available here is for session created/destroyed listeners, which make
  // available a session even though no request or response is available.
  def getSession(create: Boolean): Session

  /**
    * Return a request dispatcher usable to perform forwards and includes.
    *
    * NOTE: When isContextRelative is false, assume that the first path element points to the context. E.g. /foo/bar
    * resolves to a context mounted on /foo, and /bar is the resource pointed to in that context.
    *
    * @param path              path of the resource (must start with "/")
    * @param isContextRelative if true, path is relative to the current context root, otherwise to the document root
    * @return RequestDispatcher or null if cannot be found
    */
  def getRequestDispatcher(path: String, isContextRelative: Boolean): RequestDispatcher

  def getRequest: Request
  def getResponse: Response

  def getStartLoggerString: String
  def getEndLoggerString: String
}