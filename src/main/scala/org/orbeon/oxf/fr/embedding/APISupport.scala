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

import java.io.{ByteArrayInputStream, InputStream, OutputStream, Writer}
import java.{util ⇒ ju}
import javax.servlet.http.HttpServletRequest

import org.apache.commons.io.IOUtils
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.util.Headers._
import org.orbeon.oxf.util.NetUtils._
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.xml.XMLUtils

import scala.collection.JavaConverters._
import scala.collection.immutable

object APISupport {

    val AllActions       = List(New, Edit, View)
    val AllActionsByName = AllActions map (a ⇒ a.name → a) toMap

    def proxyPage(
            baseURL     : String,
            path        : String,
            headers     : immutable.Seq[(String, String)] = Nil,
            params      : immutable.Seq[(String, String)] = Nil)(
            implicit ctx: EmbeddingContextWithResponse): Unit = {

        val url  = formRunnerURL(baseURL, path, embeddable = true)

        callService(RequestDetails(None, url, headers, params)) match {
            case Content(content, contentType, _) ⇒
                writeResponseBody(content.right map (new ByteArrayInputStream(_)), contentType)
            case Redirect(_, _) ⇒
                throw new UnsupportedOperationException
        }
    }

    def proxyResource(requestDetails: RequestDetails)(implicit ctx: EmbeddingContextWithResponse): Unit = {

        val res = connectURL(requestDetails)
        
        ctx.setStatusCode(res.statusCode)

        proxyCapitalizeAndCombineHeaders(res.headers mapValues (List(_)), request = false) foreach (ctx.setHeader _).tupled

        useAndClose(res.inputStream) { is ⇒
            writeResponseBody(Right(is), Option(res.contentType))
        }
    }

    def formRunnerPath(app: String, form: String, action: String, documentId: Option[String], query: Option[String]) =
        appendQueryString(s"/fr/$app/$form/$action${documentId map ("/" +) getOrElse ""}", query getOrElse "")

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
    def callService(requestDetails: RequestDetails)(implicit ctx: EmbeddingContext): ContentOrRedirect = {
        val cx = connectURL(requestDetails)
        useAndClose(cx.inputStream) { is ⇒
            if (isRedirectCode(cx.statusCode))
                Redirect(cx.headers("Location"), exitPortal = true)
            else
                Content(Right(IOUtils.toByteArray(is)), Option(cx.contentType), None)
        }
    }

    // POST when we get ClientDataRequest for:
    //
    // - actions requests
    // - resources requests: Ajax requests, form posts, and uploads
    //
    // GET otherwise for:
    //
    // - render requests
    // - resources: typically image, CSS, JavaScript, etc.
    def connectURL(requestDetails: RequestDetails)(implicit ctx: EmbeddingContext) =
        ApacheHttpClient.openConnection(
            recombineQuery(requestDetails.url, requestDetails.params),
            requestDetails.content map { content ⇒

                def write(os: OutputStream): Unit =
                    content.body match {
                        case Left(string) ⇒ os.write(string.getBytes("utf-8"))
                        case Right(bytes) ⇒ os.write(bytes)
                    }

                (content.contentType, write _)
            },
            ("Orbeon-Client" → "portlet") +: requestDetails.headers
        )

    def writeResponseBody(
            data        : String Either InputStream,
            contentType : Option[String])(
            implicit ctx: EmbeddingContextWithResponse): Unit =
        contentType map getContentTypeMediaType match {
            case Some(mediatype) if XMLUtils.isTextOrJSONContentType(mediatype) || XMLUtils.isXMLMediatype(mediatype) ⇒
                // Text/JSON/XML content type: rewrite response content
                val contentAsString =
                    data match {
                        case Left(string) ⇒
                            string
                        case Right(is) ⇒
                            val encoding = contentType flatMap (t ⇒ Option(getContentTypeCharset(t))) getOrElse "utf-8"
                            IOUtils.toString(is, encoding)
                    }

                val encodeForXML = XMLUtils.isXMLMediatype(mediatype)

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
                data match {
                    case Left(string) ⇒
                        ctx.writer.write(string)
                    case Right(is) ⇒
                        IOUtils.copy(is, ctx.outputStream)
                }
        }

    // Parse a string containing WSRP encodings and encode the URLs and namespaces
    def decodeWSRPContent(content: String, ns: String, decodeURL: String ⇒ String, writer: Writer): Unit = {

        val stringLength = content.length
        var currentIndex = 0
        var index        = 0

        import org.orbeon.oxf.externalcontext.WSRPURLRewriter.{decodeURL => _, _}

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
}
