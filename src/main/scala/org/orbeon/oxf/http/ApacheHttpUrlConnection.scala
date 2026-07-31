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

import org.apache.hc.client5.http.cookie.BasicCookieStore

import java.io.OutputStream
import java.net.{HttpURLConnection, URL}
import scala.collection.mutable
import scala.jdk.CollectionConverters.*


// Expose `ApacheHttpClient` as `HttpURLConnection`
// 2019-12-13: No longer supports methods which set a body.
class ApacheHttpUrlConnection(url: URL)(client: PropertiesApacheHttpClient.type) extends HttpURLConnection(url) {

  private val _requestHeaders = new mutable.LinkedHashMap[String, mutable.ListBuffer[String]]

  private var _httpResponse: Option[HttpResponse] = None

  def connect(): Unit =
    if (_httpResponse.isEmpty)
      _httpResponse = {
        Some(
          client.connect(
            url         = url.toExternalForm,
            credentials = None,
            cookieStore = new BasicCookieStore,
            method      = HttpMethod.withNameInsensitive(Option(method) getOrElse "GET"),
            headers     = _requestHeaders.view.mapValues(_.toList).toMap,
            contentOpt  = None
          )(
            requestCtx  = None
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
    _requestHeaders.view.mapValues(_.asJava).toMap.asJava

  override def getInputStream =
    withConnection(_.content.stream)

  override def getHeaderField(name: String): String =
    withConnection(_.headers.get(Headers.capitalizeCommonOrSplitHeader(name)) flatMap (_.lastOption) orNull)

  override def getHeaderFields =
    withConnection(_.headers.view.mapValues(_.asJava).toMap.asJava)

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
  override def usingProxy               : Boolean = throw new UnsupportedOperationException
  override def getHeaderFieldKey(n: Int): String  = throw new UnsupportedOperationException
  override def getHeaderField(n: Int)   : String  = throw new UnsupportedOperationException
}
