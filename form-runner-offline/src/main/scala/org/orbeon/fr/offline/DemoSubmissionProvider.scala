package org.orbeon.fr.offline

import org.orbeon.oxf.http.{Headers, HttpMethod, StatusCode}
import org.orbeon.xforms.embedding.{SubmissionProvider, SubmissionRequest, SubmissionResponse}
import org.scalajs.dom.experimental.{Headers => FetchHeaders}

import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import scala.scalajs.js.annotation.JSGlobal
import scala.scalajs.js.typedarray.Uint8Array


@js.native
@JSGlobal("TextDecoder")
class TextDecoder extends js.Object {
  def decode(buffer: Uint8Array): String = js.native
}

object DemoSubmissionProvider extends SubmissionProvider {

  private var store = Map[String, (Option[String], Uint8Array)]()

  def submit(req: SubmissionRequest): SubmissionResponse = {

    val x = req.headers.iterator map { array =>
      val name = array(0)
      val value = array(1)

      s"$name=$value"
    } mkString "&"

    println(s"xxxx DemoSubmissionProvider: ${req.method}, ${req.url.pathname}, ${req.body.map(_.length)}, ${x}")

    HttpMethod.withNameInsensitive(req.method) match {
      case HttpMethod.GET =>

        // TODO: check pathname is persistence path
        store.get(req.url.pathname) match {
          case Some((responseContentTypeOpt, responseBody)) =>
            new SubmissionResponse {
              val statusCode = StatusCode.Ok
              val headers    = new FetchHeaders(responseContentTypeOpt.toArray.toJSArray.map(x => js.Array(Headers.ContentType, x)))
              val body       = responseBody
            }
          case None =>
            new SubmissionResponse {
              val statusCode = StatusCode.NotFound
              val headers    = new FetchHeaders
              val body       = new Uint8Array(0)
            }
        }
      case HttpMethod.PUT =>

        if (req.headers.get(Headers.ContentType).exists(_.contains("xml"))) {
          val x = new TextDecoder().decode(req.body.get)
          println(s"xxxxx PUT body string: `$x`")
        }

        // TODO: check pathname is persistence path
        val existing = store.contains(req.url.pathname)
        store += req.url.pathname -> (req.headers.get(Headers.ContentType).toOption -> req.body.getOrElse(throw new IllegalArgumentException))

        println(s"xxxxx DemoSubmissionProvider: ${req.method}, ${req.url.pathname}, $existing, ${req.body.get.length}")

        new SubmissionResponse {
          val statusCode = if (existing) StatusCode.Ok else StatusCode.Created
          val headers    = new FetchHeaders
          val body       = new Uint8Array(0)
        }
      case _ => ???
    }
  }

  def submitAsync(req: SubmissionRequest): js.Promise[SubmissionResponse] = ???
}