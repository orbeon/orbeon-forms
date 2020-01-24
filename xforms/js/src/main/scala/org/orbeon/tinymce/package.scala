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
    var mode                     : js.UndefOr[String]  = js.undefined
    var language                 : js.UndefOr[String]  = js.undefined
    var statusbar                : js.UndefOr[Boolean] = js.undefined
    var menubar                  : js.UndefOr[Boolean] = js.undefined
    var toolbar                  : js.UndefOr[String]  = js.undefined
    var gecko_spellcheck         : js.UndefOr[Boolean] = js.undefined
    var doctype                  : js.UndefOr[String]  = js.undefined
    var encoding                 : js.UndefOr[String]  = js.undefined
    var entity_encoding          : js.UndefOr[String]  = js.undefined
    var forced_root_block        : js.UndefOr[String]  = js.undefined
    var verify_html              : js.UndefOr[Boolean] = js.undefined
    var visual_table_class       : js.UndefOr[String]  = js.undefined
    var skin                     : js.UndefOr[Boolean] = js.undefined
    var content_style            : js.UndefOr[String]  = js.undefined
    var content_css              : js.UndefOr[String]  = js.undefined
    var plugins                  : js.UndefOr[String]  = js.undefined
    var autoresize_min_height    : js.UndefOr[Double]  = js.undefined
    var autoresize_bottom_margin : js.UndefOr[Double]  = js.undefined
    var suffix                   : js.UndefOr[String]  = js.undefined
  }

  object TinyMceDefaultConfig extends TinyMceConfig {
    mode               = "exact"
    language           = "en"
    statusbar          = false
    menubar            = false
    plugins            = "lists link"
    toolbar            = "bold italic | bullist numlist outdent indent | link"
    gecko_spellcheck   = true
    doctype            = "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">"
    encoding           = "xml"
    entity_encoding    = "raw"
    forced_root_block  = "div"
    verify_html        = true
    visual_table_class = "fr-tinymce-table" // Override default TinyMCE class on tables, which adds borders
                                            // We can't leave this just empty, otherwise TinyMCE puts its own CSS class
    skin               = false              // Disable skin (see https://github.com/orbeon/orbeon-forms/issues/3473)
  }
}
