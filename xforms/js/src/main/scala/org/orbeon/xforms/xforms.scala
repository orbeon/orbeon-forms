/**
  * Copyright (C) 2017 Orbeon, Inc.
  *
  * This program is free software you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation either version
  *  2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.xforms

import org.scalajs.dom.html.Element
import org.scalajs.jquery.JQueryCallback

import scala.scalajs.js
import scala.scalajs.js.annotation.{JSName, ScalaJSDefined}
import scala.scalajs.js.|

@js.native
trait Item extends js.Object {
  val label      : String                     = js.native
  val value      : String                     = js.native
  val attributes : js.UndefOr[ItemAttributes] = js.native
  val children   : js.UndefOr[js.Array[Item]] = js.native
}

@js.native
trait ItemAttributes extends js.Object {
  val `class`        : js.UndefOr[String] = js.native
  val style          : js.UndefOr[String] = js.native
  val `xxforms-open` : js.UndefOr[String] = js.native
}

@ScalaJSDefined
class XBLCompanion extends js.Object {

  // Lifecycle

  def init()                                  : Unit   = ()
  def destroy()                               : Unit   = ()

  def xformsGetValue()                        : String = null
  def xformsUpdateValue(newValue: String)     : Unit   = ()
  def xformsUpdateReadonly(readonly: Boolean) : Unit   = ()
  def xformsFocus()                           : Unit   = ()

  // Helpers

  def containerElem: Element = this.asInstanceOf[js.Dynamic].container.asInstanceOf[Element]
}

@JSName("ORBEON.xforms.XBL")
@js.native
object XBL extends js.Object {
  def declareCompanion(name: String, companion: XBLCompanion): Unit = js.native
}

@js.native
trait YUICustomEvent extends js.Object {
  def subscribe(fn: js.Function): Unit = js.native
}

@JSName("ORBEON.xforms.Events")
@js.native
object Events extends js.Object {
  def ajaxResponseProcessedEvent: YUICustomEvent = js.native
}

@JSName("ORBEON.xforms.server.AjaxServer")
@js.native
object AjaxServer extends js.Object {
  def ajaxResponseReceived: JQueryCallback = js.native
}

@JSName("ORBEON.xforms.Document")
@js.native
object Document extends js.Object {
  def dispatchEvent(event: js.Object): Unit = js.native
  def setValue(controlIdOrElem: String | Element, newValue: String, form: js.UndefOr[Element] = js.undefined): Unit = js.native
  def getValue(controlIdOrElem: String | Element, form: js.UndefOr[Element] = js.undefined): js.UndefOr[String] = js.native
}