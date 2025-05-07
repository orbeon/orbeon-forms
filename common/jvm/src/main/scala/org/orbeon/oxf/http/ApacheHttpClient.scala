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

import org.apache.http.auth.*
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.*
import org.apache.http.client.protocol.{HttpClientContext, RequestAcceptEncoding, ResponseContentEncoding}
import org.apache.http.client.{CookieStore, CredentialsProvider}
import org.apache.http.config.RegistryBuilder
import org.apache.http.conn.HttpClientConnectionManager
import org.apache.http.conn.routing.{HttpRoute, HttpRoutePlanner}
import org.apache.http.conn.socket.ConnectionSocketFactory
import org.apache.http.conn.ssl.{DefaultHostnameVerifier, NoopHostnameVerifier, SSLConnectionSocketFactory}
import org.apache.http.entity.{ContentType, InputStreamEntity}
import org.apache.http.impl.auth.{BasicScheme, NTLMScheme}
import org.apache.http.impl.client.{BasicCredentialsProvider, HttpClientBuilder}
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager
import org.apache.http.protocol.{BasicHttpContext, HttpContext, HttpCoreContext}
import org.apache.http.ssl.SSLContexts
import org.apache.http.util.EntityUtils
import org.apache.http.{ProtocolException as _, *}
import org.orbeon.connection.{ConnectionContextSupport, StreamedContent}
import org.orbeon.io.IOUtils.*
import org.orbeon.oxf.http.HttpMethod.*
import org.orbeon.oxf.util.CollectionUtils.*
import org.orbeon.oxf.util.CoreUtils.*
import org.slf4j.LoggerFactory

import java.net.{CookieStore as _, *}
import java.security.KeyStore
import javax.net.ssl.SSLContext


abstract class ApacheHttpClient(settings: HttpClientSettings)
  extends HttpClient[CookieStore] {

  import Private.*

  def createURL(urlString: String): URL

  def connect(
    url          : String,
    credentials  : Option[BasicCredentials],
    cookieStore  : CookieStore,
    method       : HttpMethod,
    headers      : Map[String, List[String]],
    content      : Option[StreamedContent]
  )(implicit
    requestCtx   : Option[RequestCtx],                                // unused
    connectionCtx: Option[ConnectionContextSupport.ConnectionContext] // unused for external HTTP connections
  ): org.orbeon.oxf.http.HttpResponse = {

    val uri               = URI.create(url)
    val httpContext       = new BasicHttpContext
    val httpClientBuilder = HttpClientBuilder.create()

    locally {
      val requestConfig = RequestConfig.custom()
          .setSocketTimeout(settings.soTimeout)
          .build()
      httpClientBuilder
        .setConnectionManager(connectionManager)
        .setDefaultRequestConfig(requestConfig)

      // Handle deflate/gzip transparently
      httpClientBuilder.addInterceptorFirst(new RequestAcceptEncoding)
      httpClientBuilder.addInterceptorLast(new ResponseContentEncoding)

      // Assign route planner for dynamic exclusion of hostnames from proxying
      routePlanner.foreach(httpClientBuilder.setRoutePlanner)


      newProxyAuthState foreach
        (httpContext.setAttribute(HttpClientContext.PROXY_AUTH_STATE, _)) // Set proxy and host authentication

      credentials foreach { actualCredentials =>

        // Make authentication preemptive when needed. Interceptor is added first, as the Authentication header
        // is added by HttpClient's RequestTargetAuthentication which is itself an interceptor, so our
        // interceptor needs to run before RequestTargetAuthentication, otherwise RequestTargetAuthentication
        // won't find the appropriate AuthState/AuthScheme/Credentials in the HttpContext.

        if (actualCredentials.preemptiveAuth)
          httpClientBuilder.addInterceptorFirst(PreemptiveAuthHttpRequestInterceptor)

        val credentialsProvider = new BasicCredentialsProvider
        httpContext.setAttribute(HttpClientContext.CREDS_PROVIDER, credentialsProvider)

        credentialsProvider.setCredentials(
          new AuthScope(uri.getHost, uri.getPort),
          actualCredentials match {
            case BasicCredentials(username, passwordOpt, _, None) =>
              new UsernamePasswordCredentials(username, passwordOpt getOrElse "")
            case BasicCredentials(username, passwordOpt, _, Some(domain)) =>
              new NTCredentials(username, passwordOpt getOrElse "", uri.getHost, domain)
          }
        )
      }

      // Set the cookie store
      httpClientBuilder.setDefaultCookieStore(cookieStore)

    }

    val httpClient    = httpClientBuilder.build()
    val requestMethod =
      method match {
        case GET     => new HttpGet(uri)
        case POST    => new HttpPost(uri)
        case HEAD    => new HttpHead(uri)
        case OPTIONS => new HttpOptions(uri)
        case PUT     => new HttpPut(uri)
        case DELETE  => new HttpDelete(uri)
        case TRACE   => new HttpTrace(uri)
        case LOCK    => new HttpLock(uri)
        case UNLOCK  => new HttpUnlock(uri)
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

    requestMethod match {
      case request: HttpEntityEnclosingRequest =>

        def contentTypeFromContent =
          content flatMap (_.contentType)

        def contentTypeFromRequest =
          Headers.firstItemIgnoreCase(headers, Headers.ContentType)

        val contentTypeHeader =
          contentTypeFromContent
            .orElse(contentTypeFromRequest)
            .getOrElse(throw new ProtocolException("Can't set request entity: Content-Type header is missing"))

        val is =
          content map (_.stream) getOrElse
          (throw new IllegalArgumentException(s"No request content provided for method ${method.entryName}"))

        val contentLength =
          content flatMap (_.contentLength) filter (_ >= 0L)

        val inputStreamEntity =
          new InputStreamEntity(is, contentLength getOrElse -1L, ContentType.parse(contentTypeHeader))

        // With HTTP 1.1, chunking is required if there is no Content-Length. But if the header is present, then
        // chunking is optional. We support disabling this to work around a limitation with eXist when we
        // write data from an XForms submission. In that case, we do pass a Content-Length, so we can disable
        // chunking.
        inputStreamEntity.setChunked(contentLength.isEmpty || settings.chunkRequests)

        request.setEntity(inputStreamEntity)

      case _ =>
    }

    val response = httpClient.execute(requestMethod, httpContext)

    new org.orbeon.oxf.http.HttpResponse {

      lazy val statusCode =
        response.getStatusLine.getStatusCode

      // NOTE: We capitalize common headers properly as we know how to do this. It's up to the caller to handle
      // querying the map properly with regard to case.
      lazy val headers =
        combineValues[String, String, List](
          for (header <- response.getAllHeaders)
          yield Headers.capitalizeCommonOrSplitHeader(header.getName) -> header.getValue
        ) toMap

      lazy val lastModified =
        DateHeaders.firstDateHeaderIgnoreCase(headers, Headers.LastModified)

      lazy val content = StreamedContent.fromStreamAndHeaders(
        Option(response.getEntity) map (_.getContent) getOrElse EmptyInputStream,
        headers
      )

      def disconnect(): Unit =
        EntityUtils.consume(response.getEntity)
    }
  }

  def shutdown(): Unit = {
    idleConnectionMonitorThread foreach (_.shutdown())
    connectionManager.shutdown()
  }

  private object Private {

    private val Logger = LoggerFactory.getLogger(List("org", "orbeon", "http") mkString ".") // so Jar Jar doesn't touch this!

    import scala.concurrent.duration.*

    class IdleConnectionMonitorThread(
      manager              : HttpClientConnectionManager,
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

            manager.closeExpiredConnections()

            _idleConnectionsDelayMs foreach
              (manager.closeIdleConnections(_, java.util.concurrent.TimeUnit.MILLISECONDS))
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

    // It seems that credentials and state are not thread-safe, so create every time
    def newProxyAuthState: Option[AuthState] =
      proxyCredentials map {
        case c: NTCredentials               => new AuthState |!> (_.update(new NTLMScheme, c))
        case c: UsernamePasswordCredentials => new AuthState |!> (_.update(new BasicScheme, c))
        case _                              => throw new IllegalStateException
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
          // Calling full constructor
          new SSLConnectionSocketFactory(
            SSLContexts.custom()
              .loadKeyMaterial(store, password.toCharArray)
              .build(),
            null,
            null,
            hostnameVerifier
          )
        case None =>
          // See http://docs.oracle.com/javase/7/docs/technotes/guides/security/jsse/JSSERefGuide.html#CustomizingStores
          // "This default SSLContext is initialized with a default KeyManager and a TrustManager. If a
          // keystore is specified by the javax.net.ssl.keyStore system property and an appropriate
          // javax.net.ssl.keyStorePassword system property, then the KeyManager created by the default
          // SSLContext will be a KeyManager implementation for managing the specified keystore.
          // (The actual implementation will be as specified in Customizing the Default Key and Trust
          // Managers.) If no such system property is specified, then the keystore managed by the KeyManager
          // will be a new empty keystore."
          new SSLConnectionSocketFactory(
            SSLContext.getInstance("Default"),
            null,
            null,
            hostnameVerifier
          )
      }

      // Create registry for connection socket factories
      val registry = RegistryBuilder.create[ConnectionSocketFactory]()
        .register("https", sslSocketFactory)
        .build()

      // Pooling connection manager with limits removed
      new PoolingHttpClientConnectionManager(registry) |!>
        (_.setMaxTotal(Integer.MAX_VALUE))             |!>
        (_.setDefaultMaxPerRoute(Integer.MAX_VALUE))   |!>
        // We used to always check for stale connections, which is now deprecated
        // Use 200 ms, overriding the default of 2 seconds, per https://stackoverflow.com/a/49734118/5295
        (_.setValidateAfterInactivity(200))
    }

    private val (proxyHost, proxyExclude, proxyCredentials) = {
      // Set proxy if defined in properties
      (settings.proxyHost, settings.proxyPort) match {
        case (Some(proxyHost), Some(proxyPort)) =>
          val _httpProxy = new HttpHost(proxyHost, proxyPort, if (settings.proxySSL) "https" else "http")
          val _proxyExclude = settings.proxyExclude

          // Proxy authentication
          val _proxyCredentials =
            (settings.proxyUsername, settings.proxyPassword) match {
              case (Some(proxyUsername), Some(proxyPassword)) =>
                Some(
                  (settings.proxyNTLMHost, settings.proxyNTLMDomain) match {
                    case (Some(ntlmHost), Some(ntlmDomain)) =>
                      new NTCredentials(proxyUsername, proxyPassword, ntlmHost, ntlmDomain)
                    case _ =>
                      new UsernamePasswordCredentials(proxyUsername, proxyPassword)
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
      (target: HttpHost, _: HttpRequest, _: HttpContext) => proxyExclude match {
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

    // The Apache folks are afraid we misuse preemptive authentication, and so force us to copy and paste some code
    // rather than providing a simple configuration flag. See:
    // http://hc.apache.org/httpcomponents-client-ga/tutorial/html/authentication.html#d4e950

    object PreemptiveAuthHttpRequestInterceptor extends HttpRequestInterceptor {
      def process(request: HttpRequest, context: HttpContext): Unit = {

        val authState           = context.getAttribute(HttpClientContext.TARGET_AUTH_STATE).asInstanceOf[AuthState]
        val credentialsProvider = context.getAttribute(HttpClientContext.CREDS_PROVIDER).asInstanceOf[CredentialsProvider]
        val targetHost          = context.getAttribute(HttpCoreContext.HTTP_TARGET_HOST).asInstanceOf[HttpHost]

        // If not auth scheme has been initialized yet
        if (authState.getAuthScheme eq null) {
          val authScope = new AuthScope(targetHost.getHostName, targetHost.getPort)
          // Obtain credentials matching the target host
          val credentials = credentialsProvider.getCredentials(authScope)
          // If found, generate preemptively
          if (credentials ne null) {
            authState.update(
              if (credentials.isInstanceOf[NTCredentials]) new NTLMScheme else new BasicScheme,
              credentials
            )
          }
        }
      }
    }
  }
}
