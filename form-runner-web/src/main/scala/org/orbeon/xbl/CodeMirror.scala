package org.orbeon.xbl

import org.orbeon.facades
import org.orbeon.xforms.facade.{XBL, XBLCompanion}
import org.orbeon.xforms.{$, AjaxClient, AjaxEvent, DocumentAPI}
import org.scalajs.dom.html
import org.scalajs.jquery.JQueryPromise

import scala.concurrent.{Future, Promise}
import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import scala.scalajs.js.|
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits._

object CodeMirror {

  XBL.declareCompanion("fr|code-mirror", js.constructorOf[CodeMirrorCompanion])

  private class CodeMirrorCompanion(containerElem: html.Element) extends XBLCompanion {

    private var editor                   : facades.CodeMirror = null
    private var handlers                 : js.Dictionary[js.Function] = js.Dictionary()
    private var hasFocus                 : Boolean = false
    private var userChangedSinceLastBlur : Boolean = false

    override def init(): Unit = {
      val editorElement = containerElem.querySelector(".xbl-fr-code-mirror-editor")
      this.editor = new facades.CodeMirror(
        editorElement,
        js.Dictionary(
          "mode"         -> "xml",
          "lineNumbers"  -> true,
          "lineWrapping" -> true,
          "indentUnit"   -> 4,
          "theme"        -> "darcula",
          "foldGutter"   -> true,
          "gutters"      -> js.Array("CodeMirror-linenumbers", "CodeMirror-foldgutter"),
          "extraKeys"    -> js.Dictionary(
            "Ctrl-Q"     -> ((cm: js.Dynamic) => cm.foldCode(cm.getCursor())),
            "Cmd-Enter"  -> js.Any.fromFunction0(userCmdCtrlEnter _),
            "Ctrl-Enter" -> js.Any.fromFunction0(userCmdCtrlEnter _)
          )
        )
      )

      this.handlers = js.Dictionary(
        "change" -> this.codeMirrorChange _,
        "focus"  -> this.codeMirrorFocus _,
        "blur"   -> this.codeMirrorBlur _
      )

      this.handlers.foreach { case (key, value) => this.editor.on(key, value) }

      this.xformsUpdateReadonly($(containerElem).is(".xforms-readonly"))
    }

    override def destroy(): Unit = {
      this.handlers.foreach { case (key, value) => this.editor.off(key, value) }
      this.handlers.clear()
      this.editor = null
      $(containerElem).find(".xbl-fr-code-mirror-editor").empty()
    }

    override def xformsFocus(): Unit = this.editor.focus()

    private def codeMirrorFocus(): Unit = this.hasFocus = true

    private def userCmdCtrlEnter(): Unit = {
      sendValueToXForms() foreach { _ =>
        AjaxClient.fireEvent(
          AjaxEvent(
            eventName = "DOMActivate",
            targetId  = this.containerElem.id
          )
        )
      }
    }

    // We update the value on blur, not on change, to be incremental, for performance on large forms
    private def codeMirrorBlur(): Unit = {
      this.hasFocus = false
      if (this.userChangedSinceLastBlur) {
        sendValueToXForms()
        this.userChangedSinceLastBlur = false
      }
    }

    private def sendValueToXForms(): Future[Unit] = {
      $(containerElem).addClass("xforms-visited")
      DocumentAPI.setValue(
        containerElem.id,
        this.xformsGetValue()
      )
    }

    private def codeMirrorChange(codeMirror: js.Dynamic, event: js.Dynamic): Unit = {
      val eventOrigin = event.origin.asInstanceOf[js.UndefOr[String]]
      if (! eventOrigin.contains("setValue")) {
        this.userChangedSinceLastBlur = true
      }
    }

    override def xformsUpdateReadonly(readonly: Boolean): Unit = {
      if (readonly) this.editor.setOption("readOnly", "true")
      else          this.editor.setOption("readOnly", false)
    }

    override def xformsUpdateValue(newValue: String): js.UndefOr[js.Promise[Unit] | JQueryPromise] = {
      val doUpdate = ! this.hasFocus && newValue != this.editor.getValue()
      if (doUpdate) {
        val editor = this.editor
        val promise = Promise[Unit]()

        js.timers.setTimeout(0) {
          editor.setValue(newValue)
          promise.success(())
        }

        promise.future.toJSPromise
      } else {
        js.undefined
      }
    }

    override def xformsGetValue(): String = this.editor.getValue()
  }
}


