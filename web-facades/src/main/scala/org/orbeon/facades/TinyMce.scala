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
import scala.scalajs.js.Dynamic.{global => g}
import scala.scalajs.js.annotation.JSGlobal


object TinyMce {

  // Since Scala.js 1.0, the method we used before didn't work when `TINYMCE_CUSTOM_CONFIG` is missing
  lazy val GlobalJsTinyMceCustomConfig: Option[TinyMceConfig] =
    if (js.typeOf(g.TINYMCE_CUSTOM_CONFIG) != "undefined")
      (g.TINYMCE_CUSTOM_CONFIG.asInstanceOf[TinyMceConfig]: js.UndefOr[TinyMceConfig]).toOption
    else
      None

  @js.native
  @JSGlobal("tinymce")
  object GlobalTinyMce extends js.Object {
    var baseURL: String = js.native
    val EditorManager: TinyMceEditorManager = js.native
  }

  @js.native
  trait TinyMceEditorManager extends js.Object

  @js.native
  trait TinyMceEditorMode extends js.Object {
    def set(mode: String): Unit = js.native
  }

  @js.native
  @JSGlobal("tinymce.Editor")
  class TinyMceEditor(
    containerId   : String,
    config        : TinyMceConfig,
    editorManager : TinyMceEditorManager
  ) extends js.Object {
    def on(name: String, callback: js.Function1[TinyMceEditor, Unit]): Unit = js.native
    def render(): Unit = js.native
    def getWin(): dom.Window = js.native
    def getContent(): String = js.native
    def setContent(c: String): Unit = js.native
    def execCommand(c: String): Unit = js.native
    def show(): Unit = js.native
    def hide(): Unit = js.native
    def focus(): Unit = js.native
    def getBody(): html.Element = js.native
    val mode: TinyMceEditorMode = js.native
  }

  trait TinyMceConfig extends js.Object {
    var inline                   : js.UndefOr[Boolean] = js.undefined
    var hidden_input             : js.UndefOr[Boolean] = js.undefined
    var language                 : js.UndefOr[String]  = js.undefined
    var statusbar                : js.UndefOr[Boolean] = js.undefined
    var menubar                  : js.UndefOr[Boolean] = js.undefined
    var toolbar                  : js.UndefOr[String]  = js.undefined
    var browser_spellcheck       : js.UndefOr[Boolean] = js.undefined
    var doctype                  : js.UndefOr[String]  = js.undefined
    var encoding                 : js.UndefOr[String]  = js.undefined
    var entity_encoding          : js.UndefOr[String]  = js.undefined
    var forced_root_block        : js.UndefOr[String]  = js.undefined
    var visual_table_class       : js.UndefOr[String]  = js.undefined
    var skin                     : js.UndefOr[Boolean] = js.undefined
    var content_css              : js.UndefOr[String]  = js.undefined
    var plugins                  : js.UndefOr[String]  = js.undefined
    var autoresize_min_height    : js.UndefOr[Double]  = js.undefined
    var autoresize_bottom_margin : js.UndefOr[Double]  = js.undefined
    var suffix                   : js.UndefOr[String]  = js.undefined
    var convert_urls             : js.UndefOr[Boolean] = js.undefined
  }

  object TinyMceDefaultConfig extends TinyMceConfig {
    inline             = true
    hidden_input       = false
    language           = "en"
    statusbar          = false
    menubar            = false
    plugins            = "lists link"
    toolbar            = "bold italic | bullist numlist outdent indent | link"
    browser_spellcheck = true
    doctype            = "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">"
    encoding           = "xml"
    entity_encoding    = "raw"
    forced_root_block  = "div"
    visual_table_class = "fr-tinymce-table" // Override default TinyMCE class on tables, which adds borders
                                            // We can't leave this just empty, otherwise TinyMCE puts its own CSS class
    skin               = false              // Disable skin (see https://github.com/orbeon/orbeon-forms/issues/3473)
    convert_urls       = false              // Don't convert an absolute URL to a relative path, which might not work after publish
  }
}
