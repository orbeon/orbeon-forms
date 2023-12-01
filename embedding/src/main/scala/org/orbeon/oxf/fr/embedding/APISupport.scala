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

import org.apache.commons.io.IOUtils
import org.apache.http.client.CookieStore
import org.apache.http.impl.client.BasicCookieStore
import org.orbeon.connection.{BufferedContent, Content, StreamedContent}
import org.orbeon.io.IOUtils._
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.fr.embedding.servlet.ServletEmbeddingContextWithResponse
import org.orbeon.oxf.http.Headers._
import org.orbeon.oxf.http.HttpMethod.{GET, POST}
import org.orbeon.oxf.http._
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.MarkupUtils._
import org.orbeon.oxf.util.PathUtils._
import org.orbeon.oxf.util.{ContentTypes, PathUtils}
import org.orbeon.wsrp.WSRPSupport
import org.orbeon.xforms.Constants
import org.slf4j.LoggerFactory

import java.io.Writer
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import scala.collection.immutable
import scala.jdk.CollectionConverters._
import scala.util.matching.Regex
import scala.util.{Failure, Success}


object APISupport {

  import Private._

  val Logger = LoggerFactory.getLogger(List("org", "orbeon", "embedding") mkString ".") // so JARJAR doesn't touch this!

  case class Redirect(
    location   : String,
    exitPortal : Boolean = false
  ) {
    require(location ne null, "Missing redirect location")
  }

  type StreamedContentOrRedirect = StreamedContent Either Redirect
  type BufferedContentOrRedirect = BufferedContent Either Redirect

  def proxyPage(
    baseURL      : String,
    path         : String,
    headers      : immutable.Seq[(String, String)] = Nil,
    params       : immutable.Seq[(String, String)] = Nil)(
    implicit ctx : EmbeddingContextWithResponse
  ): Unit = {

    Logger.debug(s"proxying page for path = `$path`")

    val url = formRunnerURL(baseURL, path, embeddable = true)

    callService(RequestDetails(None, url, path, headers, params))._1 match {
      case Left(content: StreamedContent) =>
        useAndClose(content)(writeResponseBody(mustRewriteForMediatype))
      case Right(Redirect(_, _)) =>
        throw new UnsupportedOperationException
    }
  }

  def proxyServletResources(
    req          : HttpServletRequest,
    res          : HttpServletResponse,
    namespace    : String,
    resourcePath : String
  ): Unit =
    withSettings(req, res.getWriter) { settings =>

      implicit val ctx = new ServletEmbeddingContextWithResponse(
        req,
        Right(res),
        namespace,
        settings.orbeonPrefix,
        settings.httpClient
      )

      APISupport.sanitizeResourceId(resourcePath, settings.FormRunnerResourcePathRegex) match {
        case Some(sanitizedResourcePath) =>

          val url = formRunnerURL(settings.formRunnerURL, sanitizedResourcePath, embeddable = false)

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
              path    = sanitizedResourcePath,
              headers = proxyCapitalizeAndCombineHeaders(requestHeaders(req).toList, request = true).toList,
              params  = Nil
            )
          )

        case None =>
          ctx.setStatusCode(HttpServletResponse.SC_NOT_FOUND)
      }
    }

  def proxySubmission(
    req : HttpServletRequest,
    res : HttpServletResponse
  ): Unit =
    withSettings(req, res.getWriter) { settings =>

      Logger.debug("proxying submission")

      implicit val ctx = new ServletEmbeddingContextWithResponse(
        req,
        Right(res),
        Constants.NamespacePrefix + "0",
        settings.orbeonPrefix,
        settings.httpClient
      )

      val contentFromRequest =
        StreamedContent(
          inputStream   = req.getInputStream,
          contentType   = Option(req.getContentType),
          contentLength = Some(req.getContentLength.toLong) filter (_ >= 0),
          title         = None
        )

      val (contentOrRedirect, httpResponse) =
        APISupport.callService(RequestDetails(
          content = Some(contentFromRequest),
          url     = settings.formRunnerURL.dropTrailingSlash + Constants.XFormsServerSubmit,
          path    = Constants.XFormsServerSubmit,
          headers = proxyCapitalizeAndCombineHeaders(APISupport.requestHeaders(req).toList, request = true).toList,
          params  = Nil
        ))

      contentOrRedirect match {
        case Right(Redirect(location, true)) =>
          res.sendRedirect(location)
        case Right(Redirect(_, false)) =>
          throw new NotImplementedError
        case Left(content: StreamedContent) =>

          ctx.setStatusCode(httpResponse.statusCode)
          httpResponse.content.contentType foreach (ctx.setHeader(Headers.ContentType, _))

          proxyCapitalizeAndCombineHeaders(httpResponse.headers, request = false) foreach (ctx.setHeader _).tupled

          useAndClose(content)(APISupport.writeResponseBody(mustRewriteForMediatype))
      }
    }

  def proxyResource(requestDetails: RequestDetails)(implicit ctx: EmbeddingContextWithResponse): Unit = {

    Logger.debug(s"proxying resource for URL = `${requestDetails.url}`")

    val res = connectURL(requestDetails)

    ctx.setStatusCode(res.statusCode)
    res.content.contentType foreach (ctx.setHeader(Headers.ContentType, _))

    proxyCapitalizeAndCombineHeaders(res.headers, request = false) foreach (ctx.setHeader _).tupled

    useAndClose(res.content)(writeResponseBody(mediatype => mustRewriteForMediatype(mediatype) || mustRewriteForPath(requestDetails.path)))
  }

  def formRunnerPath(app: String, form: String, mode: String, documentId: Option[String], query: Option[String]) =
    PathUtils.appendQueryString(s"/fr/$app/$form/$mode${documentId map ("/" +) getOrElse ""}", query getOrElse "")

  def formRunnerHomePath(query: Option[String]) =
    PathUtils.appendQueryString("/fr/", query getOrElse "")

  def formRunnerURL(baseURL: String, path: String, embeddable: Boolean) =
    PathUtils.appendQueryString(baseURL.dropTrailingSlash + path, if (embeddable) s"${ExternalContext.EmbeddableParam}=true" else "")

  def requestHeaders(req: HttpServletRequest) =
    for {
      name   <- req.getHeaderNames.asScala
      values = req.getHeaders(name).asScala.toList
    } yield
      name -> values

  // Match on headers in a case-insensitive way, but the header we sent follows the capitalization of the
  // header specified in the init parameter.
  def headersToForward(clientHeaders: List[(String, List[String])], configuredHeaders: Map[String, String]): Iterable[(String, String)] =
    for {
      (name, value) <- proxyAndCombineRequestHeaders(clientHeaders)
      originalName  <- configuredHeaders.get(name.toLowerCase)
    } yield
      originalName -> value

  // Call the Orbeon service at the other end
  def callService(requestDetails: RequestDetails)(implicit ctx: EmbeddingContext): (StreamedContentOrRedirect, HttpResponse) = {

    Logger.debug(s"calling service for URL = `${requestDetails.url}`")

    val cx = connectURL(requestDetails)

    val redirectOrContent =
      if (StatusCode.isRedirectCode(cx.statusCode)) {
        // https://github.com/orbeon/orbeon-forms/issues/2967
        val location = cx.headers("Location").head
        Right(Redirect(location, exitPortal = urlHasProtocol(location)))
      } else
        Left(cx.content)

    redirectOrContent -> cx
  }

  def mustRewriteForMediatype(mediatype: String): Boolean =
    ContentTypes.isTextOrXMLOrJSONContentType(mediatype)

  def mustRewriteForPath(path: String): Boolean =
    path match {
      case Constants.FormDynamicResourcesRegex(_) => true
      case _                                      => false
    }

  def writeResponseBody(doRewrite: String => Boolean)(content: Content)(implicit ctx: EmbeddingContextWithResponse): Unit =
    content.contentType flatMap ContentTypes.getContentTypeMediaType match {
      case Some(mediatype) if doRewrite(mediatype) =>
        // Text/JSON/XML content type: rewrite response content
        val encoding        = content.contentType flatMap ContentTypes.getContentTypeCharset getOrElse ExternalContext.StandardCharacterEncoding
        val contentAsString = useAndClose(content.stream)(IOUtils.toString(_, encoding))
        val encodeForXML    = ContentTypes.isXMLMediatype(mediatype)

        def decodeURL(encoded: String) = {
          val decodedURL = ctx.decodeURL(encoded)
          if (encodeForXML) decodedURL.escapeXmlMinimal else decodedURL
        }

        WSRPSupport.decodeWSRPContent(
          contentAsString,
          ctx.namespace,
          decodeURL,
          ctx.writer
        )
      case other =>
        // All other types: just copy
        Logger.debug(s"using ctx.outputStream for mediatype = `$other`")
        ctx.outputStream match {
          case Success(os) =>
            useAndClose(content.stream)(IOUtils.copy(_, os))
          case Failure(t)  =>
            Logger.warn(s"unable to obtain `OutputStream` possibly because of a missing mediatype downstream", t)
            ctx.writer.write("unable to provide content")
        }
    }

  def scopeSettings[T](req: HttpServletRequest, settings: EmbeddingSettings)(body: => T): T = {
    req.setAttribute(SettingsKey, settings)
    try body
    finally req.removeAttribute(SettingsKey)
  }

  def withSettings[T](req: HttpServletRequest, writer: => Writer)(body: EmbeddingSettings => T): Unit =
    Option(req.getAttribute(SettingsKey).asInstanceOf[EmbeddingSettings]) match {
      case Some(settings) =>
        body(settings)
      case None =>
        val msg = "ERROR: Orbeon Forms embedding filter is not configured."
        Logger.error(msg)
        writer.write(msg)
    }

  def nextNamespace(req: HttpServletRequest) = {

    val newValue =
      Option(req.getAttribute(LastNamespaceIndexKey).asInstanceOf[Integer]) match {
        case Some(value) => value + 1
        case None        => 0
      }

    req.setAttribute(LastNamespaceIndexKey, newValue)

    Constants.NamespacePrefix + newValue
  }

  val DefaultFormRunnerResourcePath =
    """(?xi)
      (
        # XForms server paths
        (?:
          /xforms-server
          (?:
            (?:
              |
              /upload
              |
              /dynamic/[^/^.]+
              |
              -submit
            )
            |
            (?:
              /.+[.]
              (?:
                css|js
              )
            )
          )
        )
        |
        # PDF/TIFF service paths
        (?:
          /fr/service/
          [^/^.]+
          /
          [^/^.]+
          /
          (?:
            pdf|tiff
          )
          /
          [^/^.]+
          /
          [0-9A-Za-z\-]+
          (?:
            /[^/]+
          )?
          [.]
          (?:
            pdf|tiff
          )
        )
        |
        # PDF/TIFF resource paths
        (?:
          /fr/
          [^/^.]+
          /
          [^/^.]+
          /
          (?:
            pdf|tiff
          )
          /
          [^/^.]+
        )
        |
        # Other asset paths
        (?:
          # Optional versioned resources token
          (?:
            /
            [^/^.]+
          )?
          /
          (?:
            apps/fr/style
            |
            ops
            |
            xbl
            |
            forms/orbeon/builder/images
          )
          /
          .+
          [.]
          (?:
            gif|css|pdf|js|map|png|jpg|ico|svg|ttf|eot|woff|woff2
          )
        )
      )
    """

  // Resources are whitelisted to prevent unauthorized access to pages
  def sanitizeResourceId(s: String, FormRunnerResourcePath: Regex): Option[String] = {

    // First level of sanitation: parse, normalize and keep the path only
    def sanitizeResourcePath(s: String) =
      new java.net.URI(s).normalize().getPath

    def hasNoParent(s: String) =
      ! s.contains("/..") && ! s.contains("../")

    Option(s) map sanitizeResourcePath filter hasNoParent collect {
      case FormRunnerResourcePath(resourcePath) => resourcePath
    }
  }

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
    def connectURL(requestDetails: RequestDetails)(implicit ctx: EmbeddingContext): HttpResponse =
      ctx.httpClient.connect(
        url         = recombineQuery(requestDetails.url, requestDetails.params),
        credentials = None,
        cookieStore = getOrCreateCookieStore,
        method      = if (requestDetails.content.isEmpty) GET else POST,
        headers     = requestDetails.headersMapWithContentType + (Headers.OrbeonClient -> List(ctx.client)),
        content     = requestDetails.content
      )

    def getOrCreateCookieStore(implicit ctx: EmbeddingContext): CookieStore =
      ctx.getSessionAttribute(RemoteSessionIdKey) map (_.asInstanceOf[CookieStore]) getOrElse {
        val newCookieStore = new BasicCookieStore
        ctx.setSessionAttribute(RemoteSessionIdKey, newCookieStore)
        newCookieStore
      }
  }
}
