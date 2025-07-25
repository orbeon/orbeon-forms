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

import java.io.*
import java.net.URL
import java.util as ju
import enumeratum.{Enum, EnumEntry}
import enumeratum.values.{IntEnum, IntEnumEntry}
import org.orbeon.io.CharsetNames
import org.orbeon.oxf.http.{Headers, HttpMethod, PathType}
import org.orbeon.oxf.util.PathUtils
import org.orbeon.oxf.util.StringUtils.*

import scala.jdk.CollectionConverters.*
import scala.collection.immutable
import scala.util.control.NonFatal


sealed trait UserRole   { def roleName: String }
case class   SimpleRole      (roleName: String)                           extends UserRole
case class   ParametrizedRole(roleName: String, organizationName: String) extends UserRole

case class UserAndGroup(
  username  : String,        // non-blank
  groupname : Option[String]
) {
  require(username.nonAllBlank)
}

object UserAndGroup {
  def fromStrings(username: String, groupname: String): Option[UserAndGroup] =
    Option(username).flatMap(_.trimAllToOpt).map(UserAndGroup(_, Option(groupname).flatMap(_.trimAllToOpt)))

  def fromStringsOrThrow(username: String, groupname: String): UserAndGroup =
    fromStrings(username, groupname).getOrElse(throw new IllegalArgumentException("missing username"))
}

case class Organization(
  levels : List[String] // levels from root to leaf
)

case class Credentials(
  userAndGroup  : UserAndGroup,
  roles         : List[UserRole],
  organizations : List[Organization]
) {
  def defaultOrganization: Option[Organization] = organizations.headOption
}

object Credentials {
  def fromStrings(username: String, groupname: String, roles: List[String]): Option[Credentials] =
    UserAndGroup.fromStrings(username, groupname).map { userAndGroup =>
      Credentials(userAndGroup, roles.flatMap(_.trimAllToOpt).map(SimpleRole.apply), Nil)
    }
}

object CredentialsSupport {

  private val CredentialsSessionKey = "org.orbeon.auth.credentials"

  def findCredentialsInSession(session: ExternalContext.Session): Option[Credentials] =
    session.getAttribute(CredentialsSessionKey) collect {
      case credentials: Credentials => credentials
    }

  def storeCredentialsInSession(
    session        : ExternalContext.Session,
    credentialsOpt : Option[Credentials]
  ): Unit = {
    credentialsOpt match {
      case Some(credentials) => session.setAttribute(CredentialsSessionKey, credentials)
      case None              => session.removeAttribute(CredentialsSessionKey)
    }
  }
}

/**
  * ExternalContext abstracts context, request and response information so that compile-time dependencies on the
  * Servlet API or Portlet API can be removed.
  *
  * It is also possible to use ExternalContext to embed Orbeon Forms and to provide a web-like request/response
  * interface.
  */
object ExternalContext {

  val EmbeddableParam                          = "orbeon-embeddable"

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

  trait Request extends RarelyUsedRequest {

    val ForwardContextPathOpt: Option[String]

    def getContainerType: String
    def getContainerNamespace: String

    def getPathInfo: String
    def getRequestPath: String
    def getContextPath: String
    def getServletPath: String

    def getClientContextPath(urlString: String): String
    def servicePrefix: String

    def getAttributesMap: ju.Map[String, AnyRef]
    def getHeaderValuesMap: ju.Map[String, Array[String]] // TODO: don't use `Array`
    def getParameterMap: ju.Map[String, Array[AnyRef]]
    def incomingCookies: Iterable[(String, String)]

    def getCharacterEncoding: String
    def getContentLength: Int
    def getContentType: String
    def getInputStream: InputStream

    def getScheme: String
    def getMethod: HttpMethod
    def getServerName: String
    def getServerPort: Int

    def getSession(create: Boolean): Session
    def sessionInvalidate(): Unit
    def getRequestedSessionId: String

    def credentials: Option[Credentials]

    def isUserInRole(role: String): Boolean

    def getQueryString: String
    def getRequestURI: String
    def queryStringOpt: Option[String]                         = Option(getQueryString)

    // TODO: return immutable.Map[String, List[AnyRef]] -> what about AnyRef?
    def parameters: collection.Map[String, Array[AnyRef]]      = getParameterMap.asScala
    def getFirstParamAsString(name: String): Option[String]    = parameters.get(name) flatMap (_ collectFirst { case s: String => s })
    def getFirstHeaderIgnoreCase(name: String): Option[String] = Headers.firstItemIgnoreCase(getHeaderValuesMap.asScala, name)

    def sessionOpt: Option[Session]                            = Option(getSession(create = false))
    lazy val contentLengthOpt: Option[Long]                    = Headers.firstNonNegativeLongHeaderIgnoreCase(getHeaderValuesMap.asScala, Headers.ContentLength)

    // For Java callers
    def getUsername: String = credentials.map(_.userAndGroup.username).orNull

    def getAttributeOpt(attributeNameOpt: Option[String]): Option[String] =
      attributeNameOpt
        .map(getAttributesMap.get)
        .map(_.asInstanceOf[String])
        .flatMap(Option(_))
  }

  trait RarelyUsedRequest {

    self: Request =>

    def getProtocol              : String                    // unused except forwarding and `oxf:request`
    def getRemoteHost            : String                    // unused except forwarding and `oxf:request`
    def getRemoteAddr            : String                    // unused except forwarding and `oxf:request`, also `Authorizer` passes as `Orbeon-Remote-Address`
    def getAuthType              : String                    // unused except forwarding and `oxf:request`/`oxf:request-security`
    def isSecure                 : Boolean                   // unused except forwarding and `oxf:request`/`oxf:request-security`
    def isRequestedSessionIdValid: Boolean                   // unused except forwarding
    def getLocale                : ju.Locale                 // unused except forwarding
    def getLocales               : ju.Enumeration[ju.Locale] // unused except forwarding
    def getPathTranslated        : String                    // unused except forwarding and `oxf:request`
    def getRequestURL            : String                    // unused except forwarding and `oxf:request`
    def getPortletMode           : String                    // unused except forwarding and `oxf:request` and full portlet (deprecated)
    def getWindowState           : String                    // unused except forwarding and `oxf:request` and full portlet (deprecated)
    def getNativeRequest         : AnyRef                    // unused except full portlet (deprecated)
  }

  trait Rewriter extends URLRewriter {
    def rewriteActionURL(urlString: String): String
    def rewriteRenderURL(urlString: String): String
    def rewriteActionURL(urlString: String, portletMode: String, windowState: String): String
    def rewriteRenderURL(urlString: String, portletMode: String, windowState: String): String
    def rewriteResourceURL(urlString: String, rewriteMode: UrlRewriteMode): String
    def getNamespacePrefix: String
  }

  trait Response extends Rewriter {
    def getWriter: PrintWriter // TODO: remove uses of this, see https://github.com/orbeon/orbeon-forms/issues/2962
    def getOutputStream: OutputStream

    def isCommitted: Boolean
    def reset(): Unit
    def setContentType(contentType: String): Unit
    def setStatus(status: Int): Unit
    def getStatus: Int
    def setContentLength(len: Int): Unit
    def setHeader(name: String, value: String): Unit
    def addHeader(name: String, value: String): Unit

    def addHeaders(headers: Map[String, List[String]]): Unit =
      for {
        (name, values) <- headers
        value          <- values
      } locally {
        addHeader(name, value)
      }

    def sendError(code: Int): Unit

    def getCharacterEncoding: String

    def sendRedirect(location: String, isServerSide: Boolean, isExitPortal: Boolean): Unit

    def setPageCaching(lastModified: Long, pathType: PathType): Unit

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
    def sessionDestroyed(session: Session): Unit
  }

  trait Session {
    def getCreationTime: Long
    def getId: String
    def getLastAccessedTime: Long
    def getMaxInactiveInterval: Int
    def invalidate(): Unit
    def isNew: Boolean
    def setMaxInactiveInterval(interval: Int): Unit

    def addListener(sessionListener: SessionListener): Unit
    def removeListener(sessionListener: SessionListener): Unit

    def getAttribute     (name: String               , scope: SessionScope = SessionScope.Local): Option[AnyRef]
    def setAttribute     (name: String, value: AnyRef, scope: SessionScope = SessionScope.Local): Unit
    def removeAttribute  (name: String               , scope: SessionScope = SessionScope.Local): Unit
    def getAttributeNames(                             scope: SessionScope = SessionScope.Local): List[String]
    def javaGetAttribute (name: String                                                         ): AnyRef = getAttribute(name).orNull
    def javaGetAttribute (name: String               , scope: SessionScope                     ): AnyRef = getAttribute(name, scope).orNull
    def javaSetAttribute (name: String, value: AnyRef                                          ): Unit   = setAttribute(name, value)

    def getNativeSession: AnyRef
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

  def getStartLoggerString: String = {

    val pathQuery =
      PathUtils.appendQueryString(
        getRequest.getRequestURI,
        getRequest.queryStringOpt.getOrElse("")
      )

    s"Start request: ${getRequest.getMethod} $pathQuery"
  }

  def getEndLoggerString: String = {

    val pathQuery =
      PathUtils.appendQueryString(
        getRequest.getRequestURI,
        getRequest.queryStringOpt.getOrElse("")
      )

    s"End request:   ${getRequest.getMethod} $pathQuery ${getResponse.getStatus}"
  }
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
    webAppListenersOption.iterator flatMap (_.iterator) foreach { listener =>
      try {
        listener.webAppDestroyed()
      } catch {
        case NonFatal(t) => log("Throwable caught when calling listener", t)
      }
    }

  private def webAppListenersOption: Option[WebAppListeners] =
    attributes.get(WebAppContext.WebAppListeners) map (_.asInstanceOf[WebAppListeners])
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
    _listeners.reverseIterator
}

private object WebAppContext {
  val WebAppListeners = "oxf.webapp.listeners"
}