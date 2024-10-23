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
import org.orbeon.web.DomSupport.*
import org.orbeon.xforms.facade.{XBL, XBLCompanion}
import org.orbeon.xforms.{AjaxClient, EventListenerSupport}
import org.scalajs.dom
import org.scalajs.dom.html

import scala.scalajs.js


object LaddaButton {

  sealed trait State
  object State {
    case object Begin   extends State
    case object Clicked extends State
    case object Sent    extends State
  }

  val ComponentName  = "ladda-button"
  val ClickEventName = s"click"

  XBL.declareCompanion(s"fr|$ComponentName", js.constructorOf[LaddaButtonCompanion])

  private class LaddaButtonCompanion(containerElem: html.Element) extends XBLCompanion {

    var state: State = State.Begin
    var ladda: Option[Ladda] = None

    private def findButton: html.Element = containerElem.querySelectorT("button")

    private val eventListenerSupport = new EventListenerSupport {}

    override def init(): Unit = {

      val button = findButton
      button.setAttribute("data-style", "slide-left")

      val isPrimary = button.closest(".xforms-trigger-appearance-xxforms-primary") != null
      button.setAttribute("data-spinner-color", if (isPrimary) "white" else "black")
      button.classList.add("ladda-button")

      ladda = Some(Ladda.create(button))

      eventListenerSupport.addListener(
        target = button,
        name   = ClickEventName,
        fn     = (_: dom.Event) => {
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
      eventListenerSupport.clearAllListeners()
      ladda.foreach (_.remove())
      ladda = None
    }
  }
}
