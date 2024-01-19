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
package org.orbeon.builder.facade

import org.scalajs.jquery.JQuery

import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal

@js.native
trait JQueryTooltip extends JQuery {
  def tooltip(config: JQueryTooltipConfig): Unit = js.native
  def tooltip(operation: String)          : Unit = js.native
}

object JQueryTooltip {
  implicit def jq2tooltip(jq: JQuery): JQueryTooltip =
    jq.asInstanceOf[JQueryTooltip]
}

abstract class JQueryTooltipConfig extends js.Object {
  val title: String
}

@JSGlobal("ORBEON._")
@js.native
object Underscore extends js.Object {
  def uniqueId(): String = js.native
  def clone[T](o: T): T = js.native
}
