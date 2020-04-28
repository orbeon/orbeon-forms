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

import cats.syntax.option._
import org.orbeon.xforms.facade.{Controls, Utils}
import org.scalajs.dom

import scala.concurrent.duration._
import scala.scalajs.js
import scala.scalajs.js.Dynamic.{newInstance, global => g}
import scala.scalajs.js.annotation.JSExportTopLevel
import scala.scalajs.js.timers
import scala.scalajs.js.timers.SetTimeoutHandle

// Progressively migrate contents of xforms.js here
object XFormsUI {

  @JSExportTopLevel("ORBEON.xforms.Globals.modalProgressPanelShown")
  var modalProgressPanelShown: Boolean = false

  @JSExportTopLevel("ORBEON.util.Utils.displayModalProgressPanel") // 2020-04-27: 1 JavaScript usage
  def displayModalProgressPanel(): Unit =
    if (! modalProgressPanelShown) {

      modalProgressPanelShown = true

      // Take out the focus from the current control
      // See https://github.com/orbeon/orbeon-forms/issues/4511
      Option(Globals.currentFocusControlId) foreach { focusControlId =>
        Controls.removeFocus(focusControlId)
        Private.focusControlIdOpt = focusControlId.some
      }

      if (Utils.isIOS && Utils.getZoomLevel() != 1.0) {
        Utils.resetIOSZoom()
        Private.timerIdOpt =
          Some(
            timers.setTimeout(200.milliseconds) {
              Private.timerIdOpt = None
              Private.panel.show()
            }
          )
      } else {
        Private.panel.show()
      }
    }

  def hideModalProgressPanel(): Unit =
    if (modalProgressPanelShown) {

      modalProgressPanelShown = false

      // Remove timer so that the modal progress panel doesn't show just after we try to hide it
      Private.timerIdOpt foreach { modalProgressPanelTimerId =>
        timers.clearTimeout(modalProgressPanelTimerId)
        Private.timerIdOpt = None
      }

      Private.panel.hide()

      // Restore focus
      // See https://github.com/orbeon/orbeon-forms/issues/4511
      Private.focusControlIdOpt foreach { modalProgressFocusControlId =>
        Controls.setFocus(modalProgressFocusControlId)
        Private.focusControlIdOpt = None
      }
    }

  private object Private {

    var timerIdOpt       : Option[SetTimeoutHandle] = None
    var focusControlIdOpt: Option[String]           = None

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
  }
}
