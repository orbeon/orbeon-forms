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

import collection.JavaConverters._
import java.net.URI
import org.orbeon.exception.OrbeonFormatter
import org.orbeon.oxf.pipeline.api.ExternalContext.Request
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.util._
import org.orbeon.oxf.resources.URLFactory
import org.orbeon.oxf.externalcontext.URLRewriter
import java.lang.{Boolean ⇒ JBoolean}
import org.orbeon.oxf.properties.PropertySet

object Authorizer extends Logging {

    import PageFlowControllerProcessor._
    import URLRewriter._

    private val HeadersToFilter = Set("transfer-encoding", "connection", "host", "content-length")
    private val AuthorizedKey = "org.orbeon.oxf.controller.service.authorized"

    // Check the session to see if the request is already authorized. If not, try to authorize, and remember the
    // authorization if successful. Return whether the request
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
            val newlyAuthorized = authorize(request)
            if (newlyAuthorized)
                rememberAuthorized()
            newlyAuthorized
        } else
            true
    }

    // Authorize the given request with the given delegate service
    def authorize(request: Request)(implicit logger: IndentedLogger, propertySet: PropertySet) =
        delegateAbsoluteBaseURIOpt(request) match {
            case Some(baseDelegateURI) ⇒
                // Forward method and headers but not the body
                val method  = request.getMethod
                val headers = request.getHeaderValuesMap.asScala filterNot { case (name, _) ⇒ HeadersToFilter(name) }
                val body    = if (Connection.requiresRequestBody(method)) Some(Array[Byte]()) else None

                val newURL  = appendToURI(baseDelegateURI, request.getRequestPath, Option(request.getQueryString)).toString

                debug("Delegating to authorizer", Seq("url" → newURL))

                val connection = Connection(method, URLFactory.createURL(newURL), None, body, headers, loadState = true, logBody = false)

                def isAuthorized(result: ConnectionResult) = NetUtils.isSuccessCode(result.statusCode)

                // TODO: state must be saved in session, not anywhere else; why is this configurable globally?
                try useAndClose(connection.connect(saveState = true))(isAuthorized)
                catch {
                    case t: Throwable ⇒
                        error("Could not connect to authorizer", Seq("url" → newURL))
                        error(OrbeonFormatter.format(t))
                        false
                }
            case None ⇒
                // No authorizer
                debug("No authorizer configured")
                false
        }

    private def appendToURI(uri: URI, path: String, query: Option[String]) = {

        val newPath  = dropTrailingSlash(uri.getPath) + appendStartingSlash(path)
        val newQuery = Option(uri.getQuery) ++ query mkString "&"

        new URI(uri.getScheme, uri.getUserInfo, uri.getHost, uri.getPort, newPath, if (newQuery.nonEmpty) newQuery else null, null)
    }

    // NOTE: If the authorizer base URL is an absolute path, it is rewritten against the host
    private def delegateAbsoluteBaseURIOpt(request: Request)(implicit propertySet: PropertySet) =
        Option(propertySet.getStringOrURIAsString(AuthorizerProperty)) map
            (p ⇒ new URI(URLRewriterUtils.rewriteServiceURL(request, p, REWRITE_MODE_ABSOLUTE_NO_CONTEXT)))
}
