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

import java.io.ByteArrayOutputStream
import java.net._

import org.apache.http.client.CookieStore
import org.apache.http.impl.client.BasicCookieStore
import org.orbeon.oxf.util.Headers
import org.orbeon.oxf.util.ScalaUtils._

import scala.collection.JavaConverters._
import scala.collection.mutable

class ApacheHttpUrlConnection(url: URL)(implicit client: HttpClient) extends HttpURLConnection(url) {
    
    private var _cookieStore: Option[CookieStore] = None
    def setCookieStore(cookieStore: CookieStore) =
        this._cookieStore = Option(cookieStore)

    private var _method: Option[String] = None

    override def setRequestMethod(methodName: String): Unit = {

        if (_httpResponse.isDefined)
            throw new ProtocolException("Can't reset method: already connected")

        _method = Some(methodName)
    }

    private var _requestBody: Option[Array[Byte]] = None
    def setRequestBody(requestBody: Array[Byte]): Unit =
        this._requestBody = Option(requestBody)

    private val _requestHeaders = new mutable.LinkedHashMap[String, mutable.ListBuffer[String]]

    private var _credentials: Option[Credentials] = None
    def setCredentials(credentials: Option[Credentials]) =
        _credentials = credentials

    private var _httpResponse   : Option[HttpResponse] = None
    private var _os             : ByteArrayOutputStream = null

    def connect(): Unit = {
        if (_httpResponse.isEmpty)
            _httpResponse = {

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

                val methodName = _method getOrElse "GET" toUpperCase

                def produceContent = (
                    _requestBody
                    orElse    (Option(_os) map (_.toByteArray))
                    getOrElse (throw new IllegalArgumentException(s"No request body set for method $methodName"))
                )

                Some(
                    client.connect(
                        url         = url.toExternalForm,
                        credentials = credentialsFromURL(url) orElse _credentials,
                        cookieStore = _cookieStore getOrElse new BasicCookieStore,
                        method      = methodName,
                        headers     = _requestHeaders mapValues (_.toList) toMap,
                        content     = produceContent
                    )
                )
            }
    }

    override def setRequestProperty(key: String, value: String): Unit =
        _requestHeaders.put(key, mutable.ListBuffer(value))

    override def addRequestProperty(key: String, value: String): Unit =
        _requestHeaders.getOrElseUpdate(key, mutable.ListBuffer()) += value

    // NOTE: No caller in our code
    override def getRequestProperty(key: String) =
        _requestHeaders.get(key) flatMap (_.lastOption) orNull

    // NOTE: No caller in our code
    override def getRequestProperties =
        (_requestHeaders mapValues (_.asJava) toMap) asJava

    override def getOutputStream = {
        if (_os == null)
            _os = new ByteArrayOutputStream
        _os
    }

    override def getInputStream =
        withConnection(_.inputStream)

    override def getHeaderField(name: String): String =
        withConnection(_.headers.get(Headers.capitalizeCommonOrSplitHeader(name)) flatMap (_.lastOption) orNull)

    override def getHeaderFields =
        withConnection(_.headers mapValues (_.asJava) asJava)

    // Rarely used methods which we don't use and haven't implemented
    override def usingProxy                = ???
    override def getHeaderFieldKey(n: Int) = ???
    override def getHeaderField(n: Int)    = ???

    override def getResponseCode =
        withConnection(_.statusCode)

    def disconnect() =
        withConnection(_.disconnect())

    override def getLastModified =
        Option(getHeaderField("last-modified")) match {
            case Some(_) ⇒ super.getLastModified
            case None    ⇒ 0L
        }

    private def withConnection[T](body: HttpResponse ⇒ T) = {
        if (_httpResponse.isEmpty)
            connect()

        body(_httpResponse.get)
    }
}
