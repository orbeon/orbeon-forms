/**
 * Copyright (C) 2026 Orbeon, Inc.
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
import org.scalajs.dom
import org.scalajs.dom.html
import org.scalatest.funspec.AnyFunSpecLike

import scala.scalajs.js


class ErrorPanelTest extends AnyFunSpecLike {

  // JSDOM does not implement showModal/close natively, so mock them if needed
  private def setupDialogMocks(): Unit = {
    val dialogProto = js.Dynamic.global.HTMLDialogElement.prototype
    if (js.isUndefined(dialogProto.showModal)) {
      dialogProto.showModal = ({ (thisDialog: js.Dynamic) =>
        thisDialog.open = true
      }: js.ThisFunction0[js.Dynamic, Unit])
    }
    if (js.isUndefined(dialogProto.close)) {
      dialogProto.close = ({ (thisDialog: js.Dynamic) =>
        thisDialog.open = false
        val evt = new dom.Event("close")
        thisDialog.dispatchEvent(evt)
      }: js.ThisFunction0[js.Dynamic, Unit])
    }
  }

  private def createFormWithDialogs(formLang: String, dialogLangs: List[Option[String]]): html.Form = {
    dom.document.firstElementChild.setAttribute("lang", formLang)
    val form = dom.document.createElement("form").asInstanceOf[html.Form]
    form.id = "xf-0"
    val container = dom.document.createElement("div").asInstanceOf[html.Div]
    container.className = "xforms-error-dialogs"
    form.appendChild(container)

    for ((langOpt, idx) <- dialogLangs.zipWithIndex) {
      val dialog = dom.document.createElement("dialog").asInstanceOf[HTMLDialogElement]
      dialog.className = "xforms-error-panel xforms-dialog"
      langOpt.foreach(dialog.setAttribute("lang", _))
      dialog.id = s"error-dialog-$idx"

      val head = dom.document.createElement("div").asInstanceOf[html.Div]
      head.className = "xxforms-dialog-head"
      val closeBtn = dom.document.createElement("a").asInstanceOf[html.Anchor]
      closeBtn.className = "container-close xforms-error-panel-close"
      head.appendChild(closeBtn)
      dialog.appendChild(head)

      val body = dom.document.createElement("div").asInstanceOf[html.Div]
      body.className = "xxforms-dialog-body"

      val showDetails = dom.document.createElement("a").asInstanceOf[html.Anchor]
      showDetails.className = "xforms-error-panel-show-details"
      val detailsHidden = dom.document.createElement("div").asInstanceOf[html.Div]
      detailsHidden.className = "xforms-error-panel-details-hidden"
      detailsHidden.appendChild(showDetails)
      body.appendChild(detailsHidden)

      val hideDetails = dom.document.createElement("a").asInstanceOf[html.Anchor]
      hideDetails.className = "xforms-error-panel-hide-details"
      val detailsShown = dom.document.createElement("div").asInstanceOf[html.Div]
      detailsShown.className = "xforms-error-panel-details-shown xforms-disabled"
      val detailsContent = dom.document.createElement("div").asInstanceOf[html.Div]
      detailsContent.className = "xforms-error-panel-details"
      detailsShown.appendChild(hideDetails)
      detailsShown.appendChild(detailsContent)
      body.appendChild(detailsShown)

      val closeLink = dom.document.createElement("a").asInstanceOf[html.Anchor]
      closeLink.className = "xforms-error-panel-close"
      body.appendChild(closeLink)

      val reloadLink = dom.document.createElement("a").asInstanceOf[html.Anchor]
      reloadLink.className = "xforms-error-panel-reload"
      body.appendChild(reloadLink)

      dialog.appendChild(body)
      container.appendChild(dialog)
    }

    form
  }

  describe("ErrorPanel initialization") {

    setupDialogMocks()

    it("must select the dialog matching the form language") {
      val form = createFormWithDialogs("fr", List(Some("en"), Some("fr"), Some("es")))
      val dialogOpt = ErrorPanel.initializeErrorPanel(form)
      assert(dialogOpt.isDefined)
      assert(dialogOpt.get.id == "error-dialog-1")
    }

    it("must fallback to the first dialog if no matching language is found") {
      val form = createFormWithDialogs("de", List(Some("en"), Some("fr")))
      val dialogOpt = ErrorPanel.initializeErrorPanel(form)
      assert(dialogOpt.isDefined)
      assert(dialogOpt.get.id == "error-dialog-0")
    }

    it("must return None if no error dialog is present") {
      val form = dom.document.createElement("form").asInstanceOf[html.Form]
      assert(ErrorPanel.initializeErrorPanel(form).isEmpty)
    }
  }

  describe("ErrorPanel interaction") {

    setupDialogMocks()

    it("must toggle details section when clicking show and hide details") {
      val form = createFormWithDialogs("en", List(Some("en")))
      val dialog = ErrorPanel.initializeErrorPanel(form).get

      val detailsHidden = dialog.querySelectorT(".xforms-error-panel-details-hidden")
      val detailsShown  = dialog.querySelectorT(".xforms-error-panel-details-shown")
      val showDetails   = dialog.querySelectorT(".xforms-error-panel-show-details").asInstanceOf[html.Element]
      val hideDetails   = dialog.querySelectorT(".xforms-error-panel-hide-details").asInstanceOf[html.Element]

      assert(! detailsHidden.hasClass("xforms-disabled"))
      assert(detailsShown.hasClass("xforms-disabled"))

      // Click show details
      showDetails.click()
      assert(detailsHidden.hasClass("xforms-disabled"))
      assert(! detailsShown.hasClass("xforms-disabled"))

      // Click hide details
      hideDetails.click()
      assert(! detailsHidden.hasClass("xforms-disabled"))
      assert(detailsShown.hasClass("xforms-disabled"))
    }

    it("must reset details section when dialog closes") {
      val form = createFormWithDialogs("en", List(Some("en")))
      val dialog = ErrorPanel.initializeErrorPanel(form).get

      val detailsHidden = dialog.querySelectorT(".xforms-error-panel-details-hidden")
      val detailsShown  = dialog.querySelectorT(".xforms-error-panel-details-shown")
      val showDetails   = dialog.querySelectorT(".xforms-error-panel-show-details").asInstanceOf[html.Element]

      // Show details
      showDetails.click()
      assert(! detailsShown.hasClass("xforms-disabled"))

      // Close the dialog
      dialog.close()
      assert(detailsHidden.hasClass("xforms-disabled") == false)
      assert(detailsShown.hasClass("xforms-disabled") == true)
    }

    it("must close the dialog when clicking any close button") {
      val form = createFormWithDialogs("en", List(Some("en")))
      val dialog = ErrorPanel.initializeErrorPanel(form).get

      dialog.showModal()
      assert(dialog.open)

      val closeBtn = dialog.querySelectorT(".container-close").asInstanceOf[html.Element]
      closeBtn.click()
      assert(! dialog.open)

      // Test the other close link in body
      dialog.showModal()
      assert(dialog.open)

      val closeLinks = dialog.querySelectorAllT(".xforms-error-panel-close")
      assert(closeLinks.size >= 2)
      closeLinks.last.asInstanceOf[html.Element].click()
      assert(! dialog.open)
    }
  }
}
