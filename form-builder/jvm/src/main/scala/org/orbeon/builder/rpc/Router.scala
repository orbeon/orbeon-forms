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

import autowire.Core.Request
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.{Decoder, Encoder, Json}
import org.orbeon.exception.OrbeonFormatter
import org.orbeon.oxf.fb.FormBuilder
import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.util.Logging._

import scala.concurrent.ExecutionContextExecutor
import scala.util.control.NonFatal

// The Autowire server/router
object Router extends autowire.Server[Json, Decoder, Encoder] with JsonSerializers {

  private implicit def Logger: IndentedLogger = FormBuilder.logger

  // We know that the execution of the route is synchronous so create a "run now"
  // `ExecutionContext` to do that.
  private implicit object RunNowExecutionContext extends ExecutionContextExecutor {

    def execute(runnable: Runnable): Unit = {
      try {
        runnable.run()
      } catch {
        case t: Throwable ⇒ reportFailure(t)
      }
    }

    def reportFailure(t: Throwable): Unit =
      error("RPC: Error processing request", List("throwable" → OrbeonFormatter.format(t)))
  }

  private val routes = route[FormBuilderRpcApi](FormBuilderRpcApiImpl)

  // When our server receives an RPC call, we call this method which take the parameters, decode them, and call
  // Autowire to route it. This results in an actual method call on the API implementation. Then serialize the result
  // to a string to send back to the client.
  // TODO: Handle failure response so we can tell the client that the call has failed.
  //@XPathFunction
  def processRequest(path: String, argsString: String): String = {
    val splitPath = path.split("/")
    try {
      debug("RPC: Processing request", List("method" → (splitPath mkString ".")))
      routes.apply(Request(splitPath, read[Json](parse(argsString).right.get).asObject.get.toMap)).value.get.get.noSpaces
    } catch {
      case NonFatal(t) ⇒
        error(
          "RPC: Error processing request",
          List(
            "method"    → (splitPath mkString "."),
            "throwable" → OrbeonFormatter.format(t)
          )
        )
        throw t
    }
  }
}
