package org.orbeon.xforms.rpc

import io.circe.parser.*
import io.circe.{Decoder, Encoder, Json}
import org.orbeon.xforms.*

import scala.concurrent.{Future, Promise}
import scala.scalajs.js.URIUtils
import scala.scalajs.js.annotation.JSExport
import scala.util.Success


trait RpcClientTrait
  extends autowire.Client[Json, Decoder, Encoder]
     with JsonSerializers {

  private var lastSequenceNumber = 0
  private var pending: Map[Int, Promise[Json]] = Map.empty

  val RpcEventName: String // Scala 3: trait parameter

  // Autowire calls this with the request containing the method call already encoded. We dispatch a custom event to
  // the server, register a promise with an id, and return a `Future` to Autowire.
  def doCall(req: Request): Future[Json] = {

    val pathValue = req.path mkString "/"
    val argsValue = Json.fromFields(req.args).noSpaces//write(req.args)

    lastSequenceNumber += 1
    val id = lastSequenceNumber

    AjaxClient.fireEvent(
      AjaxEvent(
        eventName  = RpcEventName,
        targetId   = Constants.DocumentId,
        form       = Support.allFormElems.headOption, // 2023-09-01: only used by Form Builder, so presumably only one
        properties = Map(                             // form. However, we *should* pass a form id or context to calls.
          "id"   -> id,
          "path" -> pathValue,
          "args" -> argsValue
        )
      )
    )

    val p = Promise[Json]()
    pending += id -> p
    p.future
  }

  // When the server has a response, it tells the client to call this function via JavaScript. This decodes the
  // response and completes the `Promise` with it. By doing so, Autowire then handles the response and in turn
  // completes the `Future` which was returned to the original caller with the result of the remote method call.
  @JSExport
  def processResponse(id: String, response: String): Unit =
    pending.get(id.toInt) match {
      case Some(promise) =>
        pending -= id.toInt
        promise.complete(Success(parse(URIUtils.decodeURIComponent(response)).getOrElse(throw new NoSuchElementException)))
      case None =>
        println(s"RPC: got incorrect id in response: $id")
    }
}
