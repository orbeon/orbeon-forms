package org.orbeon.builder.rpc

import cats.implicits.*
import chameleon.ext.circe.*
import io.circe.Json
import io.circe.generic.auto.*
import org.orbeon.fr.rpc.FormRunnerRpcClientBase
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.*
import sloth.*

import scala.concurrent.Future


object FormBuilderRpcClient extends FormRunnerRpcClientBase {

  override protected val EventName: String = "fb-rpc-request"

  val api: FormBuilderRpcApi = Client[Json, Future](Transport).wire[FormBuilderRpcApi]
}
