/**
 * Copyright (C) 2017 Orbeon, Inc.
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
package org.orbeon.xforms.rpc

import io.circe.parser._
import io.circe.{Decoder, Encoder, Json}
import org.orbeon.xforms.{AjaxClient, AjaxEvent, Constants, EventNames}

import scala.concurrent.{Future, Promise}
import scala.scalajs.js.URIUtils
import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}
import scala.util.Success

@JSExportTopLevel("OrbeonRpcClient")
object RpcClient
  extends autowire.Client[Json, Decoder, Encoder]
     with JsonSerializers {

  private var lastSequenceNumber = 0
  private var pending: Map[Int, Promise[Json]] = Map.empty

  // Autowire calls this with the request containing the method call already encoded. We dispatch a custom event to
  // the server, register a promise with an id, and return a `Future` to Autowire.
  def doCall(req: Request): Future[Json] = {

    val pathValue = req.path mkString "/"
    val argsValue = Json.fromFields(req.args).noSpaces//write(req.args)

    lastSequenceNumber += 1
    val id = lastSequenceNumber

    AjaxClient.fireEvent(
      AjaxEvent(
        eventName  = EventNames.XXFormsRpcRequest,
        targetId   = Constants.DocumentId,
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

  // When the server has a response, it tells the client to call this function via JavaScript. This decodes the
  // response and completes the `Promise` with it. By doing so, Autowire then handles the response and in turn
  // completes the `Future` which was returned to the original caller with the result of the remote method call.
  @JSExport
  def processResponse(id: String, response: String): Unit = {
    pending.get(id.toInt) match {
      case Some(promise) =>
        pending -= id.toInt
        promise.complete(Success(parse(URIUtils.decodeURIComponent(response)).getOrElse(throw new NoSuchElementException)))
      case None =>
        println(s"RPC: got incorrect id in response: $id")
    }
  }
}
