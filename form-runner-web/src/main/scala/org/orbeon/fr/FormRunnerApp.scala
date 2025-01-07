/**
 * Copyright (C) 2017 Orbeon, Inc.
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
package org.orbeon.fr

import org.orbeon.facades.ResizeObserver
import org.orbeon.web.DomEventNames
import org.orbeon.web.DomSupport.*
import org.orbeon.xbl
import org.orbeon.xforms.*
import org.orbeon.xforms.Session.SessionUpdate
import org.orbeon.xforms.facade.{Bootstrap, Events}
import org.scalajs.dom
import org.scalajs.dom.*

import scala.scalajs.js
import scala.scalajs.js.Dynamic.global as g
import scala.scalajs.js.timers


// Scala.js starting point for Form Runner
object FormRunnerApp extends App {

  def onOrbeonApiLoaded(): Unit = {
    XFormsApp.onOrbeonApiLoaded()
    onOrbeonApiLoaded2()
  }

  def onOrbeonApiLoaded2(): Unit = {

    val orbeonDyn = g.window.ORBEON

    val frDyn = {
      if (js.isUndefined(orbeonDyn.fr))
        orbeonDyn.fr = new js.Object
      orbeonDyn.fr
    }

    frDyn.API = FormRunnerAPI

    val frPrivateDyn = {
      if (js.isUndefined(frDyn.`private`))
        frDyn.`private` = new js.Object
      frDyn.`private`
    }

    frPrivateDyn.API = FormRunnerPrivateAPI

    // Register XBL components
    xbl.Grid
    xbl.Repeater
    xbl.DndRepeat
    xbl.DropTrigger
    xbl.Tabbable
    xbl.Number
    xbl.TreeSelect1
    xbl.WPaint
    xbl.HrefButton
    xbl.LaddaButton
    xbl.Select1Search
    xbl.AutosizeTextarea
    xbl.TinyMCE
    xbl.AttachmentMultiple
    xbl.Recaptcha
    xbl.FriendlyCaptcha
    xbl.ClipboardCopy
    xbl.Trigger

    // TODO: with embedding, unobserve when the form is destroyed
    Events.orbeonLoadedEvent.subscribe(() => {

      // Add `scroll-padding-top` and `scroll-padding-bottom` to prevent the focused form field from being below the top navbar or button bar
      def addScrollPadding(htmlElement: html.Element, cssClass: String): Unit = {
        val position    = window.getComputedStyle(htmlElement).position
        if (position == "fixed" || position == "sticky") {
          val resizeObserver = new ResizeObserver(() => {
            val documentElement = document.documentElementT
            val scrollPaddingWithMargin = htmlElement.clientHeight + 5;
            documentElement.style.setProperty(cssClass, s"${scrollPaddingWithMargin}px")
          })
          resizeObserver.observe(htmlElement)
        }
      }

      document.querySelectorOpt(".orbeon .navbar-fixed-top").foreach(addScrollPadding(_, "scroll-padding-top"))
      document.querySelectorOpt(".orbeon .fr-buttons"      ).foreach(addScrollPadding(_, "scroll-padding-bottom"))

      initSessionExpirationDialog()
    })
  }

  private def initSessionExpirationDialog(): Unit =
    document.querySelectorOpt(".fr-session-expiration-dialog").foreach { dialog =>

      val modal = Bootstrap.newModal(dialog, new js.Object {
        val backdrop = "static" // Click on the background doesn't hide dialog
        val keyboard = false    // Can't use esc to close the dialog
      })

      // Detecting whether the dialog is shown or not by retrieving its CSS classes is not reliable when aboutToExpire
      // is called multiple times in a row (e.g. locally and because of a message from another page), so we keep track
      // of it ourselves.
      var dialogShown: Boolean = false
      var didExpireTimerOpt: Option[timers.SetTimeoutHandle] = None

      if (dialog.classList.contains("fr-feature-enabled")) {
        val renewSessionButton = dialog.querySelectorT("button")
        GlobalEventListenerSupport.addListener(renewSessionButton, DomEventNames.Click, (event: dom.EventTarget) => {
          renewSession()

          // Since we sent a heartbeat when the session was renewed, the local newest event time has been updated and
          // will be broadcast to other pages.
          Session.updateWithLocalNewestEventTime()
        })

        Session.addSessionUpdateListener(sessionUpdate)
      }

      def sessionUpdate(sessionUpdate: SessionUpdate): Unit =
        if (! sessionUpdate.sessionHeartbeatEnabled) {
          sessionUpdate.sessionStatus match {
            case Session.SessionActive if dialogShown && ! Session.expired =>
              renewSession()

            case Session.SessionAboutToExpire =>
              aboutToExpire(sessionUpdate.approxSessionExpiredTimeMillis)

            case Session.SessionExpired =>
              sessionExpired()

            case _ =>
              // Nothing to do
          }
        }

      def aboutToExpire(approxSessionExpiredTimeMillis: Long): Unit =
        if (! dialogShown) {
          AjaxClient.pause()
          updateDialog()
          showDialog()

          val timeToExpiration = approxSessionExpiredTimeMillis - System.currentTimeMillis()
          didExpireTimerOpt.foreach(timers.clearTimeout)
          didExpireTimerOpt = Some(timers.setTimeout(timeToExpiration.toDouble) {
            Session.sessionHasExpired()
            updateDialog()
          })
        }

      def sessionExpired(): Unit =
        if (! dialogShown) {
          AjaxClient.pause()
          updateDialog()
          showDialog()
        } else {
          updateDialog()
        }

      def updateDialog(): Unit = {
        val headerContainer = dialog.querySelectorT(".modal-header")
        val bodyContainer   = dialog.querySelectorT(".modal-body")
        val footer          = dialog.querySelectorT(".modal-footer")

        val visibleWhenExpiring = List(headerContainer.childrenT.head, bodyContainer.childrenT.head, footer)
        val visibleWhenExpired  = List(headerContainer.childrenT.last, bodyContainer.childrenT.last)

        def setDisplay(elements: List[html.Element], display: String): Unit =
          elements.foreach(_.style.display = display)

        val (toShow, toHide) = if (Session.expired)
          (visibleWhenExpired , visibleWhenExpiring) else
          (visibleWhenExpiring, visibleWhenExpired )

        setDisplay(toShow, "block")
        setDisplay(toHide, "none")
      }

      def renewSession(): Unit = {
        didExpireTimerOpt.foreach(timers.clearTimeout)
        didExpireTimerOpt = None
        AjaxClient.sendHeartBeat()
        AjaxClient.unpause()
        hideDialog()
      }

      def showDialog(): Unit = {
        modal.show()
        dialogShown = true
      }

      def hideDialog(): Unit = {
        modal.hide()
        dialogShown = false
      }
    }

  def onPageContainsFormsMarkup(): Unit =
    XFormsApp.onPageContainsFormsMarkup()
}
