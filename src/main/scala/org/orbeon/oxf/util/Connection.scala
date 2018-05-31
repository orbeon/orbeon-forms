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

import java.io.File
import java.net.URI
import java.util.{Map ⇒ JMap}
import javax.servlet.http.{Cookie, HttpServletRequest}

import org.apache.http.client.CookieStore
import org.apache.http.impl.client.BasicCookieStore
import org.apache.log4j.Level
import org.orbeon.oxf.common.{OXFException, ValidationException}
import org.orbeon.oxf.externalcontext.ExternalContext.SessionScope
import org.orbeon.oxf.externalcontext.{ExternalContext, URLRewriter}
import org.orbeon.oxf.http.Headers._
import org.orbeon.oxf.http.HttpMethod._
import org.orbeon.oxf.http._
import org.orbeon.oxf.properties.Properties
import org.orbeon.oxf.resources.URLFactory
import org.orbeon.oxf.util.CollectionUtils._
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.PathUtils._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.xml.dom4j.LocationData

import scala.collection.JavaConverters._
import scala.collection.generic.CanBuildFrom
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
  method         : HttpMethod,
  url            : URI,
  credentials    : Option[Credentials],
  content        : Option[StreamedContent],
  headers        : Map[String, List[String]], // capitalized, or entirely lowercase in which case capitalization is attempted
  logBody        : Boolean)(implicit
  logger         : IndentedLogger
) extends ConnectionState with Logging {

  import org.orbeon.oxf.util.Connection._

  // Open the connection. This sends request headers, request body, and reads status and response headers.
  def connect(saveState: Boolean): ConnectionResult = {

    val urlString = url.toString
    val scheme    = url.getScheme

    def isTemporaryFileUri(uri: URI): Boolean = {
      uri.getScheme == "file" && {
        val uriPath = uri.normalize.getPath
        val tmpPath = new java.io.File(System.getProperty("java.io.tmpdir")).toURI.normalize.getPath

        uriPath.startsWith(tmpPath)
      }
    }

    try {
      if (scheme == "file" && ! isTemporaryFileUri(url)) {
        throw new OXFException(s"URL scheme not allowed: $scheme")
      } else if (method == GET && SupportedNonHttpReadonlySchemes(scheme)) {
        // GET for supported but non-http: or https: schemes

        // Create URL connection object
        val urlConnection = URLFactory.createURL(urlString).openConnection
        urlConnection.connect()

        // NOTE: The data: scheme doesn't have a path but can have a content type in the URL. Do this for the
        // "data:" only as urlConnection.getContentType returns funny results e.g. for "file:".
        def contentTypeFromConnection =
          if (scheme == "data") Option(urlConnection.getContentType) else None

        def contentTypeFromPath =
          Option(url.getPath) flatMap Mediatypes.findMediatypeForPath

        def contentTypeHeader =
          contentTypeFromConnection orElse contentTypeFromPath map (ct ⇒ ContentType → List(ct))

        val headers =
          urlConnection.getHeaderFields.asScala map { case (k, v) ⇒ k → v.asScala.to[List] } toMap

        val headersWithContentType =
          headers ++ contentTypeHeader.toList

        // Create result
        val connectionResult = ConnectionResult.apply(
          url        = urlString,
          statusCode = 200,
          headers    = headersWithContentType,
          content    = StreamedContent.fromStreamAndHeaders(urlConnection.getInputStream, headersWithContentType)
        )

        if (debugEnabled) {
          connectionResult.logResponseDetailsOnce(Level.DEBUG)
          connectionResult.logResponseBody(Level.DEBUG, logBody)
        }

        connectionResult

      } else if (isHTTPOrHTTPS(scheme)) {
        // Any method with http: or https:

        val cleanCapitalizedHeaders = {

          // Capitalize only headers which are entirely lowercase.
          //
          // See https://github.com/orbeon/orbeon-forms/issues/3135
          //
          // We used to capitalize all headers, but we want to keep the original capitalization for:
          //
          // 1. custom headers
          // 2. headers forwarded by name, where the header name is provided via configuration
          //
          // So could we just not capitalize anything at all? I can see some examples where old code
          // would have the explicit or forwarded header names lowercase, and that would break. Anything
          // else?

          val capitalizedHeaders =
            for {
              (name, values) ← headers.to[List]
              if values ne null
              value ← values
              if value ne null
            } yield
              (
                if (name == name.toLowerCase)
                  capitalizeCommonOrSplitHeader(name)
                else
                  name
              ) → value

          combineValues[String, String, List](capitalizedHeaders).toMap
        }

        val cookieStore = cookieStoreOpt getOrElse new BasicCookieStore
        cookieStoreOpt = Some(cookieStore)

        val (effectiveConnectionURL, client) =
          findInternalURL(urlString) match {
            case Some(internalPath) ⇒ (internalPath, InternalHttpClient)
            case _                  ⇒ (urlString,    PropertiesApacheHttpClient)
          }

        val response =
          client.connect(
            effectiveConnectionURL,
            credentials,
            cookieStore,
            method,
            cleanCapitalizedHeaders,
            content
          )

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
              url.getScheme,
              Option(url.getUserInfo) map replacePassword orNull,
              url.getHost,
              url.getPort,
              url.getPath,
              url.getQuery,
              url.getFragment
            )

          debug("opening URL connection",
            List(
              "method" → method.entryName,
              "URL"    → connectionURI.toString
            ) ++ (cleanCapitalizedHeaders mapValues (_ mkString ",")))
        }

        // Create result
        val connectionResult = ConnectionResult.apply(
          url        = urlString,
          statusCode = response.statusCode,
          headers    = response.headers,
          content    = response.content
        )

        ifDebug {
          connectionResult.logResponseDetailsOnce(Level.DEBUG)
          connectionResult.logResponseBody(Level.DEBUG, logBody)
        }

        // Save state if possible
        if (saveState)
          saveHttpState()

        connectionResult

      } else if (method != GET && Set("file", "oxf")(scheme)) {
        // Writing to file: and oxf: SHOULD be supported
        throw new OXFException("submission URL scheme not yet implemented: " + scheme)
      } else if (scheme == "mailto") {
        // MAY be supported
        throw new OXFException("submission URL scheme not yet implemented: " + scheme)
      } else {
        throw new OXFException("submission URL scheme not supported: " + scheme)
      }
    } catch {
      case NonFatal(t) ⇒ throw new ValidationException(t, new LocationData(url.toString, -1, -1))
    }
  }
}

trait ConnectionState {

  self: Connection ⇒

  import org.orbeon.oxf.util.ConnectionState._

  private val stateScope = stateScopeFromProperty
  var cookieStoreOpt: Option[CookieStore] = None

  def loadHttpState()(implicit logger: IndentedLogger): Unit = {
    cookieStoreOpt =
      stateAttributes(createSession = false) flatMap
      (m ⇒ Option(m._1(HttpCookieStoreAttribute).asInstanceOf[CookieStore]))

    debugStore("loaded HTTP state", "did not load HTTP state")
  }

  def saveHttpState()(implicit logger: IndentedLogger): Unit = {
    cookieStoreOpt foreach { cookieStore ⇒
      stateAttributes(createSession = true) foreach
      (_._2(HttpCookieStoreAttribute, cookieStore))
    }

    debugStore("saved HTTP state", "did not save HTTP state")
  }

  private def debugStore(positive: String, negative: String)(implicit logger: IndentedLogger) =
    ifDebug {
      cookieStoreOpt match {
        case Some(cookieStore) ⇒
          val cookies = cookieStore.getCookies.asScala map (_.getName) mkString " | "
          debug(positive, List(
            "scope"        → stateScope,
            "cookie names" → (if (cookies.nonEmpty) cookies else null))
          )
        case None ⇒
          debug(negative)
      }
    }

  private def stateAttributes(createSession: Boolean): Option[(String ⇒ AnyRef, (String, AnyRef) ⇒ Unit)] = {
    val externalContext = NetUtils.getExternalContext
    stateScope match {
      case "request" ⇒
        val m = externalContext.getRequest.getAttributesMap
        Some((m.get, m.put))
      case "session" if externalContext.getSessionOpt(createSession).isDefined ⇒
        val s = externalContext.getSession(createSession)
        Some(
          (name: String)                ⇒ s.getAttribute(name,        SessionScope.Local).orNull,
          (name: String, value: AnyRef) ⇒ s.setAttribute(name, value, SessionScope.Local)
        )
      case "application" ⇒
        val m = externalContext.getWebAppContext.getAttributesMap
        Some((m.get, m.put))
      case _ ⇒
        None
    }
  }
}

private object ConnectionState {

  // TODO: See `ScopeProcessorBase.Scope` enumeration.
  def stateScopeFromProperty = {
    val propertySet = Properties.instance.getPropertySet
    val scopeString = propertySet.getString(HttpStateProperty, DefaultStateScope)

    if (AllScopes(scopeString)) scopeString else DefaultStateScope
  }

  val DefaultStateScope        = "session"
  val HttpStateProperty        = "oxf.http.state"
  val HttpCookieStoreAttribute = "oxf.http.cookie-store"

  val AllScopes = Set("none", "request", "session", "application")
}

object Connection extends Logging {

  private val SupportedNonHttpReadonlySchemes = Set("file", "oxf", "data")

  private val HttpInternalPathsProperty               = "oxf.http.internal-paths"
  private val HttpForwardCookiesProperty              = "oxf.http.forward-cookies"
  private val HttpForwardCookiesSessionPrefixProperty = "oxf.http.forward-cookies.session.prefix"
  private val HttpForwardCookiesSessionSuffixProperty = "oxf.http.forward-cookies.session.suffix"
  private val HttpForwardHeadersProperty              = "oxf.http.forward-headers"
  private val LegacyXFormsHttpForwardHeadersProperty  = "oxf.xforms.forward-submission-headers"

  // Create a new Connection
  def apply(
    method      : HttpMethod,
    url         : URI,
    credentials : Option[Credentials],
    content     : Option[StreamedContent],
    headers     : Map[String, List[String]],
    loadState   : Boolean,
    logBody     : Boolean)(implicit
    logger      : IndentedLogger
  ): Connection = {

    require(! requiresRequestBody(method) || content.isDefined)

    val connection =
      new Connection(method, url, credentials, content, headers, logBody)

    // Get connection state if possible
    if (loadState && isHTTPOrHTTPS(url.getScheme))
      connection.loadHttpState()

    connection
  }

  // For Java callers
  def jApply(
    httpMethod        : HttpMethod,
    url               : URI,
    credentialsOrNull : Credentials,
    messageBodyOrNull : Array[Byte],
    headers           : Map[String, List[String]],
    loadState         : Boolean,
    logBody           : Boolean,
    logger            : IndentedLogger
  ): Connection = {


    val messageBody: Option[Array[Byte]] =
      if (requiresRequestBody(httpMethod)) Option(messageBodyOrNull) orElse Some(Array()) else None

    val content = messageBody map
      (StreamedContent.fromBytes(_, firstHeaderIgnoreCase(headers, ContentType)))

    apply(
      method      = httpMethod,
      url         = url,
      credentials = Option(credentialsOrNull),
      content     = content,
      headers     = headers,
      loadState   = loadState,
      logBody     = logBody)(
      logger      = logger
    )
  }

  def isInternalPath(path: String): Boolean = {
    val propertySet = Properties.instance.getPropertySet
    val p = propertySet.getPropertyOrThrow(HttpInternalPathsProperty)
    val r = p.associatedValue(_.value.toString.r)

    r.pattern.matcher(path).matches()
  }

  def findInternalURL(url: String): Option[String] = {

    val servicePrefix =
      URLRewriterUtils.rewriteServiceURL(
        NetUtils.getExternalContext.getRequest,
        "/",
        URLRewriter.REWRITE_MODE_ABSOLUTE
      )

    for {
      pathQuery ← url.startsWith(servicePrefix) option url.substring(servicePrefix.size - 1)
      pathOnly  = splitQuery(pathQuery)._1
      if isInternalPath(pathOnly)
    } yield
      pathQuery
  }

  private val HttpMethodsWithRequestBody = Set[HttpMethod](POST, PUT, LOCK, UNLOCK)
  def requiresRequestBody(httpMethod: HttpMethod) = HttpMethodsWithRequestBody(httpMethod)

  private def schemeRequiresHeaders(scheme: String) = ! Set("file", "oxf")(scheme)
  private def isHTTPOrHTTPS(scheme: String)         = Set("http", "https")(scheme)

  // Build all the connection headers
  def buildConnectionHeadersCapitalizedIfNeeded(
    scheme           : String,
    hasCredentials   : Boolean,
    customHeaders    : Map[String, List[String]],
    headersToForward : Set[String],
    cookiesToForward : List[String],
    getHeader        : String ⇒ Option[List[String]])(implicit
    logger           : IndentedLogger
  ): Map[String, List[String]] =
    if (schemeRequiresHeaders(scheme))
      buildConnectionHeadersCapitalized(hasCredentials, customHeaders, headersToForward, cookiesToForward, getHeader)
    else
      EmptyHeaders

  // For Java callers
  def jBuildConnectionHeadersCapitalizedIfNeeded(
    scheme              : String,
    hasCredentials      : Boolean,
    customHeadersOrNull : JMap[String, Array[String]],
    headersToForward    : String,
    getHeader           : String ⇒ Option[List[String]],
    logger              : IndentedLogger
  ): Map[String, List[String]] =
    buildConnectionHeadersCapitalizedIfNeeded(
      scheme,
      hasCredentials,
      Option(customHeadersOrNull) map (_.asScala.toMap mapValues (_.toList)) getOrElse EmptyHeaders,
      valueAs[Set](headersToForward),
      cookiesToForwardFromProperty,
      getHeader)(
      logger
    )

  def buildConnectionHeadersCapitalizedWithSOAPIfNeeded(
    scheme           : String,
    method           : HttpMethod,
    hasCredentials   : Boolean,
    mediatype        : String,
    encodingForSOAP  : String,
    customHeaders    : Map[String, List[String]],
    headersToForward : Set[String],
    getHeader        : String ⇒ Option[List[String]])(implicit
    logger           : IndentedLogger
  ): Map[String, List[String]] =
    if (schemeRequiresHeaders(scheme)) {

      // "If a header element defines the Content-Type header, then this setting overrides a Content-type set by the
      // mediatype attribute"
      val headersWithContentTypeIfNeeded =
        if (requiresRequestBody(method) && firstHeaderIgnoreCase(customHeaders, ContentType).isEmpty)
          customHeaders + (ContentType → List(mediatype ensuring (_ ne null)))
        else
          customHeaders

      // Also make sure that if a header element defines Content-Type, this overrides the mediatype attribute
      def soapMediatypeWithContentType =
        firstHeaderIgnoreCase(headersWithContentTypeIfNeeded, ContentTypeLower) getOrElse mediatype

      // NOTE: SOAP processing overrides Content-Type in the case of a POST
      // So we have: @serialization → @mediatype →  xf:header → SOAP
      val connectionHeadersCapitalized =
        buildConnectionHeadersCapitalized(
          hasCredentials,
          headersWithContentTypeIfNeeded,
          headersToForward,
          cookiesToForwardFromProperty,
          getHeader
        )

      val soapHeadersCapitalized =
        buildSOAPHeadersCapitalizedIfNeeded(
          method,
          soapMediatypeWithContentType,
          encodingForSOAP
        )

      connectionHeadersCapitalized ++ soapHeadersCapitalized
    } else
      EmptyHeaders

  private def getPropertyHandleCustom(propertyName: String) = {
    val propertySet = Properties.instance.getPropertySet

    propertySet.getNonBlankString(propertyName).to[List] ++
      propertySet.getNonBlankString(propertyName + ".private").to[List] mkString " "
  }

  private def valueAs[T[_]](value: String)(implicit cbf: CanBuildFrom[Nothing, String, T[String]]): T[String] =
    value.trimAllToOpt map (_.splitTo[T]()) getOrElse cbf().result()

  // Get a Set of header names to forward from the configuration properties
  def headersToForwardFromProperty: Set[String] =
    valueAs[Set](getPropertyHandleCustom(HttpForwardHeadersProperty)) ++
      valueAs[Set](getPropertyHandleCustom(LegacyXFormsHttpForwardHeadersProperty))

  def jHeadersToForward =
    (headersToForwardFromProperty mkString " ").trimAllToNull

  // Get a List of cookie names to forward from the configuration properties
  def cookiesToForwardFromProperty: List[String] =
    valueAs[List](getPropertyHandleCustom(HttpForwardCookiesProperty)).distinct

  // From header names and a getter for header values, find the list of headers to forward
  def getHeadersToForwardCapitalized(
    hasCredentials         : Boolean, // exclude `Authorization` header when true
    headerNamesCapitalized : Set[String],
    getHeader              : String ⇒ Option[List[String]])(implicit
    logger                 : IndentedLogger
  ): List[(String, List[String])] = {

    // NOTE: Forwarding the `Cookie` header may yield unpredictable results.

    def canForwardHeader(nameLower: String) = {
      // Only forward Authorization header if there is no credentials provided
      val canForward = nameLower != AuthorizationLower || ! hasCredentials

      if (! canForward)
        debug("not forwarding Authorization header because credentials are present")

      canForward
    }

    for {
      nameCapitalized ← headerNamesCapitalized.to[List]
      nameLower       = nameCapitalized.toLowerCase
      values          ← getHeader(nameLower)
      if canForwardHeader(nameLower)
    } yield {
      debug("forwarding header", List(
        "name"  → nameCapitalized,
        "value" → (values mkString " ")
        )
      )
      nameCapitalized → values
    }
  }

  def getHeaderFromRequest(request: ExternalContext.Request): String ⇒ Option[List[String]] =
    Option(request) match {
        case Some(request) ⇒ name ⇒ request.getHeaderValuesMap.asScala.get(name) map (_.to[List])
        case None          ⇒ _    ⇒ None
      }

  /**
   * Build connection headers to send given:
   *
   * - the incoming request if present
   * - a list of headers names and values to set
   * - whether explicit credentials are available (disables forwarding of session cookies and Authorization header)
   * - a list of headers to forward
   */
  private def buildConnectionHeadersCapitalized(
    hasCredentials           : Boolean,
    customHeadersCapitalized : Map[String, List[String]],
    headersToForward         : Set[String],
    cookiesToForward         : List[String],
    getHeader                : String ⇒ Option[List[String]])(implicit
    logger                   : IndentedLogger
  ): Map[String, List[String]] = {

    val externalContext = NetUtils.getExternalContext

    // 1. Caller-specified list of headers to forward based on a space-separated list of header names
    val headersToForwardCapitalized =
      getHeadersToForwardCapitalized(hasCredentials, headersToForward, getHeader)

    // 2. Explicit caller-specified header name/values

    // 3. Forward cookies for session handling only if no credentials have been explicitly set
    val newCookieHeaderCapitalized =
      if (! hasCredentials)
        sessionCookieHeaderCapitalized(externalContext, cookiesToForward)
      else
        None

    // 4. Authorization token
    val tokenHeaderCapitalized = {

      // Get token from web app scope
      val token =
        externalContext.getWebAppContext.attributes.getOrElseUpdate(OrbeonTokenLower, SecureUtils.randomHexId).asInstanceOf[String]

      Seq(OrbeonToken → List(token))
    }

    // Don't forward headers for which a value is explicitly passed by the caller, so start with headersToForward
    // New cookie header, if present, overrides any existing cookies
    headersToForwardCapitalized.toMap ++ customHeadersCapitalized ++ newCookieHeaderCapitalized ++ tokenHeaderCapitalized
  }

  private def sessionCookieHeaderCapitalized(
    externalContext  : ExternalContext,
    cookiesToForward : List[String])(implicit
    logger           : IndentedLogger
  ): Option[(String, List[String])] = {

    // NOTE: We use a property, as some app servers like WebLogic allow configuring the session cookie name.
    if (cookiesToForward.nonEmpty) {

      // By convention, the first cookie name is the session cookie
      val sessionCookieName = cookiesToForward.head

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
      def fromIncoming =
        nativeRequestOption flatMap
        (sessionCookieFromIncomingCapitalized(externalContext, _, cookiesToForward, sessionCookieName))

      // 2. If there is no incoming session cookie, try to make our own cookie. For this to work on WebSphere,
      // users will have the define the prefix and suffix properties.
      def fromSession =
        externalContext.getSessionOpt(false).map(session ⇒ {
          val propertySet   = Properties.instance.getPropertySet
          val prefix        = propertySet.getString(HttpForwardCookiesSessionPrefixProperty)
          val suffix        = propertySet.getString(HttpForwardCookiesSessionSuffixProperty)
          val sessionId     = session.getId
          val sessionCookie = s"$sessionCookieName=$prefix$sessionId$suffix"
          Headers.Cookie    → List(sessionCookie)
        })

      // Logging
      ifDebug {
        val incomingSessionHeaders =
          for {
            request       ← requestOption.toList
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

        val sessionOpt = externalContext.getSessionOpt(false)

        debug("setting cookie", List(
          "new session"              → (sessionOpt map (_.isNew.toString) orNull),
          "session id"               → (sessionOpt map (_.getId) orNull),
          "requested session id"     → (requestOption flatMap (r ⇒ Option(r.getRequestedSessionId)) orNull),
          "session cookie name"      → sessionCookieName,
          "incoming session cookies" → (incomingSessionCookies mkString " - "),
          "incoming session headers" → (incomingSessionHeaders mkString " - ")))
      }

      fromIncoming orElse fromSession
    } else
      None
  }

  private def sessionCookieFromIncomingCapitalized(
    externalContext   : ExternalContext,
    nativeRequest     : HttpServletRequest,
    cookiesToForward  : List[String],
    sessionCookieName : String)(implicit
    logger            : IndentedLogger
  ): Option[(String, List[String])] = {

    def requestedSessionIdMatches =
      externalContext.getSessionOpt(false) exists { session ⇒
        val requestedSessionId = externalContext.getRequest.getRequestedSessionId
        session.getId == requestedSessionId
      }

    val cookies = Option(nativeRequest.getCookies) getOrElse Array.empty[Cookie]
    if (cookies.nonEmpty) {

      val pairsToForward =
        for {
          cookie ← cookies
          // Only forward cookie listed as cookies to forward
          if cookiesToForward.contains(cookie.getName)
          // Only forward if there is the requested session id is the same as the current session. Otherwise,
          // it means that the current session is no longer valid, or that the incoming cookie is out of date.
          if sessionCookieName != cookie.getName || requestedSessionIdMatches
        } yield
          cookie.getName + '=' + cookie.getValue

      if (pairsToForward.nonEmpty) {

        // Multiple cookies in the header, separated with ";"
        val cookieHeaderValue = pairsToForward mkString "; "

        debug("forwarding cookies", List(
          "cookie"               → cookieHeaderValue,
          "requested session id" → externalContext.getRequest.getRequestedSessionId)
        )

        Some(Headers.Cookie → List(cookieHeaderValue))
      } else
        None
    } else
      None
  }

  private def buildSOAPHeadersCapitalizedIfNeeded(
    method                    : HttpMethod,
    mediatypeMaybeWithCharset : String,
    encoding                  : String)(implicit
    logger                    : IndentedLogger
  ): List[(String, List[String])] = {

    require(encoding ne null)

    import org.orbeon.oxf.util.ContentTypes._

    val contentTypeMediaType = getContentTypeMediaType(mediatypeMaybeWithCharset)

    // "If the submission mediatype contains a charset MIME parameter, then it is appended to the application/soap+xml
    // MIME type. Otherwise, a charset MIME parameter with same value as the encoding attribute (or its default) is
    // appended to the application/soap+xml MIME type." and "the charset MIME parameter is appended . The charset
    // parameter value from the mediatype attribute is used if it is specified. Otherwise, the value of the encoding
    // attribute (or its default) is used."
    def charsetSuffix(charset: Option[String]) =
      "; charset=" + charset.getOrElse(encoding)

    val newHeaders =
      method match {
        case GET if contentTypeMediaType contains SoapContentType ⇒
          // Set an Accept header

          val acceptHeader = SoapContentType + charsetSuffix(getContentTypeCharset(mediatypeMaybeWithCharset))

          // Accept header with optional charset
          List(Accept → List(acceptHeader))

        case POST if contentTypeMediaType contains SoapContentType ⇒
          // Set Content-Type and optionally SOAPAction headers

          val parameters            = getContentTypeParameters(mediatypeMaybeWithCharset)
          val overriddenContentType = "text/xml" + charsetSuffix(parameters.get(ContentTypes.CharsetParameter))
          val actionParameter       = parameters.get(ContentTypes.ActionParameter)

          // Content-Type with optional charset and SOAPAction header if any
          List(ContentType → List(overriddenContentType)) ++ (actionParameter map (a ⇒ SOAPAction → List(a)))
        case _ ⇒
          // Not a SOAP submission
          Nil
      }

    if (newHeaders.nonEmpty)
      debug("adding SOAP headers", newHeaders map { case (k, v) ⇒ k → v.head })

    newHeaders
  }

  def logRequestBody(mediatype: String, messageBody: Array[Byte])(implicit logger: IndentedLogger): Unit =
    if (ContentTypes.isXMLMediatype(mediatype) ||
      ContentTypes.isTextOrJSONContentType(mediatype) ||
      mediatype == "application/x-www-form-urlencoded")
      logger.logDebug("submission", "setting request body", "body", new String(messageBody, "UTF-8"))
    else
      logger.logDebug("submission", "setting binary request body")
}
