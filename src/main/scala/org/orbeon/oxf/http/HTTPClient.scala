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
import java.net._
import java.security.KeyStore

import jcifs.ntlmssp.{Type3Message, Type2Message, Type1Message}
import jcifs.util.Base64
import org.apache.http.auth._
import org.apache.http.client.CredentialsProvider
import org.apache.http.client.protocol.{ClientContext, RequestAcceptEncoding, ResponseContentEncoding}
import org.apache.http.conn.routing.{HttpRoute, HttpRoutePlanner}
import org.apache.http.conn.scheme.{PlainSocketFactory, Scheme, SchemeRegistry}
import org.apache.http.conn.ssl.SSLSocketFactory
import org.apache.http.impl.auth.{NTLMEngineException, NTLMEngine, BasicScheme, NTLMScheme}
import org.apache.http.impl.client.{BasicCredentialsProvider, DefaultHttpClient}
import org.apache.http.impl.conn.PoolingClientConnectionManager
import org.apache.http.params.{BasicHttpParams, HttpConnectionParams}
import org.apache.http.protocol.{ExecutionContext, BasicHttpContext, HttpContext}
import org.apache.http.{ProtocolException ⇒ _, _}
import org.orbeon.oxf.util.ScalaUtils._

class HTTPClient(settings: ConnectionSettings) {

    import Private._

    final def newHttpClient(credentials: Option[Credentials], host: String, port: Int) = {

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

        credentials foreach { actualCredentials ⇒

            // Make authentication preemptive when needed. Interceptor is added first, as the Authentication header
            // is added by HttpClient's RequestTargetAuthentication which is itself an interceptor, so our
            // interceptor needs to run before RequestTargetAuthentication, otherwise RequestTargetAuthentication
            // won't find the appropriate AuthState/AuthScheme/Credentials in the HttpContext.

            if (actualCredentials.preemptiveAuth)
                httpClient.addRequestInterceptor(PreemptiveAuthHttpRequestInterceptor, 0)

            val credentialsProvider = new BasicCredentialsProvider
            httpContext.setAttribute(ClientContext.CREDS_PROVIDER, credentialsProvider)

            credentialsProvider.setCredentials(
                new AuthScope(host, port),
                actualCredentials match {
                    case Credentials(username, passwordOpt, _, None) ⇒
                        new UsernamePasswordCredentials(username, passwordOpt getOrElse "")
                    case Credentials(username, passwordOpt, _, Some(domain)) ⇒
                        new NTCredentials(username, passwordOpt getOrElse "", host, domain)
                }
            )
        }

        (httpClient, httpContext)
    }

    final def shutdown() = connectionManager.shutdown()
    final def usingProxy = proxyHost.isDefined

    private object Private {

        // BasicHttpParams is not thread-safe per the doc
        def newHttpParams =
            new BasicHttpParams |!>
            (HttpConnectionParams.setStaleCheckingEnabled(_, settings.staleCheckingEnabled)) |!>
            (HttpConnectionParams.setSoTimeout(_, settings.soTimeout))

        // It seems that credentials and state are not thread-safe, so create every time
        def newProxyAuthState = proxyCredentials map {
            case c: NTCredentials ⇒ new AuthState |!> (_.update(new NTLMScheme(JCIFSEngine), c))
            case c: UsernamePasswordCredentials ⇒ new AuthState |!> (_.update(new BasicScheme, c))
            case _ ⇒ throw new IllegalStateException
        }

        // The single ConnectionManager
        val connectionManager = {

            // Create SSL context, based on a custom key store if specified
            val trustStore =
                (settings.sslKeystoreURI, settings.sslKeystorePassword) match {
                    case (Some(keyStoreURI), Some(keyStorePassword)) ⇒
                        val url = new URL(null, keyStoreURI)
                        val is = url.openStream
                        val result = KeyStore.getInstance(KeyStore.getDefaultType)
                        result.load(is, keyStorePassword.toCharArray)
                        Some(result → keyStorePassword)
                    case _ ⇒
                        None
                }

            // Create SSL hostname verifier
            val hostnameVerifier = settings.sslHostnameVerifier match {
                case "browser-compatible" ⇒ SSLSocketFactory.BROWSER_COMPATIBLE_HOSTNAME_VERIFIER
                case "allow-all" ⇒ SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER
                case _ ⇒ SSLSocketFactory.STRICT_HOSTNAME_VERIFIER
            }

            // Declare schemes (though having to declare common schemes like HTTP and HTTPS seems wasteful)
            val schemeRegistry = new SchemeRegistry
            schemeRegistry.register(new Scheme("http", 80, PlainSocketFactory.getSocketFactory))

            // Calling "full" constructor: https://github.com/apache/httpclient/blob/4.2.x/httpclient/src/main/java/org/apache/http/conn/ssl/SSLSocketFactory.java#L203
            val sslSocketFactory =
                new SSLSocketFactory(
                    SSLSocketFactory.TLS,
                    trustStore map (_._1) orNull,
                    trustStore map (_._2) orNull,
                    trustStore map (_._1) orNull,
                    null,
                    null,
                    hostnameVerifier
                )

            schemeRegistry.register(new Scheme("https", 443, sslSocketFactory))

            // Pooling connection manager with limits removed
            new PoolingClientConnectionManager(schemeRegistry) |!>
                    (_.setMaxTotal(Integer.MAX_VALUE)) |!>
                    (_.setDefaultMaxPerRoute(Integer.MAX_VALUE))
        }


        val (proxyHost, proxyExclude, proxyCredentials) = {
            // Set proxy if defined in properties
            (settings.proxyHost, settings.proxyPort) match {
                case (Some(proxyHost), Some(proxyPort)) ⇒
                    val _httpProxy = new HttpHost(proxyHost, proxyPort, if (settings.proxySSL) "https" else "http")
                    val _proxyExclude = settings.proxyExclude

                    // Proxy authentication
                    val _proxyCredentials =
                        (settings.proxyUsername, settings.proxyPassword) match {
                            case (Some(proxyUsername), Some(proxyPassword)) ⇒
                                Some(
                                    (settings.proxyNTLMHost, settings.proxyNTLMDomain) match {
                                        case (Some(ntlmHost), Some(ntlmDomain)) ⇒
                                            new NTCredentials(proxyUsername, proxyPassword, ntlmHost, ntlmDomain)
                                        case _ ⇒
                                            new UsernamePasswordCredentials(proxyUsername, proxyPassword)
                                    }
                                )
                            case _ ⇒ None
                        }

                    (Some(_httpProxy), _proxyExclude, _proxyCredentials)
                case _ ⇒
                    (None, None, None)
            }
        }

        val routePlanner = proxyHost map { proxyHost ⇒
            new HttpRoutePlanner {
                def determineRoute(target: HttpHost, request: HttpRequest, context: HttpContext) =
                    proxyExclude match {
                        case Some(proxyExclude) if target != null && target.getHostName.matches(proxyExclude) ⇒
                            new HttpRoute(target, null, "https".equalsIgnoreCase(target.getSchemeName))
                        case _ ⇒
                            new HttpRoute(target, null, proxyHost, "https".equalsIgnoreCase(target.getSchemeName))
                    }
            }
        }

        /**
         * The Apache folks are afraid we misuse preemptive authentication, and so force us to copy paste some code rather
         * than providing a simple configuration flag. See:
         *
         * http://hc.apache.org/httpcomponents-client-ga/tutorial/html/authentication.html#d4e950
         */
        object PreemptiveAuthHttpRequestInterceptor extends HttpRequestInterceptor {
            def process(request: HttpRequest, context: HttpContext) {

                val authState = context.getAttribute(ClientContext.TARGET_AUTH_STATE).asInstanceOf[AuthState]
                val credentialsProvider = context.getAttribute(ClientContext.CREDS_PROVIDER).asInstanceOf[CredentialsProvider]
                val targetHost = context.getAttribute(ExecutionContext.HTTP_TARGET_HOST).asInstanceOf[HttpHost]

                // If not auth scheme has been initialized yet
                if (authState.getAuthScheme == null) {
                    val authScope = new AuthScope(targetHost.getHostName, targetHost.getPort)
                    // Obtain credentials matching the target host
                    val credentials = credentialsProvider.getCredentials(authScope)
                    // If found, generate preemptively
                    if (credentials != null) {
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
                        case ex: IOException ⇒
                            throw new NTLMEngineException("Invalid Type2 message", ex)
                    }

                val t3m =
                    new Type3Message(t2m, password, domain, username, workstation, 0)

                Base64.encode(t3m.toByteArray)
            }
        }

    }
}
