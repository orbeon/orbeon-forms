/**
 * Copyright (C) 2019 Orbeon, Inc.
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
package org.orbeon.xforms

import org.orbeon.web.DomSupport.*
import org.orbeon.xforms
import org.scalajs.dom
import org.scalajs.dom.html

import scala.scalajs.js
import scala.scalajs.js.Dynamic.{newInstance, global as g}


object ErrorPanel {

  import Private.*

  def initializeErrorPanel(formElem: html.Form): Option[js.Object] = {

    // See `error-dialog.xml` for the expected layout of the HTML

    // We support multiple error panels, try to find one with a `lang` attribute that matches the language of the form,
    // and if we can't find one, use the error panel we find
    val allErrorPanelsNode           = formElem.querySelectorAll(".xforms-error-dialogs > .xforms-error-panel")
    val allErrorPanelsElements       = allErrorPanelsNode.to(List).asInstanceOf[List[html.Element]]
    val formLang                     = dom.document.firstElementChild.getAttribute("lang")
    val panelElemWithMatchingLangOpt = allErrorPanelsElements.find(_.getAttribute("lang") == formLang)
    val panelElemOpt                 = panelElemWithMatchingLangOpt.orElse(allErrorPanelsElements.headOption)

    panelElemOpt map { panelElem =>

      panelElem.classList.remove(Constants.InitiallyHiddenClass)

      val panel = newInstance(g.YAHOO.widget.Panel)(panelElem, new js.Object {
        val modal               = true
        val fixedcenter         = false
        val underlay            = "shadow"
        val visible             = false
        val constraintoviewport = true
        val draggable           = true
      })

      panel.render()

      overlayUseDisplayHidden(panel)

      // When the error dialog is closed, we make sure that the "details" section is closed,
      // so it will be closed the next time the dialog is opened.
      panel.beforeHideEvent.subscribe(
        ((_: js.Any) => toggleDetails(panelElem, show = false)): js.Function
      )

      Option(panelElem.querySelector(".xforms-error-panel-show-details")).foreach(_.addEventListener(
        `type` = "click",
        listener = (_: dom.Event) => toggleDetails(panelElem, show = true)
      ))

      Option(panelElem.querySelector(".xforms-error-panel-hide-details")).foreach(_.addEventListener(
        `type` = "click",
        listener = (_: dom.Event) => toggleDetails(panelElem, show = false)
      ))

      Option(panelElem.querySelector(".xforms-error-panel-close")).foreach(_.addEventListener(
        `type` = "click",
        listener = (_: dom.Event) => panel.hide()
      ))

      Option(panelElem.querySelector(".xforms-error-panel-reload")).foreach(_.addEventListener(
        `type` = "click",
        listener = (_: dom.Event) => dom.window.location.reload()
      ))

      panel
    }
  }

  private def overlayUseDisplayHidden(overlay: js.Dynamic): Unit = {
    overlay.element.style.display = "none"
    // For why use subscribers.unshift instead of subscribe, see:
    // http://wiki.orbeon.com/forms/projects/ui/mobile-and-tablet-support#TOC-Avoiding-scroll-when-showing-a-mess
    overlay.beforeShowEvent.subscribers.unshift(
      newInstance(g.YAHOO.util.Subscriber)((() => {
          overlay.element.style.display = "block"
        }): js.Function)
    )
    overlay.beforeHideEvent.subscribe((() => {
        overlay.element.style.display = "none"
      }): js.Function
    )
  }

  def showError(currentForm: xforms.Form, detailsOrNull: String): Unit = {

    val formErrorPanel  = currentForm.errorPanel.asInstanceOf[js.Dynamic]
    val errorPanelElem  = formErrorPanel.element.asInstanceOf[html.Element]

    errorPanelElem.style.display = "block"

    Option(detailsOrNull) match {
      case Some(details) =>
        errorPanelElem.querySelectorT(".xforms-error-panel-details").innerHTML = details
        toggleDetails(errorPanelElem, show = true)
      case None =>
        errorPanelElem.querySelectorT(".xforms-error-panel-details-hidden").classList.add("xforms-disabled")
        errorPanelElem.querySelectorT(".xforms-error-panel-details-shown").classList.add("xforms-disabled")
    }

    formErrorPanel.show()
    Globals.lastDialogZIndex += 2
    formErrorPanel.cfg.setProperty("zIndex", Globals.lastDialogZIndex)
    formErrorPanel.center()

    // Focus within the dialog so that screen readers handle aria attributes
    errorPanelElem.querySelectorT(".container-close").focus()
  }

  private object Private {

    def toggleDetails(errorPanelElem: html.Element, show: Boolean): Unit =
      if (show) {
        errorPanelElem.querySelectorT(".xforms-error-panel-details-hidden").classList.add("xforms-disabled")
        errorPanelElem.querySelectorT(".xforms-error-panel-details-shown").classList.remove("xforms-disabled")
      } else {
        errorPanelElem.querySelectorT(".xforms-error-panel-details-hidden").classList.remove("xforms-disabled")
        errorPanelElem.querySelectorT(".xforms-error-panel-details-shown").classList.add("xforms-disabled")
      }
  }
}
