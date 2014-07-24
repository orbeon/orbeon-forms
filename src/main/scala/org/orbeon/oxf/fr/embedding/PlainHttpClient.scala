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

import java.io.OutputStream
import java.net.{HttpURLConnection, URL}

import org.orbeon.oxf.util.ScalaUtils._

import collection.immutable.Iterable
import scala.util.control.NonFatal
import scala.collection.JavaConverters._

case class PlainHttpResponse(cx: HttpURLConnection) extends HttpResponse {
    def statusCode  = cx.getResponseCode
    def headers     = cx.getHeaderFields.asScala.toMap map { case (name, values) ⇒ name → (values.asScala mkString ",") }
    def inputStream = cx.getInputStream
    def contentType = cx.getContentType
}

object PlainHttpClient extends HttpClient {

    def openConnection(
        url         : String,
        content     : Option[(Option[String], OutputStream ⇒ Unit)],
        headers     : Iterable[(String, String)])(
        implicit ctx: EmbeddingContext
    ): HttpResponse = {

        val cx = new URL(url).openConnection.asInstanceOf[HttpURLConnection]

        cx.setInstanceFollowRedirects(false)
        cx.setDoInput(true)

        content foreach { case (contentType, _) ⇒
            cx.setDoOutput(true)
            cx.setRequestMethod("POST")
            contentType foreach (cx.setRequestProperty("Content-Type", _))
        }

        for ((name, value) ← headers if name.toLowerCase != "content-type") // handled above via Content
            cx.addRequestProperty(name, value)

        CookieManager.processRequestCookieHeaders(cx, url)

        cx.connect()
        try {
            // Write content
            // NOTE: At this time we don't support application/x-www-form-urlencoded. When that type of encoding is
            // taking place, the portal doesn't provide a body and instead makes the content available via parameters.
            // So we would need to re-encode the POST. As of 2012-05-10, the XForms engine instead uses the
            // multipart/form-data encoding on the main form to help us here.
            content foreach { case (_, write) ⇒
                write(cx.getOutputStream)
            }

            CookieManager.processResponseSetCookieHeaders(cx, url)

            PlainHttpResponse(cx)
        } catch {
            case NonFatal(t) ⇒
                runQuietly {
                    val is = cx.getInputStream
                    if (is ne null)
                        is.close()
                }
                throw t
        }
    }
}