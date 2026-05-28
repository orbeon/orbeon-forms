package org.orbeon.fr.rpc

import cats.implicits.*
import chameleon.ext.circe.*
import io.circe.Json
import io.circe.generic.auto.*
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.*
import sloth.*

import scala.concurrent.Future


object FormRunnerRpcClient extends FormRunnerRpcClientBase {

  override protected val EventName: String = "fr-rpc-request"

  val api: FormRunnerRpcApi = Client[Json, Future](Transport).wire[FormRunnerRpcApi]
}
