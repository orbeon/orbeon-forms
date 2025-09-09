/**
 * Copyright (C) 2020 Orbeon, Inc.
 *
 * This program is free software you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.xbl

import io.udash.wrappers.jquery.JQueryPromise
import org.orbeon.facades.TinyMce.*
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.web.DomSupport.*
import org.orbeon.web.DomEventNames
import org.orbeon.xforms.facade.{Events, XBL, XBLCompanion}
import org.orbeon.xforms.{$, DocumentAPI, Page, EventListenerSupport}
import org.scalajs.dom
import org.scalajs.dom.*
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.*

import scala.concurrent.Promise
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*
import scala.scalajs.js.{UndefOr, |}


object TinyMCE {

  // When the TinyMCE adds positioned elements to the DOM directly under the body, and we have a dialog open,
  // move them inside the dialog, so they don't show under the dialog.
  document.addEventListener("DOMContentLoaded", { (_: dom.Event) =>

    val PositionedElementClasses = List(
      "tox-tinymce-inline", // Inline toolbar
      "tox-tinymce-aux"     // Dialog, e.g. link editor
    )

    // TODO: see if we can use the `MutationObserver` code in `DomSupport.scala`
    val observer = new MutationObserver({ (mutations, _) =>
      mutations.foreach { mutation =>
          mutation.addedNodes.foreach {
            case element: Element =>
              val classes = element.classList.toList
              if (PositionedElementClasses.exists(classes.contains)) {
                val openDialog = document.querySelector("dialog[open]")
                if (openDialog != null) {
                  openDialog.appendChild(element)
                }
              }
            case _ =>
          }
      }
    })

    observer.observe(
      target  = document.body,
      options = new MutationObserverInit { childList = true; subtree = false }
    )
  })

  var baseUrlInitialized = false

  XBL.declareCompanion("fr|tinymce", js.constructorOf[TinyMCECompanion])

  private class TinyMCECompanion(containerElem: html.Element) extends XBLCompanion {

    var tinyMceObjectOpt  : Option[TinyMceEditor] = None
    var tinyMceInitialized: Boolean               = false
    private object EventSupport extends EventListenerSupport

    override def init(): Unit = {

      val isStaticReadonly = containerElem.querySelector(".xbl-fr-tinymce-div .xforms-output-output") ne null

      if (! isStaticReadonly) {

        if (! baseUrlInitialized) {
          // Tell TinyMCE about base URL, which it can't guess in combined resources

          val href = containerElem.querySelector(".tinymce-base-url").getAttribute("href")
          // Remove the magic number and extension at the end of the URL. The magic number was added to allow for
          // URL post-processing for portlets. The extension is added so that the version number is added to the URL.
          val baseURL = href.substring(0, href.length - "1b713b2e6d7fd45753f4b8a6270b776e.js".length)
          GlobalTinyMce.baseURL = baseURL

          baseUrlInitialized = true
        }

        def fromDataAtt: Option[TinyMceConfig] =
          containerElem.querySelectorT(".tinymce-base-url")
            .dataset
            .get("tinymceConfig")
            .flatMap(_.trimAllToOpt)
            .map(js.JSON.parse(_).asInstanceOf[TinyMceConfig])

        val tinyMceConfig =
          GlobalJsTinyMceCustomConfig
            .orElse(fromDataAtt)
            .getOrElse(TinyMceDefaultConfig)

        // Without this, with `combine-resources` set to `false`, instead of `silver/theme.min.js`,
        // TinyMCE tried to load `silver/theme.js`, which doesn't exist
        tinyMceConfig.suffix      = ".min"

        val tinyMceDiv = containerElem.querySelector(".xbl-fr-tinymce-div")
        val tinyMceObject = new TinyMceEditor(tinyMceDiv.id, tinyMceConfig, GlobalTinyMce.EditorManager)
        tinyMceObjectOpt = Some(tinyMceObject)

        val isReadonly = containerElem.classList.contains("xforms-readonly")
        xformsUpdateReadonly(isReadonly)

        withInitializedTinyMce { tinyMceObject =>

          // Send value to the server on blur, as well as on Ctrl+Enter or Cmd+Enter
          tinyMceObject.on("blur", _ => clientToServer())
          EventSupport.addListener[dom.KeyboardEvent](
            tinyMceObject.getBody(),
            DomEventNames.KeyDown,
            (e: dom.KeyboardEvent) => if (e.key == "Enter" && (e.metaKey || e.ctrlKey)) clientToServer()
          )
          // Remove an anchor added by TinyMCE to handle key, as it grabs the focus and breaks tabbing between fields
          $(containerElem).find("a[accesskey]").detach()
          tinyMceInitialized = true
          Events.componentChangedLayoutEvent.fire()
        }

        // - Unfortunately, we need to use polling; we can't rely on an Ajax response, e.g. if in Bootstrap tab as in
        //   the Form Builder Control Settings dialog
        // - As `destroy()` isn't called when the component is a repeat, which is the case for the Form Builder Control
        //   Settings dialog, we stop trying to render if we notice `containerElem` isn't in the document anymore
        def renderIfVisible(): Unit =
          if (document.getElementById(containerElem.id) == containerElem)
            if ($(tinyMceDiv).is(":visible")) {
              tinyMceObject.render()
            } else {
              val shortDelay = Page.getXFormsFormFromHtmlElemOrThrow(containerElem).configuration.internalShortDelay
              js.timers.setTimeout(shortDelay)(renderIfVisible())
            }
        renderIfVisible()
      }
    }

    override def destroy(): Unit = {
      EventSupport.clearAllListeners()
      tinyMceObjectOpt foreach { _ =>
        // TODO: How to clean-up? API is unclear. `remove()`?
      }
    }

    // Send value in MCE to server
    private def clientToServer(): Unit =
      tinyMceObjectOpt foreach { tinyMceObject =>
        // https://github.com/orbeon/orbeon-forms/issues/5963
        if (containerElem.closestOpt("form").isDefined) {
          val rawContent = tinyMceObject.getContent()
          // Workaround to TinyMCE issue, see https://twitter.com/avernet/status/579031182605750272
          val cleanedContent = if (rawContent == "<div>\u00a0</div>") "" else rawContent
          DocumentAPI.setValue(containerElem, cleanedContent)
        }
      }

    // TinyMCE got the focus
    private def onFocus(event: js.Dynamic): Unit = {
      // From the perspective of the XForms engine, the focus is on the XBL component
      event.target = containerElem
      // Forward to the "XForms engine"
      Events.focus.asInstanceOf[js.Function1[js.Dynamic, Unit]](event)
    }

    private def hasFocus(): Boolean = {
      val activeElement                   = dom.document.activeElement
      val focusInsideComponent            = containerElem.contains(activeElement)
      val focusOnAbsolutelyPositionedMenu = $(activeElement).parent(".mceListBoxMenu").is("*")
      focusInsideComponent || focusOnAbsolutelyPositionedMenu
    }

    override def xformsGetValue(): String =
      tinyMceObjectOpt map { tinyMceObject =>
        val rawContent = tinyMceObject.getContent()
        // Workaround to TinyMCE issue, see https://twitter.com/avernet/status/579031182605750272
        if (rawContent == "<div>\u00a0</div>") "" else rawContent
      } getOrElse ""

    override def xformsUpdateValue(newValue: String): UndefOr[js.Promise[Unit] | JQueryPromise[js.Function1[js.Any, js.Any], js.Any]] = {
      val promise = Promise[Unit]()
      withInitializedTinyMce { tinyMceObject =>
        if (! hasFocus()) { // Heuristic: if TinyMCE has focus, users might still be editing so don't update
          tinyMceObject.setContent(newValue)
          promise.success(())
        } else {
          // TODO: What in this case? `promise.Failure()`?
        }
      }
      promise.future.toJSPromise.asInstanceOf[js.UndefOr[js.Promise[Unit] | JQueryPromise[js.Function1[js.Any, js.Any], js.Any]]] // HACK: otherwise this doesn't compile with `-Xsource-features:v2.13.14`
    }

    override def xformsFocus(): Unit =
      withInitializedTinyMce { tinyMceObject =>
        tinyMceObject.focus()
      }

    override def xformsUpdateReadonly(readonly: Boolean): Unit =
      withInitializedTinyMce { tinyMceObject =>
        tinyMceObject.getBody().contentEditable = (! readonly).toString
        tinyMceObject.mode.set(if (readonly) "readonly" else "design")
      }

    // trait TinyMceInit extends js.Object {
    //  var tinyMceInitialized: Boolean
    //  var tinyMceObjectOpt  : Option[TinyMceEditor]

    def withInitializedTinyMce(thunk: TinyMceEditor => Unit): Unit =
      if (tinyMceInitialized)
        tinyMceObjectOpt foreach thunk
      else
        tinyMceObjectOpt foreach { tinyMceObject =>
          tinyMceObject.on("init", _ => {
            tinyMceInitialized = true
            thunk(tinyMceObject)
          })
        }
  }
}