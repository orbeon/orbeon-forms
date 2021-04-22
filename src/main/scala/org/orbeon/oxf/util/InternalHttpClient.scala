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
package org.orbeon.oxf.util

import org.apache.http.client.CookieStore
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.externalcontext.{Credentials => _, _}
import org.orbeon.oxf.http.HttpMethod.GET
import org.orbeon.oxf.http._
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.webapp.ProcessorService

import scala.annotation.tailrec

// HTTP client for internal requests
//
// - no actual HTTP requests are performed
// - internal requests are made to the Orbeon servlet
object InternalHttpClient extends HttpClient[CookieStore] {

  def connect(
    url         : String,
    credentials : Option[BasicCredentials], // ignored
    cookieStore : CookieStore,         // ignored
    method      : HttpMethod,
    headers     : Map[String, List[String]],
    content     : Option[StreamedContent]
  ): HttpResponse = {

    require(url.startsWith("/"), "InternalHttpClient only supports absolute paths")

    val currentProcessorService =
      ProcessorService.currentProcessorService.value getOrElse
      (throw new OXFException(s"InternalHttpClient: missing current servlet or portlet connecting to $url."))

    val incomingExternalContext = NetUtils.getExternalContext
    val incomingRequest         = incomingExternalContext.getRequest

    // NOTE: Only `oxf:redirect` calls `Response.sendRedirect` with `isServerSide = true`. In turn, `oxf:redirect`
    // is only called from the PFC with action results, and only passes `isServerSide = true` if
    // `instance-passing = "forward"`, which is not the default. Form Runner doesn't make use of this. Even in that
    // case data is passed as a URL parameter called `$instance` instead of using a request body. However, one user
    // has reported needing this to work as of 2015-05.
    @tailrec
    def processRedirects(
      pathQuery : String,
      method    : HttpMethod,
      headers   : Map[String, List[String]],
      content   : Option[StreamedContent]
    ): LocalResponse = {

      val request =
        new LocalRequest(
          incomingRequest         = incomingRequest,
          contextPath             = incomingRequest.getContextPath,
          pathQuery               = pathQuery,
          method                  = method,
          headersMaybeCapitalized = headers,
          content                 = content
        )

      // Honor `Orbeon-Client` header (see also ServletExternalContext)
      val urlRewriter =
        Headers.firstItemIgnoreCase(headers, Headers.OrbeonClient) match {
          case Some(client) if Headers.EmbeddedClientValues(client) =>
            new WSRPURLRewriter(URLRewriterUtils.getPathMatchersCallable, request, wsrpEncodeResources = true)
          case Some(_) =>
            new ServletURLRewriter(request)
          case None =>
            incomingExternalContext.getResponse: URLRewriter
        }

      val response = new LocalResponse(urlRewriter)

      currentProcessorService.service(
        new PipelineContext,
        new LocalExternalContext(
          incomingExternalContext.getWebAppContext,
          request,
          response
        )
      )

      // NOTE: It is unclear which headers should be passed upon redirect. For example, if we have a User-Agent
      // header coming from the browser, it should be kept. But headers associated with content, such as
      // Content-Type and Content-Length, must not be provided upon redirect. Possibly, only headers  coming from
      // the incoming request should be passed, minus content headers.
      response.serverSideRedirect match {
        case Some(location) => processRedirects(location, GET, Map.empty, None)
        case None           => response
      }
    }

    val response = processRedirects(url, method, headers, content)

    new HttpResponse {
      lazy val statusCode   = response.statusCode
      lazy val headers      = response.capitalizedHeaders
      lazy val lastModified = DateHeaders.firstDateHeaderIgnoreCase(headers, Headers.LastModified)
      lazy val content      = response.streamedContent
      def disconnect()      = content.close()
    }
  }

  def shutdown(): Unit = ()
}
