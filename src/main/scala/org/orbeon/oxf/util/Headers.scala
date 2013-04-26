/**
 * Copyright (C) 2013 Orbeon, Inc.
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
package org.orbeon.oxf.util

import collection.breakOut

object Headers {

    // These headers are connection headers and must never be forwarded (content-length is handled separately below)
    private val HeadersToFilterOut = Set("transfer-encoding", "connection", "host")

    // See: https://groups.google.com/d/msg/scala-sips/wP6dL8nIAQs/TUfwXWWxkyMJ
    // Q: Doesn't Scala already have such a type?
    // Q: Should the type be parametrized with String?
    type ConvertibleToStringSeq[T[_]] = T[String] ⇒ Seq[String]

    // Filter headers that that should never be propagated in our proxies
    // Also combine headers with the same name into a single header
    def filterCapitalizeAndCombineHeaders[T[_]: ConvertibleToStringSeq](headers: Iterable[(String, T[String])], out: Boolean): Iterable[(String, String)] =
        filterAndCapitalizeHeaders(headers, out) map { case (name, values) ⇒ name → (values mkString ",") }

    def filterAndCapitalizeHeaders[T[_]: ConvertibleToStringSeq](headers: Iterable[(String, T[String])], out: Boolean): Iterable[(String, T[String])] =
        filterHeaders(headers, out) map { case (n, v) ⇒ capitalizeCommonOrSplitHeader(n) → v }

    // NOTE: Filtering is case-insensitive, but original case is unchanged
    def filterAndCombineHeaders[T[_]: ConvertibleToStringSeq](headers: Iterable[(String, T[String])], out: Boolean): Iterable[(String, String)] =
        filterHeaders(headers, out) map { case (name, values) ⇒ name → (values mkString ",") }

    // NOTE: Filtering is case-insensitive, but original case is unchanged
    def filterHeaders[T[_]: ConvertibleToStringSeq](headers: Iterable[(String, T[String])], out: Boolean): Iterable[(String, T[String])] =
        for {
            (name, values) ← headers
            if ! HeadersToFilterOut(name.toLowerCase)
            if ! out || name.toLowerCase != "content-length"
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
