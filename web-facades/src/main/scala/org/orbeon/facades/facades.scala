/**
  * Copyright (C) 2020 Orbeon, Inc.
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
package org.orbeon.facades

import org.scalajs.dom
import org.scalajs.dom.html

import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal

@js.native
@JSGlobal("autosize")
object Autosize extends js.Function1[html.TextArea, Unit] {
  def apply  (textarea: html.TextArea): Unit = js.native
  def update (textarea: html.TextArea): Unit = js.native
  def destroy(textarea: html.TextArea): Unit = js.native
}

@js.native
trait Ladda extends js.Object {
  def start()                    : Unit    = js.native
  def setProgress(width: Double) : Unit    = js.native
  def stop()                     : Unit    = js.native
  def toggle()                   : Unit    = js.native
  def isLoading()                : Boolean = js.native
  def remove()                   : Unit    = js.native
}

@js.native
@JSGlobal("Ladda")
object Ladda extends js.Object {
  def create(button: html.Element): Ladda = js.native
}

@js.native
@JSGlobal("CodeMirror")
class CodeMirror(
  elem: dom.Element,
  config: js.Dictionary[js.Any]
) extends js.Object {
  def setOption(key: String, value: js.Any): Unit = js.native
  def getValue(): String = js.native
  def setValue(value: String): Unit = js.native
  def focus(): Unit = js.native
  def on(event: String, handler: js.Function): Unit = js.native
  def off(event: String, handler: js.Function): Unit = js.native
}
