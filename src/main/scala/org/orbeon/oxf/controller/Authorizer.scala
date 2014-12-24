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

import java.lang.{Boolean ⇒ JBoolean}
import java.net.URI

import org.orbeon.exception.OrbeonFormatter
import org.orbeon.oxf.externalcontext.URLRewriter._
import org.orbeon.oxf.http.Headers._
import org.orbeon.oxf.http.{EmptyInputStream, StreamedContent}
import org.orbeon.oxf.pipeline.api.ExternalContext
import org.orbeon.oxf.pipeline.api.ExternalContext.Request
import org.orbeon.oxf.properties.PropertySet
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.util._
import org.orbeon.oxf.webapp.HttpStatusCodeException

import scala.collection.JavaConverters._
import scala.util.control.NonFatal

object Authorizer extends Logging {

    import org.orbeon.oxf.controller.PageFlowControllerProcessor._

    // For now don't remember authorization, because simply remembering in the session is not enough if the authorization
    // depends on the request method, path, or headers.
    private val RememberAuthorization = false
    private val AuthorizedKey = "org.orbeon.oxf.controller.service.authorized"

    // Whether the incoming request is authorized either with a token or via the delegate
    def authorized(ec: ExternalContext)(implicit logger: IndentedLogger, propertySet: PropertySet) =
        authorizedWithToken(ec) || (if (RememberAuthorization) authorizeIfNeededAndRemember(ec.getRequest) else authorizedWithDelegate(ec.getRequest))

    // Whether the incoming request is authorized with a token
    def authorizedWithToken(ec: ExternalContext): Boolean =
        authorizedWithToken(k ⇒ Option(ec.getRequest.getHeaderValuesMap.get(k)), k ⇒ ec.getWebAppContext.attributes.get(k))

    def authorizedWithToken(header: String ⇒ Option[Array[String]], attribute: String ⇒ Option[AnyRef]): Boolean = {

        val requestToken =
            header(OrbeonTokenLower).toList.flatten.headOption

        def applicationToken =
            attribute(OrbeonTokenLower) collect { case token: String ⇒ token }

        requestToken.isDefined && requestToken == applicationToken
    }

    // Check the session to see if the request is already authorized. If not, try to authorize, and remember the
    // authorization if successful. Return whether the request is authorized.
    def authorizeIfNeededAndRemember(request: Request)(implicit logger: IndentedLogger, propertySet: PropertySet) = {

        def alreadyAuthorized =
            Option(request.getSession(false)) flatMap
            (_.getAttributesMap.asScala.get(AuthorizedKey)) collect
            { case value: JBoolean ⇒ value.booleanValue() } exists
            identity

        def rememberAuthorized() =
            Option(request.getSession(true)) foreach
            (_.getAttributesMap.put(AuthorizedKey, JBoolean.TRUE))

        if (! alreadyAuthorized) {
            val newlyAuthorized = authorizedWithDelegate(request)
            if (newlyAuthorized)
                rememberAuthorized()
            newlyAuthorized
        } else
            true
    }

    // Authorize the given request with the given delegate service
    def authorizedWithDelegate(request: Request)(implicit logger: IndentedLogger, propertySet: PropertySet) = {

        def appendToURI(uri: URI, path: String, query: Option[String]) = {

            val newPath  = dropTrailingSlash(uri.getRawPath) + appendStartingSlash(path)
            val newQuery = Option(uri.getRawQuery) ++ query mkString "&"

            new URI(uri.getScheme, uri.getRawUserInfo, uri.getHost, uri.getPort, newPath, if (newQuery.nonEmpty) newQuery else null, null)
        }

        // NOTE: If the authorizer base URL is an absolute path, it is rewritten against the host
        def delegateAbsoluteBaseURIOpt =
            Option(propertySet.getStringOrURIAsString(AuthorizerProperty)) map
                (p ⇒ new URI(URLRewriterUtils.rewriteServiceURL(request, p, REWRITE_MODE_ABSOLUTE_NO_CONTEXT)))

        delegateAbsoluteBaseURIOpt match {
            case Some(baseDelegateURI) ⇒
                // Forward method and headers but not the body

                // NOTE: There is a question of whether we need to forward cookies for authorization purposes. If we
                // do, there is the issue of the first incoming request which doesn't have incoming cookies. So at this
                // point, we just follow the header proxying method we use in other places and remove Cookie/Set-Cookie.

                val method  = request.getMethod
                val newURL  = appendToURI(baseDelegateURI, request.getRequestPath, Option(request.getQueryString))
                val headers = proxyAndCapitalizeHeaders(request.getHeaderValuesMap.asScala, request = true)

                val content = Connection.requiresRequestBody(method) option
                    StreamedContent(
                        EmptyInputStream,
                        Some("application/octet-stream"),
                        Some(0L),
                        None
                    )

                debug("Delegating to authorizer", Seq("url" → newURL.toString))

                val connection =
                    Connection(
                        httpMethodUpper = method,
                        url             = newURL,
                        credentials     = None,
                        content         = content,
                        headers         = headers.toMap mapValues (_.toList),
                        loadState       = true,
                        logBody         = false
                    )

                // TODO: state must be saved in session, not anywhere else; why is this configurable globally?
                try ConnectionResult.withSuccessConnection(connection.connect(saveState = true), closeOnSuccess = true)(_ ⇒ true)
                catch {
                    case HttpStatusCodeException(code, _, _) ⇒
                        debug("Unauthorized", Seq("code" → code.toString))
                        false
                    case NonFatal(t) ⇒
                        error("Could not connect to authorizer", Seq("url" → newURL.toString))
                        error(OrbeonFormatter.format(t))
                        false
                }
            case None ⇒
                // No authorizer
                debug("No authorizer configured")
                false
        }
    }
}
