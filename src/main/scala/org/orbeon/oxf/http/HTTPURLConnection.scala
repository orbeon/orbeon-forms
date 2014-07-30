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
import java.net._
import java.{util ⇒ ju}

import org.apache.http.client.CookieStore
import org.apache.http.client.methods._
import org.apache.http.entity.ByteArrayEntity
import org.apache.http.impl.client.BasicCookieStore
import org.apache.http.util.EntityUtils
import org.apache.http.{ProtocolException ⇒ _, _}
import org.orbeon.oxf.util.ScalaUtils._

import scala.collection.JavaConverters._
import scala.collection.mutable

class HTTPURLConnection(url: URL)(implicit client: HttpClientImpl) extends HttpURLConnection(url) {
    
    private var _connected = false

    private var _cookieStore: Option[CookieStore] = None
    def setCookieStore(cookieStore: CookieStore) =
        this._cookieStore = Option(cookieStore)

    private var _method: Option[HttpUriRequest] = None

    override def setRequestMethod(methodName: String): Unit = {

        if (_connected)
            throw new ProtocolException("Can't reset method: already connected")

        _method = Some(
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
        )
    }

    private var _requestBody: Array[Byte] = null
    def setRequestBody(requestBody: Array[Byte]): Unit =
        this._requestBody = requestBody

    private val _requestProperties = new mutable.LinkedHashMap[String, mutable.ListBuffer[String]]

    private var _credentials: Option[Credentials] = None
    def setCredentials(credentials: Option[Credentials]) =
        _credentials = credentials

    private var _httpResponse   : HttpResponse = null
    private var _responseHeaders: Map[String, String] = null
    private var _os             : ByteArrayOutputStream = null

    def connect(): Unit = {
        if (! _connected) {

            def credentialsFromURL(url: URL) = {
                nonEmptyOrNone(url.getUserInfo) flatMap { userInfo ⇒
                    // Set username and optional password specified on URL
                    val separatorPosition = userInfo.indexOf(":")

                    val (username, password) =
                        if (separatorPosition == -1)
                            userInfo → ""
                        else
                            userInfo.substring(0, separatorPosition) → userInfo.substring(separatorPosition + 1)

                    // If the username/password contain special character, those character will be encoded, since we
                    // are getting this from a URL. Now do the decoding.

                    val usernameOpt = nonEmptyOrNone(username) map (URLDecoder.decode(_, "utf-8"))
                    val passwordOpt = nonEmptyOrNone(password) map (URLDecoder.decode(_, "utf-8"))

                    usernameOpt map { username ⇒
                        Credentials(username, passwordOpt, preemptiveAuth = true, None)
                    }
                }
            }

            // Create the HTTP client and HTTP context for the client (we expect this to be fairly lightweight)
            val credentials = credentialsFromURL(url) orElse _credentials
            val (httpClient, httpContext) = client.newHttpClient(credentials, url.getHost, url.getPort)

            // Set cookie store, creating a new one if none was provided to us
            httpClient.setCookieStore(_cookieStore getOrElse new BasicCookieStore)

            // If method has not been set, use GET
            // This can happen e.g. when this connection handler is used from URLFactory
            if (_method.isEmpty)
                setRequestMethod("GET")

            val method = _method.get

            val skipAuthorizationHeader = credentials.isDefined

            // Set all headers
            for {
                (name, values) ← _requestProperties
                value          ← values
                // Skip over Authorization header if user authentication specified
                if ! (skipAuthorizationHeader && name.toLowerCase == "authorization")
            } locally {
                method.addHeader(name, value)
            }

            // Create request entity with body
            method match {
                case enclosingRequest: HttpEntityEnclosingRequest ⇒

                    // Use the body that was set directly, or the result of writing to the OutputStream
                    val body = if (_requestBody != null) _requestBody else if (_os != null) _os.toByteArray else null
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
            _httpResponse = httpClient.execute(method, httpContext)
            _connected = true
        }
    }

    override def getInputStream: InputStream = {
        if (! _connected)
            connect()
        val entity = _httpResponse.getEntity
        if (entity != null)
            entity.getContent
        else
            new InputStream {
                def read = -1
            }
    }

    override def getOutputStream = {
        if (_os == null)
            _os = new ByteArrayOutputStream
        _os
    }

    private def initResponseHeaders(): Unit = {
        if (! _connected)
            connect()

        if (_responseHeaders eq null)
            _responseHeaders = {
                for (header ← _httpResponse.getAllHeaders)
                yield
                    header.getName.toLowerCase → header.getValue
            } toMap
    }

    /**
     * This method will be called by URLConnection.getLastModified(), URLConnection.getContentLength(), etc.
     */
    override def getHeaderField(name: String): String = {
        initResponseHeaders()
        // We return the first header value only. This is not really right, is it? But it will work for the few calls
        // done by URLConnection.
         _responseHeaders.get(name).orNull
    }

    override def getHeaderFields: ju.Map[String, ju.List[String]] = {
        initResponseHeaders()
        _responseHeaders mapValues ju.Collections.singletonList[String] asJava
    }

    override def setRequestProperty(key: String, value: String): Unit =
        _requestProperties.put(key, mutable.ListBuffer(value))

    override def addRequestProperty(key: String, value: String): Unit =
        _requestProperties.getOrElseUpdate(key, mutable.ListBuffer()) += value

    // NOTE: No caller in our code
    override def getRequestProperty(key: String) =
        _requestProperties.get(key) flatMap (_.headOption) orNull

    // NOTE: No caller in our code
    // This is not safe as the caller could mutate both the map or the contained buffers
    override def getRequestProperties: ju.Map[String, ju.List[String]] =
        _requestProperties mapValues (_.asJava) asJava

    override def getResponseCode = _httpResponse.getStatusLine.getStatusCode

    def disconnect() =
        EntityUtils.consume(_httpResponse.getEntity)

    override def getLastModified: Long = {
        val field = getHeaderField("last-modified")
        if (field != null) super.getLastModified else 0
    }

    override def usingProxy = client.usingProxy
}
