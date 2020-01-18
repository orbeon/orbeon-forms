package org.orbeon

import org.scalajs.dom
import org.scalajs.dom.html

import scala.scalajs.js
import scala.scalajs.js.annotation.{JSGlobal, JSGlobalScope}

package object tinymce {

  @JSGlobalScope
  @js.native
  private object TinyMceGlobalScope extends js.Object {
    val TINYMCE_CUSTOM_CONFIG: js.UndefOr[TinyMceConfig] = js.native
  }

  val TinyMceCustomConfig = TinyMceGlobalScope.TINYMCE_CUSTOM_CONFIG

  @js.native
  @JSGlobal("tinymce")
  object TinyMce extends js.Object {
    var baseURL: String = js.native
    val EditorManager: TinyMceEditorManager = js.native
  }

  @js.native
  trait TinyMceEditorManager extends js.Object {
    var baseURL: String = js.native
  }

  @js.native
  @JSGlobal("tinymce.Editor")
  class TinyMceEditor(
    containerId   : String,
    config        : TinyMceConfig,
    editorManager : TinyMceEditorManager
  ) extends js.Object {
    val initialized: js.UndefOr[Boolean] = js.native
    val editorContainer: dom.Element = js.native
    val container: dom.Element = js.native
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
    def setMode(mode: String): Unit = js.native
  }

  @js.native
  trait TinyMceEvent extends js.Object {
    def add(f: js.Function1[TinyMceEditor, Unit])
  }

  trait TinyMceConfig extends js.Object {
    var plugins: js.UndefOr[String]
    var autoresize_min_height: js.UndefOr[Double] = js.undefined
    var autoresize_bottom_margin: js.UndefOr[Double] = js.undefined
    var suffix: js.UndefOr[String] = js.undefined
  }

  object TinyMceDefaultConfig extends TinyMceConfig {
    var mode = "exact"
    var language = "en"
    var statusbar = false
    var menubar            = false
    override var plugins            = "lists link"
    var toolbar            = "bold italic | bullist numlist outdent indent | link"
    var gecko_spellcheck   = true
    var doctype            = "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">"
    var encoding           = "xml"
    var entity_encoding    = "raw"
    var forced_root_block  = "div"
    var verify_html        = true
    var visual_table_class = "fr-tinymce-table" // Override default TinyMCE class on tables, which adds borders. We can't leave this just empty, otherwise TinyMCE puts its own CSS class.
    var skin               = false              // Disable skin (see https://github.com/orbeon/orbeon-forms/issues/3473)
    var content_style      = "body { font-size: 13px; margin: 8px 12px }"
  }
}
