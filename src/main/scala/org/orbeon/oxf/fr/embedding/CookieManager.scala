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

import java.net.{URI, HttpURLConnection}
import java.{util ⇒ ju}

import org.apache.http.client.CookieStore
import org.apache.http.cookie.CookieOrigin
import org.apache.http.impl.client.BasicCookieStore
import org.apache.http.impl.cookie.BrowserCompatSpec
import org.apache.http.message.BasicHeader
import collection.JavaConverters._

object CookieManager {

    private val RemoteSessionIdKey = "org.orbeon.oxf.xforms.portlet.remote-session-id"

    def processRequestCookieHeaders(connection: HttpURLConnection, url: String)(implicit ctx: EmbeddingContext): Unit = {

        val cookieSpec   = new BrowserCompatSpec // because not thread-safe
        val cookieOrigin = getCookieOrigin(url)
        val cookieStore  = getOrCreateCookieStore

        cookieStore.clearExpired(new ju.Date)

        val relevantCookies =
            for {
                cookie ← cookieStore.getCookies.asScala.toList
                if cookieSpec.`match`(cookie, cookieOrigin)
            } yield
                cookie

        // NOTE: BrowserCompatSpec always only return a single Cookie header
        if (relevantCookies.nonEmpty)
            for (header ← cookieSpec.formatCookies(relevantCookies.asJava).asScala)
                connection.setRequestProperty(header.getName, header.getValue)
    }

    def processResponseSetCookieHeaders(connection: HttpURLConnection, url: String)(implicit ctx: EmbeddingContext): Unit = {

        val cookieSpec   = new BrowserCompatSpec // because not thread-safe
        val cookieOrigin = getCookieOrigin(url)
        val cookieStore  = getOrCreateCookieStore

        for {
            (name, values) ← connection.getHeaderFields.asScala.toList
            if (name ne null) && name.toLowerCase == "set-cookie" // Yes, name can be null! Crazy.
            value          ← values.asScala
            cookie         ← cookieSpec.parse(new BasicHeader(name, value), cookieOrigin).asScala
        } locally {
            cookieStore.addCookie(cookie)
        }
    }

    private def getCookieOrigin(url: String) = {
        val uri = new URI(url)
        def defaultPort   = if (uri.getScheme == "https") 443 else 80
        def effectivePort = if (uri.getPort < 0) defaultPort else uri.getPort
        new CookieOrigin(uri.getHost, effectivePort, uri.getPath, uri.getScheme == "https")
    }

    private def getOrCreateCookieStore(implicit ctx: EmbeddingContext) =
        Option(ctx.getSessionAttribute(RemoteSessionIdKey).asInstanceOf[CookieStore]) getOrElse {
            val newCookieStore = new BasicCookieStore
            ctx.setSessionAttribute(RemoteSessionIdKey, newCookieStore)
            newCookieStore
        }
}
