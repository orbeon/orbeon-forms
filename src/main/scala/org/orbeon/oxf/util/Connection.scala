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

import cats.Eval
import cats.syntax.option._
import org.apache.http.client.CookieStore
import org.apache.http.impl.client.BasicCookieStore
import org.log4s
import org.orbeon.datatypes.BasicLocationData
import org.orbeon.io.UriScheme
import org.orbeon.oxf.common.{OXFException, ValidationException}
import org.orbeon.oxf.externalcontext.ExternalContext.SessionScope
import org.orbeon.oxf.externalcontext.{ExternalContext, UrlRewriteMode}
import org.orbeon.oxf.http.Headers._
import org.orbeon.oxf.http.HttpMethod._
import org.orbeon.oxf.http._
import org.orbeon.oxf.properties.{Properties, PropertySet}
import org.orbeon.oxf.resources.URLFactory
import org.orbeon.oxf.util.CollectionUtils._
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.Logging._
import org.orbeon.oxf.util.PathUtils._
import org.orbeon.oxf.util.StringUtils._

import java.io.File
import java.net.URI
import java.{util => ju}
import javax.servlet.http.{Cookie, HttpServletRequest}
import scala.collection.compat._
import scala.jdk.CollectionConverters._
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
object Connection extends ConnectionTrait {

  import Private._

  def connectNow(
    method          : HttpMethod,
    url             : URI,
    credentials     : Option[BasicCredentials],
    content         : Option[StreamedContent],
    headers         : Map[String, List[String]],
    loadState       : Boolean,
    saveState       : Boolean,
    logBody         : Boolean)(implicit
    logger          : IndentedLogger,
    externalContext : ExternalContext
  ): ConnectionResult = {

    val (cookieStore, cxr) =
      connectInternal(
        method,
        url,
        credentials,
        content,
        headers,
        loadState,
        logBody
      ).value

    if (saveState)
      ConnectionState.saveHttpState(cookieStore, ConnectionState.stateScopeFromProperty)

    cxr
  }

  // Create a connection including loading state, but defer the actual connection to a lazy `Eval`.
  def connectLater(
    method          : HttpMethod,
    url             : URI,
    credentials     : Option[BasicCredentials],
    content         : Option[StreamedContent],
    headers         : Map[String, List[String]],
    loadState       : Boolean,
    logBody         : Boolean)(implicit
    logger          : IndentedLogger,
    externalContext : ExternalContext
  ): Eval[ConnectionResult] =
    connectInternal(
      method,
      url,
      credentials,
      content,
      headers,
      loadState,
      logBody
    ) map (_._2)

  // For Java callers
  def jConnectNow(
    httpMethod        : HttpMethod,
    url               : URI,
    credentialsOrNull : BasicCredentials,
    messageBodyOrNull : Array[Byte],
    headers           : Map[String, List[String]],
    loadState         : Boolean,
    saveState         : Boolean,
    logBody           : Boolean,
    logger            : IndentedLogger,
    externalContext   : ExternalContext
  ): ConnectionResult = {

    val messageBody: Option[Array[Byte]] =
      if (HttpMethodsWithRequestBody(httpMethod)) Option(messageBodyOrNull) orElse Some(Array()) else None

    val content = messageBody map
      (StreamedContent.fromBytes(_, firstItemIgnoreCase(headers, ContentType)))

    connectNow(
      method          = httpMethod,
      url             = url,
      credentials     = Option(credentialsOrNull),
      content         = content,
      headers         = headers,
      loadState       = loadState,
      saveState       = saveState,
      logBody         = logBody)(
      logger          = logger,
      externalContext = externalContext
    )
  }

  def isInternalPath(path: String): Boolean = {
    val propertySet = Properties.instance.getPropertySet
    val p = propertySet.getPropertyOrThrow(HttpInternalPathsProperty)
    val r = p.associatedValue(_.stringValue.r)

    r.pattern.matcher(path).matches()
  }

  def findInternalUrl(
    normalizedUrl : URI,
    filter        : String => Boolean)(implicit
    ec            : ExternalContext
  ): Option[String] = {

    val normalizedUrlString = normalizedUrl.toString

    val servicePrefix =
      URLRewriterUtils.rewriteServiceURL(
        ec.getRequest,
        "/",
        UrlRewriteMode.Absolute
      )

    for {
      pathQuery <- normalizedUrlString.substringAfterOpt(servicePrefix) map (_.prependSlash)
      if filter(splitQuery(pathQuery)._1)
    } yield
      pathQuery
  }

  // Build all the connection headers
  def buildConnectionHeadersCapitalizedIfNeeded(
    url                      : URI, // scheme can be `null`; should we force a scheme, for example `http:/my/service`?
    hasCredentials           : Boolean,
    customHeaders            : Map[String, List[String]],
    headersToForward         : Set[String],
    cookiesToForward         : List[String],
    getHeader                : String => Option[List[String]])(implicit
    logger                   : IndentedLogger,
    externalContext          : ExternalContext,
    coreCrossPlatformSupport : CoreCrossPlatformSupportTrait
  ): Map[String, List[String]] =
    if ((url.getScheme eq null) || UriScheme.SchemesWithHeaders(UriScheme.withName(url.getScheme)))
      buildConnectionHeadersCapitalized(url.normalize, hasCredentials, customHeaders, headersToForward, cookiesToForward, getHeader)
    else
      EmptyHeaders

  // For Java callers
  // 2020-01-21: 2 Java callers
  def jBuildConnectionHeadersCapitalizedIfNeeded(
    url                      : URI,
    hasCredentials           : Boolean,
    customHeadersOrNull      : ju.Map[String, Array[String]],
    headersToForward         : String,
    getHeader                : String => Option[List[String]],
    logger                   : IndentedLogger,
    externalContext          : ExternalContext,
    coreCrossPlatformSupport : CoreCrossPlatformSupportTrait
  ): Map[String, List[String]] =
    buildConnectionHeadersCapitalizedIfNeeded(
      url                      = url,
      hasCredentials           = hasCredentials,
      customHeaders            = (Option(customHeadersOrNull) map (_.asScala.iterator map { case (k, v) => k -> v.toList } toMap)) getOrElse EmptyHeaders,
      headersToForward         = valueAs[Set](headersToForward),
      cookiesToForward         = cookiesToForwardFromProperty,
      getHeader                = getHeader)(
      logger                   = logger,
      externalContext          = externalContext,
      coreCrossPlatformSupport = coreCrossPlatformSupport
    )

  // Get a Set of header names to forward from the configuration properties
  def headersToForwardFromProperty: Set[String] =
    valueAs[Set](getPropertyHandleCustom(HttpForwardHeadersProperty)) ++
      valueAs[Set](getPropertyHandleCustom(LegacyXFormsHttpForwardHeadersProperty))

  def jHeadersToForward: String =
    (headersToForwardFromProperty mkString " ").trimAllToNull

  // Get a List of cookie names to forward from the configuration properties
  def cookiesToForwardFromProperty: List[String] =
    valueAs[List](getPropertyHandleCustom(HttpForwardCookiesProperty)).distinct

  def sessionCookieHeaderCapitalized(
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
        (r => Option(r.getNativeRequest)) collect
        { case r: HttpServletRequest => r }

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
        externalContext.getSessionOpt(false).map(session => {
          val propertySet   = Properties.instance.getPropertySet
          val prefix        = propertySet.getString(HttpForwardCookiesSessionPrefixProperty, default = "")
          val suffix        = propertySet.getString(HttpForwardCookiesSessionSuffixProperty, default = "")
          val sessionId     = session.getId
          val sessionCookie = s"$sessionCookieName=$prefix$sessionId$suffix"
          Headers.Cookie    -> List(sessionCookie)
        })

      // Logging
      ifDebug {
        val incomingSessionHeaders =
          for {
            request       <- requestOption.toList
            cookieHeaders <- request.getHeaderValuesMap.asScala.get("cookie").toList
            cookieValue   <- cookieHeaders
            if cookieValue.contains(sessionCookieName) // rough test
          } yield
            cookieValue

        val incomingSessionCookies =
          for {
            nativeRequest <- nativeRequestOption.toList
            cookies       <- Option(nativeRequest.getCookies).toList
            cookie        <- cookies
            if cookie.getName == sessionCookieName
          } yield
            cookie.getValue

        val sessionOpt = externalContext.getSessionOpt(false)

        debug("setting cookie", List(
          "new session"              -> (sessionOpt map (_.isNew.toString) orNull),
          "session id"               -> (sessionOpt map (_.getId) orNull),
          "requested session id"     -> (requestOption flatMap (r => Option(r.getRequestedSessionId)) orNull),
          "session cookie name"      -> sessionCookieName,
          "incoming session cookies" -> (incomingSessionCookies mkString " - "),
          "incoming session headers" -> (incomingSessionHeaders mkString " - ")))
      }

      fromIncoming orElse fromSession
    } else
      None
  }

  private object Private {

    val SupportedNonHttpReadonlySchemes = Set[UriScheme](UriScheme.File, UriScheme.Oxf, UriScheme.Data)

    val HttpInternalPathsProperty               = "oxf.http.internal-paths"
    val HttpForwardCookiesProperty              = "oxf.http.forward-cookies"
    val HttpForwardCookiesSessionPrefixProperty = "oxf.http.forward-cookies.session.prefix"
    val HttpForwardCookiesSessionSuffixProperty = "oxf.http.forward-cookies.session.suffix"
    val HttpForwardHeadersProperty              = "oxf.http.forward-headers"
    val LegacyXFormsHttpForwardHeadersProperty  = "oxf.xforms.forward-submission-headers"

    def valueAs[T[_]](value: String)(implicit cbf: Factory[String, T[String]]): T[String] =
      value.trimAllToOpt map (_.splitTo[T]()) getOrElse cbf.newBuilder.result()

    def connectInternal(
      method          : HttpMethod,
      url             : URI,
      credentials     : Option[BasicCredentials],
      content         : Option[StreamedContent],
      headers         : Map[String, List[String]],
      loadState       : Boolean,
      logBody         : Boolean)(implicit
      logger          : IndentedLogger,
      externalContext : ExternalContext
    ): Eval[(CookieStore, ConnectionResult)] = {

      val normalizedUrlString = url.toString

      def isHttpOrHttps(scheme: UriScheme): Boolean =
        scheme == UriScheme.Http || scheme == UriScheme.Https

      val cookieStore =
        loadState && isHttpOrHttps(UriScheme.withName(url.getScheme))           flatOption
          ConnectionState.loadHttpState(ConnectionState.stateScopeFromProperty) getOrElse
          new BasicCookieStore

      Eval.later {
        (
          cookieStore,
          connect(
            method        = method,
            normalizedUrl = url,
            credentials   = credentials,
            content       = content,
            cookieStore   = cookieStore,
            findClient    = findInternalUrl(url, isInternalPath) match {
              case Some(internalPath) => (internalPath, InternalHttpClient)
              case _ => (normalizedUrlString, PropertiesApacheHttpClient)
            },
            headers       = headers,
            logBody       = logBody
          )
        )
      }
    }

    def connect(
      method          : HttpMethod,
      normalizedUrl   : URI,
      credentials     : Option[BasicCredentials],
      content         : Option[StreamedContent],
      cookieStore     : CookieStore,
      findClient      : => (String, HttpClient[CookieStore]),
      headers         : Map[String, List[String]], // capitalized, or entirely lowercase in which case capitalization is attempted
      logBody         : Boolean)(implicit
      logger          : IndentedLogger
    ): ConnectionResult = {

      val normalizedUrlString = normalizedUrl.toString

      try {
        UriScheme.withName(normalizedUrl.getScheme) match {
          case scheme @ UriScheme.File if ! org.orbeon.io.FileUtils.isTemporaryFileUri(normalizedUrl) =>
            throw new OXFException(s"URL scheme `${scheme.entryName}` not allowed")
          case scheme if method == GET && SupportedNonHttpReadonlySchemes(scheme) =>

            // Create URL connection object
            val url           = URLFactory.createURL(normalizedUrlString)
            val urlConnection = url.openConnection
            urlConnection.connect()

            // NOTE: The data: scheme doesn't have a path but can have a content type in the URL. Do this for the
            // "data:" only as urlConnection.getContentType returns funny results e.g. for "file:".
            def contentTypeFromConnection =
              if (scheme == UriScheme.Data) Option(urlConnection.getContentType) else None

            def contentTypeFromPath =
              Option(normalizedUrl.getPath) flatMap Mediatypes.findMediatypeForPath

            def contentTypeHeader =
              contentTypeFromConnection orElse contentTypeFromPath map (ct => ContentType -> List(ct))

            val headersFromConnection =
              urlConnection.getHeaderFields.asScala map { case (k, v) => k -> v.asScala.to(List) } toMap

            val headersWithContentType =
              headersFromConnection ++ contentTypeHeader.toList

            // Take care of HTTP ranges with local files
            val (statusCode, rangeHeaders, inputStream) =
              if (url.getProtocol == "file") {
                val streamedFile = HttpRanges(headers).get.streamedFile(new File(url.toURI), urlConnection.getInputStream).get
                (streamedFile.statusCode, streamedFile.headers, streamedFile.inputStream)
              } else {
                (StatusCode.Ok,           Map(),                urlConnection.getInputStream)
              }

            // Create result
            val connectionResult = ConnectionResult(
              url        = normalizedUrlString,
              statusCode = statusCode,
              headers    = headersWithContentType ++ rangeHeaders,
              content    = StreamedContent.fromStreamAndHeaders(inputStream, headersWithContentType)
            )

            ifDebug {
              connectionResult.logResponseDetailsOnce(log4s.Debug)
              connectionResult.logResponseBody(log4s.Debug, logBody)
            }

            connectionResult

          case UriScheme.Http | UriScheme.Https =>

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
                  (name, values) <- headers.toList
                  if values ne null
                  value <- values
                  if value ne null
                } yield
                  (
                    if (name == name.toLowerCase)
                      capitalizeCommonOrSplitHeader(name)
                    else
                      name
                  ) -> value

              combineValues[String, String, List](capitalizedHeaders).toMap
            }

            val (effectiveConnectionUrlString, client) = findClient

            val response =
              client.connect(
                url         = effectiveConnectionUrlString,
                credentials = credentials,
                cookieStore = cookieStore,
                method      = method,
                headers     = cleanCapitalizedHeaders,
                content     = content
              )

            ifDebug {

              def replacePassword(s: String) = {
                val colonIndex = s.indexOf(':')
                if (colonIndex != -1)
                  s.substring(0, colonIndex + 1) + PropertySet.PasswordPlaceholder
                else
                  s
              }

              val effectiveConnectionUrl = URI.create(effectiveConnectionUrlString)

              val effectiveConnectionUriNoPassword =
                new URI(
                  effectiveConnectionUrl.getScheme,
                  Option(effectiveConnectionUrl.getUserInfo) map replacePassword orNull,
                  effectiveConnectionUrl.getHost,
                  effectiveConnectionUrl.getPort,
                  effectiveConnectionUrl.getPath,
                  effectiveConnectionUrl.getQuery,
                  effectiveConnectionUrl.getFragment
                )

              debug("opened connection",
                List(
                  "client"        -> (if (client == InternalHttpClient) "internal" else "Apache"),
                  "effective URL" -> effectiveConnectionUriNoPassword.toString,
                  "method"        -> method.entryName
                ) ++ (cleanCapitalizedHeaders mapValues (_ mkString ",")))
            }

            // Create result
            val connectionResult = ConnectionResult(
              url        = normalizedUrlString,
              statusCode = response.statusCode,
              headers    = response.headers,
              content    = response.content
            )

            ifDebug {
              connectionResult.logResponseDetailsOnce(log4s.Debug)
              connectionResult.logResponseBody(log4s.Debug, logBody)
            }

            connectionResult

          case scheme =>
            throw new OXFException(s"URL scheme `$scheme` not supported for method `$method`")
        }
      } catch {
        case NonFatal(t) => throw new ValidationException(t, BasicLocationData(normalizedUrlString, -1, -1))
      }
    }

    def getPropertyHandleCustom(propertyName: String): String = {
      val propertySet = Properties.instance.getPropertySet

      propertySet.getNonBlankString(propertyName).toList ++
        propertySet.getNonBlankString(propertyName + ".private").toList mkString " "
    }

    def sessionCookieFromIncomingCapitalized(
      externalContext   : ExternalContext,
      nativeRequest     : HttpServletRequest,
      cookiesToForward  : List[String],
      sessionCookieName : String)(implicit
      logger            : IndentedLogger
    ): Option[(String, List[String])] = {

      def requestedSessionIdMatches =
        externalContext.getSessionOpt(false) exists { session =>
          val requestedSessionId = externalContext.getRequest.getRequestedSessionId
          session.getId == requestedSessionId
        }

      val cookies = Option(nativeRequest.getCookies) getOrElse Array.empty[Cookie]
      if (cookies.nonEmpty) {

        val pairsToForward =
          for {
            cookie <- cookies
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
            "cookie"               -> cookieHeaderValue,
            "requested session id" -> externalContext.getRequest.getRequestedSessionId)
          )

          Some(Headers.Cookie -> List(cookieHeaderValue))
        } else
          None
      } else
        None
    }
  }

  private object ConnectionState {

    val HttpStateProperty        = "oxf.http.state"
    val HttpCookieStoreAttribute = "oxf.http.cookie-store"

    val DefaultStateScope        = ExternalContext.Scope.Session

    def loadHttpState(
      stateScope      : ExternalContext.Scope)(implicit
      logger          : IndentedLogger,
      externalContext : ExternalContext
    ): Option[CookieStore] = {

      val cookieStoreOpt =
        stateAttributes(stateScope, createSession = false) flatMap
        (m => Option(m._1(HttpCookieStoreAttribute).asInstanceOf[CookieStore]))

      debugStore(cookieStoreOpt, stateScope, "loaded HTTP state", "did not load HTTP state")

      cookieStoreOpt
    }

    def saveHttpState(
      cookieStore     : CookieStore,
      stateScope      : ExternalContext.Scope)(implicit
      logger          : IndentedLogger,
      externalContext : ExternalContext
    ): Unit = {

      stateAttributes(stateScope, createSession = true) foreach
        (_._2(HttpCookieStoreAttribute, cookieStore))

      debugStore(cookieStore.some, stateScope, "saved HTTP state", "did not save HTTP state")
    }

    def stateScopeFromProperty: ExternalContext.Scope = {
      val propertySet = Properties.instance.getPropertySet
      val scopeString = propertySet.getString(HttpStateProperty, DefaultStateScope.entryName.toLowerCase)

      ExternalContext.Scope.withNameLowercaseOnlyOption(scopeString).getOrElse(DefaultStateScope)
    }

    def debugStore(
      cookieStoreOpt : Option[CookieStore],
      stateScope     : ExternalContext.Scope,
      positive       : String,
      negative       : String)(implicit
      logger         : IndentedLogger
    ): Unit =
      ifDebug {
        cookieStoreOpt match {
          case Some(cookieStore) =>
            val cookies = cookieStore.getCookies.asScala map (_.getName) mkString " | "
            debug(positive, List(
              "scope"        -> stateScope.entryName.toLowerCase,
              "cookie names" -> (if (cookies.nonEmpty) cookies else null))
            )
          case None =>
            debug(negative)
        }
      }

    def stateAttributes(
      stateScope      : ExternalContext.Scope,
      createSession   : Boolean)(implicit
      externalContext : ExternalContext
    ): Option[(String => AnyRef, (String, AnyRef) => Unit)] =
      stateScope match {
        case ExternalContext.Scope.Request =>
          val m = externalContext.getRequest.getAttributesMap
          Some((m.get, m.put))
        case ExternalContext.Scope.Session if externalContext.getSessionOpt(createSession).isDefined =>
          val s = externalContext.getSession(createSession)
          Some(
            (name: String)                => s.getAttribute(name,        SessionScope.Local).orNull,
            (name: String, value: AnyRef) => s.setAttribute(name, value, SessionScope.Local)
          )
        case ExternalContext.Scope.Application =>
          val m = externalContext.getWebAppContext.getAttributesMap
          Some((m.get, m.put))
        case _ =>
          None
      }
  }
}
