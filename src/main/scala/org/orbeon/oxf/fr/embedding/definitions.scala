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
package org.orbeon.oxf.fr.embedding

import java.io.{InputStream, OutputStream, Writer}
import java.net.HttpURLConnection

import org.orbeon.oxf.http.HttpClientImpl

import scala.collection.JavaConverters._
import scala.collection.immutable

sealed trait  Action              { val name: String }
case   object New  extends Action { val name = "new" }
case   object Edit extends Action { val name = "edit" }
case   object View extends Action { val name = "view" }

sealed trait ContentOrRedirect

case class Content(
    body       : String Either Array[Byte],
    contentType: Option[String],
    title      : Option[String]
) extends ContentOrRedirect

case class Redirect(
    location   : String,
    exitPortal : Boolean = false
) extends ContentOrRedirect {
    require(location ne null, "Missing Location header in redirect response")
}

case class RequestDetails(
    content: Option[Content],
    url    : String,
    headers: immutable.Seq[(String, String)],
    params : immutable.Seq[(String, String)]
)

trait EmbeddingContext {
    def namespace                                       : String
    def getSessionAttribute(name: String)               : AnyRef
    def setSessionAttribute(name: String, value: AnyRef): Unit
    def removeSessionAttribute(name: String)            : Unit
    def log(message: String)                            : Unit  // consider removing
    def httpClient                                      : HttpClientImpl
}

trait EmbeddingContextWithResponse extends EmbeddingContext{
    def writer                                : Writer
    def outputStream                          : OutputStream    // for binary resources only
    def setHeader(name: String, value: String): Unit
    def setStatusCode(code: Int)              : Unit
    def decodeURL(encoded: String)            : String
}

case class HttpURLConnectionResponse(cx: HttpURLConnection) extends HttpResponse {
    def statusCode  = cx.getResponseCode
    def headers     = cx.getHeaderFields.asScala.toMap map { case (name, values) ⇒ name → (values.asScala mkString ",") }
    def inputStream = cx.getInputStream
    def contentType = cx.getContentType
}

trait HttpResponse {
    def statusCode : Int
    def headers    : immutable.Map[String, String]
    def inputStream: InputStream
    def contentType: String
}

trait HttpClient {
    def openConnection(
        url         : String,
        content     : Option[(Option[String], OutputStream ⇒ Unit)],
        headers     : immutable.Iterable[(String, String)])(
        implicit ctx: EmbeddingContext
    ): HttpResponse
}
