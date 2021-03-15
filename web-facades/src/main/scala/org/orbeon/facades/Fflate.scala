package org.orbeon.facades

import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal
import scala.scalajs.js.typedarray.Uint8Array


@js.native
@JSGlobal("fflate")
object Fflate extends js.Object {
  def unzipSync(data: Uint8Array): js.Dictionary[Uint8Array] = js.native
}
