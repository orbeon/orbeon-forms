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
import java.net.{HttpURLConnection, URL}
import java.{util ⇒ ju}
import javax.servlet.http.HttpServletRequest

import org.apache.commons.io.IOUtils
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.util.Headers._
import org.orbeon.oxf.util.NetUtils
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.xml.XMLUtils

import scala.collection.immutable
import scala.util.control.NonFatal

import scala.collection.JavaConverters._

sealed trait Action { val name: String }
case object New  extends Action { val name = "new" }
case object Edit extends Action { val name = "edit" }
case object View extends Action { val name = "view" }

object APISupport {

    val AllActions       = List(New, Edit, View)
    val AllActionsByName = AllActions map (a ⇒ a.name → a) toMap

    def proxyPage(
            baseURL     : String,
            path        : String,
            headers     : immutable.Seq[(String, String)] = Nil,
            params      : immutable.Seq[(String, String)] = Nil)(
            implicit ctx: EmbeddingContextWithResponse): Unit = {

        val url  = buildFormRunnerURL(baseURL, path, embeddable = true)

        callService(RequestDetails(None, url, headers, params)) match {
            case Content(content, contentType, _) ⇒
                writeResponse(content.right map (new ByteArrayInputStream(_)), contentType)
            case Redirect(_, _) ⇒
                throw new UnsupportedOperationException
        }
    }

    def proxyResource(requestDetails: RequestDetails)(implicit ctx: EmbeddingContextWithResponse): Unit = {

        val connection = connectURL(requestDetails)

        useAndClose(connection.getInputStream) { is ⇒

            ctx.setStatusCode(connection.getResponseCode)

            filterCapitalizeAndCombineHeaders(connection.getHeaderFields.asScala mapValues (_.asScala), out = false) foreach
                (ctx.setHeader _).tupled

            writeResponse(Right(is), Option(connection.getContentType))
        }
    }

    def buildFormRunnerPath(app: String, form: String, action: String, documentId: Option[String], query: Option[String]) =
        NetUtils.appendQueryString("/fr/" + app + "/" + form + "/" + action + (documentId map ("/" +) getOrElse ""), query getOrElse "")

    def buildFormRunnerHomePath(query: Option[String]) =
        NetUtils.appendQueryString("/fr/", query getOrElse "")

    def buildFormRunnerURL(baseURL: String, path: String, embeddable: Boolean) =
        NetUtils.appendQueryString(dropTrailingSlash(baseURL) + path, if(embeddable) "orbeon-embeddable=true" else "")

    def requestHeaders(req: HttpServletRequest) =
        for {
            name           ← req.getHeaderNames.asInstanceOf[ju.Enumeration[String]].asScala
            values         = req.getHeaders(name).asInstanceOf[ju.Enumeration[String]].asScala.toList
        } yield
            name → values

    // Match on headers in a case-insensitive way, but the header we sent follows the capitalization of the
    // header specified in the init parameter.
    def headersToForward(clientHeaders: List[(String, List[String])], configuredHeaders: Map[String, String]) =
        for {
            (name, value) ← filterAndCombineHeaders(clientHeaders, out = true)
            originalName  ← configuredHeaders.get(name.toLowerCase)
        } yield
            originalName → value

    // Call the Orbeon service at the other end
    def callService(requestDetails: RequestDetails)(implicit ctx: EmbeddingContext): ContentOrRedirect = {
        val connection = connectURL(requestDetails)
        useAndClose(connection.getInputStream) { is ⇒
            if (NetUtils.isRedirectCode(connection.getResponseCode))
                Redirect(connection.getHeaderField("Location"), exitPortal = true) // we could consider an option for intra-portlet redirection
            else
                Content(Right(IOUtils.toByteArray(is)), Option(connection.getHeaderField("Content-Type")), None)
        }
    }

    def connectURL(requestDetails: RequestDetails)(implicit ctx: EmbeddingContext): HttpURLConnection = {

        // POST when we get ClientDataRequest for:
        //
        // - actions requests
        // - resources requests: Ajax requests, form posts, and uploads
        //
        // GET otherwise for:
        //
        // - render requests
        // - resources: typically image, CSS, JavaScript, etc.

        val newURL = recombineQuery(requestDetails.url, requestDetails.params)

        // TODO: Ability to use other HTTP client
        val connection = new URL(newURL).openConnection.asInstanceOf[HttpURLConnection]

        connection.setInstanceFollowRedirects(false)
        connection.setDoInput(true)

        requestDetails.content foreach { content ⇒
            connection.setDoOutput(true)
            connection.setRequestMethod("POST")
            content.contentType foreach (connection.setRequestProperty("Content-Type", _))
        }

        def setRequestHeaders(headers: Seq[(String, String)], connection: HttpURLConnection): Unit =
            for ((name, value) ← headers if name.toLowerCase != "content-type") // handled via requestDetails.content
                connection.addRequestProperty(name, value)

        def setRequestRemoteSessionIdAndHeaders(connection: HttpURLConnection, url: String)(implicit ctx: EmbeddingContext): Unit = {
            // Tell Orbeon Forms explicitly that we are in fact in a portlet or portlet-like environment. This causes
            // the server to use WSRP URL rewriting for the resulting HTML and CSS.
            connection.addRequestProperty("Orbeon-Client", "portlet")
            // Set Cookie header
            CookieManager.processRequestCookieHeaders(connection, url)
        }

        setRequestHeaders(requestDetails.headers, connection)
        setRequestRemoteSessionIdAndHeaders(connection, newURL)

        connection.connect()
        try {
            // Write content
            // NOTE: At this time we don't support application/x-www-form-urlencoded. When that type of encoding is
            // taking place, the portal doesn't provide a body and instead makes the content available via parameters.
            // So we would need to re-encode the POST. As of 2012-05-10, the XForms engine instead uses the
            // multipart/form-data encoding on the main form to help us here.
            requestDetails.content foreach { content ⇒
                content.body match {
                    case Left(string) ⇒ connection.getOutputStream.write(string.getBytes("utf-8"))
                    case Right(bytes) ⇒ connection.getOutputStream.write(bytes)
                }
            }

            CookieManager.processResponseSetCookieHeaders(connection, newURL)

            connection
        } catch {
            case NonFatal(t) ⇒
                val is = connection.getInputStream
                if (is ne null)
                    runQuietly(is.close())

                throw t
        }
    }
    
    def writeResponse(data: String Either InputStream, contentType: Option[String])(implicit ctx: EmbeddingContextWithResponse): Unit =
        contentType map NetUtils.getContentTypeMediaType match {
            case Some(mediatype) if XMLUtils.isTextOrJSONContentType(mediatype) || XMLUtils.isXMLMediatype(mediatype) ⇒
                // Text/JSON/XML content type: rewrite response content
                val contentAsString =
                    data match {
                        case Left(string) ⇒
                            string
                        case Right(is) ⇒
                            val encoding = contentType flatMap (ct ⇒ Option(NetUtils.getContentTypeCharset(ct))) getOrElse "utf-8"
                            IOUtils.toString(is, encoding)
                    }

                decodeWSRPContent(
                    contentAsString,
                    ctx.namespace,
                    XMLUtils.isXMLMediatype(mediatype),
                    ctx.decodeURL,
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
    def decodeWSRPContent(content: String, namespace: String, encodeForXML: Boolean, decodeURL: String ⇒ String, writer: Writer): Unit = {

        val stringLength = content.length
        var currentIndex = 0
        var index        = 0

        import org.orbeon.oxf.externalcontext.WSRPURLRewriter.{decodeURL ⇒ _, _}

        while ({index = content.indexOf(BaseTag, currentIndex); index} != -1) {

            // Write up to the current mark
            writer.write(content, currentIndex, index - currentIndex)

            // Check if escaping is requested
            if (index + BaseTagLength * 2 <= stringLength && content.substring(index + BaseTagLength, index + BaseTagLength * 2) == BaseTag) {
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
                val decodedURL = decodeURL(encodedURL)

                writer.write(if (encodeForXML) XMLUtils.escapeXMLMinimal(decodedURL) else decodedURL)
            } else if (index < stringLength - BaseTagLength && content.charAt(index + BaseTagLength) == '_') {
                // Namespace encoding
                writer.write(namespace)
                currentIndex = index + PrefixTagLength
            } else
                throw new OXFException("Invalid WSRP rewrite tagging.")
        }

        // Write remainder of string
        if (currentIndex < stringLength)
            writer.write(content, currentIndex, content.length - currentIndex)
    }
}

// Immutable responses
sealed trait ContentOrRedirect
case class Content(body: String Either Array[Byte], contentType: Option[String], title: Option[String]) extends ContentOrRedirect
case class Redirect(location: String, exitPortal: Boolean = false) extends ContentOrRedirect {
    require(location ne null, "Missing Location header in redirect response")
}

// Immutable information about an outgoing request
case class RequestDetails(
    content  : Option[Content],
    url      : String,
    headers  : immutable.Seq[(String, String)],
    params   : immutable.Seq[(String, String)]
)

trait EmbeddingContext {
    def namespace: String
    def getSessionAttribute(name: String): AnyRef
    def setSessionAttribute(name: String, value: AnyRef): Unit
    def removeSessionAttribute(name: String): Unit
    def log(message: String): Unit                              // consider removing
}

trait EmbeddingContextWithResponse extends EmbeddingContext{
    def writer: Writer
    def outputStream: OutputStream                              // for binary resources only
    def setHeader(name: String, value: String): Unit
    def setStatusCode(code: Int): Unit
    def decodeURL(encoded: String): String
}
