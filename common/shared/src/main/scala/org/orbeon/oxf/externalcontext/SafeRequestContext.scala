package org.orbeon.oxf.externalcontext

import org.orbeon.oxf.http.HttpMethod

import java.util as ju
import scala.jdk.CollectionConverters.*


// This is used to pass information that does not keep a handle on an incoming `Request` object. This is necessary for
// asynchronous calls in particular. See https://github.com/orbeon/orbeon-forms/issues/6760.
//
// This is generally immutable, except:
//
// 1. In order to be able to recreate an `ExternalContext` from this, we keep a handle on the `WebAppContext`. This
//    typically keeps a handle on a native `ServletContext`, which has mutation, specifically for attributes.
// 2. We also keep a handle on a `Session` object, which is necessary for session-related operations. The session also
//    contains mutable attributes.
case class SafeRequestContext(
  scheme            : String,
  method            : HttpMethod,
  serverName        : String,
  serverPort        : Int,
  requestPath       : String,
  servicePrefix     : String,
  contextPath       : String,
  cookies           : Iterable[(String, String)],
  credentials       : Option[Credentials],
  requestedSessionId: Option[String],
  containerType     : String,
  containerNamespace: String,
  attributes        : Map[String, AnyRef],
  other             : RarelyUsedRequestContext,
)(
  val webAppContext : WebAppContext,
  session           : Option[ExternalContext.Session]
) {

  def findSessionOrThrow(create: Boolean): Option[ExternalContext.Session] =
    session match {
      case some @ Some(_)    => some
      case None if ! create  => None
      case None              => throw new IllegalStateException("Session creation not supported")
    }
}

case class RarelyUsedRequestContext(
  protocol               : String,
  remoteHost             : String,
  remoteAddr             : String,
  authType               : String,
  secure                 : Boolean,
  requestedSessionIdValid: Boolean,
  locale                 : ju.Locale,
  locales                : List[ju.Locale],
  pathTranslated         : String,
  requestURL             : String,
  portletMode            : String,
  windowState            : String,
)

object SafeRequestContext {

  def apply(
    externalContext: ExternalContext
  ): SafeRequestContext =
    apply(
      webAppContext = externalContext.getWebAppContext,
      request       = externalContext.getRequest
    )

  def apply(
    webAppContext: WebAppContext,
    request      : ExternalContext.Request,
  ): SafeRequestContext =
    SafeRequestContext(
      scheme             = request.getScheme,
      method             = request.getMethod,
      serverName         = request.getServerName,
      serverPort         = request.getServerPort,
      requestPath        = request.getRequestPath,
      servicePrefix      = request.servicePrefix,
      contextPath        = request.getContextPath,
      cookies            = request.incomingCookies,
      credentials        = request.credentials,
      requestedSessionId = Option(request.getRequestedSessionId),
      containerType      = request.getContainerType,
      containerNamespace = request.getContainerNamespace,
      attributes         = Map.from(request.getAttributesMap.asScala), // make copy as original `ju.HashMap` can point to request
      other              =
        RarelyUsedRequestContext(
          protocol                = request.getProtocol,
          remoteHost              = request.getRemoteHost,
          remoteAddr              = request.getRemoteAddr,
          authType                = request.getAuthType,
          secure                  = request.isSecure,
          requestedSessionIdValid = request.isRequestedSessionIdValid,
          locale                  = request.getLocale,
          locales                 = request.getLocales.asScala.toList,
          pathTranslated          = request.getPathTranslated,
          requestURL              = request.getRequestURL,
          portletMode             = request.getPortletMode,
          windowState             = request.getWindowState,
        )
    )(
      webAppContext      = webAppContext,
      session            = Option(request.getSession(create = false))
    )
}
