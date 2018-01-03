/**
  * Copyright (C) 2018 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  *  2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.builder

import autowire._
import org.orbeon.builder.rpc.FormBuilderRpcApi
import org.orbeon.xforms.rpc.RpcClient

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal
import scala.scalajs.js.timers

@js.native
@JSGlobal("bowser")
object Bowser extends js.Object {
  val msie    : js.UndefOr[Boolean] = js.native
  val msedge  : js.UndefOr[Boolean] = js.native
  val version : String  = js.native
  val name    : String  = js.native
}

object BrowserCheck {

  def checkSupportedBrowser(): Unit = {
    // If we don't delay, `Globals.ns` doesn't appear to be initialized. This is not ideal and the initialization order should be fixed.
    if (Bowser.msie.contains(true) || Bowser.msedge.contains(true) && Bowser.version.toDouble < BrowserVersion.MinimalEdgeVersion)
      timers.setTimeout(100.millis) {
        RpcClient[FormBuilderRpcApi].unsupportedBrowser(Bowser.name, Bowser.version.toDouble).call()
      }
  }

}
