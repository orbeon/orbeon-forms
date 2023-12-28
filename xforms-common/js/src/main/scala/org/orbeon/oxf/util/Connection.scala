/**
 * Copyright (C) 2020 Orbeon, Inc.
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

import cats.effect.IO
import org.orbeon.connection._
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.http._
import org.orbeon.oxf.util.Logging._
import org.orbeon.xforms.embedding.{SubmissionProvider, SubmissionRequest, SubmissionResponse}
import org.orbeon.{fs2dom, sjsdom}
import org.scalajs.dom.experimental.{Headers => JSHeaders, URL => JSURL}

import java.io.InputStream
import java.net.URI
import scala.scalajs.js
import scala.scalajs.js.Iterator
import scala.scalajs.js.JSConverters._
import scala.scalajs.js.annotation.JSName
import scala.scalajs.js.typedarray.Uint8Array


object Connection extends ConnectionTrait {

  var resourceResolver: String => Option[ConnectionResult] = _

  var submissionProvider: Option[SubmissionProvider] = None

  def connectNow(
    method          : HttpMethod,
    url             : URI,
    credentials     : Option[BasicCredentials],
    content         : Option[StreamedContent],
    headers         : Map[String, List[String]],
    loadState       : Boolean,
    saveState       : Boolean,
    logBody         : Boolean
  )(implicit
    logger          : IndentedLogger,
    externalContext : ExternalContext
  ): ConnectionResult = {
    method match {
      case HttpMethod.PUT =>
        fromSubmissionProviderSync(method, url, content, headers)
      case HttpMethod.GET =>
        fromResourceResolver(method, url) orElse
          fromSubmissionProviderSync(method, url, content, headers)
      case _ =>
        Some(methodNotAllowed(url))
    }
  } getOrElse
    notFound(url)

  def connectAsync(
    method          : HttpMethod,
    url             : URI,
    credentials     : Option[BasicCredentials],
    content         : Option[AsyncStreamedContent],
    headers         : Map[String, List[String]],
    loadState       : Boolean,
    logBody         : Boolean
  )(implicit
    logger          : IndentedLogger,
    externalContext : ExternalContext
  ): IO[AsyncConnectionResult] = {
    method match {
      case HttpMethod.PUT =>
        fromSubmissionProviderAsync(method, url, content, headers)
      case HttpMethod.GET =>
        fromResourceResolver(method, url).map(cxr => IO.pure(ConnectionResult.syncToAsync(cxr))) orElse
          fromTemporaryFile(method, url).map(IO.pure)                                            orElse
          fromSubmissionProviderAsync(method, url, content, headers)
      case _ =>
        Some(IO.pure(ConnectionResult.syncToAsync(methodNotAllowed(url))))
    }
  } getOrElse
    IO.pure(ConnectionResult.syncToAsync(notFound(url)))

  private class IteratorEntry extends Iterator.Entry[Short] {

    private var _done: Boolean = false
    private var _value: Short = 0

    def update(done: Boolean, value: Short): Unit = {
      _done = done
      _value = value
    }

    def done: Boolean = _done
    def value: Short = _value
  }

  private def inputStreamIterable(is: InputStream): js.Iterable[Short] =
    new js.Iterable[Short] {

      @JSName(js.Symbol.iterator)
      def jsIterator(): js.Iterator[Short] = new js.Iterator[Short] {

      var current = is.read()

      val entry = new IteratorEntry

      def next(): Iterator.Entry[Short] = {

        if (current == -1) {
          entry.update(done = true, 0)
        } else {
          entry.update(done = false, current.toShort)
          current = is.read()
        }
        entry
      }
    }
  }

  private def fromTemporaryFile(method: HttpMethod, url: URI): Option[AsyncConnectionResult] =
    method match {
      case HttpMethod.GET if url.getScheme == JsFileSupport.UploadUriScheme =>
        JsFileSupport.readTemporaryFile(url) match {
          case Some(readableStream) =>
            Some(
              ConnectionResultT(
                url.toString,
                StatusCode.Ok,
                Map.empty,
                StreamedContent(
                  stream        = fs2dom.readReadableStream(IO(readableStream), cancelAfterUse = true),
                  contentType   = None, // xxx content-type
                  contentLength = None
                ),
                hasContent         = true, // not expecting empty temp files?
                dontHandleResponse = false,
              )
            )
          case None =>
            None
        }
      case _ =>
        None
    }

  private def fromResourceResolver(method: HttpMethod, url: URI): Option[ConnectionResult] =
    method match {
      case HttpMethod.GET => resourceResolver(url.toString)
      case _              => None
    }

  private def fromSubmissionProviderSync(
    method : HttpMethod,
    url    : URI,
    content: Option[StreamedContent],
    headers: Map[String, List[String]]
  )(implicit
    logger : IndentedLogger
  ): Option[ConnectionResult] =
    submissionProvider.map { provider =>

      val methodString   = method.entryName
      val jsUrl          = new JSURL(url.toString, "http://invalid/") // URL needs to be absolute; using `invalid` as base, see RFC6761
      val requestHeaders = makeJsHeaders(url, headers)

      val requestDataOpt: Option[Uint8Array] =
        content.map(c => new Uint8Array(inputStreamIterable(c.stream)))

      val submissionRequest =
        new SubmissionRequest {
          val method  = methodString
          val url     = jsUrl
          val headers = requestHeaders
          val body    = requestDataOpt.orUndefined
        }

      processSubmissionResponseSync(url, provider.submit(submissionRequest))
    }

  private def fromSubmissionProviderAsync(
    method : HttpMethod,
    url    : URI,
    content: Option[AsyncStreamedContent],
    headers: Map[String, List[String]]
  )(implicit
    logger   : IndentedLogger
  ): Option[IO[AsyncConnectionResult]] =
    submissionProvider.map { provider =>

      val methodString   = method.entryName
      val jsUrl          = new JSURL(url.toString, "http://invalid/") // URL needs to be absolute; using `invalid` as base, see RFC6761
      val requestHeaders = makeJsHeaders(url, headers)

      def newSubmissionRequest(readableStreamOpt: Option[sjsdom.ReadableStream[Uint8Array]]) =
        new SubmissionRequest {
          val method  = methodString
          val url     = jsUrl
          val headers = requestHeaders
          val body    = readableStreamOpt.orUndefined
        }

      val initialStream =
        content match {
          case Some(c) =>
            c.stream
              .through(fs2dom.toReadableStream)
              .map(rs => newSubmissionRequest(Some(rs)))
          case None =>
            fs2.Stream.emit(newSubmissionRequest(None))
        }

      initialStream.evalMap(submissionRequest => IO.fromPromise(IO(provider.submitAsync(submissionRequest))))
        .map(processSubmissionResponseAsync(url, _))
        .compile
        .onlyOrError
    }

  private def makeJsHeaders(
    url    : URI,
    headers: Map[String, List[String]]
  )(implicit
    logger   : IndentedLogger
  ): JSHeaders = // The `Headers` constructor expects key/value pairs, with only one value per header
      // TODO: Check if we ever have more than one value per header
      new JSHeaders(
        headers collect { case (k, v @ head :: tail) =>
          if (tail.nonEmpty)
            warn(
              s"more than one header value for header",
              List(
                "name"   -> k,
                "values" -> v.mkString(", "),
                "url"    -> url.toString
              )
            )
          js.Array(k, head)
        } toJSArray
      )

  private def processSubmissionResponseSync(
    url     : URI,
    response: SubmissionResponse
  )(implicit
    logger   : IndentedLogger
  ): ConnectionResult = {

    val responseHeaders =
      response.headers.jsIterator().toIterator map { kv =>
        kv(0) -> List(kv(1))
      } toMap

    val responseContentTypeOpt =
      Headers.firstItemIgnoreCase(responseHeaders, Headers.ContentType)

    def contentFromJsIterable(v: js.Iterable[_]) =
      StreamedContent.fromBytes(v.asInstanceOf[js.Iterable[Byte]].toArray[Byte], responseContentTypeOpt)

    // NOTE: Can't match on `js.Iterable[_]` "because it is a JS trait"
    val responseBody = response.body.toOption match {
      case Some(v: js.Array[_]) => contentFromJsIterable(v)
      case Some(v: Uint8Array)  => contentFromJsIterable(v)
      case _                    => warn("unrecognized response body type, considering empty body"); StreamedContent.Empty
    }

    ConnectionResult(
      url                = url.toString,
      statusCode         = response.statusCode,
      headers            = responseHeaders,
      content            = responseBody,
      dontHandleResponse = false,
    )
  }

  private def processSubmissionResponseAsync(
    url     : URI,
    response: SubmissionResponse
  )(implicit
    logger   : IndentedLogger
  ): AsyncConnectionResult = {

    val responseHeaders =
      response.headers.jsIterator().toIterator map { kv =>
        kv(0) -> List(kv(1))
      } toMap

    val responseContentTypeOpt =
      Headers.firstItemIgnoreCase(responseHeaders, Headers.ContentType)

    def streamFromJsIterable(v: js.Iterable[_]) =
      fs2.Stream.emits(v.asInstanceOf[js.Iterable[Byte]].toArray[Byte])

    // NOTE: Can't match on `js.Iterable[_]` "because it is a JS trait"
    val responseStream: Option[(fs2.Stream[IO, Byte], Option[Long])] = response.body.toOption map {
      case v: js.Array[_]                       => streamFromJsIterable(v)          -> Some(v.length)
      case v: Uint8Array                        => streamFromJsIterable(v)          -> Some(v.length)
      case v: js.Object => // matching on `sjsdom.ReadableStream[_]` doesn't compile
        fs2dom.readReadableStream(IO(v.asInstanceOf[sjsdom.ReadableStream[Uint8Array]]), cancelAfterUse = true) -> None
      case _                                    =>
        warn("unrecognized response body type, considering empty body")
        fs2.Stream.empty -> None
    }

    val responseBody =
      responseStream.map { case (stream, contentLengthOpt) =>
        StreamedContent(stream, responseContentTypeOpt, contentLengthOpt)
      }

    ConnectionResultT(
      url                = url.toString,
      statusCode         = response.statusCode,
      headers            = responseHeaders,
      content            = responseBody.getOrElse(StreamedContent.EmptyAsync),
      hasContent         = responseBody.isDefined,
      dontHandleResponse = false,
    )
  }

  def isInternalPath(path: String): Boolean = false

  def findInternalUrl(
    normalizedUrl : URI,
    filter        : String => Boolean
  )(implicit
    request       : ExternalContext.Request
  ): Option[String] = None

  def buildConnectionHeadersCapitalizedIfNeeded(
    url                      : URI, // scheme can be `null`; should we force a scheme, for example `http:/my/service`?
    hasCredentials           : Boolean,
    customHeaders            : Map[String, List[String]],
    headersToForward         : Set[String],
    cookiesToForward         : List[String],
    getHeader                : String => Option[List[String]])(implicit
    logger                   : IndentedLogger,
    externalContext          : ExternalContext,
    coreCrossPlatformSupport : CoreCrossPlatformSupportTrait
  ): Map[String, List[String]] = customHeaders

  def headersToForwardFromProperty: Set[String]  = Set.empty
  def cookiesToForwardFromProperty: List[String] = Nil

  def sessionCookieHeaderCapitalized(
    externalContext  : ExternalContext,
    cookiesToForward : List[String])(implicit
    logger           : IndentedLogger
  ): Option[(String, List[String])] = None
}
