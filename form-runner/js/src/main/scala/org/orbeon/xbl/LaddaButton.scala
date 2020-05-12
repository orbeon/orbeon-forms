/**
 * Copyright (C) 2020 Orbeon, Inc.
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
package org.orbeon.xbl

import org.orbeon.facades.Ladda
import org.orbeon.jquery._
import org.orbeon.xforms.facade.{XBL, XBLCompanion}
import org.orbeon.xforms.{$, AjaxClient}
import org.scalajs.jquery.{JQuery, JQueryEventObject}

import scala.scalajs.js

object LaddaButton {

  sealed trait State
  object State {
    case object Begin   extends State
    case object Clicked extends State
    case object Sent    extends State
  }

  val ComponentName  = "ladda-button"
  val ListenerSuffix = s".orbeon-$ComponentName"
  val ClickEventName = s"click$ListenerSuffix"

  XBL.declareCompanion(
    name      = s"fr|$ComponentName",
    companion = new XBLCompanion {

      var state: State = State.Begin
      var ladda: Option[Ladda] = None

      private def findButton: JQuery = $(containerElem).find("button")

      override def init(): Unit = {

        val jButton = findButton
        jButton.attr("data-style", "slide-left")

        val isPrimary = jButton.parents(".xforms-trigger-appearance-xxforms-primary").is("*")
        jButton.attr("data-spinner-color", if (isPrimary) "white" else "black")
        jButton.addClass("ladda-button")

        ladda = Some(Ladda.create(jButton(0)))

        jButton.onWithSelector(
          events   = ClickEventName,
          selector = null,
          handler  = (_: JQueryEventObject) => {
            if (state == State.Begin)
              js.timers.setTimeout(0) { // defer so we don't prevent other `click` listeners from being called
                ladda.foreach (_.start())
                state = State.Clicked
              }
          }
        )

        AjaxClient.beforeSendingEvent.add(_ => {
          if (state == State.Clicked)
            state = State.Sent
        })

        AjaxClient.ajaxResponseReceived.add(_ => {
          if (state == State.Sent) {
            ladda.foreach (_.stop())
            state = State.Begin
          }
        })
      }

      override def destroy(): Unit = {
        // TODO: remove from `AjaxClient.beforeSendingEvent` and `AjaxClient.ajaxResponseReceived`
        findButton.off(events = ClickEventName, selector = null)
        ladda.foreach (_.remove())
        ladda = None
      }
    }
  )
}
