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

import org.orbeon.jquery._
import org.orbeon.xforms.facade.Utils
import org.scalajs.dom
import org.scalajs.dom.html
import org.scalajs.jquery.JQueryEventObject

import scala.scalajs.js
import scala.scalajs.js.Dynamic.{newInstance, global => g}

object ErrorPanel {

  import Private._

  def initializeErrorPanel(formElem: html.Form): Option[js.Object] = {

    // Expected layout of the HTML:
    //
    // <div class="xforms-error-dialogs">
    //   <div class="xforms-error-panel xforms-initially-hidden">
    //       <div class="hd" id="error-dialog-title"/>
    //       <div class="bd">
    //           <ul>
    //               <li><a class="xforms-error-panel-close">Close this dialog</a> etc.</li>
    //               <li><a class="xforms-error-panel-reload">Reload this page</a> etc.</li>
    //           </ul>
    //           <div class="xforms-error-panel-details-hidden">
    //               <p>
    //                   <a class="xforms-error-panel-show-details">
    //                       <img src="/ops/images/xforms/section-closed.png" alt="Show Details"/>
    //                       <span>Show details</span>
    //                   </a>
    //               </p>
    //           </div>
    //           <div class="xforms-error-panel-details-shown xforms-disabled">
    //               <p>
    //                   <a class="xforms-error-panel-hide-details">
    //                       <img src="/ops/images/xforms/section-opened.png" alt="Hide Details"/>
    //                       <span>Hide details</span>
    //                   </a>
    //               </p>
    //               <div class="xforms-error-panel-details"/>
    //           </div>
    //       </div>
    //   </div>
    // </div>

    val panelElemOrUndef: js.UndefOr[html.Element] =
      $(formElem).find(".xforms-error-dialogs > .xforms-error-panel")(0)

    panelElemOrUndef.toOption map { panelElem =>

      val jPanelElem = $(panelElem)

      jPanelElem.removeClass(Constants.InitiallyHiddenClass)

      val panel = newInstance(g.YAHOO.widget.Panel)(panelElem, new js.Object {
        val modal               = true
        val fixedcenter         = false
        val underlay            = "shadow"
        val visible             = false
        val constraintoviewport = true
        val draggable           = true
      })

      panel.render()

      Utils.overlayUseDisplayHidden(panel)

      // When the error dialog is closed, we make sure that the "details" section is closed,
      // so it will be closed the next time the dialog is opened.
      panel.beforeHideEvent.subscribe(
        ((_: js.Any) => toggleDetails(panelElem, show = false)): js.Function
      )

      jPanelElem.onWithSelector(
        events   = "click",
        selector = ".xforms-error-panel-show-details",
        handler  = (_: JQueryEventObject) => toggleDetails(panelElem, show = true)
      )

      jPanelElem.onWithSelector(
        events   = "click",
        selector = ".xforms-error-panel-hide-details",
        handler  = (_: JQueryEventObject) => toggleDetails(panelElem, show = false)
      )

      jPanelElem.onWithSelector(
        events   = "click",
        selector = ".xforms-error-panel-close",
        handler  = (_: JQueryEventObject) => panel.hide()
      )

      jPanelElem.onWithSelector(
        events   = "click",
        selector = ".xforms-error-panel-reload",
        handler  = (_: JQueryEventObject) => dom.window.location.reload(flag = true)
      )

      panel
    }
  }

  def showError(formId: String, detailsOrNull: String): Unit = {
    val formErrorPanel = Page.getForm(formId).errorPanel.asInstanceOf[js.Dynamic]

    val jErrorPanelElem = $(formErrorPanel.element)

    jErrorPanelElem.css("display: block")

    Option(detailsOrNull) match {
      case Some(details) =>
        jErrorPanelElem.find(".xforms-error-panel-details").html(details)
        toggleDetails(jErrorPanelElem(0), show = true)
      case None =>
        jErrorPanelElem.find(".xforms-error-panel-details-hidden").addClass("xforms-disabled")
        jErrorPanelElem.find(".xforms-error-panel-details-shown").addClass("xforms-disabled")
    }

    formErrorPanel.show()
    Globals.lastDialogZIndex += 2
    formErrorPanel.cfg.setProperty("zIndex", Globals.lastDialogZIndex)
    formErrorPanel.center()

    // Focus within the dialog so that screen readers handle aria attributes
    jErrorPanelElem.find(".container-close").focus()
  }

  private object Private {

    def toggleDetails(errorPanelElem: html.Element, show: Boolean): Unit = {

      val jErrorPanelElem = $(errorPanelElem)

      jErrorPanelElem.find(".xforms-error-panel-details-hidden").toggleClass("xforms-disabled", show)
      jErrorPanelElem.find(".xforms-error-panel-details-shown").toggleClass("xforms-disabled", ! show)
    }
  }
}
