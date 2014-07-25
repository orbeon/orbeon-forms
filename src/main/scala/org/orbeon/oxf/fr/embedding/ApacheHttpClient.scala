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

import java.io.{ByteArrayOutputStream, OutputStream}
import java.net.URL

import org.orbeon.oxf.http.HTTPURLConnection
import org.orbeon.oxf.util.ScalaUtils._

import scala.collection.immutable.Iterable
import scala.util.control.NonFatal



object ApacheHttpClient extends HttpClient {

    def openConnection(
        url         : String,
        content     : Option[(Option[String], OutputStream ⇒ Unit)],
        headers     : Iterable[(String, String)])(
        implicit ctx: EmbeddingContext
    ): HttpResponse = {

        val cx = new HTTPURLConnection(new URL(url))(ctx.httpClient)

        cx.setInstanceFollowRedirects(false) // TODO
        cx.setDoInput(true)

        content foreach { case (contentType, _) ⇒
            cx.setDoOutput(true)
            cx.setRequestMethod("POST")
            contentType foreach (cx.setRequestProperty("Content-Type", _))
        }

        for ((name, value) ← headers if name.toLowerCase != "content-type") // handled above via Content
            cx.addRequestProperty(name, value)

        // FIXME: Stream content as memory usage is large for uploads.
        content foreach { case (_, write) ⇒
            val os = new ByteArrayOutputStream()
            write(os)
            cx.setRequestBody(os.toByteArray)
        }

        cx.setCookieStore(CookieManager.getOrCreateCookieStore)

        cx.connect()
        try {
            HttpURLConnectionResponse(cx)
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
