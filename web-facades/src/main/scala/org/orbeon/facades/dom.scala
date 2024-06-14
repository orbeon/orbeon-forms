package org.orbeon.facades

import org.scalajs.dom

import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal
import scala.scalajs.js.typedarray.Uint8Array

@js.native
@JSGlobal
class HTMLDialogElement extends dom.html.Element {
  def showModal(): Unit = js.native
  def close(): Unit = js.native
}
