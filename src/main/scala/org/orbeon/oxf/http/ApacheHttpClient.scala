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

import java.io.IOException
import java.net.{CookieStore => _, _}
import java.security.KeyStore
import javax.net.ssl.SSLContext
import jcifs.ntlmssp.{Type1Message, Type2Message, Type3Message}
import jcifs.util.Base64
import org.apache.http.auth._
import org.apache.http.client.methods._
import org.apache.http.client.protocol.{ClientContext, RequestAcceptEncoding, ResponseContentEncoding}
import org.apache.http.client.{CookieStore, CredentialsProvider}
import org.apache.http.conn.ClientConnectionManager
import org.apache.http.conn.routing.{HttpRoute, HttpRoutePlanner}
import org.apache.http.conn.scheme.{PlainSocketFactory, Scheme, SchemeRegistry}
import org.apache.http.conn.ssl.SSLSocketFactory
import org.apache.http.entity.{ContentType, InputStreamEntity}
import org.apache.http.impl.auth.{BasicScheme, NTLMEngine, NTLMEngineException, NTLMScheme}
import org.apache.http.impl.client.{BasicCredentialsProvider, DefaultHttpClient}
import org.apache.http.impl.conn.PoolingClientConnectionManager
import org.apache.http.params.{BasicHttpParams, HttpConnectionParams}
import org.apache.http.protocol.{BasicHttpContext, ExecutionContext, HttpContext}
import org.apache.http.util.EntityUtils
import org.apache.http.{ProtocolException => _, _}
import org.orbeon.oxf.http.HttpMethod._
import org.orbeon.oxf.util.CollectionUtils._
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.io.IOUtils._
import org.orbeon.oxf.resources.URLFactory
import org.slf4j.LoggerFactory


class ApacheHttpClient(settings: HttpClientSettings) extends HttpClient[CookieStore] {

  import Private._

  def connect(
    url         : String,
    credentials : Option[BasicCredentials],
    cookieStore : CookieStore,
    method      : HttpMethod,
    headers     : Map[String, List[String]],
    content     : Option[StreamedContent]
  ): HttpResponse = {

    val uri = URI.create(url)

    val httpClient  = new DefaultHttpClient(connectionManager, newHttpParams)
    val httpContext = new BasicHttpContext

    newProxyAuthState foreach
      (httpContext.setAttribute(ClientContext.PROXY_AUTH_STATE, _)) // Set proxy and host authentication

    // Handle deflate/gzip transparently
    httpClient.addRequestInterceptor(new RequestAcceptEncoding)
    httpClient.addResponseInterceptor(new ResponseContentEncoding)

    // Assign route planner for dynamic exclusion of hostnames from proxying
    routePlanner foreach
      httpClient.setRoutePlanner

    credentials foreach { actualCredentials =>

      // Make authentication preemptive when needed. Interceptor is added first, as the Authentication header
      // is added by HttpClient's RequestTargetAuthentication which is itself an interceptor, so our
      // interceptor needs to run before RequestTargetAuthentication, otherwise RequestTargetAuthentication
      // won't find the appropriate AuthState/AuthScheme/Credentials in the HttpContext.

      if (actualCredentials.preemptiveAuth)
        httpClient.addRequestInterceptor(PreemptiveAuthHttpRequestInterceptor, 0)

      val credentialsProvider = new BasicCredentialsProvider
      httpContext.setAttribute(ClientContext.CREDS_PROVIDER, credentialsProvider)

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

    httpClient.setCookieStore(cookieStore)

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

        val contentTypeHeader = (
          contentTypeFromContent
          orElse contentTypeFromRequest
          getOrElse (throw new ProtocolException("Can't set request entity: Content-Type header is missing"))
        )

        val is =
          content map (_.inputStream) getOrElse
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

    new HttpResponse {

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

      def disconnect() =
        EntityUtils.consume(response.getEntity)
    }
  }

  def shutdown(): Unit = {
    idleConnectionMonitorThread foreach (_.shutdown())
    connectionManager.shutdown()
  }

  def usingProxy: Boolean = proxyHost.isDefined

  private object Private {

    val Logger = LoggerFactory.getLogger(List("org", "orbeon", "http") mkString ".") // so JARJAR doesn't touch this!

    import scala.concurrent.duration._

    class IdleConnectionMonitorThread(
      manager              : ClientConnectionManager,
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

    // BasicHttpParams is not thread-safe per the doc
    def newHttpParams =
      new BasicHttpParams |!>
      (HttpConnectionParams.setStaleCheckingEnabled(_, settings.staleCheckingEnabled)) |!>
      (HttpConnectionParams.setSoTimeout(_, settings.soTimeout))

    // It seems that credentials and state are not thread-safe, so create every time
    def newProxyAuthState = proxyCredentials map {
      case c: NTCredentials               => new AuthState |!> (_.update(new NTLMScheme(JCIFSEngine), c))
      case c: UsernamePasswordCredentials => new AuthState |!> (_.update(new BasicScheme, c))
      case _                              => throw new IllegalStateException
    }

    // The single ConnectionManager
    val connectionManager: PoolingClientConnectionManager = {

      // Create SSL context, based on a custom key store if specified
      val trustStore =
        (settings.sslKeystoreURI, settings.sslKeystorePassword) match {
          case (Some(keyStoreURI), Some(keyStorePassword)) =>

            val keyStoreType =
              settings.sslKeystoreType getOrElse KeyStore.getDefaultType

            val keyStore =
              useAndClose(URLFactory.createURL(keyStoreURI).openStream) { is => // URL is typically local (file:, etc.)
                KeyStore.getInstance(keyStoreType) |!>
                  (_.load(is, keyStorePassword.toCharArray))
              }

            Some(keyStore -> keyStorePassword)
          case _ =>
            None
        }

      // Create SSL hostname verifier
      val hostnameVerifier = settings.sslHostnameVerifier match {
        case "browser-compatible" => SSLSocketFactory.BROWSER_COMPATIBLE_HOSTNAME_VERIFIER
        case "allow-all"          => SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER
        case _                    => SSLSocketFactory.STRICT_HOSTNAME_VERIFIER
      }

      // Declare schemes (though having to declare common schemes like HTTP and HTTPS seems wasteful)
      val schemeRegistry = new SchemeRegistry
      schemeRegistry.register(new Scheme("http", 80, PlainSocketFactory.getSocketFactory))

      val sslSocketFactory = trustStore match {
        case Some(trustStore) =>
          // Calling full constructor
          new SSLSocketFactory(
            SSLSocketFactory.TLS,
            trustStore._1,
            trustStore._2,
            trustStore._1,
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
          new SSLSocketFactory(SSLContext.getInstance("Default"), hostnameVerifier)
      }

      schemeRegistry.register(new Scheme("https", 443, sslSocketFactory))

      // Pooling connection manager with limits removed
      new PoolingClientConnectionManager(schemeRegistry) |!>
        (_.setMaxTotal(Integer.MAX_VALUE))               |!>
        (_.setDefaultMaxPerRoute(Integer.MAX_VALUE))
    }

    val (proxyHost, proxyExclude, proxyCredentials) = {
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

    val routePlanner = proxyHost map { proxyHost =>
      new HttpRoutePlanner {
        def determineRoute(target: HttpHost, request: HttpRequest, context: HttpContext) =
          proxyExclude match {
            case Some(proxyExclude) if (target ne null) && target.getHostName.matches(proxyExclude) =>
              new HttpRoute(target, null, "https".equalsIgnoreCase(target.getSchemeName))
            case _ =>
              new HttpRoute(target, null, proxyHost, "https".equalsIgnoreCase(target.getSchemeName))
          }
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

    // The Apache folks are afraid we misuse preemptive authentication, and so force us to copy paste some code
    // rather than providing a simple configuration flag. See:
    // http://hc.apache.org/httpcomponents-client-ga/tutorial/html/authentication.html#d4e950

    object PreemptiveAuthHttpRequestInterceptor extends HttpRequestInterceptor {
      def process(request: HttpRequest, context: HttpContext): Unit = {

        val authState           = context.getAttribute(ClientContext.TARGET_AUTH_STATE).asInstanceOf[AuthState]
        val credentialsProvider = context.getAttribute(ClientContext.CREDS_PROVIDER).asInstanceOf[CredentialsProvider]
        val targetHost          = context.getAttribute(ExecutionContext.HTTP_TARGET_HOST).asInstanceOf[HttpHost]

        // If not auth scheme has been initialized yet
        if (authState.getAuthScheme eq null) {
          val authScope = new AuthScope(targetHost.getHostName, targetHost.getPort)
          // Obtain credentials matching the target host
          val credentials = credentialsProvider.getCredentials(authScope)
          // If found, generate preemptively
          if (credentials ne null) {
            authState.update(
              if (credentials.isInstanceOf[NTCredentials]) new NTLMScheme(JCIFSEngine) else new BasicScheme,
              credentials
            )
          }
        }
      }
    }

    object JCIFSEngine extends NTLMEngine {

      def generateType1Msg(domain: String, workstation: String): String = {
        val t1m = new Type1Message(Type1Message.getDefaultFlags, domain, workstation)
        Base64.encode(t1m.toByteArray)
      }

      def generateType3Msg(username: String, password: String, domain: String, workstation: String, challenge: String): String = {
        val t2m =
          try
            new Type2Message(Base64.decode(challenge))
          catch {
            case ex: IOException =>
              throw new NTLMEngineException("Invalid Type2 message", ex)
          }

        val t3m =
          new Type3Message(t2m, password, domain, username, workstation, 0)

        Base64.encode(t3m.toByteArray)
      }
    }
  }
}
