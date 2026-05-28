package org.orbeon.fr.rpc

import io.circe.Json
import io.circe.parser.*
import org.orbeon.xforms.*
import sloth.*

import scala.concurrent.{Future, Promise}
import scala.scalajs.js.URIUtils
import scala.util.Success


trait FormRunnerRpcClientBase {

  private var lastSequenceNumber = 0
  private var pending: Map[Int, Promise[Json]] = Map.empty

  protected val EventName: String

  protected object Transport extends RequestTransport[Json, Future] {

    override def apply(request: Request[Json]): Future[Json] = {

      val pathValue = s"${request.method.traitName}/${request.method.methodName}"
      val argsValue = request.payload.noSpaces

      lastSequenceNumber += 1
      val id = lastSequenceNumber

      AjaxClient.fireEvent(
        AjaxEvent(
          eventName  = EventName,
          targetId   = Constants.DocumentId,
          form       = Support.allFormElems.headOption,
          properties = Map(
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
  }

  // When the server has a response, it dispatches an `fr-rpc-response` event, which the XForms layer routes to
  // `FormRunnerPrivateAPI.processRpcResponse`, which calls this method. This decodes the response and completes
  // the `Promise`, which in turn completes the `Future` returned to the original caller.
  def processResponse(id: String, response: String): Unit =
    pending.get(id.toInt) match {
      case Some(promise) =>
        pending -= id.toInt
        promise.complete(Success(parse(URIUtils.decodeURIComponent(response)).getOrElse(throw new NoSuchElementException)))
      case None =>
        println(s"RPC: got incorrect id in response: $id")
    }
}
