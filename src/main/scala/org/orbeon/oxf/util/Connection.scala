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

import cats.effect.IO
import cats.syntax.option.*
import org.apache.http.client.CookieStore
import org.apache.http.impl.client.BasicCookieStore
import org.log4s
import org.orbeon.connection.*
import org.orbeon.connection.ConnectionContextSupport.{ConnectionContexts, EmptyConnectionContexts}
import org.orbeon.datatypes.BasicLocationData
import org.orbeon.io.{UriScheme, UriUtils}
import org.orbeon.oxf.common.{OXFException, ValidationException}
import org.orbeon.oxf.externalcontext.*
import org.orbeon.oxf.externalcontext.ExternalContext.SessionScope
import org.orbeon.oxf.http.*
import org.orbeon.oxf.http.Headers.*
import org.orbeon.oxf.http.HttpMethod.*
import org.orbeon.oxf.properties.{Properties, PropertySet}
import org.orbeon.oxf.resources.URLFactory
import org.orbeon.oxf.util.CollectionUtils.*
import org.orbeon.oxf.util.CoreUtils.*
import org.orbeon.oxf.util.Logging.*
import org.orbeon.oxf.util.PathUtils.*
import org.orbeon.oxf.util.StringUtils.*

import java.io.File
import java.net.URI
import java.util as ju
import scala.collection.Factory
import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal


/**
 * Connection to a URL.
 *
 * Handles:
 *
 * - `PUT`ting or POSTing a body
 * - credentials
 * - HTTP headers
 * - forwarding session cookies
 * - forwarding HTTP headers
 * - managing SOAP POST and GET a la XForms 1.1 (should this be here?)
 */
object Connection extends ConnectionTrait {

  import Private.*

  def connectNow(
    method          : HttpMethod,
    url             : URI,
    credentials     : Option[BasicCredentials],
    content         : Option[StreamedContent],
    headers         : Map[String, List[String]],
    loadState       : Boolean,
    saveState       : Boolean,
    logBody         : Boolean
  )(implicit
    logger          : IndentedLogger,
    externalContext : ExternalContext,
    resourceResolver: Option[ResourceResolver]
  ): ConnectionResult = {

    implicit val connectionCtx : ConnectionContexts = EmptyConnectionContexts
    implicit val safeRequestCtx: SafeRequestContext = SafeRequestContext(externalContext)

    val (cookieStore, cxr) =
      connectInternal(
        method      = method,
        url         = url,
        credentials = credentials,
        content     = content,
        headers     = headers,
        loadState   = loadState,
        logBody     = logBody,
        isAsync     = false
      )

    if (saveState)
      ConnectionState.saveHttpState(cookieStore)

    cxr
  }

  // Called by `RegularSubmission` and `FormRunnerPersistence`
  def connectAsync(
    method          : HttpMethod,
    url             : URI,
    credentials     : Option[BasicCredentials],
    content         : Option[AsyncStreamedContent],
    headers         : Map[String, List[String]],
    loadState       : Boolean,
    logBody         : Boolean
  )(implicit
    logger          : IndentedLogger,
    safeRequestCtx  : SafeRequestContext,
    connectionCtx   : ConnectionContexts,
    resourceResolver: Option[ResourceResolver]
  ): IO[AsyncConnectionResult] = {

    // Copy `IndentedLogger` as we cannot share logger state with other threads, and the caller's logger might be
    // associated with the `XFormsContainingDocument` and reused by the calling thread.
    val newLogger = IndentedLogger(logger)

    // Here we convert an `fs2.Stream` to a Java `InputStream` which is used downstream. This works if the producer and
    // the consumer are in different threads. Ideally, our downstream code would be able to deal with an `fs2.Stream`.
//    def requestStreamedContentOptF: Future[Option[StreamedContent]] =
//      content.map(c =>
//        c.stream
//          .through(fs2.io.toInputStream)
//          .map(is =>
//            StreamedContent(
//              inputStream   = is,
//              contentType   = c.contentType,
//              contentLength = c.contentLength,
//              title         = c.title
//            ).some
//          ).compile.onlyOrError.unsafeToFuture()
//      ).getOrElse(Future.successful(None))

    def requestStreamedContentOptIo: IO[Option[StreamedContent]] =
      content.map(ConnectionSupport.asyncToSyncStreamedContent).map(_.map(_.some))
        .getOrElse(IO.pure(None))

    def connectWithContentIo(requestStreamedContentOpt: Option[StreamedContent]): IO[AsyncConnectionResult] =
      IO {
        val (_, cxr) =
          connectInternal(
            method      = method,
            url         = url,
            credentials = credentials,
            content     = requestStreamedContentOpt,
            headers     = headers,
            loadState   = loadState,
            logBody     = logBody,
            isAsync     = true
          )(
            logger         = newLogger,
            safeRequestCtx = safeRequestCtx,
            connectionCtx  = connectionCtx
          )

        // Return an `AsyncConnectionResult` even though for now we obtain a synchronous `ConnectionResult`, so that
        // the callers deal with an `fs2.Stream` consistently for async calls on the JVM as well as JavaScript. Later
        // we can create an `AsyncConnectionResult` directly.
        ConnectionResult.syncToAsync(cxr)
      }

    requestStreamedContentOptIo.flatMap(connectWithContentIo)
  }

  // For Java callers
  def jConnectNow(
    httpMethod       : HttpMethod,
    url              : URI,
    credentialsOrNull: BasicCredentials,
    messageBodyOrNull: Array[Byte],
    headers          : Map[String, List[String]],
    loadState        : Boolean,
    saveState        : Boolean,
    logBody          : Boolean,
    logger           : IndentedLogger,
    externalContext  : ExternalContext
  ): ConnectionResult = {

    val messageBody: Option[Array[Byte]] =
      if (HttpMethodsWithRequestBody(httpMethod)) Option(messageBodyOrNull) orElse Some(Array()) else None

    val content = messageBody map
      (StreamedContent.fromBytes(_, firstItemIgnoreCase(headers, ContentType)))

    connectNow(
      method           = httpMethod,
      url              = url,
      credentials      = Option(credentialsOrNull),
      content          = content,
      headers          = headers,
      loadState        = loadState,
      saveState        = saveState,
      logBody          = logBody
    )(
      logger           = logger,
      externalContext  = externalContext,
      resourceResolver = None
    )
  }

  def isInternalPath(path: String): Boolean = {
    val propertySet = Properties.instance.getPropertySet
    val p = propertySet.getPropertyOrThrow(HttpInternalPathsProperty)
    val r = p.associatedValue(_.stringValue.r)

    r.pattern.matcher(path).matches()
  }

  def findInternalUrl(
    normalizedUrl  : URI,
    filter         : String => Boolean,
    servicePrefix  : String
  ): Option[String] =
    for {
      pathQuery <- normalizedUrl.toString.substringAfterOpt(servicePrefix).map(_.prependSlash)
      if filter(splitQuery(pathQuery)._1)
    } yield
      pathQuery

  // Build all the connection headers
  def buildConnectionHeadersCapitalizedIfNeeded(
    url             : URI, // scheme can be `null`; should we force a scheme, for example `http:/my/service`?
    hasCredentials  : Boolean,
    customHeaders   : Map[String, List[String]],
    headersToForward: Set[String],
    cookiesToForward: List[String],
    getHeader       : String => Option[List[String]]
  )(implicit
    logger          : IndentedLogger,
    safeRequestCtx  : SafeRequestContext
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
    externalContext          : ExternalContext
  ): Map[String, List[String]] =
    buildConnectionHeadersCapitalizedIfNeeded(
      url              = url,
      hasCredentials   = hasCredentials,
      customHeaders    = (Option(customHeadersOrNull) map (_.asScala.iterator map { case (k, v) => k -> v.toList } toMap)) getOrElse EmptyHeaders,
      headersToForward = valueAs[Set](headersToForward),
      cookiesToForward = cookiesToForwardFromProperty,
      getHeader        = getHeader
    )(
      logger           = logger,
      safeRequestCtx   = SafeRequestContext(externalContext)
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

  protected def sessionCookieHeaderCapitalized(
    cookiesToForward: List[String]
  )(implicit
    safeRequestCtx  : SafeRequestContext,
    logger          : IndentedLogger
  ): Option[(String, List[String])] = {

    // NOTE: We use a property, as some app servers like WebLogic allow configuring the session cookie name.
    if (cookiesToForward.nonEmpty) {

      // By convention, the first cookie name is the session cookie
      // xxx check
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

      // 1. If there is an incoming JSESSIONID cookie, use it. The reason is that there is not necessarily an
      // obvious mapping between "session id" and JSESSIONID cookie value. With Tomcat, this works, but with e.g.
      // WebSphere, you get session id="foobar" and JSESSIONID=0000foobar:-1. So we must first try to get the
      // incoming JSESSIONID. To do this, we get the cookie, then serialize it as a header.
      def fromIncoming: Option[(String, List[String])] =
        sessionCookieFromIncomingCapitalized(cookiesToForward, sessionCookieName)

      // 2. If there is no incoming session cookie, try to make our own cookie. For this to work on WebSphere,
      // users will have the define the prefix and suffix properties.
      def fromSession: Option[(String, List[String])] =
        safeRequestCtx.findSessionOrThrow(false).map(session => {
          val propertySet   = Properties.instance.getPropertySet
          val prefix        = propertySet.getString(HttpForwardCookiesSessionPrefixProperty, default = "")
          val suffix        = propertySet.getString(HttpForwardCookiesSessionSuffixProperty, default = "")
          val sessionId     = session.getId
          val sessionCookie = s"$sessionCookieName=$prefix$sessionId$suffix"
          Headers.Cookie    -> List(sessionCookie)
        })

      // Logging
      ifDebug {
        val incomingSessionCookieValue =
          for {
            (name, value) <- safeRequestCtx.cookies
            if name == sessionCookieName
          } yield
            value

        val sessionOpt = safeRequestCtx.findSessionOrThrow(false)

        debug("setting cookie", List(
          "new session"             -> (sessionOpt.map(_.isNew.toString).orNull),
          "session id"              -> (sessionOpt.map(_.getId) orNull),
          "requested session id"    -> (safeRequestCtx.requestedSessionId.orNull),
          "session cookie name"     -> sessionCookieName,
          "incoming session cookie" -> (incomingSessionCookieValue mkString " - ")))
      }

      fromIncoming orElse fromSession
    } else
      None
  }

  protected def buildTokenHeaderCapitalized(implicit
    logger        : IndentedLogger,
    safeRequestCtx: SafeRequestContext
  ): Option[(String, List[String])] = {

    val token =
      safeRequestCtx.webAppContext
        .attributes
        .getOrElseUpdate(OrbeonTokenLower, CoreCrossPlatformSupport.randomHexId).asInstanceOf[String]

    Some(OrbeonToken -> List(token))
  }

  private object Private {

    private val SupportedNonHttpReadonlySchemes = Set[UriScheme](UriScheme.File, UriScheme.Oxf, UriScheme.Data)

    val HttpInternalPathsProperty               = "oxf.http.internal-paths"
    val HttpForwardCookiesProperty              = "oxf.http.forward-cookies"
    val HttpForwardCookiesSessionPrefixProperty = "oxf.http.forward-cookies.session.prefix"
    val HttpForwardCookiesSessionSuffixProperty = "oxf.http.forward-cookies.session.suffix"
    val HttpForwardHeadersProperty              = "oxf.http.forward-headers"
    val LegacyXFormsHttpForwardHeadersProperty  = "oxf.xforms.forward-submission-headers"

    def valueAs[T[_]](value: String)(implicit cbf: Factory[String, T[String]]): T[String] =
      value.trimAllToOpt map (_.splitTo[T]()) getOrElse cbf.newBuilder.result()

    def connectInternal(
      method        : HttpMethod,
      url           : URI,
      credentials   : Option[BasicCredentials],
      content       : Option[StreamedContent],
      headers       : Map[String, List[String]],
      loadState     : Boolean,
      logBody       : Boolean,
      isAsync       : Boolean
    )(implicit
      logger        : IndentedLogger,
      safeRequestCtx: SafeRequestContext,
      connectionCtx : ConnectionContexts
    ): (CookieStore, ConnectionResult) = {

      def isHttpOrHttps(scheme: UriScheme): Boolean =
        scheme == UriScheme.Http || scheme == UriScheme.Https

      val cookieStore =
        loadState && isHttpOrHttps(UriScheme.withName(url.getScheme))  flatOption
          ConnectionState.loadHttpState getOrElse
          new BasicCookieStore

      (
        cookieStore,
        connect(
          method        = method,
          normalizedUrl = url,
          credentials   = credentials,
          content       = content,
          cookieStore   = cookieStore,
          headers       = headers,
          logBody       = logBody,
          isAsync       = isAsync
        )
      )
    }

    private def connect(
      method        : HttpMethod,
      normalizedUrl : URI,
      credentials   : Option[BasicCredentials],
      content       : Option[StreamedContent],
      cookieStore   : CookieStore,
      headers       : Map[String, List[String]], // capitalized, or entirely lowercase in which case capitalization is attempted
      logBody       : Boolean,
      isAsync       : Boolean
    )(implicit
      logger        : IndentedLogger,
      safeRequestCtx: SafeRequestContext,
      connectionCtx : ConnectionContexts
    ): ConnectionResult = {

      val normalizedUrlString = normalizedUrl.toString

      try {
        UriScheme.withName(normalizedUrl.getScheme) match {
          case scheme @ UriScheme.File if ! org.orbeon.io.FileUtils.isTemporaryFileUri(normalizedUrl) =>
            throw new OXFException(s"URL scheme `${scheme.entryName}` not allowed")
          case scheme if method == GET && SupportedNonHttpReadonlySchemes(scheme) =>
            try {
              // Create URL connection object
              val urlConnection = URLFactory.createURL(normalizedUrl).openConnection()
              urlConnection.connect()

              // NOTE: The data: scheme doesn't have a path but can have a content type in the URL. Do this for the
              // "data:" only as urlConnection.getContentType returns funny results e.g. for "file:".
              def contentTypeFromConnection =
                if (scheme == UriScheme.Data) Option(urlConnection.getContentType) else None

              def contentTypeFromPath =
                Option(normalizedUrl.getPath) flatMap Mediatypes.findMediatypeForPath

              def contentTypeFromQueryParameters =
                splitQueryDecodeParams(normalizedUrl.toString)._2.toMap.get("mediatype")

              def contentTypeHeader =
                contentTypeFromConnection
                  .orElse(contentTypeFromPath)
                  .orElse(contentTypeFromQueryParameters)
                  .map(ct => ContentType -> List(ct))

              val headersFromConnection =
                urlConnection.getHeaderFields.asScala map { case (k, v) => k -> v.asScala.to(List) } toMap

              val headersWithContentType =
                headersFromConnection ++ contentTypeHeader.toList

              // Take care of HTTP ranges with local files
              val (statusCode, rangeHeaders, inputStream) =
                if (scheme == UriScheme.File) { // must be a temp file

                  val tempFile       = new File(UriUtils.removeQueryAndFragment(normalizedUrl))
                  val streamResponse = HttpRanges(headers).get.streamResponse(
                      length             = tempFile.length(),
                      partialInputStream = (httpRange: HttpRange) => FileRangeInputStream(tempFile, httpRange),
                      fullInputStream    = urlConnection.getInputStream
                  ).get

                  (streamResponse.statusCode, streamResponse.headers, streamResponse.inputStream)
                } else {
                  (StatusCode.Ok,             Map(),                  urlConnection.getInputStream)
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

            } catch {
              case _: java.io.FileNotFoundException =>
                notFound(normalizedUrl)
            }

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

            val (clientType, effectiveConnectionUrlString, response) =
              findInternalUrl(normalizedUrl, isInternalPath, safeRequestCtx.servicePrefix) match {
                case Some(internalPath) =>
                  (
                    "internal",
                    internalPath,
                    ConnectionContextSupport.maybeWithContext(URI.create(internalPath), method, headers, Map.empty, isAsync) {
                      InternalHttpClient.connect(
                        url         = internalPath,
                        credentials = credentials,
                        cookieStore = cookieStore,
                        method      = method,
                        headers     = cleanCapitalizedHeaders,
                        content     = content
                      )(
                        requestCtx  = Some(safeRequestCtx)
                      )
                    }
                  )
                case _ =>
                  (
                    "Apache",
                    normalizedUrlString,
                    PropertiesApacheHttpClient.connect(
                      url         = normalizedUrlString,
                      credentials = credentials,
                      cookieStore = cookieStore,
                      method      = method,
                      headers     = cleanCapitalizedHeaders,
                      contentOpt  = content
                    )(
                      requestCtx  = None
                    )
                  )
              }

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
                  "client"        -> clientType,
                  "effective URL" -> effectiveConnectionUriNoPassword.toString,
                  "method"        -> method.entryName
                ) ++ cleanCapitalizedHeaders.view.mapValues(_ mkString ",")
              )
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
      cookiesToForward : List[String],
      sessionCookieName: String
    )(implicit
      safeRequestCtx  : SafeRequestContext,
      logger           : IndentedLogger
    ): Option[(String, List[String])] = {

      def requestedSessionIdMatches =
        safeRequestCtx.findSessionOrThrow(false) exists { session =>
          val requestedSessionId = safeRequestCtx.requestedSessionId
          requestedSessionId.contains(session.getId)
        }

      val cookies = safeRequestCtx.cookies
      if (cookies.nonEmpty) {

        val pairsToForward =
          for {
            (name, value) <- cookies
            // Only forward cookie listed as cookies to forward
            if cookiesToForward.contains(name)
            // Only forward if there is the requested session id is the same as the current session. Otherwise,
            // it means that the current session is no longer valid, or that the incoming cookie is out of date.
            if sessionCookieName != name || requestedSessionIdMatches
          } yield
            s"$name=$value"

        if (pairsToForward.nonEmpty) {

          // Multiple cookies in the header, separated with ";"
          val cookieHeaderValue = pairsToForward mkString "; "

          debug("forwarding cookies", List(
            "cookie"               -> cookieHeaderValue,
            "requested session id" -> safeRequestCtx.requestedSessionId.orNull)
          )

          Some(Headers.Cookie -> List(cookieHeaderValue))
        } else
          None
      } else
        None
    }
  }

  private object ConnectionState {

    private val HttpCookieStoreAttribute = "oxf.http.cookie-store"

    def loadHttpState(implicit
      logger         : IndentedLogger,
      safeRequestCtx: SafeRequestContext
    ): Option[CookieStore] = {

      val cookieStoreOpt =
        stateAttributes(createSession = false)
          .flatMap(m => m._1(HttpCookieStoreAttribute))
          .map(_.asInstanceOf[CookieStore])

      debugStore(cookieStoreOpt, "loaded HTTP state", "did not load HTTP state")

      cookieStoreOpt
    }

    def saveHttpState(cookieStore: CookieStore)(implicit
      logger         : IndentedLogger,
      safeRequestCtx: SafeRequestContext
    ): Unit = {

      stateAttributes(createSession = true)
        .foreach(_._2.apply(HttpCookieStoreAttribute, cookieStore))

      debugStore(cookieStore.some, "saved HTTP state", "did not save HTTP state")
    }

    private def debugStore(
      cookieStoreOpt : Option[CookieStore],
      positive       : String,
      negative       : String
    )(implicit
      logger         : IndentedLogger
    ): Unit =
      ifDebug {
        cookieStoreOpt match {
          case Some(cookieStore) =>
            val cookies = cookieStore.getCookies.asScala map (_.getName) mkString " | "
            debug(positive, List(
              "cookie names" -> (if (cookies.nonEmpty) cookies else null))
            )
          case None =>
            debug(negative)
        }
      }

    private def stateAttributes(createSession: Boolean)(implicit
      safeRequestCtx: SafeRequestContext
    ): Option[(String => Option[AnyRef], (String, AnyRef) => Unit)] =
      safeRequestCtx.findSessionOrThrow(createSession).map { session =>
        (
          (name: String)                => session.getAttribute(name, SessionScope.Local),
          (name: String, value: AnyRef) => session.setAttribute(name, value, SessionScope.Local)
        )
      }
  }
}
