/**
 * Copyright (C) 2012 Orbeon, Inc.
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
package org.orbeon.oxf.util

import ScalaUtils._
import Headers._
import collection.JavaConverters._
import java.net.{URI, URLConnection, URL}
import java.util.{Map ⇒ JMap}
import javax.servlet.http.{Cookie, HttpServletRequest}
import org.apache.http.client.CookieStore
import org.apache.log4j.Level
import org.orbeon.oxf.common.{ValidationException, OXFException}
import org.orbeon.oxf.pipeline.api.ExternalContext
import org.orbeon.oxf.properties.Properties
import org.orbeon.oxf.resources.handler.HTTPURLConnection
import org.orbeon.oxf.xml.XMLUtils
import org.orbeon.oxf.xml.dom4j.LocationData
import org.apache.commons.lang3.StringUtils
import Connection._
import scala.util.control.NonFatal

/**
 * Connection to a URL.
 *
 * Handles:
 *
 * - PUTting or POSTing a body
 * - credentials
 * - HTTP headers
 * - forwarding session cookies
 * - forwarding HTTP headers
 * - managing SOAP POST and GET a la XForms 1.1 (should this be here?)
 */
class Connection(
        httpMethod: String,
        connectionURL: URL,
        credentials: Option[Credentials],
        requestBody: Option[Array[Byte]],
        headers: collection.Map[String, Array[String]],
        logBody: Boolean)(implicit logger: IndentedLogger)
    extends ConnectionState with Logging {

    require(StringUtils.isAllUpperCase(httpMethod))

    // Open the connection. This sends request headers, request body, and reads status and response headers.
    def connect(saveState: Boolean): ConnectionResult = {
        val scheme = connectionURL.getProtocol

        try {
            if (httpMethod == "GET" && Set("file", "oxf")(scheme)) {
                // GET with file: or oxf:

                // Create URL connection object
                val urlConnection = connectionURL.openConnection
                urlConnection.connect()

                // Create result
                val connectionResult = new ConnectionResult(connectionURL.toExternalForm)

                connectionResult.statusCode = 200
                connectionResult.responseHeaders = urlConnection.getHeaderFields
                connectionResult.setLastModified(NetUtils.getLastModifiedAsLong(urlConnection))
                connectionResult.setResponseContentType(urlConnection.getContentType, "application/xml")
                connectionResult.setResponseInputStream(urlConnection.getInputStream)

                // Log response details except body
                if (debugEnabled)
                    connectionResult.logResponseDetailsIfNeeded(logger, Level.DEBUG, "")

                // Log response body
                if (debugEnabled)
                    connectionResult.logResponseBody(logger, Level.DEBUG, "", logBody)

                connectionResult

            } else if (isHTTPOrHTTPS(scheme)) {
                // Any method with http: or https:

                // Create URL connection object
                val httpURLConnection = connectionURL.openConnection.asInstanceOf[HTTPURLConnection]

                // Configure HTTPURLConnection
                httpURLConnection.setDoOutput(requestBody.isDefined)
                httpURLConnection.setCookieStore(cookieStoreOption.orNull)
                httpURLConnection.setRequestMethod(httpMethod)

                // Set credentials if any
                credentials foreach { credentials ⇒
                    httpURLConnection.setUsername(credentials.username)
                    if (credentials.password ne null)
                        httpURLConnection.setPassword(credentials.password)
                    if (credentials.preemptiveAuthentication ne null)
                        httpURLConnection.setPreemptiveAuthentication(credentials.preemptiveAuthentication)
                    if (credentials.domain ne null)
                        httpURLConnection.setDomain(credentials.domain)
                }

                // Set headers on connection
                val capitalizedHeaders = setHeaders(headers, httpURLConnection)

                // Set request body if any
                requestBody foreach { messageBody ⇒

                    if (logBody) {
                        val contentType = headers.get("content-type") flatMap (_.lift(0)) getOrElse "application/octet-stream"
                        logRequestBody(logger, contentType, messageBody)
                    }

                    httpURLConnection.setRequestBody(messageBody)
                }

                ifDebug {

                    def replacePassword(s: String) = {
                        val colonIndex = s.indexOf(':')
                        if (colonIndex != -1)
                            s.substring(0, colonIndex + 1) + "xxxxxxxx"
                        else
                            s
                    }

                    val connectionURI =
                        new URI(
                            connectionURL.getProtocol,
                            Option(connectionURL.getUserInfo) map replacePassword orNull,
                            connectionURL.getHost,
                            connectionURL.getPort,
                            connectionURL.getPath,
                            connectionURL.getQuery,
                            connectionURL.getRef)

                    debug("opening URL connection",
                        Seq("method" → httpMethod, "URL" → connectionURI.toString) ++ capitalizedHeaders)
                }

                // Connect
                httpURLConnection.connect()

                // Get state if possible
                // This is either the state we set above before calling connect(), or a new state if we didn't provide any
                cookieStoreOption = Option(httpURLConnection.getCookieStore)

                // Create result
                val connectionResult = new ConnectionResult(connectionURL.toExternalForm) {
                    override def close(): Unit = {
                        super.close()
                        httpURLConnection.disconnect()
                    }
                }

                // Get response information
                connectionResult.statusCode = httpURLConnection.getResponseCode
                connectionResult.responseHeaders = httpURLConnection.getHeaderFields
                connectionResult.setLastModified(NetUtils.getLastModifiedAsLong(httpURLConnection))
                connectionResult.setResponseContentType(httpURLConnection.getContentType, "application/xml")
                connectionResult.setResponseInputStream(httpURLConnection.getInputStream)

                ifDebug {
                    connectionResult.logResponseDetailsIfNeeded(logger, Level.DEBUG, "")
                    connectionResult.logResponseBody(logger, Level.DEBUG, "", logBody)
                }

                // Save state if possible
                if (saveState)
                    saveHttpState()

                connectionResult

            } else if (httpMethod != "GET" && Set("file", "oxf")(scheme)) {
                // Writing to file: and oxf: SHOULD be supported
                throw new OXFException("submission URL scheme not yet implemented: " + scheme)
            } else if (scheme == "mailto") {
                // MAY be supported
                throw new OXFException("submission URL scheme not yet implemented: " + scheme)
            } else {
                throw new OXFException("submission URL scheme not supported: " + scheme)
            }
        } catch {
            case NonFatal(t) ⇒ throw new ValidationException(t, new LocationData(connectionURL.toExternalForm, -1, -1))
        }
    }
}

trait ConnectionState {
    self: Connection ⇒

    import ConnectionState._

    private val stateScope = stateScopeFromProperty
    var cookieStoreOption: Option[CookieStore] = None

    def loadHttpState()(implicit logger: IndentedLogger): Unit = {
        cookieStoreOption = stateAttributes(createSession = false) flatMap (m ⇒ Option(m.get(HttpCookieStoreAttribute).asInstanceOf[CookieStore]))
        debugStore("loaded HTTP state", "did not load HTTP state")
    }

    def saveHttpState()(implicit logger: IndentedLogger): Unit = {
        cookieStoreOption foreach { cookieStore ⇒
            stateAttributes(createSession = true) foreach (_.put(HttpCookieStoreAttribute, cookieStore))
        }
        debugStore("saved HTTP state", "did not save HTTP state")
    }

    private def debugStore(positive: String, negative: String)(implicit logger: IndentedLogger) =
        ifDebug {
            cookieStoreOption match {
                case Some(cookieStore) ⇒
                    val cookies = cookieStore.getCookies.asScala map (_.getName) mkString " | "
                    debug(positive, Seq(
                        "scope" → stateScope,
                        "cookie names" → (if (cookies.nonEmpty) cookies else null)))
                case None ⇒
                    debug(negative)
            }
        }

    private def stateAttributes(createSession: Boolean) = {
        val externalContext = NetUtils.getExternalContext
        stateScope match {
            case "request" ⇒
                Some(externalContext.getRequest.getAttributesMap)
            case "session" if externalContext.getSession(createSession) ne null ⇒
                Some(externalContext.getSession(createSession).getAttributesMap)
            case "application" ⇒
                Some(externalContext.getWebAppContext.getAttributesMap)
            case _ ⇒
                None
        }
    }
}

private object ConnectionState {

    def stateScopeFromProperty = {
        val propertySet = Properties.instance.getPropertySet
        val scopeString = propertySet.getString(HttpStateProperty, DefaultStateScope)

        if (AllScopes(scopeString)) scopeString else DefaultStateScope
    }

    val DefaultStateScope = "session"
    val HttpStateProperty = "oxf.http.state"
    val HttpCookieStoreAttribute = "oxf.http.cookie-store"

    val AllScopes = Set("none", "request", "session", "application")
}

object Connection extends Logging {

    val TokenKey = "orbeon-token"
    val AuthorizationHeader = "Authorization"

    val EmptyHeaders = Map.empty[String, Array[String]]

    private val HttpForwardCookiesProperty = "oxf.http.forward-cookies"
    private val HttpForwardHeadersProperty = "oxf.http.forward-headers"

    case class Credentials(username: String, password: String, preemptiveAuthentication: String, domain: String) {
        assert(username ne null)
        def getPrefix = Option(password) map (username + ":" + _ + "@") getOrElse username + "@"
    }

    // Whether the given method requires a request body
    def requiresRequestBody(method: String) = Set("POST", "PUT")(method)

    // Whether the given scheme requires setting headers
    def requiresHeaders(scheme: String) = ! Set("file", "oxf")(scheme)

    // Whether the scheme is http: or https:
    def isHTTPOrHTTPS(scheme: String) = Set("http", "https")(scheme)

    // Build all the connection headers
    def buildConnectionHeaders(
            scheme: String,
            credentials: Option[Credentials],
            headers: Map[String, Array[String]],
            headersToForward: Option[String])(logger: IndentedLogger): Map[String, Array[String]] =
        if (requiresHeaders(scheme))
            buildConnectionHeaders(credentials, headers, headersToForward)(logger)
        else
            EmptyHeaders

    // For Java callers
    def jBuildConnectionHeaders(
            scheme: String,
            credentialsOrNull: Credentials,
            headersOrNull: JMap[String, Array[String]],
            headersToForward: String,
            logger: IndentedLogger): Map[String, Array[String]] =
        buildConnectionHeaders(scheme, Option(credentialsOrNull), Option(headersOrNull) map (_.asScala.toMap) getOrElse EmptyHeaders, Option(headersToForward))(logger)

    // For Java callers
    def buildConnectionHeadersWithSOAP(
            httpMethod: String,
            credentialsOrNull: Credentials,
            mediatype: String,
            encodingForSOAP: String,
            headersOrNull: Map[String, Array[String]],
            headersToForward: String,
            logger: IndentedLogger): Map[String, Array[String]] = {

        // "If a header element defines the Content-Type header, then this setting overrides a Content-type set by the
        // mediatype attribute"
        val headersWithContentType = {

            val headers = Option(headersOrNull) map (_.toMap) getOrElse EmptyHeaders

            if (requiresRequestBody(httpMethod) && ! headers.contains("content-type"))
                headers + ("content-type" → Array(mediatype ensuring (_ ne null)))
            else
                headers
        }

        // Also make sure that if a header element defines Content-Type, this overrides the mediatype attribute
        def soapMediatypeWithContentType = headersWithContentType.getOrElse("content-type", Array(mediatype)) head

        // NOTE: SOAP processing overrides Content-Type in the case of a POST
        // So we have: @serialization → @mediatype →  xf:header → SOAP
        buildConnectionHeaders(Option(credentialsOrNull), headersWithContentType, Option(headersToForward))(logger) ++
            soapHeaders(httpMethod, soapMediatypeWithContentType, encodingForSOAP)(logger)
    }

    // For Java callers
    def jApply(
            httpMethod: String,
            connectionURL: URL,
            credentialsOrNull: Credentials,
            messageBodyOrNull: Array[Byte],
            headers: Map[String, Array[String]],
            loadState: Boolean,
            logBody: Boolean,
            logger: IndentedLogger): Connection = {

        val messageBody: Option[Array[Byte]] =
            if (requiresRequestBody(httpMethod)) Option(messageBodyOrNull) orElse Some(Array()) else None

        apply(httpMethod, connectionURL, Option(credentialsOrNull), messageBody, headers, loadState, logBody)(logger)
    }

    // Create a new Connection
    def apply(
            httpMethod: String,
            connectionURL: URL,
            credentials: Option[Credentials],
            messageBody: Option[Array[Byte]],
            headers: collection.Map[String, Array[String]],
            loadState: Boolean,
            logBody: Boolean)(implicit logger: IndentedLogger): Connection = {

        require(! requiresRequestBody(httpMethod) || messageBody.isDefined)

        val connection =
            new Connection(httpMethod, connectionURL, credentials, messageBody, headers, logBody)

        // Get connection state if possible
        if (loadState && isHTTPOrHTTPS(connectionURL.getProtocol))
            connection.loadHttpState()

        connection
    }

    // Get a space-separated list of header names to forward from the configuration properties
    def getForwardHeaders: String = {
        val propertySet = Properties.instance.getPropertySet
        propertySet.getString(HttpForwardHeadersProperty, "")
    }

    // Get a list of cookie names to forward from the configuration properties
    def getForwardCookies: List[String] = {
        val propertySet = Properties.instance.getPropertySet
        val stringValue = propertySet.getString(HttpForwardCookiesProperty, "JSESSIONID JSESSIONIDSSO")

        stringValue split """\s+""" toList
    }

    /**
     * Build connection headers to send given:
     *
     * - the incoming request if present
     * - a list of headers names and values to set
     * - credentials information
     * - a list of headers to forward
     *
     * NOTE: All header names returned are lowercase.
     */
    def buildConnectionHeaders(
            credentials: Option[Credentials],
            headers: Map[String, Array[String]],
            headersToForward: Option[String])(implicit logger: IndentedLogger): Map[String, Array[String]] = {

        val externalContext = NetUtils.getExternalContext

        // 1. Caller-specified list of headers to forward based on a space-separated list of header names
        val headersToForwardLowercase =
            Option(externalContext.getRequest) match {
                case Some(request) ⇒

                    val forwardHeaderNamesLowercase = stringOptionToSet(headersToForward) map (_.toLowerCase)

                    // NOTE: Forwarding the "Cookie" header may yield unpredictable results because of the above work done w/ session cookies
                    val requestHeaderValuesMap = request.getHeaderValuesMap.asScala

                    def canForwardHeader(name: String) = {
                        // Only forward Authorization header if there is no credentials provided
                        val canForward = ! name.equalsIgnoreCase(AuthorizationHeader) || credentials.isEmpty

                        if (! canForward)
                            debug("not forwarding Authorization header because credentials are present")

                        canForward
                    }

                    for {
                        nameLowercase ← forwardHeaderNamesLowercase.toList
                        values ← requestHeaderValuesMap.get(nameLowercase)
                        if canForwardHeader(nameLowercase)
                    } yield {
                        debug("forwarding header", Seq("name" → nameLowercase, "value" → (values mkString " ")))
                        nameLowercase → values
                    }
                case None ⇒
                    Seq()
            }

        // 2. Explicit caller-specified header name/values
        val explicitHeadersLowercase = headers map { case (k, v) ⇒ k.toLowerCase → v }

        // 3. Forward cookies for session handling only if no credentials have been explicitly set
        val newCookieHeader = credentials match {
            case None    ⇒ sessionCookieHeader(externalContext)
            case Some(_) ⇒ None
        }

        // 4. Authorization token
        val tokenHeader = {

            // Get token from web app scope
            val token = externalContext.getWebAppContext.attributes.getOrElseUpdate(TokenKey, SecureUtils.randomHexId).asInstanceOf[String]

            Seq(TokenKey → Array(token))
        }

        // Don't forward headers for which a value is explicitly passed by the caller, so start with headersToForward
        // New cookie header, if present, overrides any existing cookies
        headersToForwardLowercase.toMap ++ explicitHeadersLowercase ++ newCookieHeader ++ tokenHeader
    }

    private def sessionCookieHeader(externalContext: ExternalContext)(implicit logger: IndentedLogger): Option[(String, Array[String])] = {
        // NOTE: We use a property, as some app servers like WebLogic allow configuring the session cookie name.
        val cookiesToForward = getForwardCookies
        if (cookiesToForward.nonEmpty) {

            // By convention, the first cookie name is the session cookie
            val sessionCookieName = cookiesToForward(0)

            // NOTES 2011-01-22:
            //
            // If this is requested when a page is generated, it turns out we cannot rely on a JSESSIONID that makes
            // sense right after authentication, even in the scenario where the JSESSIONID is clean, because Tomcat
            // replays the initial request. In other words the JSESSIONID cookie can be stale.
            //
            // This means that the forwarding done below often doesn't make sense.
            //
            // We could possibly allow it only for XForms Ajax/page updates, where the probability that JSESSIONID is
            // correct is greater.
            //
            // A stronger fix might be to simply disable JSESSIONID forwarding, or support a stronger SSO option.
            //
            // See: http://forge.ow2.org/tracker/?func=detail&atid=350207&aid=315104&group_id=168
            //      https://issues.apache.org/bugzilla/show_bug.cgi?id=50633
            //

            // TODO: ExternalContext must provide direct access to cookies
            val requestOption = Option(externalContext.getRequest)
            val nativeRequestOption =
                requestOption flatMap
                (r ⇒ Option(r.getNativeRequest)) collect
                { case r: HttpServletRequest ⇒ r }

            // 1. If there is an incoming JSESSIONID cookie, use it. The reason is that there is not necessarily an
            // obvious mapping between "session id" and JSESSIONID cookie value. With Tomcat, this works, but with e.g.
            // WebSphere, you get session id="foobar" and JSESSIONID=0000foobar:-1. So we must first try to get the
            // incoming JSESSIONID. To do this, we get the cookie, then serialize it as a header.
            def fromIncoming = nativeRequestOption flatMap (sessionCookieFromIncoming(externalContext, _, cookiesToForward, sessionCookieName))

            // 2. If there is no incoming session cookie, try to make our own cookie. This may fail with e.g. WebSphere.
            def fromSession = sessionCookieFromGuess(externalContext, sessionCookieName)

            // Logging
            ifDebug {
                val incomingSessionHeaders =
                    for {
                        request ← requestOption.toList
                        cookieHeaders ← request.getHeaderValuesMap.asScala.get("cookie").toList
                        cookieValue   ← cookieHeaders
                        if cookieValue.contains(sessionCookieName) // rough test
                    } yield
                        cookieValue

                val incomingSessionCookies =
                    for {
                        nativeRequest ← nativeRequestOption.toList
                        cookies       ← Option(nativeRequest.getCookies).toList
                        cookie        ← cookies
                        if cookie.getName == sessionCookieName
                    } yield
                        cookie.getValue

                val sessionOption = Option(externalContext.getSession(false))

                debug("setting cookie", Seq(
                    "new session"              → (sessionOption map (_.isNew.toString) orNull),
                    "session id"               → (sessionOption map (_.getId) orNull),
                    "requested session id"     → (requestOption map (_.getRequestedSessionId) orNull),
                    "session cookie name"      → sessionCookieName,
                    "incoming session cookies" → (incomingSessionCookies mkString " - "),
                    "incoming session headers" → (incomingSessionHeaders mkString " - ")))
            }

            fromIncoming orElse fromSession
        } else
            None
    }

    private def sessionCookieFromIncoming(
            externalContext: ExternalContext,
            nativeRequest: HttpServletRequest,
            cookiesToForward: Seq[String],
            sessionCookieName: String)(implicit logger: IndentedLogger): Option[(String, Array[String])] = {

        // Figure out if we need to forward session cookies. We only forward if there is the requested
        // session id is the same as the current session. Otherwise, it means that the current session is no
        // longer valid, or that the incoming cookie is out of date.
        def requestedSessionIdMatches =
            Option(externalContext.getSession(false)) exists { session ⇒
                val requestedSessionId = externalContext.getRequest.getRequestedSessionId
                session.getId == requestedSessionId
            }

        val cookies = Option(nativeRequest.getCookies) getOrElse Array.empty[Cookie]
        if (requestedSessionIdMatches && cookies.nonEmpty) {

            val pairsToForward =
                for {
                    cookie ← cookies
                    if cookiesToForward.contains(cookie.getName)
                } yield
                    cookie.getName + '=' + cookie.getValue

            if (pairsToForward.nonEmpty) {

                // Multiple cookies in the header, separated with ";"
                val cookieHeaderValue = pairsToForward mkString "; "

                debug("forwarding cookies", Seq(
                    "cookie" → cookieHeaderValue,
                    "requested session id" → externalContext.getRequest.getRequestedSessionId))

                Some("cookie" → Array(cookieHeaderValue))
            } else
                None
        } else
            None
    }

    private def sessionCookieFromGuess(externalContext: ExternalContext, sessionCookieName: String): Option[(String, Array[String])] =
        Option(externalContext.getSession(false)) map
            { session ⇒ "cookie" →  Array(sessionCookieName + "=" + session.getId) }

    // Return SOAP-related headers if needed
    def soapHeaders(httpMethod: String, mediatypeMaybeWithCharset: String, encoding: String)(implicit logger: IndentedLogger): Seq[(String, Array[String])] = {

        require(encoding ne null)

        import NetUtils.{APPLICATION_SOAP_XML, getContentTypeMediaType, getContentTypeParameters}

        val contentTypeMediaType = getContentTypeMediaType(mediatypeMaybeWithCharset)

        // "If the submission mediatype contains a charset MIME parameter, then it is appended to the application/soap+xml
        // MIME type. Otherwise, a charset MIME parameter with same value as the encoding attribute (or its default) is
        // appended to the application/soap+xml MIME type." and "the charset MIME parameter is appended . The charset
        // parameter value from the mediatype attribute is used if it is specified. Otherwise, the value of the encoding
        // attribute (or its default) is used."
        def charsetSuffix(parameters: collection.Map[String, String]) =
            "; charset=" + parameters.getOrElse("charset", encoding)

        val newHeaders =
            httpMethod match {
                case "GET" if contentTypeMediaType == APPLICATION_SOAP_XML ⇒
                    // Set an Accept header

                    val parameters = getContentTypeParameters(mediatypeMaybeWithCharset).asScala
                    val acceptHeader = APPLICATION_SOAP_XML + charsetSuffix(parameters)

                    // Accept header with optional charset
                    Seq("accept" → Array(acceptHeader))

                case "POST" if contentTypeMediaType == APPLICATION_SOAP_XML ⇒
                    // Set Content-Type and optionally SOAPAction headers

                    val parameters = getContentTypeParameters(mediatypeMaybeWithCharset).asScala
                    val overriddenContentType = "text/xml" + charsetSuffix(parameters)
                    val actionParameter = parameters.get("action")

                    // Content-Type with optional charset and SOAPAction header if any
                    Seq("content-type" → Array(overriddenContentType)) ++ (actionParameter map (a ⇒ "soapaction" → Array(a)))
                case _ ⇒
                    // Not a SOAP submission
                    Seq()
            }

        if (newHeaders.nonEmpty)
            debug("adding SOAP headers", newHeaders map { case (k, v) ⇒ capitalizeCommonOrSplitHeader(k) → v(0) })

        newHeaders
    }

    private def setHeaders(headers: collection.Map[String, Array[String]], urlConnection: URLConnection) = {

        // Gather all headers nicely capitalized
        val capitalizedHeaders =
            for {
                (name, values) ← headers.toList
                if values ne null
                value ← values
                if value ne null
            } yield
                capitalizeCommonOrSplitHeader(name) → value

        // Set headers on connection
        capitalizedHeaders foreach {
            case (name, value) ⇒ urlConnection.addRequestProperty(name, value)
        }

        capitalizedHeaders
    }
    
    def logRequestBody(logger: IndentedLogger, mediatype: String, messageBody: Array[Byte]): Unit =
        if (XMLUtils.isXMLMediatype(mediatype) ||
            XMLUtils.isTextOrJSONContentType(mediatype) ||
            mediatype == "application/x-www-form-urlencoded")
            logger.logDebug("submission", "setting request body", "body", new String(messageBody, "UTF-8"))
        else
            logger.logDebug("submission", "setting binary request body")
}
