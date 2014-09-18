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
import org.orbeon.oxf.externalcontext.{LocalResponse, LocalExternalContext, LocalRequest, URLRewriter}
import org.orbeon.oxf.http._
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.servlet.OrbeonServlet

// HTTP client for internal requests
//
// - no actual HTTP requests are performed
// - internal requests are made to the Orbeon servlet
object InternalHttpClient extends HttpClient{

    def connect(
        url        : String,
        credentials: Option[Credentials], // ignored
        cookieStore: CookieStore,         // ignored
        method     : String,
        headers    : Map[String, List[String]],
        content    : Option[StreamedContent]
    ): HttpResponse = {

        require(url.startsWith("/"), "InternalHttpClient only supports absolute paths")

        val currentServlet = OrbeonServlet.currentServlet.value.get

        val incomingExternalContext = NetUtils.getExternalContext
        val incomingRequest         = incomingExternalContext.getRequest
        val urlRewriter             = incomingExternalContext.getResponse: URLRewriter

        val request =
            new LocalRequest(
                incomingRequest         = incomingRequest,
                contextPath             = incomingRequest.getContextPath,
                pathQuery               = url,
                method                  = method,
                headersMaybeCapitalized = headers,
                content                 = content
            )

        val response = new LocalResponse(urlRewriter)

        currentServlet.processorService.service(
            new PipelineContext,
            new LocalExternalContext(
                incomingExternalContext.getWebAppContext,
                request,
                response
            )
        )

        new HttpResponse {
            lazy val statusCode   = response.statusCode
            lazy val headers      = response.capitalizedHeaders
            lazy val lastModified = Headers.firstDateHeaderIgnoreCase(headers, Headers.LastModified)
            lazy val content      = StreamedContent(
                inputStream       = response.getInputStream,
                contentType       = Headers.firstHeaderIgnoreCase(headers, Headers.ContentType),
                contentLength     = Headers.firstLongHeaderIgnoreCase(headers, Headers.ContentLength),
                title             = None
            )
            def disconnect()      = content.close()
        }
    }

    override def shutdown() = ()
}
