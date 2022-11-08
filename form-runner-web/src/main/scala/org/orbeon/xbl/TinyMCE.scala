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
import org.orbeon.xforms.{$, AjaxClient, AjaxEvent, DocumentAPI, Page}
import org.scalajs.dom
import org.scalajs.dom.html
import org.scalajs.dom.raw
import org.scalajs.jquery.{JQueryEventObject, JQueryPromise}

import scala.scalajs.js
import scala.scalajs.js.{Promise, UndefOr, |}

object TinyMCE {

  var baseUrlInitialized = false

  XBL.declareCompanion(
    "fr|tinymce",
    new XBLCompanion {

      var myEditor             : TinyMceEditor = _
      var tinymceInitialized   : Boolean       = false

      override def init(): Unit = {

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

        // Without this, with `combine-resources` set to `false`, instead of `silver/theme.min.js`,
        // TinyMCE tried to load `silver/theme.js`, which doesn't exist
        tinyMceConfig.suffix      = ".min"
        tinyMceConfig.content_css = GlobalTinyMce.baseURL + "/../../content.css"

        val tinyMceDiv = containerElem.querySelector(".xbl-fr-tinymce-div")
        val tabindex = tinyMceDiv.getAttribute("tabindex")
        myEditor = new TinyMceEditor(tinyMceDiv.id, tinyMceConfig, GlobalTinyMce.EditorManager)

        val isReadonly = containerElem.classList.contains("xforms-readonly")
        xformsUpdateReadonly(isReadonly)

        onInit(() => {

          // Send value to the server on blur
          myEditor.on("blur", _ => clientToServer())
          // Remove an anchor added by TinyMCE to handle key, as it grabs the focus and breaks tabbing between fields
          $(containerElem).find("a[accesskey]").detach()
          val iframe = $(containerElem).find("iframe")
          // On click inside the iframe, propagate the click outside, so code listening on click on an ancestor gets called
          iframe.contents().on("click", (_: JQueryEventObject) => containerElem.click())
          $(iframe.get(0).asInstanceOf[raw.HTMLIFrameElement].contentWindow).on("focus", onFocus _)
          // Copy the tabindex on the iframe
          if (tabindex != null && tabindex != "") iframe.attr("tabindex", tabindex)
          tinymceInitialized = true
          Events.componentChangedLayoutEvent.fire()
        })

        // Render the component when visible (see https://github.com/orbeon/orbeon-forms/issues/172)
        // - unfortunately, we need to use polling can't use Ajax response e.g. if in Bootstrap tab, as
        //   in FB Control Settings dialog
        def renderIfVisible(): Unit = {
          if ($(tinyMceDiv).is(":visible")) {
            myEditor.render()
          } else {
            val shortDelay = Page.getFormFromElemOrThrow(containerElem).configuration.internalShortDelay
            js.timers.setTimeout(shortDelay)(renderIfVisible())
          }
        }
        renderIfVisible()
      }

      // Send value in MCE to server
      private def clientToServer(): Unit = {
        val rawContent = myEditor.getContent()
        // Workaround to TinyMCE issue, see https://twitter.com/avernet/status/579031182605750272
        val cleanedContent = if (rawContent == "<div>\u00a0</div>") "" else rawContent
        DocumentAPI.setValue(containerElem, cleanedContent)
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

      // Runs a function when the TinyMCE is initialized
      private def onInit(thunk: () => Unit): Unit = {
        if (tinymceInitialized) thunk()
        else myEditor.on("init", _ => thunk())
      }

      override def xformsGetValue(): String = {
        val rawContent = myEditor.getContent()
        // Workaround to TinyMCE issue, see https://twitter.com/avernet/status/579031182605750272
        if (rawContent == "<div>\u00a0</div>") "" else rawContent
      }

      override def xformsUpdateValue(newValue: String): UndefOr[Promise[Unit] | JQueryPromise] = {
        if (! hasFocus()) // Heuristic: if TinyMCE has focus, users might still be editing so don't update
          onInit(() => myEditor.setContent(newValue))
        js.undefined
      }

      override def xformsFocus(): Unit = { onInit(myEditor.focus) }

      override def xformsUpdateReadonly(readonly: Boolean): Unit = {
        onInit(() => {
          myEditor.getBody().contentEditable = (! readonly).toString
          myEditor.setMode(if (readonly) "readonly" else "design")
        })
      }
    }
  )
}
