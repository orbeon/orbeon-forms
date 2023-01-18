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

import cats.Eval
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.http._
import org.orbeon.oxf.util.Logging._
import org.orbeon.xforms.embedding.{SubmissionProvider, SubmissionRequest}
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
    logBody         : Boolean)(implicit
    logger          : IndentedLogger,
    externalContext : ExternalContext
  ): ConnectionResult =
    connectLater(
      method,
      url,
      credentials,
      content,
      headers,
      loadState,
      logBody
    ).value

  def connectLater(
    method          : HttpMethod,
    url             : URI,
    credentials     : Option[BasicCredentials],
    content         : Option[StreamedContent],
    headers         : Map[String, List[String]],
    loadState       : Boolean,
    logBody         : Boolean)(implicit
    logger          : IndentedLogger,
    externalContext : ExternalContext
  ): Eval[ConnectionResult] = Eval.now { // Eval.later

    val urlString = url.toString

    def fromResourceResolver: Option[ConnectionResult] =
      method match {
        case HttpMethod.GET => resourceResolver(urlString)
        case _              => None
      }

    class IteratorEntry extends Iterator.Entry[Short] {

      private var _done: Boolean = false
      private var _value: Short = 0

      def update(done: Boolean, value: Short): Unit = {
        _done = done
        _value = value
      }

      def done: Boolean = _done
      def value: Short = _value
    }

    def fromSubmissionProvider: Option[ConnectionResult] = {
      submissionProvider map { provider =>

        val methodString = method.entryName
        val jsUrl = new JSURL(urlString, "http://invalid/") // URL needs to be absolute; using `invalid` as base, see RFC6761

        def inputStreamIterable(is: InputStream) =
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

        val requestDataOpt =
          content map (c => new Uint8Array(inputStreamIterable(c.inputStream)))

        // The `Headers` constructor expects key/value pairs, with only one value per header
        // TODO: Check if we ever have more than one value per header
        val requestHeaders =
          new JSHeaders(
            headers collect { case (k, v @ head :: tail) =>
              if (tail.nonEmpty)
                warn(
                  s"more than one header value for header",
                  List(
                    "name"   -> k,
                    "values" -> v.mkString(", "),
                    "url"    -> urlString
                  )
                )
              js.Array(k, head)
            } toJSArray
          )

        val response =
          provider.submit(
            new SubmissionRequest {
              val method  = methodString
              val url     = jsUrl
              val headers = requestHeaders
              val body    = requestDataOpt.orUndefined
            }
          )

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
          url                = urlString,
          statusCode         = response.statusCode,
          headers            = responseHeaders,
          content            = responseBody,
          dontHandleResponse = false,
        )
      }
    }

    def notFound =
      ConnectionResult(
        url                = urlString,
        statusCode         = StatusCode.NotFound,
        headers            = Map.empty,
        content            = StreamedContent.Empty,
        dontHandleResponse = false,
      )

//    def methodNotAllowed =
//      ConnectionResult(
//        url                = url.toString,
//        statusCode         = StatusCode.MethodNotAllowed,
//        headers            = Map.empty,
//        content            = StreamedContent.Empty,
//        dontHandleResponse = false
//      )

    fromResourceResolver orElse fromSubmissionProvider getOrElse notFound
//    fromResourceResolver getOrElse notFound
  }

  def isInternalPath(path: String): Boolean = false

  def findInternalUrl(
    normalizedUrl : URI,
    filter        : String => Boolean)(implicit
    ec            : ExternalContext
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
