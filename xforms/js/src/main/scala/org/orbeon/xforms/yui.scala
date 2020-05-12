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

import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal

// YUI definitions. Eventually this will go away, see:
// https://github.com/orbeon/orbeon-forms/issues/1599

@js.native
trait YUICustomEvent extends js.Object {
  def subscribe(fn: js.Function)   : Unit = js.native
  def unsubscribe(fn: js.Function) : Unit = js.native
  def fire(args: Any*)             : Unit = js.native
}

@JSGlobal("YAHOO.widget.ProgressBar")
@js.native
class ProgressBar(config: js.Object) extends js.Object {
  def render(parent: Element, before: js.UndefOr[Element] = js.undefined) : Unit       = js.native
  def set(key: String, value: Int)                                        : Unit       = js.native
  def get(key: String)                                                    : js.Dynamic = js.native
}
