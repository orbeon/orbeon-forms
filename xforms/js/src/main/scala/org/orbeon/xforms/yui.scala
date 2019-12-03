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

import org.scalajs.dom.html
import org.scalajs.dom.html.Element

import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal
import scala.scalajs.js.|

// YUI definitions. Eventually this will go away, see:
// https://github.com/orbeon/orbeon-forms/issues/1599

@JSGlobal("YAHOO.util.Connect")
@js.native
object YUIConnect extends js.Object {

  def setForm(
    formId          : html.Form | String,
    isUpload        : Boolean,
    secureUri       : Boolean
  ): Unit = js.native

  def setDefaultPostHeader(b: Boolean): Unit = js.native

  def initHeader(label: String, value: String, isDefault: Boolean): Unit = js.native

  def asyncRequest(
    method          : String,
    uri             : String,
    callback        : YUICallback,
    postData        : js.UndefOr[String]              = js.undefined
  ): js.Object = js.native

  def abort(
    o               : js.Object,
    callback        : js.UndefOr[YUICallback]         = js.undefined,
    isTimeout       : js.UndefOr[Boolean]             = js.undefined
  ): Boolean = js.native

  val startEvent    : CustomEvent = js.native
  val failureEvent  : CustomEvent = js.native
}

@js.native
trait CustomEvent extends js.Object {
  def subscribe(
    fn              : js.Function,
    obj             : js.UndefOr[js.Object]           = js.undefined,
    overrideContext : js.UndefOr[Boolean | js.Object] = js.undefined
  ): Unit = js.native
}

trait YUICallback extends js.Object {
  var timeout  : js.UndefOr[Int]         = js.undefined
  val upload   : js.UndefOr[js.Function] = js.undefined
  val success  : js.UndefOr[js.Function] = js.undefined
  val failure  : js.Function
  val argument : js.Object
}

@js.native
trait YUICustomEvent extends js.Object {
  def subscribe(fn: js.Function)   : Unit = js.native
  def unsubscribe(fn: js.Function) : Unit = js.native
  def fire()                       : Unit = js.native
}

@JSGlobal("YAHOO.widget.ProgressBar")
@js.native
class ProgressBar(config: js.Object) extends js.Object {
  def render(parent: Element, before: js.UndefOr[Element] = js.undefined) : Unit       = js.native
  def set(key: String, value: Int)                                        : Unit       = js.native
  def get(key: String)                                                    : js.Dynamic = js.native
}

@JSGlobal("YAHOO.util.Event")
@js.native
object Event extends js.Object {
  def preventDefault(event: js.Object): Unit = js.native
}
