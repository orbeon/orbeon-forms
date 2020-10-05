/**
 * Copyright (C) 2020 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.xforms

import org.orbeon.xforms.facade.{Controls, Utils}
import org.scalajs.dom
import org.scalajs.dom.ext._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.scalajs.js
import scala.scalajs.js.Dynamic.{newInstance, global => g}
import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}
import scala.scalajs.js.timers
import scala.scalajs.js.timers.SetTimeoutHandle


// Progressively migrate contents of xforms.js here
@JSExportTopLevel("OrbeonXFormsUi")
object XFormsUI {

  @JSExport // 2020-04-27: 6 JavaScript usages
  var modalProgressPanelShown: Boolean = false

  @JSExport // 2020-04-27: 1 JavaScript usage
  def displayModalProgressPanel(): Unit =
    if (! modalProgressPanelShown) {

      modalProgressPanelShown = true

      // Take out the focus from the current control
      // See https://github.com/orbeon/orbeon-forms/issues/4511
      val focusControlIdOpt =
        Option(Globals.currentFocusControlId) map { focusControlId =>
          Controls.removeFocus(focusControlId)
          focusControlId
        }

      val timerIdOpt =
        if (Utils.isIOS && Utils.getZoomLevel() != 1.0) {
          Utils.resetIOSZoom()
            Some(
              timers.setTimeout(200.milliseconds) {
                Private.panel.show()
              }
            )
        } else {
          Private.panel.show()
          None
        }

      AjaxClient.ajaxResponseReceivedForCurrentEventQueueF("modal panel") foreach { details =>

        // Hide the modal progress panel, unless the server tells us to do a submission or load, so we don't want
        // to remove it otherwise users could start interacting with a page which is going to be replaced shortly.
        //
        // We remove the modal progress panel before handling DOM response, as script actions may dispatch
        // events and we don't want them to be filtered. If there are server events, we don't remove the
        // panel until they have been processed, i.e. the request sending the server events returns.
        val mustHideProgressDialog =
          ! (
            // `exists((//xxf:submission, //xxf:load)[empty(@target) and empty(@show-progress)])`
            details.responseXML.getElementsByTagNameNS(Namespaces.XXF, "submission").iterator ++
              details.responseXML.getElementsByTagNameNS(Namespaces.XXF, "load").iterator exists
              (e => ! e.hasAttribute("target") && e.getAttribute("show-progress") != "false")
          )

        if (mustHideProgressDialog)
          Private.hideModalProgressPanel(timerIdOpt, focusControlIdOpt)
      }
    }

  private object Private {

    lazy val panel: js.Dynamic = {

      val panel =
        newInstance(g.YAHOO.widget.Panel)(
          Page.namespaceIdIfNeeded(Support.formElemOrDefaultForm(js.undefined).id, "orbeon-spinner"),
          new js.Object {
            val modal       = true
            val fixedcenter = true
            val visible     = true
            val draggable   = false
            val width       = "60px"
            val close       = false
            val zindex      = 4
          }
        )

      panel.setBody("""<div class="xforms-modal-progress"/>""")
      panel.render(dom.document.body)

      panel
    }

    def hideModalProgressPanel(
      timerIdOpt        : Option[SetTimeoutHandle],
      focusControlIdOpt : Option[String]
    ): Unit =
      if (modalProgressPanelShown) {

        modalProgressPanelShown = false

        // So that the modal progress panel doesn't show just after we try to hide it
        timerIdOpt foreach timers.clearTimeout

        Private.panel.hide()

        // Restore focus
        // See https://github.com/orbeon/orbeon-forms/issues/4511
        focusControlIdOpt foreach Controls.setFocus
      }
  }
}
