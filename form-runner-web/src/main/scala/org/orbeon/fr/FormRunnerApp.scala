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

import org.orbeon.facades.{HTMLDialogElement, Ladda}
import org.orbeon.oxf.util.StringUtils.*
import org.scalajs.dom.{MutationObserver, MutationObserverInit, ResizeObserver}
import org.orbeon.web.DomSupport.*
import org.orbeon.web.{DomEventNames, DomSupport}
import org.orbeon.xbl
import org.orbeon.xforms.*
import org.orbeon.xforms.Constants.InitiallyHiddenClass
import org.orbeon.xforms.Session.SessionUpdate
import org.orbeon.xforms.facade.Bootstrap
import org.scalajs.dom
import org.scalajs.dom.*
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.*

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
    xbl.ImageAttachment
    xbl.Recaptcha
    xbl.FriendlyCaptcha
    xbl.ClipboardCopy
    xbl.Trigger
    xbl.Pager
    xbl.Countdown
    xbl.FrWizard
    xbl.Timestamp
    xbl.ContentFilter

    DomSupport.atLeastDomReadyStateF(document, DomSupport.DomReadyState.Interactive) foreach { _ =>
      DomSupport.onElementFoundOrAdded(document.body, ".orbeon .navbar.fixed-top, .orbeon .navbar-fixed-top", addScrollPadding(_, "scroll-padding-top"))
      DomSupport.onElementFoundOrAdded(document.body, ".orbeon .fr-buttons"                                 , addScrollPadding(_, "scroll-padding-bottom"))
      DomSupport.onElementFoundOrAdded(document.body, ".orbeon .fr-buttons"                                 , initStickyButtons)
      DomSupport.onElementFoundOrAdded(document.body, ".orbeon .fr-session-expiration-dialog"               , initSessionExpirationDialog)
      DomSupport.onElementFoundOrAdded(document.body, ".orbeon .fr-duplicate-tab-dialog"                    , initDuplicateTabDialog)
      DomSupport.onElementFoundOrAdded(document.body, ".orbeon .fr-color-scheme-nav"                        , initColorSchemeSelector)
      DomSupport.onElementFoundOrAdded(document.body, ".orbeon .fr-data-icon"                               , initDataStatusPopover)
    }
  }

  private def initDataStatusPopover(dataIconElem: html.Element): Unit = {

    def activeStatusIconOpt: Option[html.Element] =
      if (dataIconElem.closestOpt("form.fr-client-data-dirty").isDefined)
        dataIconElem.querySelectorOpt(".fr-status-unsafe")
      else if (dataIconElem.classList.contains("fr-data-status-new"))
        dataIconElem.querySelectorOpt(".fr-status-new")
      else if (dataIconElem.classList.contains("fr-data-status-safe"))
        dataIconElem.querySelectorOpt(".fr-status-safe")
      else if (dataIconElem.classList.contains("fr-data-status-autosaved"))
        dataIconElem.querySelectorOpt(".fr-status-autosaved")
      else if (dataIconElem.classList.contains("fr-data-status-unsafe"))
        dataIconElem.querySelectorOpt(".fr-status-unsafe")
      else
        None

    def currentPopoverElemOpt: Option[html.Element] =
      Option(dataIconElem.getAttribute("aria-describedby"))
        .filter(_.nonEmpty)
        .flatMap(id => dom.document.getElementByIdOpt(id))
        .filter(_.classList.contains("fr-data-status-popover"))

    def isPopoverShown: Boolean =
      currentPopoverElemOpt.isDefined

    def hidePopover(): Unit =
      Bootstrap.getPopover(dataIconElem).foreach(_.destroy())

    def showOrUpdatePopover(toggleIfShown: Boolean): Unit =
      if (isPopoverShown && toggleIfShown) {
        hidePopover()
      } else {
        hidePopover()
        activeStatusIconOpt.foreach { iconElem =>
          val title   = Option(iconElem.getAttribute("data-orbeon-title")).getOrElse("")
          val content = Option(iconElem.getAttribute("data-orbeon-content")).getOrElse("")
          if (title.nonEmpty || content.nonEmpty) {
            val popoverConfig = js.Dynamic.literal(
              placement = "bottom",
              trigger   = "manual",
              title     = title,
              content   = content,
              html      = false,
              animation = false
            )
            val popoverHandle = Bootstrap.newPopover(dataIconElem, popoverConfig)
            popoverHandle.show()
            popoverHandle.tipElementOpt.map(_.asInstanceOf[html.Element]).foreach { popover =>
              popover.classList.add("fr-data-status-popover")
            }
          }
        }
      }

    dataIconElem.addEventListener("click", (e: dom.MouseEvent) => {
      e.stopPropagation()
      showOrUpdatePopover(toggleIfShown = true)
    })

    dataIconElem.addEventListener("keydown", (e: dom.KeyboardEvent) =>
      if (e.key == "Enter" || e.key == " ") {
        e.preventDefault()
        e.stopPropagation()
        showOrUpdatePopover(toggleIfShown = true)
      }
    )

    document.addEventListener("click", (e: dom.MouseEvent) =>
      if (isPopoverShown) {
        val target               = e.target.asInstanceOf[dom.Node]
        val clickedInsidePopover = currentPopoverElemOpt.exists(_.contains(target))
        if (! dataIconElem.contains(target) && ! clickedInsidePopover)
          hidePopover()
      }
    )

    document.addEventListener("keyup", (e: dom.KeyboardEvent) =>
      if (e.key == "Escape" && isPopoverShown)
        hidePopover()
    )

    if (js.typeOf(g.MutationObserver) != "undefined") {
      val observer = new MutationObserver((_, _) =>
        if (isPopoverShown)
          showOrUpdatePopover(toggleIfShown = false)
      )
      val options = new MutationObserverInit {
        attributes      = true
        attributeFilter = js.Array("class")
      }
      observer.observe(dataIconElem, options)
      dataIconElem.closestOpt("form").foreach(observer.observe(_, options))
    }
  }

  private def initColorSchemeSelector(navElem: html.Element): Unit = {
    val ColorSchemeCookieName = "orbeon-color-scheme"
    val AllowedSchemes = Set("light", "dark", "system")

    val frViewElemOpt = document.getElementByIdOpt("fr-view")
    val iconElemOpt   = navElem.querySelectorOpt(".fr-color-scheme-icon-active")
    val itemButtons   = navElem.querySelectorAllT("button[data-orbeon-color-scheme]")

    def getEffectiveCurrentScheme: String = {
      val classListOpt = frViewElemOpt.map(_.classList)
      if (classListOpt.exists(_.contains("fr-color-scheme-dark"))) "dark"
      else if (classListOpt.exists(_.contains("fr-color-scheme-system"))) "system"
      else if (classListOpt.exists(_.contains("fr-color-scheme-light"))) "light"
      else {
        val fromCookie = document.cookie.splitTo[List](";")
          .map(_.trimAllToEmpty)
          .find(_.startsWith(s"$ColorSchemeCookieName="))
          .map(_.substring(ColorSchemeCookieName.length + 1).trimAllToEmpty)
          .filter(AllowedSchemes)
        fromCookie.getOrElse("light")
      }
    }

    def updateUI(scheme: String): Unit = {
      iconElemOpt.foreach { iconElem =>
        List("fa-sun", "fa-moon", "fa-circle-half-stroke").foreach(iconElem.classList.remove)
        val iconClass = scheme match {
          case "light"  => "fa-sun"
          case "dark"   => "fa-moon"
          case "system" => "fa-circle-half-stroke"
          case _        => "fa-circle-half-stroke"
        }
        iconElem.classList.add(iconClass)
      }

      itemButtons.foreach { btn =>
        val btnScheme = btn.getAttribute("data-orbeon-color-scheme")
        val isActive = btnScheme == scheme
        btn.closestOpt(".dropdown-item").foreach { li =>
          li.classList.toggle("disabled", isActive)
        }
        btn.setAttribute("aria-checked", isActive.toString)
      }
    }

    def setColorScheme(scheme: String): Unit =
      if (AllowedSchemes.contains(scheme)) {
        val toRemove = List("fr-color-scheme-light", "fr-color-scheme-dark", "fr-color-scheme-system")
        frViewElemOpt.foreach { frView =>
          toRemove.foreach(frView.classList.remove)
          frView.classList.add(s"fr-color-scheme-$scheme")
        }
        Option(document.body).foreach { body =>
          toRemove.foreach(body.classList.remove)
          body.classList.add(s"fr-color-scheme-$scheme")
        }

        document.cookie = s"$ColorSchemeCookieName=$scheme; path=/; max-age=31536000; SameSite=Lax"

        updateUI(scheme)
      }

    itemButtons.foreach { btn =>
      val btnScheme = btn.getAttribute("data-orbeon-color-scheme")
      btn.addEventListener("click", (_: dom.MouseEvent) => setColorScheme(btnScheme))
    }

    updateUI(getEffectiveCurrentScheme)
  }

  // Add `scroll-padding-top` and `scroll-padding-bottom` to prevent the focused form field from being
  // below the top navbar or button bar
  private def addScrollPadding(htmlElement: html.Element, cssClass: String): Unit = {
    val position = window.getComputedStyle(htmlElement).position
    if (position == "fixed" || position == "sticky") {
      if (js.typeOf(g.ResizeObserver) != "undefined") { // ResizeObserver isn't implemented in jsdom, used in tests
        val resizeObserver = new ResizeObserver((_, _) => {
          val documentElement = document.documentElementT
          val scrollPaddingWithMargin = htmlElement.clientHeight + 5;
          documentElement.style.setProperty(cssClass, s"${scrollPaddingWithMargin}px")
        })
        resizeObserver.observe(htmlElement)
      }
    }
  }

  // Once Safari and Firefox ship native support for `@container scroll-state(stuck: bottom)`, that Scala.js code
  // can simply be deleted and replaced with pure CSS.
  private def initStickyButtons(buttonsElem: html.Element): Unit = {
    val position = window.getComputedStyle(buttonsElem).position
    if (position == "fixed" || position == "sticky") {
      var ticking = false

      val update = () => {
        val isOverForm =
          Option(buttonsElem.parentElement).exists { parent =>
            val parentBottom  = parent.getBoundingClientRect().bottom
            val buttonsBottom = buttonsElem.getBoundingClientRect().bottom
            buttonsBottom >= window.innerHeight - 2 && parentBottom - buttonsBottom > 2
          }
        buttonsElem.toggleClass("fr-buttons-floating", isOverForm)
      }

      val requestUpdate = () =>
        if (!ticking) {
          ticking = true
          window.requestAnimationFrame(_ => {
            update()
            ticking = false
          })
        }

      GlobalEventListenerSupport.addListener(window, DomEventNames.Scroll, (_: dom.Event) => requestUpdate(), useCapture = true)
      GlobalEventListenerSupport.addListener(window, "resize", (_: dom.Event) => requestUpdate())

      if (js.typeOf(g.ResizeObserver) != "undefined") {
        val resizeObserver = new ResizeObserver((_, _) => requestUpdate())
        Option(buttonsElem.parentElement).foreach(resizeObserver.observe)
        resizeObserver.observe(buttonsElem)
      }

      update()
    }
  }

  private def initSessionExpirationDialog(dialogElem: html.Element): Unit = {
    val dialog = dialogElem.asInstanceOf[HTMLDialogElement]

    // Detecting whether the dialog is shown or not by retrieving its CSS classes is not reliable when aboutToExpire
    // is called multiple times in a row (e.g. locally and because of a message from another page), so we keep track
    // of it ourselves.
    var dialogShown: Boolean = false
    var didExpireTimerOpt: Option[timers.SetTimeoutHandle] = None

    if (dialog.hasClass("fr-feature-enabled")) {
      // Remove XForms-level dialog we're replacing
      dom.document.querySelectorAllT(s".xforms-login-detected-dialog").foreach(_.remove())

      val renewButton = dialog.querySelectorT(".fr-renew-button")
      GlobalEventListenerSupport.addListener(renewButton, DomEventNames.Click, (_: dom.EventTarget) => {
        renewSession()
        // Since we sent a heartbeat when the session was renewed, the local newest event time has been updated and
        // will be broadcast to other pages.
        Session.updateWithLocalNewestEventTime()
      })

      val reloadButton = dialog.querySelectorT(".fr-reload-button")
      val laddaReloadButton = Ladda.create(reloadButton)
      GlobalEventListenerSupport.addListener(
        target = reloadButton,
        name   = DomEventNames.Click,
        fn     = (_: dom.EventTarget) => {
          laddaReloadButton.start()
          dom.window.location.href = dom.window.location.href
        }
      )

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
      val headerContent = dialog.querySelectorAllT(".xxforms-dialog-head .xforms-output")
      val bodyContent   = dialog.querySelectorAllT(".xxforms-dialog-body .xforms-mediatype-text-html")
      val renewButton   = dialog.querySelectorT   (".fr-renew-button")
      val reloadButton  = dialog.querySelectorT   (".fr-reload-button")

      val visibleWhenExpiring = List(headerContent.head, bodyContent.head, renewButton)
      val visibleWhenExpired  = List(headerContent.last, bodyContent.last, reloadButton)

      def setDisplay(elements: List[html.Element], display: String): Unit =
        elements.foreach(_.style.display = display)

      val (toShow, toHide) = if (Session.expired)
        (visibleWhenExpired , visibleWhenExpiring) else
        (visibleWhenExpiring, visibleWhenExpired )

      setDisplay(toShow, "")
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
      dialog.showModal()
      dialogShown = true
    }

    def hideDialog(): Unit = {
      dialog.close()
      dialogShown = false
    }
  }

  private def initDuplicateTabDialog(dialogElem: html.Element): Unit =
    DuplicateTab.duplicateTabDetectedF.foreach { _ =>
      dialogElem.closestOpt(s"form.$InitiallyHiddenClass").foreach(_.classList.remove(InitiallyHiddenClass))
      dialogElem.asInstanceOf[HTMLDialogElement].showModal()
    }

  def onPageContainsFormsMarkup(): Unit =
    XFormsApp.onPageContainsFormsMarkup()
}
