/**
 * Copyright (C) 2026 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.facades

import org.scalajs.dom.html

import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal


@js.native
@JSGlobal("Chart")
class ChartJs(ctx: html.Canvas, config: js.Dynamic) extends js.Object {
  var data   : js.Dynamic = js.native
  var options: js.Dynamic = js.native

  def update(mode: js.UndefOr[String] = js.undefined): Unit = js.native
  def resize(): Unit                                        = js.native
  def destroy(): Unit                                       = js.native
}
