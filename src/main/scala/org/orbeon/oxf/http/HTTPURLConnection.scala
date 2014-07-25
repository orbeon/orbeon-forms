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
package org.orbeon.oxf.http

import java.io.{ByteArrayOutputStream, InputStream}
import java.net.{ProtocolException, URL, URLConnection, URLDecoder}
import java.security.KeyStore
import java.{util ⇒ ju}

import org.apache.commons.lang3.StringUtils
import org.apache.http._
import org.apache.http.auth._
import org.apache.http.client.CookieStore
import org.apache.http.client.methods._
import org.apache.http.client.protocol.ClientContext
import org.apache.http.conn.ClientConnectionManager
import org.apache.http.conn.params.{ConnManagerParams, ConnPerRouteBean}
import org.apache.http.conn.routing.{HttpRoute, HttpRoutePlanner}
import org.apache.http.conn.scheme.{PlainSocketFactory, Scheme, SchemeRegistry}
import org.apache.http.conn.ssl.SSLSocketFactory
import org.apache.http.entity.ByteArrayEntity
import org.apache.http.impl.client.{BasicCookieStore, BasicCredentialsProvider, DefaultHttpClient}
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager
import org.apache.http.params.{BasicHttpParams, HttpConnectionParamBean, HttpParams}
import org.apache.http.protocol.{BasicHttpContext, HttpContext}
import org.apache.http.util.EntityUtils
import org.orbeon.oxf.properties.Properties
import org.orbeon.oxf.util.{Connection, LoggerFactory, StringConversions}



class HTTPURLConnection(url: URL) extends URLConnection(url) {
    
    import org.orbeon.oxf.http.HTTPURLConnection._

    private var cookieStore: CookieStore = null
    private var _connected: Boolean = false
    private var method: HttpUriRequest = null
    private var httpResponse: HttpResponse = null
    private var requestBody: Array[Byte] = null
    private val requestProperties: ju.Map[String, Array[String]] = new ju.LinkedHashMap[String, Array[String]]
    private var responseHeaders: ju.HashMap[String, ju.List[String]] = null
    private var username: String = null
    private var password: String = null
    private var preemptiveAuthentication: String = null
    private var domain: String = null
    private var os: ByteArrayOutputStream = null

    def setRequestMethod(methodName: String): Unit = {
        if (_connected)
            throw new ProtocolException("Can't reset method: already connected")

        method =
            methodName match {
                case "GET"     ⇒ new HttpGet(url.toString)
                case "POST"    ⇒ new HttpPost(url.toString)
                case "HEAD"    ⇒ new HttpHead(url.toString)
                case "OPTIONS" ⇒ new HttpOptions(url.toString)
                case "PUT"     ⇒ new HttpPut(url.toString)
                case "DELETE"  ⇒ new HttpDelete(url.toString)
                case "TRACE"   ⇒ new HttpTrace(url.toString)
                case _         ⇒ throw new ProtocolException(s"Method $methodName is not supported")
            }
    }

    def getCookieStore = cookieStore
    def setCookieStore(cookieStore: CookieStore) = this.cookieStore = cookieStore

    def connect(): Unit = {
        if (! _connected) {
            val userInfo = url.getUserInfo
            val isAuthenticationRequestedWithUsername = StringUtils.isNotBlank(username)

            // Create the HTTP client and HTTP context for the client (we expect this to be fairly lightweight)
            val httpClient = new DefaultHttpClient(connectionManager, httpParams)
            val httpContext = new BasicHttpContext

            // Assign route planner for dynamic exclusion of hostnames from proxying
            if (httpProxy != null) {
                httpClient.setRoutePlanner(routePlanner)
            }

            // Set cookie store, creating a new one if none was provided to us
            if (cookieStore == null)
                cookieStore = new BasicCookieStore
            httpClient.setCookieStore(cookieStore)

            // Set proxy and host authentication
            if (proxyAuthState != null)
                httpContext.setAttribute(ClientContext.PROXY_AUTH_STATE, proxyAuthState)
            if (userInfo != null || isAuthenticationRequestedWithUsername) {

                // Make authentication preemptive; interceptor is added first, as the Authentication header is added
                // by HttpClient's RequestTargetAuthentication which is itself an interceptor, so our interceptor
                // needs to run before RequestTargetAuthentication, otherwise RequestTargetAuthentication won't find
                // the appropriate AuthState/AuthScheme/Credentials in the HttpContext

                // Don't add the interceptor if we don't want preemptive authentication!
                if (preemptiveAuthentication != "false")
                    httpClient.addRequestInterceptor(preemptiveAuthHttpRequestInterceptor, 0)

                val credentialsProvider = new BasicCredentialsProvider
                httpContext.setAttribute(ClientContext.CREDS_PROVIDER, credentialsProvider)
                val authScope  = new AuthScope(url.getHost, url.getPort)
                val credentials =
                    if (userInfo != null) {
                        // Set username and optional password specified on URL
                        val separatorPosition = userInfo.indexOf(":")
                        var username = if (separatorPosition == -1) userInfo else userInfo.substring(0, separatorPosition)
                        var password = if (separatorPosition == -1) "" else userInfo.substring(separatorPosition + 1)

                        // If the username/password contain special character, those character will be encoded, since we
                        // are getting this from a URL. Now do the decoding.
                        username = URLDecoder.decode(username, "utf-8")
                        password = URLDecoder.decode(password, "utf-8")
                        new UsernamePasswordCredentials(username, password)
                    } else {
                        // Set username and password specified externally
                        if (domain == null)
                            new UsernamePasswordCredentials(username, if (password == null) "" else password)
                        else
                            new NTCredentials(username, password, url.getHost, domain)
                    }
                credentialsProvider.setCredentials(authScope, credentials)
            }

            // If method has not been set, use GET
            // This can happen e.g. when this connection handler is used from URLFactory
            if (method == null)
                setRequestMethod("GET")

            val skipAuthorizationHeader = userInfo != null || username != null
            import scala.collection.JavaConverters._

            // Set all headers
            for {
                (name, values) ← requestProperties.asScala
                value          ← values
                // Skip over Authorization header if user authentication specified
                if ! (skipAuthorizationHeader && name.toLowerCase == Connection.AuthorizationHeader.toLowerCase)
            } locally {
                method.addHeader(name, value)
            }

            // Create request entity with body
            method match {
                case enclosingRequest: HttpEntityEnclosingRequest ⇒

                    // Use the body that was set directly, or the result of writing to the OutputStream
                    val body = if (requestBody != null) requestBody else if (os != null) os.toByteArray else null
                    if (body != null) {
                        val contentTypeHeader = method.getFirstHeader("Content-Type") // header names are case-insensitive for comparison
                        if (contentTypeHeader == null)
                            throw new ProtocolException("Can't set request entity: Content-Type header is missing")
                        val byteArrayEntity = new ByteArrayEntity(body)
                        byteArrayEntity.setContentType(contentTypeHeader)
                        enclosingRequest.setEntity(byteArrayEntity)
                    }
                case _ ⇒
            }

            // Make request
            httpResponse = httpClient.execute(method, httpContext)
            _connected = true
        }
    }

    override def getInputStream: InputStream = {
        if (! _connected)
            connect()
        val entity = httpResponse.getEntity
        if (entity != null)
            entity.getContent
        else
            new InputStream {
                def read = -1
            }
    }

    def setRequestBody(requestBody: Array[Byte]): Unit =
        this.requestBody = requestBody

    override def getOutputStream = {
        if (os == null)
            os = new ByteArrayOutputStream
        os
    }

    private def initResponseHeaders(): Unit = {
        if (! _connected)
            connect()
        if (responseHeaders == null) {
            responseHeaders = new ju.HashMap[String, ju.List[String]]
            for (header ← httpResponse.getAllHeaders)
                responseHeaders.put(header.getName.toLowerCase, ju.Collections.singletonList(header.getValue))
        }
    }

    /**
     * This method will be called by URLConnection.getLastModified(), URLConnection.getContentLength(), etc.
     */
    override def getHeaderField(name: String): String = {
        initResponseHeaders()
        // We return the first header value only. This is not really right, is it? But it will work for the few calls
        // done by URLConnection.
        val values = responseHeaders.get(name)
        if (values != null) values.get(0) else null
    }

    override def getHeaderFields: ju.Map[String, ju.List[String]] = {
        initResponseHeaders()
        responseHeaders
    }

    override def setRequestProperty(key: String, value: String): Unit = {
        super.setRequestProperty(key, value)
        requestProperties.put(key, Array(value))
    }

    override def addRequestProperty(key: String, value: String): Unit = {
        super.addRequestProperty(key, value)
        StringConversions.addValueToStringArrayMap(requestProperties, key, value)
    }

    override def getRequestProperty(key: String): String = {
        // Not sure what should be returned so return the first value if any. But likely nobody is calling this method.
        val values = requestProperties.get(key)
        if (values == null) null else values(0)
    }

    override def getRequestProperties: ju.Map[String, ju.List[String]] = {
        super.getRequestProperties
    }

    def getResponseCode = httpResponse.getStatusLine.getStatusCode

    // "As of version 4.1 one should be using EntityUtils#consume() instead."
    // http://mail-archives.apache.org/mod_mbox/hc-httpclient-users/201012.mbox/%3C1291222613.3781.68.camel@ubuntu%3E
    def disconnect() =
        EntityUtils.consume(httpResponse.getEntity)

    def setUsername(username: String): Unit =
        this.username = username.trim

    def setPassword(password: String): Unit =
        this.password = password.trim

    def setDomain(domain: String): Unit =
        this.domain = domain.trim

    def setPreemptiveAuthentication(preemptiveAuthentication: String): Unit =
        this.preemptiveAuthentication = preemptiveAuthentication

    override def getLastModified: Long = {
        val field = getHeaderField("last-modified")
        if (field != null) super.getLastModified else 0
    }

    private val routePlanner = new HttpRoutePlanner {
        def determineRoute(target: HttpHost, request: HttpRequest, context: HttpContext) =
            if (proxyExclude == null || target == null || ! target.getHostName.matches(proxyExclude)) {
                logger.debug((if (target == null) "NULL" else target.getHostName) + " does not match " + (if (proxyExclude == null) "NULL" else proxyExclude) + " and will be proxied")
                new HttpRoute(target, null, httpProxy, "https".equalsIgnoreCase(target.getSchemeName))
            } else {
                logger.debug((if (target == null) "NULL" else target.getHostName) + " does match " + (if (proxyExclude == null) "NULL" else proxyExclude) + " and will be excluded from proxy")
                new HttpRoute(target, null, "https".equalsIgnoreCase(target.getSchemeName))
            }
    }
}

object HTTPURLConnection {
    private val logger = LoggerFactory.createLogger(classOf[HTTPURLConnection])
    val STALE_CHECKING_ENABLED_PROPERTY = "oxf.http.stale-checking-enabled"
    val SO_TIMEOUT_PROPERTY = "oxf.http.so-timeout"
    val PROXY_HOST_PROPERTY = "oxf.http.proxy.host"
    val PROXY_PORT_PROPERTY = "oxf.http.proxy.port"
    val PROXY_EXCLUDE_PROPERTY = "oxf.http.proxy.exclude"
    val SSL_HOSTNAME_VERIFIER = "oxf.http.ssl.hostname-verifier"
    val SSL_KEYSTORE_URI = "oxf.http.ssl.keystore.uri"
    val SSL_KEYSTORE_PASSWORD = "oxf.http.ssl.keystore.password"
    val PROXY_SSL_PROPERTY = "oxf.http.proxy.use-ssl"
    val PROXY_USERNAME_PROPERTY = "oxf.http.proxy.username"
    val PROXY_PASSWORD_PROPERTY = "oxf.http.proxy.password"
    val PROXY_NTLM_HOST_PROPERTY = "oxf.http.proxy.ntlm.host"
    val PROXY_NTLM_DOMAIN_PROPERTY = "oxf.http.proxy.ntlm.domain"

    private val preemptiveAuthHttpRequestInterceptor = new PreemptiveAuthHttpRequestInterceptor

    // Use a single shared connection manager so we can have efficient connection pooling
    private var connectionManager: ClientConnectionManager = null
    private var httpParams: HttpParams = null
    private var proxyAuthState: AuthState = null
    private var httpProxy: HttpHost = null
    private var proxyExclude: String = null

    locally {
        val basicHttpParams = new BasicHttpParams
        // Remove limit on the number of connections per host
        ConnManagerParams.setMaxConnectionsPerRoute(basicHttpParams, new ConnPerRouteBean(Integer.MAX_VALUE))
        // Remove limit on the number of max connections
        ConnManagerParams.setMaxTotalConnections(basicHttpParams, Integer.MAX_VALUE)

        // Set parameters per as configured in the properties
        val paramBean = new HttpConnectionParamBean(basicHttpParams)
        val propertySet = Properties.instance.getPropertySet
        paramBean.setStaleCheckingEnabled(propertySet.getBoolean(STALE_CHECKING_ENABLED_PROPERTY, default = true))
        paramBean.setSoTimeout(propertySet.getInteger(SO_TIMEOUT_PROPERTY, 0))

        // Create SSL context, based on a custom key store if specified
        val keyStorePassword = propertySet.getString(SSL_KEYSTORE_PASSWORD)

        val trustStore = Option(propertySet.getStringOrURIAsString(SSL_KEYSTORE_URI, allowEmpty = false)) map { keyStoreURI ⇒
            val url = new URL(null, keyStoreURI)
            val is = url.openStream
            val result = KeyStore.getInstance(KeyStore.getDefaultType)
            result.load(is, keyStorePassword.toCharArray)
            result
        }

        // Create SSL hostname verifier
        val hostnameVerifierProperty = propertySet.getString(SSL_HOSTNAME_VERIFIER, "strict")
        val hostnameVerifier = hostnameVerifierProperty match {
            case "browser-compatible" ⇒ SSLSocketFactory.BROWSER_COMPATIBLE_HOSTNAME_VERIFIER
            case "allow-all"          ⇒ SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER
            case _                    ⇒ SSLSocketFactory.STRICT_HOSTNAME_VERIFIER
        }

        // Declare schemes (though having to declare common schemes like HTTP and HTTPS seems wasteful)
        val schemeRegistry = new SchemeRegistry
        schemeRegistry.register(new Scheme("http", 80, PlainSocketFactory.getSocketFactory))

        // Calling "full" constructor: https://github.com/apache/httpclient/blob/4.2.x/httpclient/src/main/java/org/apache/http/conn/ssl/SSLSocketFactory.java#L203
        val sslSocketFactory = new SSLSocketFactory(SSLSocketFactory.TLS, trustStore.orNull, keyStorePassword, trustStore.orNull, null, null, hostnameVerifier)
        schemeRegistry.register(new Scheme("https", 443, sslSocketFactory))

        connectionManager = new ThreadSafeClientConnManager(basicHttpParams, schemeRegistry)

        // Set proxy if defined in properties
        val proxyHost = propertySet.getString(PROXY_HOST_PROPERTY)
        val proxyPort = propertySet.getInteger(PROXY_PORT_PROPERTY)
        if (proxyHost != null && proxyPort != null) {
            val useTLS = propertySet.getBoolean(PROXY_SSL_PROPERTY, default = false)
            httpProxy = new HttpHost(proxyHost, proxyPort, if (useTLS) "https" else "http")
            proxyExclude = propertySet.getString(PROXY_EXCLUDE_PROPERTY)

            // Proxy authentication
            val proxyUsername = propertySet.getString(PROXY_USERNAME_PROPERTY)
            val proxyPassword = propertySet.getString(PROXY_PASSWORD_PROPERTY)
            if (proxyUsername != null && proxyPassword != null) {
                val ntlmHost = propertySet.getString(PROXY_NTLM_HOST_PROPERTY)
                val ntlmDomain = propertySet.getString(PROXY_NTLM_DOMAIN_PROPERTY)

                val proxyCredentials =
                    if (ntlmHost != null && ntlmDomain != null)
                        new NTCredentials(proxyUsername, proxyPassword, ntlmHost, ntlmDomain)
                    else
                        new UsernamePasswordCredentials(proxyUsername, proxyPassword)

                proxyAuthState = new AuthState
                proxyAuthState.setCredentials(proxyCredentials)
            }
        }

        // Save HTTP parameters which we'll need when instantiating an HttpClient (even though it could get the
        // parameters from the connection manager)
        httpParams = basicHttpParams
    }
}