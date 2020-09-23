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
import java.net.URL
import java.{util => ju}

import enumeratum.{Enum, EnumEntry}
import enumeratum.values.{IntEnum, IntEnumEntry}
import org.orbeon.io.CharsetNames
import org.orbeon.oxf.http.{Headers, HttpMethod}

import scala.collection.JavaConverters._
import scala.collection.immutable
import scala.util.control.NonFatal


sealed trait UserRole   { def roleName: String }
case class   SimpleRole      (roleName: String)                           extends UserRole
case class   ParametrizedRole(roleName: String, organizationName: String) extends UserRole

case class Organization(
  levels       : List[String] // levels from root to leaf
)

case class Credentials(
  username      : String,
  group         : Option[String],
  roles         : List[UserRole],
  organizations : List[Organization]
) {
  def defaultOrganization: Option[Organization] = organizations.headOption
}

/**
  * ExternalContext abstracts context, request and response information so that compile-time dependencies on the
  * Servlet API or Portlet API can be removed.
  *
  * It is also possible to use ExternalContext to embed Orbeon Forms and to provide a web-like request/response
  * interface.
  */
object ExternalContext {

  val StandardCharacterEncoding       : String = CharsetNames.Utf8
  val StandardHeaderCharacterEncoding : String = StandardCharacterEncoding
  val StandardFormCharacterEncoding   : String = StandardCharacterEncoding

  sealed trait Scope extends EnumEntry
  object Scope extends Enum[Scope] {

    val values = findValues

    case object Request     extends Scope
    case object Session     extends Scope
    case object Application extends Scope
  }

  sealed abstract class SessionScope(val value: Int) extends IntEnumEntry
  object SessionScope extends IntEnum[SessionScope] {

    val values: immutable.IndexedSeq[SessionScope] = findValues

    case object Application extends SessionScope(1)
    case object Local       extends SessionScope(2)
  }

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

    def getProtocol: String
    def getRemoteHost: String
    def getRemoteAddr: String
    def getScheme: String
    def getMethod: HttpMethod
    def getServerName: String
    def getServerPort: Int

    def getSession(create: Boolean): Session
    def sessionInvalidate(): Unit
    def isRequestedSessionIdValid: Boolean
    def getRequestedSessionId: String

    def getAuthType: String
    def isSecure: Boolean

    def credentials: Option[Credentials]

    // For Java callers
    def getUsername: String = credentials map (_.username) orNull

    def isUserInRole(role: String): Boolean

    def getLocale: ju.Locale
    def getLocales: ju.Enumeration[_]

    def getPathTranslated: String
    def getQueryString: String
    def getRequestURI: String
    def getRequestURL: String

    def getPortletMode: String
    def getWindowState: String

    def getNativeRequest: Any

    // TODO: return immutable.Map[String, List[AnyRef]] -> what about AnyRef?
    def parameters: collection.Map[String, Array[AnyRef]]   = getParameterMap.asScala
    def getFirstParamAsString(name: String): Option[String] = Option(getParameterMap.get(name)) flatMap (_ collectFirst { case s: String => s })
    def getFirstHeader(name: String): Option[String]        = Option(getHeaderValuesMap.get(name)) flatMap (_.headOption)
    def sessionOpt: Option[Session]                         = Option(getSession(create = false))
    lazy val contentLengthOpt: Option[Long]                 = Headers.firstNonNegativeLongHeaderIgnoreCase(getHeaderValuesMap.asScala, Headers.ContentLength)
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
    def getWriter: PrintWriter // TODO: remove uses of this, see https://github.com/orbeon/orbeon-forms/issues/2962
    def getOutputStream: OutputStream

    def isCommitted: Boolean
    def reset(): Unit
    def setContentType(contentType: String)
    def setStatus(status: Int)
    def setContentLength(len: Int)
    def setHeader(name: String, value: String)
    def addHeader(name: String, value: String)

    def sendError(code: Int): Unit

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
    def setResourceCaching(lastModified: Long, expires: Long): Unit

    def checkIfModifiedSince(request: Request, lastModified: Long): Boolean

    def setTitle(title: String): Unit

    def getNativeResponse: AnyRef
  }

  trait SessionListener extends java.io.Serializable {
    def sessionDestroyed(session: Session)
  }

  trait Session {
    def getCreationTime: Long
    def getId: String
    def getLastAccessedTime: Long
    def getMaxInactiveInterval: Int
    def invalidate()
    def isNew: Boolean
    def setMaxInactiveInterval(interval: Int)

    def addListener(sessionListener: SessionListener)
    def removeListener(sessionListener: SessionListener)

    def getAttribute    (name: String               , scope: SessionScope = SessionScope.Local): Option[AnyRef]
    def setAttribute    (name: String, value: AnyRef, scope: SessionScope = SessionScope.Local): Unit
    def removeAttribute (name: String               , scope: SessionScope = SessionScope.Local): Unit
    def javaGetAttribute(name: String                                                         ): AnyRef = getAttribute(name).orNull
    def javaGetAttribute(name: String               , scope: SessionScope                     ): AnyRef = getAttribute(name, scope).orNull
    def javaSetAttribute(name: String, value: AnyRef                                          ): Unit   = setAttribute(name, value)
  }
}

trait ExternalContext {

  import ExternalContext._

  def getWebAppContext: WebAppContext

  // NOTE: The only reason the session is available here is for session created/destroyed listeners, which make
  // available a session even though no request or response is available.
  def getSession(create: Boolean): Session
  def getSessionOpt(create: Boolean): Option[Session] = Option(getSession(create))

  def getRequest: Request
  def getResponse: Response

  def getStartLoggerString: String
  def getEndLoggerString: String
}

// Abstraction for ServletContext and PortletContext
trait WebAppContext {
  // Resource handling
  def getResource(s: String): URL
  def getResourceAsStream(s: String): InputStream
  def getRealPath(s: String): String

  // Immutable context initialization parameters
  def initParameters: Map[String, String]

  def jInitParameters: ju.Map[String, String] = initParameters.asJava
  def getInitParametersMap: ju.Map[String, String] = jInitParameters

  // Mutable context attributes backed by the actual context
  def attributes: collection.mutable.Map[String, AnyRef]

  def jAttributes: ju.Map[String, AnyRef] = attributes.asJava
  def getAttributesMap: ju.Map[String, AnyRef] = jAttributes

  // Logging
  def log(message: String, throwable: Throwable): Unit
  def log(message: String): Unit

  // Add webAppDestroyed listener
  def addListener(listener: WebAppListener): Unit =
    attributes.getOrElseUpdate(WebAppContext.WebAppListeners, new WebAppListeners)
      .asInstanceOf[WebAppListeners].addListener(listener)

  // Remove webAppDestroyed listener
  def removeListener(listener: WebAppListener): Unit =
   webAppListenersOption foreach (_.removeListener(listener))

  // Call all webAppDestroyed listeners
  def webAppDestroyed(): Unit =
    webAppListenersOption.toIterator flatMap (_.iterator) foreach { listener =>
      try {
        listener.webAppDestroyed()
      } catch {
        case NonFatal(t) => log("Throwable caught when calling listener", t)
      }
    }

  private def webAppListenersOption: Option[WebAppListeners] =
    attributes.get(WebAppContext.WebAppListeners) map (_.asInstanceOf[WebAppListeners])

  // Access to native context
  def getNativeContext: AnyRef
}

trait WebAppListener {
  def webAppDestroyed(): Unit
}

class WebAppListeners extends Serializable {

  @transient private var _listeners: List[WebAppListener] = Nil

  def addListener(listener: WebAppListener): Unit =
    _listeners ::= listener

  def removeListener(listener: WebAppListener): Unit =
    _listeners = _listeners filter (_ ne listener)

  def iterator: Iterator[WebAppListener] =
    _listeners.reverse.toIterator
}

private object WebAppContext {
  val WebAppListeners = "oxf.webapp.listeners"
}