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
import org.orbeon.xbl
import org.orbeon.xforms.facade.Events
import org.orbeon.xforms.{$, AjaxClient, App, GlobalEventListenerSupport, Page, XFormsApp}
import org.scalajs.dom
import org.scalajs.dom.{Element, document, html, raw, window}
import org.scalajs.dom.raw.HTMLElement

import scala.scalajs.js
import scala.scalajs.js.Dynamic.{global => g}
import org.scalajs.dom.ext._

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
    xbl.CodeMirror
    xbl.AttachmentMultiple
    xbl.Recaptcha
    xbl.ClipboardCopy

    // TODO: with embedding, unobserve when the form is destroyed
    Events.orbeonLoadedEvent.subscribe(() => {

      // Add `scroll-padding-top` and `scroll-padding-bottom` to prevent the focused form field from being below the top navbar or button bar
      def addScrollPadding(rawElement: raw.Element, cssClass: String): Unit = {
        val htmlElement = rawElement.asInstanceOf[HTMLElement]
        val position    = window.getComputedStyle(htmlElement).position
        if (position == "fixed" || position == "sticky") {
          val resizeObserver = new ResizeObserver(() => {
            val documentElement = document.documentElement.asInstanceOf[HTMLElement]
            val scrollPaddingWithMargin = htmlElement.clientHeight + 5;
            documentElement.style.setProperty(cssClass, s"${scrollPaddingWithMargin}px")
          })
          resizeObserver.observe(htmlElement)
        }
      }

      Option(document.querySelector(".orbeon .navbar-fixed-top")).foreach(addScrollPadding(_, "scroll-padding-top"))
      Option(document.querySelector(".orbeon .fr-buttons"      )).foreach(addScrollPadding(_, "scroll-padding-bottom"))

      initSessionExpirationDialog()
    })
  }

  private def initSessionExpirationDialog(): Unit =
    Option(document.querySelector(".fr-session-expiration-dialog")) foreach { dialog =>

    def dialogShown: Boolean        = dialog.classList.contains("in")
    var expirationTimeMillis        = 0L
    var expiredOpt: Option[Boolean] = None
    var didExpireTimerOpt: Option[timers.SetTimeoutHandle] = None

    if (dialog.classList.contains("fr-feature-enabled")) {
      val renewSessionButton = dialog.querySelector("button").asInstanceOf[html.Element]
      GlobalEventListenerSupport.addListener(renewSessionButton, DomEventNames.Click, (event: dom.raw.EventTarget) => {
        renewSession()
      })
      Page.addSessionAboutToExpireListener(aboutToExpire)
    }

    def aboutToExpire(sessionHeartbeatEnabled : Boolean, approxSessionExpiredTimeMillis: Long): Unit = {
      expirationTimeMillis = approxSessionExpiredTimeMillis
      if (! sessionHeartbeatEnabled && ! dialogShown) {
        AjaxClient.pause()
        updateDialog(expired = false)
        showDialog()
        // TODO: make this padding configurable
        val timeToExpiration = (approxSessionExpiredTimeMillis - System.currentTimeMillis()) - 10000
        didExpireTimerOpt = Some(timers.setTimeout(
          timeToExpiration){
          updateDialog(expired = true)
        })
      }
    }

    def updateDialog(expired: Boolean): Unit = {
      val headerContainer     = dialog.querySelector(".modal-header")
      val bodyContainer       = dialog.querySelector(".modal-body")
      val footer              = dialog.querySelector(".modal-footer")

      val visibleWhenExpiring = List(headerContainer.firstElementChild, bodyContainer.firstElementChild, footer)
      val visibleWhenExpired  = List(headerContainer.lastElementChild , bodyContainer.lastElementChild)

      def setDisplay(elements: List[Element], display: String): Unit =
        elements.foreach(_.asInstanceOf[html.Element].style.display = display)

      val (toShow, toHide) = if (expired)
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

    def showDialog(): Unit =
      $(dialog).asInstanceOf[js.Dynamic].modal(new js.Object {
        val backdrop = "static" // Click on the background doesn't hide dialog
        val keyboard = false    // Can't use esc to close the dialog
      })

    def hideDialog(): Unit =
      $(dialog).asInstanceOf[js.Dynamic].modal("hide")
  }

  def onPageContainsFormsMarkup(): Unit =
    XFormsApp.onPageContainsFormsMarkup()
}
