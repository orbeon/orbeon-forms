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

import org.orbeon.facades.TinyMce._
import org.orbeon.xforms.facade.{Events, XBL, XBLCompanion}
import org.orbeon.xforms.{$, DocumentAPI, Page}
import org.scalajs.dom
import org.scalajs.dom.html
import org.scalajs.jquery.JQueryPromise
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits._
import org.orbeon.polyfills.HTMLPolyfills._

import scala.concurrent.Promise
import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import scala.scalajs.js.{UndefOr, |}


object TinyMCE {

  var baseUrlInitialized = false

  XBL.declareCompanion("fr|tinymce", js.constructorOf[TinyMCECompanion])

  private class TinyMCECompanion(containerElem: html.Element) extends XBLCompanion {

    var tinyMceObjectOpt  : Option[TinyMceEditor] = None
    var tinyMceInitialized: Boolean               = false

    override def init(): Unit = {

      val isStaticReadonly = containerElem.querySelector(".xbl-fr-tinymce-div .xforms-output-output") ne null

      if (! isStaticReadonly) {

        if (! baseUrlInitialized) {
          // Tell TinyMCE about base URL, which it can't guess in combined resources

          val href = dom.document.querySelector(".tinymce-base-url").getAttribute("href")
          // Remove the magic number and extension at the end of the URL. The magic number was added to allow for
          // URL post-processing for portlets. The extension is added so that the version number is added to the URL.
          val baseURL = href.substring(0, href.length - "1b713b2e6d7fd45753f4b8a6270b776e.js".length)
          GlobalTinyMce.baseURL = baseURL

          baseUrlInitialized = true
        }

        val tinyMceConfig = TinyMceCustomConfig.getOrElse(TinyMceDefaultConfig)

        // Force these important settings
        tinyMceConfig.inline       = true
        tinyMceConfig.hidden_input = false

        // Without this, with `combine-resources` set to `false`, instead of `silver/theme.min.js`,
        // TinyMCE tried to load `silver/theme.js`, which doesn't exist
        tinyMceConfig.suffix      = ".min"

        val tinyMceDiv = containerElem.querySelector(".xbl-fr-tinymce-div")
        val tinyMceObject = new TinyMceEditor(tinyMceDiv.id, tinyMceConfig, GlobalTinyMce.EditorManager)
        tinyMceObjectOpt = Some(tinyMceObject)

        val isReadonly = containerElem.classList.contains("xforms-readonly")
        xformsUpdateReadonly(isReadonly)

        withInitializedTinyMce { tinyMceObject =>

          // Send value to the server on blur
          tinyMceObject.on("blur", _ => clientToServer())
          // Remove an anchor added by TinyMCE to handle key, as it grabs the focus and breaks tabbing between fields
          $(containerElem).find("a[accesskey]").detach()
          tinyMceInitialized = true
          Events.componentChangedLayoutEvent.fire()
        }

        // Render the component when visible (see https://github.com/orbeon/orbeon-forms/issues/172)
        // - unfortunately, we need to use polling can't use Ajax response e.g. if in Bootstrap tab, as
        //   in FB Control Settings dialog
        def renderIfVisible(): Unit = {
          if ($(tinyMceDiv).is(":visible")) {
            tinyMceObject.render()
          } else {
            val shortDelay = Page.getXFormsFormFromHtmlElemOrThrow(containerElem).configuration.internalShortDelay
            js.timers.setTimeout(shortDelay)(renderIfVisible())
          }
        }
        renderIfVisible()
      }
    }

    override def destroy(): Unit =
      tinyMceObjectOpt foreach { tinyMceObject =>
        // TODO: How to clean-up? API is unclear. `remove()`?
      }

    // Send value in MCE to server
    private def clientToServer(): Unit =
      tinyMceObjectOpt foreach { tinyMceObject =>
        // https://github.com/orbeon/orbeon-forms/issues/5963
        if (containerElem.closest("form").isDefined) {
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

    override def xformsUpdateValue(newValue: String): UndefOr[js.Promise[Unit] | JQueryPromise] = {
      val promise = Promise[Unit]()
      withInitializedTinyMce { tinyMceObject =>
        if (! hasFocus()) { // Heuristic: if TinyMCE has focus, users might still be editing so don't update
          tinyMceObject.setContent(newValue)
          promise.success(())
        } else {
          // TODO: What in this case? `promise.Failure()`?
        }
      }
      promise.future.toJSPromise
    }

    override def xformsFocus(): Unit =
      withInitializedTinyMce { tinyMceObject =>
        tinyMceObject.focus()
      }

    override def xformsUpdateReadonly(readonly: Boolean): Unit =
      withInitializedTinyMce { tinyMceObject =>
        tinyMceObject.getBody().contentEditable = (! readonly).toString
        tinyMceObject.setMode(if (readonly) "readonly" else "design")
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