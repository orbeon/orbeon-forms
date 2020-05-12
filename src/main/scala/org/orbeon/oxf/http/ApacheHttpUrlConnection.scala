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

import java.io.OutputStream
import java.net._

import org.apache.http.impl.client.BasicCookieStore
import org.orbeon.io.CharsetNames
import org.orbeon.oxf.util.StringUtils._

import scala.collection.JavaConverters._
import scala.collection.mutable

// Expose `ApacheHttpClient` as `HttpURLConnection`
// 2019-12-13: No longer supports methods which set a body.
class ApacheHttpUrlConnection(url: URL)(implicit client: HttpClient) extends HttpURLConnection(url) {

  private val _requestHeaders = new mutable.LinkedHashMap[String, mutable.ListBuffer[String]]

  private var _httpResponse: Option[HttpResponse] = None

  def connect(): Unit =
    if (_httpResponse.isEmpty)
      _httpResponse = {

        def credentialsFromURL(url: URL): Option[Credentials] = {
          url.getUserInfo.trimAllToOpt flatMap { userInfo =>
            // Set username and optional password specified on URL
            val separatorPosition = userInfo.indexOf(":")

            val (username, password) =
              if (separatorPosition == -1)
                userInfo -> ""
              else
                userInfo.substring(0, separatorPosition) -> userInfo.substring(separatorPosition + 1)

            // If the username/password contain special character, those characters will be encoded, since
            // we are getting this from a URL. Now do the decoding.

            val usernameOpt = username.trimAllToOpt map (URLDecoder.decode(_, CharsetNames.Utf8))
            val passwordOpt = password.trimAllToOpt map (URLDecoder.decode(_, CharsetNames.Utf8))

            usernameOpt map { username =>
              Credentials(username, passwordOpt, preemptiveAuth = true, None)
            }
          }
        }

        Some(
          client.connect(
            url         = url.toExternalForm,
            credentials = credentialsFromURL(url),
            cookieStore = new BasicCookieStore,
            method      = HttpMethod.withNameInsensitive(Option(method) getOrElse "GET"),
            headers     = _requestHeaders mapValues (_.toList) toMap,
            content     = None
          )
        )
      }

  override def setRequestProperty(key: String, value: String): Unit =
    _requestHeaders.put(Headers.capitalizeCommonOrSplitHeader(key), mutable.ListBuffer(value))

  override def addRequestProperty(key: String, value: String): Unit =
    _requestHeaders.getOrElseUpdate(Headers.capitalizeCommonOrSplitHeader(key), mutable.ListBuffer()) += value

  // NOTE: No caller in our code
  override def getRequestProperty(key: String) =
    _requestHeaders.get(Headers.capitalizeCommonOrSplitHeader(key)) flatMap (_.lastOption) orNull

  // NOTE: No caller in our code
  override def getRequestProperties =
    (_requestHeaders mapValues (_.asJava) toMap) asJava

  override def getInputStream =
    withConnection(_.content.inputStream)

  override def getHeaderField(name: String): String =
    withConnection(_.headers.get(Headers.capitalizeCommonOrSplitHeader(name)) flatMap (_.lastOption) orNull)

  override def getHeaderFields =
    withConnection(_.headers mapValues (_.asJava) asJava)

  override def getResponseCode =
    withConnection(_.statusCode)

  def disconnect() =
    withConnection(_.disconnect())

  override def getLastModified =
    Option(getHeaderField(Headers.LastModifiedLower)) match {
      case Some(_) => super.getLastModified
      case None    => 0L
    }

  private def withConnection[T](body: HttpResponse => T) = {
    if (_httpResponse.isEmpty)
      connect()

    body(_httpResponse.get)
  }

  override def getOutputStream: OutputStream = throw new UnsupportedOperationException

  // Rarely used methods which we don't use and haven't implemented
  override def usingProxy                = throw new UnsupportedOperationException
  override def getHeaderFieldKey(n: Int) = throw new UnsupportedOperationException
  override def getHeaderField(n: Int)    = throw new UnsupportedOperationException
}
