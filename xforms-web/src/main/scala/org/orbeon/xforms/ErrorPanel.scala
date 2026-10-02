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

import org.orbeon.facades.HTMLDialogElement
import org.orbeon.web.DomSupport.*
import org.orbeon.xforms
import org.scalajs.dom
import org.scalajs.dom.html


object ErrorPanel {

  import Private.*

  def initializeErrorPanel(formElem: html.Form): Option[HTMLDialogElement] = {

    // See `error-dialog.xml` for the expected layout of the HTML

    // We support multiple error panels, try to find one with a `lang` attribute that matches the language of the form,
    // and if we can't find one, use the error panel we find
    val allErrorPanelsNode           = formElem.querySelectorAll(".xforms-error-dialogs > .xforms-error-panel")
    val allErrorPanelsElements       = allErrorPanelsNode.to(List).asInstanceOf[List[HTMLDialogElement]]
    val formLang                     = dom.document.firstElementChild.getAttribute("lang")
    val panelElemWithMatchingLangOpt = allErrorPanelsElements.find(_.getAttribute("lang") == formLang)
    val panelElemOpt                 = panelElemWithMatchingLangOpt.orElse(allErrorPanelsElements.headOption)

    panelElemOpt map { dialogElem =>

      // When the error dialog is closed, we make sure that the "details" section is closed,
      // so it will be closed the next time the dialog is opened.
      dialogElem.addEventListener(
        `type`   = "close",
        listener = (_: dom.Event) => toggleDetails(dialogElem, show = false)
      )

      dialogElem.querySelectorOpt(".xforms-error-panel-show-details").foreach(_.addEventListener(
        `type`   = "click",
        listener = (event: dom.Event) => {
          event.preventDefault()
          toggleDetails(dialogElem, show = true)
        }
      ))

      dialogElem.querySelectorOpt(".xforms-error-panel-hide-details").foreach(_.addEventListener(
        `type`   = "click",
        listener = (event: dom.Event) => {
          event.preventDefault()
          toggleDetails(dialogElem, show = false)
        }
      ))

      dialogElem.querySelectorAllT(".xforms-error-panel-close").foreach(_.addEventListener(
        `type`   = "click",
        listener = (event: dom.Event) => {
          event.preventDefault()
          dialogElem.close()
        }
      ))

      dialogElem.querySelectorOpt(".xforms-error-panel-reload").foreach(_.addEventListener(
        `type`   = "click",
        listener = (event: dom.Event) => {
          event.preventDefault()
          dom.window.location.reload()
        }
      ))

      dialogElem
    }
  }

  def showError(currentForm: xforms.Form, detailsOrNull: String): Unit = {

    val dialogElem = currentForm.errorPanel

    Option(detailsOrNull) match {
      case Some(details) =>
        dialogElem.querySelectorT(".xforms-error-panel-details").innerHTML = details
        toggleDetails(dialogElem, show = true)
      case None =>
        dialogElem.querySelectorT(".xforms-error-panel-details-hidden").classList.add("xforms-disabled")
        dialogElem.querySelectorT(".xforms-error-panel-details-shown").classList.add("xforms-disabled")
    }

    dialogElem.showModal()

    // Focus within the dialog so that screen readers handle aria attributes
    dialogElem.querySelectorOpt(".container-close").foreach(_.focus())
  }

  private object Private {

    def toggleDetails(errorPanelElem: html.Element, show: Boolean): Unit = {
      errorPanelElem.querySelectorT(".xforms-error-panel-details-hidden").toggleClass("xforms-disabled", show)
      errorPanelElem.querySelectorT(".xforms-error-panel-details-shown").toggleClass("xforms-disabled",  ! show)
    }
  }
}
