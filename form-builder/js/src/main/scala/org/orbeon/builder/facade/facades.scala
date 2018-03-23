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

import org.scalajs.dom
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

abstract class TinyMceEditorManager extends js.Object

@JSGlobal("tinymce.EditorManager")
@js.native
object TinyMceDefaultEditorManager extends TinyMceEditorManager {
  var baseURL: String = js.native
}

@js.native
@JSGlobal("tinymce.Editor")
class TinyMceEditor(
  containerId   : String,
  config        : TinyMceConfig,
  editorManager : TinyMceEditorManager
) extends js.Object {
  val initialized                                                   : js.UndefOr[Boolean] = js.native
  val editorContainer                                               : dom.Element         = js.native
  val container                                                     : dom.Element         = js.native
  def on(name: String, callback: js.Function1[TinyMceEditor, Unit]) : Unit                = js.native
  def render()                                                      : Unit                = js.native
  def getWin()                                                      : dom.Window          = js.native
  def getContent()                                                  : String              = js.native
  def setContent(c: String)                                         : Unit                = js.native
  def execCommand(c: String)                                        : Unit                = js.native
  def show()                                                        : Unit                = js.native
  def hide()                                                        : Unit                = js.native
  def focus()                                                       : Unit                = js.native
}

@js.native
trait TinyMceEvent extends js.Object {
  def add(f: js.Function1[TinyMceEditor, Unit])
}

@js.native
trait TinyMceConfig extends js.Object {
  var plugins                  : String = js.native
  var autoresize_min_height    : Double = js.native
  var autoresize_bottom_margin : Double = js.native
}

@JSGlobal("YAHOO.xbl.fr.Tinymce.DefaultConfig")
@js.native
object TinyMceDefaultConfig extends TinyMceConfig

@JSGlobal("_")
@js.native
object Underscore extends js.Object {
  def uniqueId(): String = js.native
  def clone[T](o: T): T = js.native
}
