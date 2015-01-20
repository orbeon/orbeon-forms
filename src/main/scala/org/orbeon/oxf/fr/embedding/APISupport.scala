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

import java.io.Writer
import java.{util ⇒ ju}
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}

import org.apache.commons.io.IOUtils
import org.apache.http.client.CookieStore
import org.apache.http.impl.client.BasicCookieStore
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.fr.embedding.servlet.ServletEmbeddingContextWithResponse
import org.orbeon.oxf.http.Headers._
import org.orbeon.oxf.http._
import org.orbeon.oxf.util.NetUtils._
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.xml.XMLUtils
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._
import scala.collection.immutable

object APISupport {

    import org.orbeon.oxf.fr.embedding.APISupport.Private._

    val Logger = LoggerFactory.getLogger("org.orbeon.embedding")

    val AllModes       = List(New, Edit, View)
    val AllModesByName = AllModes map (a ⇒ a.name → a) toMap
    
    def proxyPage(
        baseURL     : String,
        path        : String,
        headers     : immutable.Seq[(String, String)] = Nil,
        params      : immutable.Seq[(String, String)] = Nil)(
        implicit ctx: EmbeddingContextWithResponse
    ): Unit = {

        val url  = formRunnerURL(baseURL, path, embeddable = true)

        callService(RequestDetails(None, url, headers, params)) match {
            case content: StreamedContent ⇒
                useAndClose(content)(writeResponseBody)
            case Redirect(_, _) ⇒
                throw new UnsupportedOperationException
        }
    }
    
    def proxyServletResources(
        req         : HttpServletRequest,
        res         : HttpServletResponse,
        namespace   : String,
        resourcePath: String
    ): Unit =
        withSettings(req, res.getWriter) { settings ⇒

            implicit val ctx = new ServletEmbeddingContextWithResponse(
                req,
                Right(res),
                namespace,
                settings.orbeonPrefix,
                settings.httpClient
            )

            val url = formRunnerURL(settings.formRunnerURL, resourcePath, embeddable = false)

            val contentFromRequest =
                req.getMethod == "POST" option
                    StreamedContent(
                        req.getInputStream,
                        Option(req.getContentType),
                        Some(req.getContentLength.toLong) filter (_ >= 0L),
                        None
                    )

            proxyResource(
                RequestDetails(
                    content = contentFromRequest,
                    url     = url,
                    headers = proxyCapitalizeAndCombineHeaders(requestHeaders(req).to[List], request = true).to[List],
                    params  = Nil
                )
            )
        }

    def proxyResource(requestDetails: RequestDetails)(implicit ctx: EmbeddingContextWithResponse): Unit = {

        Logger.debug("proxying resource {}", requestDetails.url)

        val res = connectURL(requestDetails)
        
        ctx.setStatusCode(res.statusCode)
        res.content.contentType foreach (ctx.setHeader(Headers.ContentType, _))

        proxyCapitalizeAndCombineHeaders(res.headers, request = false) foreach (ctx.setHeader _).tupled

        useAndClose(res.content)(writeResponseBody)
    }

    def formRunnerPath(app: String, form: String, mode: String, documentId: Option[String], query: Option[String]) =
        appendQueryString(s"/fr/$app/$form/$mode${documentId map ("/" +) getOrElse ""}", query getOrElse "")

    def formRunnerHomePath(query: Option[String]) =
        appendQueryString("/fr/", query getOrElse "")

    def formRunnerURL(baseURL: String, path: String, embeddable: Boolean) =
        appendQueryString(dropTrailingSlash(baseURL) + path, if(embeddable) "orbeon-embeddable=true" else "")

    def requestHeaders(req: HttpServletRequest) =
        for {
            name   ← req.getHeaderNames.asInstanceOf[ju.Enumeration[String]].asScala
            values = req.getHeaders(name).asInstanceOf[ju.Enumeration[String]].asScala.toList
        } yield
            name → values

    // Match on headers in a case-insensitive way, but the header we sent follows the capitalization of the
    // header specified in the init parameter.
    def headersToForward(clientHeaders: List[(String, List[String])], configuredHeaders: Map[String, String]) =
        for {
            (name, value) ← proxyAndCombineRequestHeaders(clientHeaders)
            originalName  ← configuredHeaders.get(name.toLowerCase)
        } yield
            originalName → value

    // Call the Orbeon service at the other end
    def callService(requestDetails: RequestDetails)(implicit ctx: EmbeddingContext): StreamedContentOrRedirect = {

        Logger.debug("proxying page {}", requestDetails.url)

        val cx = connectURL(requestDetails)
        if (isRedirectCode(cx.statusCode))
            Redirect(cx.headers("Location").head, exitPortal = true)
        else
            cx.content
    }

    def writeResponseBody(content: Content)(implicit ctx: EmbeddingContextWithResponse): Unit =
        content.contentType map getContentTypeMediaType match {
            case Some(mediatype) if XMLUtils.isTextOrJSONContentType(mediatype) || XMLUtils.isXMLMediatype(mediatype) ⇒
                // Text/JSON/XML content type: rewrite response content
                val encoding        = content.contentType flatMap (t ⇒ Option(getContentTypeCharset(t))) getOrElse "utf-8"
                val contentAsString = useAndClose(content.inputStream)(IOUtils.toString(_, encoding))
                val encodeForXML    = XMLUtils.isXMLMediatype(mediatype)

                def decodeURL(encoded: String) = {
                    val decodedURL = ctx.decodeURL(encoded)
                    if (encodeForXML) XMLUtils.escapeXMLMinimal(decodedURL) else decodedURL
                }

                decodeWSRPContent(
                    contentAsString,
                    ctx.namespace,
                    decodeURL,
                    ctx.writer
                )
            case _ ⇒
                // All other types: just output
                useAndClose(content.inputStream)(IOUtils.copy(_, ctx.outputStream))
        }

    def scopeSettings[T](req: HttpServletRequest, settings: EmbeddingSettings)(body: ⇒ T): T = {
        req.setAttribute(SettingsKey, settings)
        try body
        finally req.removeAttribute(SettingsKey)
    }
    
    def withSettings[T](req: HttpServletRequest, writer: ⇒ Writer)(body: EmbeddingSettings ⇒ T): Unit =
        Option(req.getAttribute(SettingsKey).asInstanceOf[EmbeddingSettings]) match {
            case Some(settings) ⇒
                body(settings)
            case None ⇒
                val msg = "ERROR: Orbeon Forms embedding filter is not configured."
                Logger.error(msg)
                writer.write(msg)
        }

    def nextNamespace(req: HttpServletRequest) = {

        val newValue =
            Option(req.getAttribute(LastNamespaceIndexKey).asInstanceOf[Integer]) match {
                case Some(value) ⇒ value + 1
                case None        ⇒ 0
            }

        req.setAttribute(LastNamespaceIndexKey, newValue)

        NamespacePrefix + newValue
    }

    val NamespacePrefix = "o"

    private object Private {

        val SettingsKey           = "orbeon.form-runner.filter-settings"
        val RemoteSessionIdKey    = "orbeon.form-runner.remote-session-id"
        val LastNamespaceIndexKey = "orbeon.form-runner.last-namespace-index"

        // POST when we get RequestDetails for:
        //
        // - actions requests
        // - resources requests: Ajax requests, form posts, and uploads
        //
        // GET otherwise for:
        //
        // - render requests
        // - resources: typically image, CSS, JavaScript, etc.
        def connectURL(requestDetails: RequestDetails)(implicit ctx: EmbeddingContext) =
            ctx.httpClient.connect(
                url         = recombineQuery(requestDetails.url, requestDetails.params),
                credentials = None,
                cookieStore = getOrCreateCookieStore,
                method      = if (requestDetails.content.isEmpty) "GET" else "POST",
                headers     = requestDetails.headersMapWithContentType + (Headers.OrbeonClient → List("portlet")),
                content     = requestDetails.content
            )

        // Parse a string containing WSRP encodings and encode the URLs and namespaces
        def decodeWSRPContent(content: String, ns: String, decodeURL: String ⇒ String, writer: Writer): Unit = {

            val stringLength = content.length
            var currentIndex = 0
            var index        = 0

            import org.orbeon.oxf.externalcontext.WSRPURLRewriter.{decodeURL ⇒ _, _}

            while ({index = content.indexOf(BaseTag, currentIndex); index} != -1) {

                // Write up to the current mark
                writer.write(content, currentIndex, index - currentIndex)

                // Check if escaping is requested
                if (index + BaseTagLength * 2 <= stringLength &&
                        content.substring(index + BaseTagLength, index + BaseTagLength * 2) == BaseTag) {
                    // Write escaped tag, update index and keep looking
                    writer.write(BaseTag)
                    currentIndex = index + BaseTagLength * 2
                } else if (index < stringLength - BaseTagLength && content.charAt(index + BaseTagLength) == '?') {
                    // URL encoding
                    // Find the matching end mark
                    val endIndex = content.indexOf(EndTag, index)
                    if (endIndex == -1)
                        throw new OXFException("Missing end tag for WSRP encoded URL.")
                    val encodedURL = content.substring(index + StartTagLength, endIndex)
                    currentIndex = endIndex + EndTagLength

                    writer.write(decodeURL(encodedURL))
                } else if (index < stringLength - BaseTagLength && content.charAt(index + BaseTagLength) == '_') {
                    // Namespace encoding
                    writer.write(ns)
                    currentIndex = index + PrefixTagLength
                } else
                    throw new OXFException("Invalid WSRP rewrite tagging.")
            }

            // Write remainder of string
            if (currentIndex < stringLength)
                writer.write(content, currentIndex, content.length - currentIndex)
        }

        def getOrCreateCookieStore(implicit ctx: EmbeddingContext) =
            Option(ctx.getSessionAttribute(RemoteSessionIdKey).asInstanceOf[CookieStore]) getOrElse {
                val newCookieStore = new BasicCookieStore
                ctx.setSessionAttribute(RemoteSessionIdKey, newCookieStore)
                newCookieStore
            }
    }
}
