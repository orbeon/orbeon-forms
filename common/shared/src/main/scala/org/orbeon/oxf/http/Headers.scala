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

import org.orbeon.io.CharsetNames
import org.orbeon.oxf.util.NumericUtils

import java.net.URLEncoder


object Headers {

  val OrbeonToken                  = "Orbeon-Token"
  val OrbeonUsername               = "Orbeon-Username"
  val OrbeonGroup                  = "Orbeon-Group"
  val OrbeonLastModifiedByUsername = "Orbeon-Last-Modified-By-Username"
  val OrbeonOrganization           = "Orbeon-Organization"
  val OrbeonRoles                  = "Orbeon-Roles"
  val OrbeonCredentials            = "Orbeon-Credentials"
  val OrbeonWorkflowStage          = "Orbeon-Workflow-Stage"

  val OrbeonCreated                = "Orbeon-Created"
  val OrbeonLastModified           = "Orbeon-Last-Modified"

  val OrbeonRemoteAddress          = "Orbeon-Remote-Address"

  val ContentType                  = "Content-Type"
  val ContentDisposition           = "Content-Disposition"
  val ContentId                    = "Content-ID"
  val ContentLength                = "Content-Length"
  val LastModified                 = "Last-Modified"
  val Authorization                = "Authorization"
  val Location                     = "Location"
  val OrbeonClient                 = "Orbeon-Client"
  val Created                      = "Created"
  val Cookie                       = "Cookie"
  val Accept                       = "Accept"
  val AcceptLanguage               = "Accept-Language"
  val SOAPAction                   = "SOAPAction"
  val Timeout                      = "Timeout"
  val TimeoutValuePrefix           = "Second-"

  val Range                        = "Range"
  val IfRange                      = "If-Range"
  val AcceptRanges                 = "Accept-Ranges"
  val ContentRange                 = "Content-Range"

  val OrbeonTokenLower             = OrbeonToken.toLowerCase
  val OrbeonUsernameLower          = OrbeonUsername.toLowerCase
  val OrbeonGroupLower             = OrbeonGroup.toLowerCase
  val OrbeonOrganizationLower      = OrbeonOrganization.toLowerCase
  val OrbeonRolesLower             = OrbeonRoles.toLowerCase
  val OrbeonCredentialsLower       = OrbeonCredentials.toLowerCase

  val ContentTypeLower             = ContentType.toLowerCase
  val ContentDispositionLower      = ContentDisposition.toLowerCase
  val ContentLengthLower           = ContentLength.toLowerCase
  val LastModifiedLower            = LastModified.toLowerCase
  val AuthorizationLower           = Authorization.toLowerCase
  val LocationLower                = Location.toLowerCase
  val OrbeonClientLower            = OrbeonClient.toLowerCase
  val AcceptLanguageLower          = AcceptLanguage.toLowerCase
  val CreatedLower                 = Created.toLowerCase
  val TimeoutLower                 = Timeout.toLowerCase

  val GeneralEmbeddedClient         = "embedded"
  val PortletEmbeddingClient        = "portlet"
  val JavaApiEmbeddingClient        = "java-api"
  val JavaScriptApiEmbeddingClient  = "javascript-api"
  val AppEmbeddingClient            = "app" // tentative naming

  val EmbeddedClientValues = Set(
    GeneralEmbeddedClient,
    PortletEmbeddingClient,
    JavaApiEmbeddingClient,
    JavaScriptApiEmbeddingClient,
    AppEmbeddingClient
  )

  // These headers are connection headers and must never be forwarded (content-length is handled separately below)
  //
  // - Don't proxy Content-Length and Content-Type. Proxies must associate these with the content and propagate via
  //   other means.
  // - We are not able to properly proxy directly a content-encoded response, so we don't proxy the relevant headers.
  private val HeadersToRemove = Set("connection", "transfer-encoding", ContentLength, ContentType) map (_.toLowerCase)
  val RequestHeadersToRemove  = HeadersToRemove ++ List("host", "cookie", "cookie2", "accept-encoding")
  val ResponseHeadersToRemove = HeadersToRemove ++ List("set-cookie", "set-cookie2", "content-encoding")

  val EmptyHeaders: Map[String, List[String]] = Map.empty

  // See: https://groups.google.com/d/msg/scala-sips/wP6dL8nIAQs/TUfwXWWxkyMJ
  type ConvertibleToSeq[T[_], U] = T[U] => Seq[U]

  // Filter headers that that should never be propagated in our proxies
  // Also combine headers with the same name into a single header
  def proxyCapitalizeAndCombineHeaders[T[_] <: AnyRef](
    headers : Iterable[(String, T[String])],
    request : Boolean)(implicit
    _conv   : ConvertibleToSeq[T, String]
  ): Iterable[(String, String)] =
    proxyAndCapitalizeHeaders(headers, request) map { case (name, values) => name -> (values mkString ",") }

  def proxyAndCapitalizeHeaders[T[_] <: AnyRef, U](
    headers : Iterable[(String, T[U])],
    request : Boolean)(implicit
    _conv   : ConvertibleToSeq[T, U]
  ): Iterable[(String, T[U])] =
    proxyHeaders(headers, request) map { case (n, v) => capitalizeCommonOrSplitHeader(n) -> v }

  // NOTE: Filtering is case-insensitive, but original case is unchanged
  def proxyAndCombineRequestHeaders[T[_] <: AnyRef](
    headers : Iterable[(String, T[String])])(implicit
    _conv   : ConvertibleToSeq[T, String]
  ): Iterable[(String, String)] =
    proxyHeaders(headers, request = true) map { case (name, values) => name -> (values mkString ",") }

  // NOTE: Filtering is case-insensitive, but original case is unchanged
  def proxyHeaders[T[_] <: AnyRef, U](
    headers : Iterable[(String, T[U])],
    request : Boolean)(implicit
    _conv   : ConvertibleToSeq[T, U]
  ): Iterable[(String, T[U])] =
    for {
      (name, values) <- headers
      if name ne null // HttpURLConnection.getHeaderFields returns null names. Great.
      if (request && ! RequestHeadersToRemove(name.toLowerCase)) || (! request && ! ResponseHeadersToRemove(name.toLowerCase))
      if (values ne null) && values.nonEmpty
    } yield
      name -> values

  // Capitalize any header
  def capitalizeCommonOrSplitHeader(name: String): String =
    capitalizeCommonHeader(name) getOrElse capitalizeSplitHeader(name)

  // Try to capitalize a common HTTP header
  def capitalizeCommonHeader(name: String): Option[String] =
    lowercaseToCommonCapitalization.get(name.toLowerCase)

  // Capitalize a header of the form foo-bar-baz to Foo-Bar-Baz
  def capitalizeSplitHeader(name: String): String =
    name split '-' map (_.toLowerCase.capitalize) mkString "-"

  // NOTE: This logic is not restricted to headers. Pull it out from here.
  // For an `Iterable` mapping names to values where each value is itself `Iterable`, find the first
  // matching leaf value. Unicity of names is not enforced.
  def firstItemIgnoreCase[T, V](coll: Iterable[(String, T)], name: String)(implicit ev: T => Iterable[V]): Option[V] =
    coll collectFirst {
      case (key, value) if name.equalsIgnoreCase(key) && value.nonEmpty => value.head
    }

  def allItemsIgnoreCase[T, V](coll: Iterable[(String, T)], name: String)(implicit ev: T => Iterable[V]): Iterable[V] =
    (coll collect {
      case (key, value) if name.equalsIgnoreCase(key) && value.nonEmpty => value
    }).flatten

  def firstNonNegativeLongHeaderIgnoreCase[T](headers: Iterable[(String, T)], name: String)(implicit ev: T => Iterable[String]): Option[Long] =
    firstItemIgnoreCase(headers, name) flatMap NumericUtils.parseLong filter (_ >= 0L)

  def buildContentDispositionHeader(contentFilename: String): (String, String) = {
    // To support spaces and non-US-ASCII characters, we encode the filename using RFC 5987 percentage encoding.
    // This is what the XPath `encode-for-uri()` does. Here we use the URLEncoder, which does
    // application/x-www-form-urlencoded encoding, which seems to differ from RFC 5987 in that the space is
    // encoded as a "+". Also see https://stackoverflow.com/a/2678602/5295.
    val filenameEncodedForHeader = URLEncoder.encode(contentFilename, CharsetNames.Utf8).replace("+", "%20")

    // All browsers since IE7, Safari 5 support the `filename*=UTF-8''` syntax to indicate that the filename is
    // UTF-8 percent encoded. Also see https://stackoverflow.com/a/6745788/5295.
    "Content-Disposition" -> ("attachment; filename*=UTF-8''" + filenameEncodedForHeader)
  }

  def embeddedClientValueFromHeaders[T](headers: Iterable[(String, T)])(implicit ev: T => Iterable[String]): Option[String] =
    Headers.firstItemIgnoreCase(headers, OrbeonClient)

  // 2022-07-28: Only 1 use left.
  def isEmbeddedFromHeaders[T](headers: Iterable[(String, T)])(implicit ev: T => Iterable[String]): Boolean =
    embeddedClientValueFromHeaders(headers) exists EmbeddedClientValues

  // List of common HTTP headers
  val CommonHeaders = Seq(
    "Accept",
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
    "Content-ID",
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

  private val lowercaseToCommonCapitalization: Map[String, String] = CommonHeaders.iterator.map(name => name.toLowerCase -> name).toMap
}
