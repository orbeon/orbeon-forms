/**
 *  Copyright (C) 2012 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.controller

import java.lang.{Boolean => JBoolean}
import java.net.URI
import org.orbeon.exception.OrbeonFormatter
import org.orbeon.oxf.externalcontext.{ExternalContext, UrlRewriteMode}
import org.orbeon.oxf.http.Headers._
import org.orbeon.oxf.http.HttpMethod.HttpMethodsWithRequestBody
import org.orbeon.oxf.http.{EmptyInputStream, HttpStatusCodeException, StreamedContent}
import org.orbeon.oxf.properties.PropertySet
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.PathUtils._
import org.orbeon.oxf.util._

import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal


object Authorizer extends Logging {

  import org.orbeon.oxf.controller.PageFlowControllerProcessor._

  // For now don't remember authorization, because simply remembering in the session is not enough if the authorization
  // depends on the request method, path, or headers.
  private val RememberAuthorization = false
  private val AuthorizedKey = "org.orbeon.oxf.controller.service.authorized"

  // Whether the incoming request is authorized either with a token or via the delegate
  def authorized(
    ec          : ExternalContext)(implicit
    logger      : IndentedLogger,
    propertySet : PropertySet
  ): Boolean =
    authorizedWithToken(ec) || (
      if (RememberAuthorization)
        authorizeIfNeededAndRemember(ec)
      else
        authorizedWithDelegate(ec)
    )

  // Whether the incoming request is authorized with a token
  def authorizedWithToken(ec: ExternalContext): Boolean =
    authorizedWithToken(k => Option(ec.getRequest.getHeaderValuesMap.get(k)), k => ec.getWebAppContext.attributes.get(k))

  def authorizedWithToken(header: String => Option[Array[String]], attribute: String => Option[AnyRef]): Boolean = {

    val requestToken =
      header(OrbeonTokenLower).toList.flatten.headOption

    def applicationToken =
      attribute(OrbeonTokenLower) collect { case token: String => token }

    requestToken.isDefined && requestToken == applicationToken
  }

  // Check the session to see if the request is already authorized. If not, try to authorize, and remember the
  // authorization if successful. Return whether the request is authorized.
  private def authorizeIfNeededAndRemember(
    ec          : ExternalContext)(implicit
    logger      : IndentedLogger,
    propertySet : PropertySet
  ): Boolean = {

    val request = ec.getRequest

    def alreadyAuthorized: Boolean =
      request.sessionOpt flatMap
      (_.getAttribute(AuthorizedKey)) collect
      { case value: JBoolean => value.booleanValue() } exists
      identity

    def rememberAuthorized(): Unit =
      Option(request.getSession(true)) foreach
      (_.setAttribute(AuthorizedKey, JBoolean.TRUE))

    if (! alreadyAuthorized) {
      val newlyAuthorized = authorizedWithDelegate(ec)
      if (newlyAuthorized)
        rememberAuthorized()
      newlyAuthorized
    } else
      true
  }

  // Authorize the given request with the given delegate service
  private def authorizedWithDelegate(
    ec          : ExternalContext)(implicit
    logger      : IndentedLogger,
    propertySet : PropertySet
  ): Boolean = {

    val request = ec.getRequest

    def appendToURI(uri: URI, path: String, query: Option[String]) = {

      val newPath  = uri.getRawPath.dropTrailingSlash + path.prependSlash
      val newQuery = Option(uri.getRawQuery) ++ query mkString "&"

      new URI(uri.getScheme, uri.getRawUserInfo, uri.getHost, uri.getPort, newPath, if (newQuery.nonEmpty) newQuery else null, null)
    }

    // NOTE: If the authorizer base URL is an absolute path, it is rewritten against the host
    def delegateAbsoluteBaseURIOpt =
      propertySet.getStringOrURIAsStringOpt(AuthorizerProperty) map
        (p => new URI(URLRewriterUtils.rewriteServiceURL(request, p, UrlRewriteMode.AbsoluteNoContext)))

    delegateAbsoluteBaseURIOpt match {
      case Some(baseDelegateURI) =>
        // Forward method and headers but not the body

        // NOTE: There is a question of whether we need to forward cookies for authorization purposes. If we
        // do, there is the issue of the first incoming request which doesn't have incoming cookies. So at this
        // point, we just follow the header proxying method we use in other places and remove Cookie/Set-Cookie.

        val method  = request.getMethod
        val newURL  = appendToURI(baseDelegateURI, request.getRequestPath, Option(request.getQueryString))

        // Add remote address to help authorizer filter
        val allHeaders = {

          val proxiedHeaders =
            proxyAndCapitalizeHeaders(request.getHeaderValuesMap.asScala, request = true).toMap mapValues (_.toList)

          proxiedHeaders + (OrbeonRemoteAddress -> Option(request.getRemoteAddr).toList)
        }

        val content =
          HttpMethodsWithRequestBody(method) option
            StreamedContent(
              EmptyInputStream,
              Some("application/octet-stream"),
              Some(0L),
              None
            )

        debug("Delegating to authorizer", Seq("url" -> newURL.toString))

        val cxr =
          Connection.connectNow(
            method          = method,
            url             = newURL,
            credentials     = None,
            content         = content,
            headers         = allHeaders,
            loadState       = true,
            saveState       = true,
            logBody         = false)(
            logger          = logger,
            externalContext = ec
          )

        // TODO: state must be saved in session, not anywhere else; why is this configurable globally?
        try
          ConnectionResult.withSuccessConnection(cxr, closeOnSuccess = true)(_ => true)
        catch {
          case HttpStatusCodeException(code, _, _) =>
            debug("Unauthorized", Seq("code" -> code.toString))
            false
          case NonFatal(t) =>
            error("Could not connect to authorizer", Seq("url" -> newURL.toString))
            error(OrbeonFormatter.format(t))
            false
        }
      case None =>
        // No authorizer
        debug("No authorizer configured")
        false
    }
  }
}
