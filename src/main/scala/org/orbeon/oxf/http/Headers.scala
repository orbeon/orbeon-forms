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

import org.orbeon.oxf.util.DateUtils

import collection.breakOut

object Headers {

    val OrbeonToken         = "Orbeon-Token"
    val OrbeonUsername      = "Orbeon-Username"
    val OrbeonGroup         = "Orbeon-Group"
    val OrbeonRoles         = "Orbeon-Roles"
    
    val ContentType         = "Content-Type"
    val ContentLength       = "Content-Length"
    val LastModified        = "Last-Modified"
    val Authorization       = "Authorization"
    val OrbeonClient        = "Orbeon-Client"
            
    val OrbeonTokenLower    = OrbeonToken.toLowerCase
    val OrbeonUsernameLower = OrbeonUsername.toLowerCase
    val OrbeonGroupLower    = OrbeonGroup.toLowerCase
    val OrbeonRolesLower    = OrbeonRoles.toLowerCase
    
    val ContentTypeLower    = ContentType.toLowerCase
    val ContentLengthLower  = ContentLength.toLowerCase
    val LastModifiedLower   = LastModified.toLowerCase
    val AuthorizationLower  = Authorization.toLowerCase
    val OrbeonClientLower   = OrbeonClient.toLowerCase

    // These headers are connection headers and must never be forwarded (content-length is handled separately below)
    //
    // - Don't proxy Content-Length and Content-Type. Proxies must associate these with the content and propagate via
    //   other means.
    // - We are not able to properly proxy directly a content-encoded response, so we don't proxy the relevant headers.
    private val HeadersToRemove = Set("connection", "transfer-encoding", ContentLength, ContentType) map (_.toLowerCase)
    val RequestHeadersToRemove  = HeadersToRemove ++ List("host", "cookie", "cookie2", "accept-encoding")
    val ResponseHeadersToRemove = HeadersToRemove ++ List("set-cookie", "set-cookie2", "content-encoding")

    val EmptyHeaders = Map.empty[String, List[String]]

    // See: https://groups.google.com/d/msg/scala-sips/wP6dL8nIAQs/TUfwXWWxkyMJ
    type ConvertibleToSeq[T[_], U] = T[U] ⇒ Seq[U]

    // Filter headers that that should never be propagated in our proxies
    // Also combine headers with the same name into a single header
    def proxyCapitalizeAndCombineHeaders[T[_]](
        headers : Iterable[(String, T[String])],
        request : Boolean)(implicit
        _conv   : ConvertibleToSeq[T, String]
    ): Iterable[(String, String)] =
        proxyAndCapitalizeHeaders(headers, request) map { case (name, values) ⇒ name → (values mkString ",") }

    def proxyAndCapitalizeHeaders[T[_], U](
        headers : Iterable[(String, T[U])],
        request : Boolean)(implicit
        _conv   : ConvertibleToSeq[T, U]
    ): Iterable[(String, T[U])] =
        proxyHeaders(headers, request) map { case (n, v) ⇒ capitalizeCommonOrSplitHeader(n) → v }

    // NOTE: Filtering is case-insensitive, but original case is unchanged
    def proxyAndCombineRequestHeaders[T[_]](
        headers : Iterable[(String, T[String])])(implicit
        _conv   : ConvertibleToSeq[T, String]
    ): Iterable[(String, String)] =
        proxyHeaders(headers, request = true) map { case (name, values) ⇒ name → (values mkString ",") }

    // NOTE: Filtering is case-insensitive, but original case is unchanged
    def proxyHeaders[T[_], U](
        headers : Iterable[(String, T[U])],
        request : Boolean)(implicit
        _conv   : ConvertibleToSeq[T, U]
    ): Iterable[(String, T[U])] =
        for {
            (name, values) ← headers
            if name ne null // HttpURLConnection.getHeaderFields returns null names. Great.
            if (request && ! RequestHeadersToRemove(name.toLowerCase)) || (! request && ! ResponseHeadersToRemove(name.toLowerCase))
            if (values ne null) && values.nonEmpty
        } yield
            name → values

    // Capitalize any header
    def capitalizeCommonOrSplitHeader(name: String) =
        capitalizeCommonHeader(name) getOrElse capitalizeSplitHeader(name)

    // Try to capitalize a common HTTP header
    def capitalizeCommonHeader(name: String) =
        lowercaseToCommonCapitalization.get(name.toLowerCase)

    // Capitalize a header of the form foo-bar-baz to Foo-Bar-Baz
    def capitalizeSplitHeader(name: String) =
        name split '-' map (_.toLowerCase.capitalize) mkString "-"

    def firstHeaderIgnoreCase(headers: Iterable[(String, Seq[String])], name: String): Option[String] =
        headers collectFirst {
            case (key, value) if name.equalsIgnoreCase(key) && value.nonEmpty ⇒ value.head
        }

    def firstLongHeaderIgnoreCase(headers: Iterable[(String, Seq[String])], name: String): Option[Long] =
        firstHeaderIgnoreCase(headers, name) map (_.toLong) filter (_ >= 0L)

    def firstDateHeaderIgnoreCase(headers: Iterable[(String, Seq[String])], name: String): Option[Long] =
        firstHeaderIgnoreCase(headers, name) flatMap DateUtils.tryParseRFC1123 filter (_ > 0L)

    // List of common HTTP headers
    val CommonHeaders = Seq(
        "﻿Accept",
        "Accept-Charset",
        "Accept-Datetime",
        "Accept-Encoding",
        "Accept-Language",
        "Accept-Ranges",
        "Age",
        "Allow",
        "Authorization",
        "Cache-Control",
        "Connection",
        "Content-Disposition",
        "Content-Encoding",
        "Content-Language",
        "Content-Length",
        "Content-Location",
        "Content-MD5",
        "Content-Range",
        "Content-Security-Policy",
        "Content-Type",
        "Cookie",
        "Cookie2",
        "DNT",
        "Date",
        "ETag",
        "Expect",
        "Expires",
        "Frame-Options",
        "From",
        "Front-End-Https",
        "Host",
        "If-Match",
        "If-Modified-Since",
        "If-None-Match",
        "If-Range",
        "If-Unmodified-Since",
        "Last-Modified",
        "Link",
        "Location",
        "Max-Forwards",
        "Origin",
        "P3P",
        "Pragma",
        "Pragma",
        "Proxy-Authenticate",
        "Proxy-Authorization",
        "Proxy-Connection",
        "Range",
        "Referer",
        "Refresh",
        "Retry-After",
        "Server",
        "Set-Cookie",
        "Set-Cookie2",
        "SOAPAction",
        "Status",
        "Strict-Transport-Security",
        "TE",
        "Trailer",
        "Transfer-Encoding",
        "Upgrade",
        "User-Agent",
        "Vary",
        "Via",
        "Via",
        "WWW-Authenticate",
        "Warning",
        "X-ATT-DeviceId",
        "X-Content-Security-Policy",
        "X-Content-Type-Options",
        "X-Forwarded-For",
        "X-Forwarded-Proto",
        "X-Frame-Options",
        "X-Powered-By",
        "X-Requested-With",
        "X-UA-Compatible",
        "X-Wap-Profile",
        "X-WebKit-CSP",
        "X-XSS-Protection"
    )

    private val lowercaseToCommonCapitalization: Map[String, String] = CommonHeaders.map(name ⇒ name.toLowerCase → name)(breakOut)
}
