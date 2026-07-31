/**
 * Copyright (C) 2014 Orbeon, Inc.
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
package org.orbeon.oxf.http

import org.apache.hc.client5.http.HttpRoute
import org.apache.hc.client5.http.auth.*
import org.apache.hc.client5.http.classic.methods.*
import org.apache.hc.client5.http.config.ConnectionConfig
import org.apache.hc.client5.http.cookie.CookieStore
import org.apache.hc.client5.http.entity.EntityBuilder
import org.apache.hc.client5.http.impl.auth.{BasicAuthCache, BasicCredentialsProvider, BasicScheme, NTLMScheme}
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder
import org.apache.hc.client5.http.impl.io.{PoolingHttpClientConnectionManager, PoolingHttpClientConnectionManagerBuilder}
import org.apache.hc.client5.http.protocol.HttpClientContext
import org.apache.hc.client5.http.routing.HttpRoutePlanner
import org.apache.hc.client5.http.ssl.{DefaultClientTlsStrategy, DefaultHostnameVerifier, NoopHostnameVerifier}
import org.apache.hc.core5.http.io.entity.EntityUtils
import org.apache.hc.core5.http.protocol.HttpContext
import org.apache.hc.core5.http.{ClassicHttpRequest, ContentType, HttpHost}
import org.apache.hc.core5.ssl.SSLContexts
import org.orbeon.connection.StreamedContent
import org.orbeon.io.IOUtils.*
import org.orbeon.io.UriUtils
import org.orbeon.oxf.http.HttpMethod.*
import org.orbeon.oxf.util.CollectionUtils.*
import org.orbeon.oxf.util.CoreUtils.*
import org.orbeon.oxf.util.StringUtils.OrbeonStringOps
import org.slf4j.LoggerFactory

import java.net.{CookieStore as _, *}
import java.security.KeyStore
import javax.net.ssl.SSLContext
import scala.util.chaining.*


abstract class ApacheHttpClient(settings: HttpClientSettings)
  extends HttpClient[CookieStore] {

  import Private.*

  def createURL(urlString: String): URL

  def connect(
    url        : String,
    credentials: Option[BasicCredentials],
    cookieStore: CookieStore,
    method     : HttpMethod,
    headers    : Map[String, List[String]],
    contentOpt : Option[StreamedContent]
  )(implicit
    requestCtx : Option[RequestCtx] // unused
  ): org.orbeon.oxf.http.HttpResponse = {

    // https://github.com/orbeon/orbeon-forms/issues/7794
    val (uriNoUserInfo, uriCredentials) = {

      val rawUri = URI.create(url)

      val uriCredentials =
        rawUri.getUserInfo.trimAllToOpt.flatMap { userInfo =>
          val separatorPosition = userInfo.indexOf(":")
          if (separatorPosition == -1)
            Some(BasicCredentials(userInfo, None, preemptiveAuth = true, None))
          else
            Some(BasicCredentials(userInfo.substring(0, separatorPosition), Some(userInfo.substring(separatorPosition + 1)), preemptiveAuth = true, None))
        }

      (UriUtils.removeUserInfo(rawUri), uriCredentials)
    }

    val httpContext       = HttpClientContext.create()
    val httpClientBuilder = HttpClientBuilder.create()

    locally {
      httpClientBuilder
        .setConnectionManager(connectionManager)

      // Assign route planner for dynamic exclusion of hostnames from proxying
      routePlanner.foreach(httpClientBuilder.setRoutePlanner)

      proxyHost foreach { host =>
        proxyCredentials foreach { creds =>
          val authExchange = new AuthExchange()
          val authScheme = creds match {
            case _: NTCredentials               => new NTLMScheme()
            case _: UsernamePasswordCredentials => new BasicScheme()
            case _                              => throw new IllegalStateException
          }
          authExchange.select(authScheme)
          httpContext.setAuthExchange(host, authExchange)
        }
      }

      credentials.orElse(uriCredentials).foreach { actualCredentials =>

        // Make authentication preemptive when needed. We populate an AuthCache in HttpClientContext
        // so that the client performs preemptive authentication for the target host.
        val hcCredentials =
          actualCredentials match {
            case BasicCredentials(username, passwordOpt, _, None) =>
              new UsernamePasswordCredentials(username, (passwordOpt getOrElse "").toCharArray)
            case BasicCredentials(username, passwordOpt, _, Some(domain)) =>
              new NTCredentials(username, (passwordOpt getOrElse "").toCharArray, uriNoUserInfo.getHost, domain)
          }

        if (actualCredentials.preemptiveAuth) {
          val authCache  = new BasicAuthCache()
          val authScheme = actualCredentials match {
            case BasicCredentials(_, _, _, Some(_)) =>
              new NTLMScheme()
            case _ =>
              new BasicScheme()
                .tap(_.initPreemptive(hcCredentials)) // needed for preemptive auth
          }
          val targetHost = new HttpHost(uriNoUserInfo.getScheme, uriNoUserInfo.getHost, uriNoUserInfo.getPort)
          authCache.put(targetHost, authScheme)
          httpContext.setAuthCache(authCache)
        }

        val credentialsProvider = new BasicCredentialsProvider
        httpContext.setCredentialsProvider(credentialsProvider)

        credentialsProvider.setCredentials(
          new AuthScope(uriNoUserInfo.getHost, uriNoUserInfo.getPort),
          hcCredentials
        )
      }

      // Set the cookie store
      httpClientBuilder.setDefaultCookieStore(cookieStore)
    }

    val httpClient = httpClientBuilder.build()

    val requestMethod: ClassicHttpRequest =
      method match {
        case GET     => new HttpGet(uriNoUserInfo)
        case POST    => new HttpPost(uriNoUserInfo)
        case HEAD    => new HttpHead(uriNoUserInfo)
        case OPTIONS => new HttpOptions(uriNoUserInfo)
        case PUT     => new HttpPut(uriNoUserInfo)
        case DELETE  => new HttpDelete(uriNoUserInfo)
        case TRACE   => new HttpTrace(uriNoUserInfo)
        case LOCK    => new HttpLock(uriNoUserInfo)
        case UNLOCK  => new HttpUnlock(uriNoUserInfo)
      }

    val skipAuthorizationHeader = credentials.isDefined

    // Set all headers
    for {
      (name, values) <- headers
      value          <- values
      // Skip over Authorization header if user authentication specified
      if ! (skipAuthorizationHeader && name.toLowerCase == "authorization")
    } locally {
      requestMethod.addHeader(name, value)
    }

    (requestMethod, contentOpt) match {
      case (request: ClassicHttpRequest, Some(content)) =>

        val contentTypeHeader = {
          def contentTypeFromContent = content.contentType
          def contentTypeFromRequest = Headers.firstItemIgnoreCase(headers, Headers.ContentType)
          contentTypeFromContent
            .orElse(contentTypeFromRequest)
            .getOrElse(throw new ProtocolException("Can't set request entity: Content-Type header is missing"))
        }

        val contentLength = content.contentLength.filter(_ >= 0L)
        val isChunked = contentLength.isEmpty || settings.chunkRequests

        val builder =
          EntityBuilder.create()
            .setStream(content.stream)
            .setContentType(ContentType.parse(contentTypeHeader))

        if (isChunked)
          builder.chunked()

        request.setEntity(builder.build())

      case _ =>
    }

    val response = httpClient.executeOpen(null, requestMethod, httpContext)

    new org.orbeon.oxf.http.HttpResponse {

      lazy val statusCode: Int =
        response.getCode

      // NOTE: We capitalize common headers properly as we know how to do this. It's up to the caller to handle
      // querying the map properly with regard to case.
      lazy val headers: Map[String, List[String]] =
        combineValues[String, String, List](
          for (header <- response.getHeaders)
          yield Headers.capitalizeCommonOrSplitHeader(header.getName) -> header.getValue
        ).toMap

      lazy val lastModified: Option[Long] =
        DateHeaders.firstDateHeaderIgnoreCase(headers, Headers.LastModified)

      lazy val content: StreamedContent = StreamedContent.fromStreamAndHeaders(
        Option(response.getEntity) map (_.getContent) getOrElse EmptyInputStream,
        headers
      )

      def disconnect(): Unit = {
        try EntityUtils.consume(response.getEntity) finally response.close()
      }
    }
  }

  def shutdown(): Unit = {
    idleConnectionMonitorThread foreach (_.shutdown())
    connectionManager.close()
  }

  private object Private {

    private val Logger = LoggerFactory.getLogger(List("org", "orbeon", "http") mkString ".") // so Jar Jar doesn't touch this!

    import scala.concurrent.duration.*

    class IdleConnectionMonitorThread(
      manager              : PoolingHttpClientConnectionManager,
      pollingDelay         : FiniteDuration,        // for example  5.seconds
      idleConnectionsDelay : Option[FiniteDuration] // for example 30.seconds
    ) extends Thread("Orbeon HTTP connection monitor") {

      thread =>

      private var _mustShutdown = false

      private val _pollingDelayMs         = pollingDelay.toMillis
      private val _idleConnectionsDelayMs = idleConnectionsDelay map (_.toMillis)

      override def run(): Unit = {

        Logger.info(s"starting ${thread.getName} thread")

        try {
          while (! _mustShutdown) {

            thread.synchronized {
              wait(_pollingDelayMs)
            }

            if (_idleConnectionsDelayMs.isEmpty)
              Logger.debug(s"closing expired connections if any")
            else
              Logger.debug(s"closing expired and idle connections if any")

            manager.closeExpired()

            _idleConnectionsDelayMs foreach { delayMs =>
              manager.closeIdle(org.apache.hc.core5.util.TimeValue.ofMilliseconds(delayMs))
            }
          }
        } catch {
          case _: InterruptedException =>
        }

        Logger.info(s"stopping ${thread.getName} thread")
      }

      def shutdown(): Unit = {
        _mustShutdown = true
        thread.synchronized {
          notifyAll()
        }
      }
    }

    // The single ConnectionManager
    val connectionManager: PoolingHttpClientConnectionManager = {

      // Create SSL context, based on a custom key store if specified
      val keyStore =
        (settings.sslKeystoreURI, settings.sslKeystorePassword) match {
          case (Some(keyStoreURI), Some(keyStorePassword)) =>

            val keyStoreType =
              settings.sslKeystoreType getOrElse KeyStore.getDefaultType

            val keyStore =
              useAndClose(createURL(keyStoreURI).openStream) { is => // URL is typically local (file:, etc.)
                KeyStore.getInstance(keyStoreType) |!>
                  (_.load(is, keyStorePassword.toCharArray))
              }

            Some(keyStore -> keyStorePassword)
          case _ =>
            None
        }

      // Create SSL hostname verifier
      val hostnameVerifier = settings.sslHostnameVerifier match {
        case "browser-compatible" => new DefaultHostnameVerifier()
        case "allow-all"          => NoopHostnameVerifier.INSTANCE
        case _                    => new DefaultHostnameVerifier()
      }

      // Create SSL socket factory
      val sslSocketFactory = keyStore match {
        case Some((store, password)) =>
          val sslContext = SSLContexts.custom()
            .loadKeyMaterial(store, password.toCharArray)
            .build()
          new DefaultClientTlsStrategy(sslContext, hostnameVerifier)
        case None =>
          val sslContext = SSLContext.getInstance("Default")
          new DefaultClientTlsStrategy(sslContext, hostnameVerifier)
      }

      val connectionConfig =
        ConnectionConfig.custom()
          .setSocketTimeout(org.apache.hc.core5.util.Timeout.ofMilliseconds(settings.soTimeout))
          .setValidateAfterInactivity(org.apache.hc.core5.util.TimeValue.ofMilliseconds(200))
          .build()

      // Pooling connection manager with limits removed
      val connManager = PoolingHttpClientConnectionManagerBuilder.create()
        .setTlsSocketStrategy(sslSocketFactory)
        .setMaxConnTotal(Integer.MAX_VALUE)
        .setMaxConnPerRoute(Integer.MAX_VALUE)
        .setDefaultConnectionConfig(connectionConfig)
        .build()
      connManager
    }

    val (proxyHost, proxyExclude, proxyCredentials) = {
      // Set proxy if defined in properties
      (settings.proxyHost, settings.proxyPort) match {
        case (Some(proxyHost), Some(proxyPort)) =>
          val _httpProxy = new HttpHost(if (settings.proxySSL) "https" else "http", proxyHost, proxyPort)
          val _proxyExclude = settings.proxyExclude

          // Proxy authentication
          val _proxyCredentials =
            (settings.proxyUsername, settings.proxyPassword) match {
              case (Some(proxyUsername), Some(proxyPassword)) =>
                Some(
                  (settings.proxyNTLMHost, settings.proxyNTLMDomain) match {
                    case (Some(ntlmHost), Some(ntlmDomain)) =>
                      new NTCredentials(proxyUsername, proxyPassword.toCharArray, ntlmHost, ntlmDomain)
                    case _ =>
                      new UsernamePasswordCredentials(proxyUsername, proxyPassword.toCharArray)
                  }
                )
              case _ => None
            }

          (Some(_httpProxy), _proxyExclude, _proxyCredentials)
        case _ =>
          (None, None, None)
      }
    }

    val routePlanner: Option[HttpRoutePlanner] = proxyHost map { proxyHost =>
      (target: HttpHost, _: HttpContext) => proxyExclude match {
        case Some(proxyExclude) if (target ne null) && target.getHostName.matches(proxyExclude) =>
          new HttpRoute(target, null, "https".equalsIgnoreCase(target.getSchemeName))
        case _ =>
          new HttpRoute(target, null, proxyHost, "https".equalsIgnoreCase(target.getSchemeName))
      }
    }

    val idleConnectionMonitorThread: Option[IdleConnectionMonitorThread] =
      settings.expiredConnectionsPollingDelay map { expiredConnectionsPollingDelay =>
        new IdleConnectionMonitorThread(
          manager              = connectionManager,
          pollingDelay         = expiredConnectionsPollingDelay,
          idleConnectionsDelay = settings.idleConnectionsDelay
        ) |!> (_.start())
      }
  }
}
