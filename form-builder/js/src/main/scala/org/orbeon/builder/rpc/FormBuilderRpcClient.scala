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
package org.orbeon.builder.rpc

import cats.implicits.*
import chameleon.ext.circe.*
import io.circe.Json
import io.circe.generic.auto.*
import io.circe.parser.*
import org.orbeon.xforms.*
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.*
import sloth.*

import scala.concurrent.{Future, Promise}
import scala.scalajs.js.URIUtils
import scala.util.Success


object FormBuilderRpcClient {

  private var lastSequenceNumber = 0
  private var pending: Map[Int, Promise[Json]] = Map.empty

  private val EventName = "fb-rpc-request"

  private object Transport extends RequestTransport[Json, Future] {
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

  // When the server has a response, it dispatches an `fb-rpc-response` event, which the XForms layer routes to
  // `FormBuilderPrivateAPI.processRpcResponse`, which calls this method. This decodes the response and completes
  // the `Promise`, which in turn completes the `Future` returned to the original caller.
  def processResponse(id: String, response: String): Unit =
    pending.get(id.toInt) match {
      case Some(promise) =>
        pending -= id.toInt
        promise.complete(Success(parse(URIUtils.decodeURIComponent(response)).getOrElse(throw new NoSuchElementException)))
      case None =>
        println(s"RPC: got incorrect id in response: $id")
    }

  val api: FormBuilderRpcApi = Client[Json, Future](Transport).wire[FormBuilderRpcApi]
}
