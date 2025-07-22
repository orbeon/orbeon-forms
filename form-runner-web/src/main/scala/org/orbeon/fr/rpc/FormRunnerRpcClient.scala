package org.orbeon.fr.rpc

import org.orbeon.xforms.rpc.RpcClientTrait

import scala.scalajs.js.annotation.JSExportTopLevel


@JSExportTopLevel("OrbeonFormRunnerRpcClient")
object FormRunnerRpcClient extends RpcClientTrait {
  val RpcEventName: String = "fr-rpc-request"
}
