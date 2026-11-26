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
    xbl.PopoverSelectPicker.init()
    xbl.TinyMCE
    xbl.Markdown
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
    xbl.Color
    xbl.MaskedInput
    xbl.Url
    xbl.FrRange
    xbl.FrRating
    xbl.FrScale
    xbl.FrDuration
    xbl.PartialDate
    xbl.FrOpenSelect
    xbl.FrCodeInput
    xbl.ConsoleChart
    xbl.XPathCompletion
    xbl.ProcessEditorDnD
    xbl.ProcessTextEditor
    xbl.ProcessActionPicker

    DomSupport.atLeastDomReadyStateF(document, DomSupport.DomReadyState.Interactive) foreach { _ =>
      DomSupport.onElementFoundOrAdded(document.body, ".orbeon .navbar.fixed-top, .orbeon .navbar-fixed-top", addScrollPadding(_, "scroll-padding-top"))
      DomSupport.onElementFoundOrAdded(document.body, ".orbeon .fr-buttons"                                 , addScrollPadding(_, "scroll-padding-bottom"))
      DomSupport.onElementFoundOrAdded(document.body, ".orbeon .fr-buttons"                                 , initStickyButtons)
      DomSupport.onElementFoundOrAdded(document.body, ".orbeon .fr-session-expiration-dialog"               , initSessionExpirationDialog)
      DomSupport.onElementFoundOrAdded(document.body, ".orbeon .fr-duplicate-tab-dialog"                    , initDuplicateTabDialog)
      DomSupport.onElementFoundOrAdded(document.body, ".orbeon .fr-color-scheme-nav"                        , initColorSchemeSelector)
      DomSupport.onElementFoundOrAdded(document.body, ".orbeon .fr-data-icon, .orbeon .fr-validity-icon, .orbeon .fr-validity-badge", initStatusIconPopover)
      DomSupport.onElementFoundOrAdded(document.body, ".orbeon .fr-landing-toolbar"                         , initLandingQuickFilter)
      DomSupport.onElementFoundOrAdded(document.body, ".orbeon [data-fr-filter-shortcut]"                   , initQuickFilterShortcut)
      DomSupport.onElementFoundOrAdded(document.body, ".orbeon .fr-landing-grid"                            , initLandingCardExtraRows)
      SearchResultsTableSupport.init()
      initSummaryCellTooltipClickForwarding()
    }
  }

  private def initSummaryCellTooltipClickForwarding(): Unit =
    document.body.addEventListener(
      DomEventNames.Click,
      (event: dom.Event) =>
        event match {
          case mouseEvent: dom.MouseEvent if ! mouseEvent.defaultPrevented && mouseEvent.button == 0 =>
            mouseEvent.target match {
              case targetElem: html.Element
                if targetElem.closestOpt("a, button, input, select, textarea, label").isEmpty &&
                   Option(dom.window.getSelection()).forall(_.isCollapsed) =>
                for {
                  cellValue  <- targetElem.closestOpt(".fr-summary-cell-value")
                  tdElem     <- cellValue.closestOpt("td")
                  cellAnchor <- tdElem.querySelectorOpt("a.fr-summary-cell-link:not(.xforms-hidden)")
                  href       <- cellAnchor.attValueOpt("href").flatMap(_.trimAllToOpt)
                } locally {
                  if (mouseEvent.metaKey || mouseEvent.ctrlKey)
                    dom.window.open(href, "_blank", "noopener")
                  else
                    cellAnchor.click()
                }
              case _ =>
            }
          case _ =>
        }
    )

  private def initStatusIconPopover(statusIconElem: html.Element): Unit = {

    val InitializedAttr = "data-orbeon-status-popover-initialized"
    if (statusIconElem.hasAttribute(InitializedAttr))
      return
    statusIconElem.setAttribute(InitializedAttr, "true")

    def activeStatusIconOpt: Option[html.Element] =
      if (! statusIconElem.isConnected || statusIconElem.closestOpt(".xforms-disabled").isDefined)
        None
      else if (statusIconElem.classList.contains("fr-validity-badge"))
        Some(statusIconElem)
      else if (statusIconElem.classList.contains("fr-validity-icon"))
        statusIconElem.querySelectorOpt(".fr-status-valid")
      else if (statusIconElem.closestOpt("form.fr-client-data-dirty").isDefined)
        statusIconElem.querySelectorOpt(".fr-status-unsafe")
      else if (statusIconElem.classList.contains("fr-data-status-new"))
        statusIconElem.querySelectorOpt(".fr-status-new")
      else if (statusIconElem.classList.contains("fr-data-status-safe"))
        statusIconElem.querySelectorOpt(".fr-status-safe")
      else if (statusIconElem.classList.contains("fr-data-status-autosaved"))
        statusIconElem.querySelectorOpt(".fr-status-autosaved")
      else if (statusIconElem.classList.contains("fr-data-status-unsafe"))
        statusIconElem.querySelectorOpt(".fr-status-unsafe")
      else
        None

    def activePopoverStateOpt: Option[(html.Element, String, String)] =
      activeStatusIconOpt.flatMap { iconElem =>
        val title   = Option(iconElem.getAttribute("data-orbeon-title")).getOrElse("")
        val content = Option(iconElem.getAttribute("data-orbeon-content")).getOrElse("")
        if (title.nonEmpty || content.nonEmpty)
          Some((iconElem, title, content))
        else
          None
      }

    var lastPopoverStateOpt: Option[(html.Element, String, String)] = None

    def currentPopoverElemOpt: Option[html.Element] =
      Option(statusIconElem.getAttribute("aria-describedby"))
        .filter(_.nonEmpty)
        .flatMap(id => dom.document.getElementByIdOpt(id))
        .filter(_.classList.contains("fr-data-status-popover"))

    def isPopoverShown: Boolean =
      currentPopoverElemOpt.isDefined

    def hidePopover(): Unit = {
      lastPopoverStateOpt = None
      statusIconElem.classList.remove("show")
      Bootstrap.getPopover(statusIconElem).foreach(_.destroy())
    }

    def showOrUpdatePopover(toggleIfShown: Boolean): Unit =
      if (isPopoverShown && toggleIfShown) {
        hidePopover()
      } else {
        hidePopover()
        activePopoverStateOpt.foreach { case state @ (_, title, content) =>
          val popoverConfig = js.Dynamic.literal(
            placement = "bottom",
            trigger   = "manual",
            title     = title,
            content   = content,
            html      = false,
            animation = false
          )
          val popoverHandle = Bootstrap.newPopover(statusIconElem, popoverConfig)
          popoverHandle.show()
          lastPopoverStateOpt = Some(state)
          statusIconElem.classList.add("show")
          popoverHandle.tipElementOpt.map(_.asInstanceOf[html.Element]).foreach { popover =>
            popover.classList.add("fr-data-status-popover")
          }
        }
      }

    statusIconElem.addEventListener("click", (e: dom.MouseEvent) => {
      showOrUpdatePopover(toggleIfShown = true)
      if (e.detail > 0)
        Option(dom.document.activeElement)
          .collect { case elem: html.Element if statusIconElem.contains(elem) => elem }
          .foreach(_.blur())
    })

    statusIconElem.addEventListener("keydown", (e: dom.KeyboardEvent) =>
      if (e.key == "Enter" || e.key == " ") {
        e.preventDefault()
        showOrUpdatePopover(toggleIfShown = true)
      }
    )

    val hideIfTargetOutside: js.Function1[dom.Event, Unit] = (e: dom.Event) =>
      if (isPopoverShown) {
        val target          = e.target.asInstanceOf[dom.Node]
        val isInsidePopover = currentPopoverElemOpt.exists(_.contains(target))
        if (! statusIconElem.contains(target) && ! isInsidePopover)
          hidePopover()
      }

    val hideOnEscape: js.Function1[dom.KeyboardEvent, Unit] = (e: dom.KeyboardEvent) =>
      if (e.key == "Escape" && isPopoverShown)
        hidePopover()

    document.addEventListener("click"  , hideIfTargetOutside)
    document.addEventListener("focusin", hideIfTargetOutside)
    document.addEventListener("keyup"  , hideOnEscape)

    lazy val observer: MutationObserver = new MutationObserver((_, _) =>
      if (! statusIconElem.isConnected) {
        hidePopover()
        statusIconElem.removeAttribute(InitializedAttr)
        observer.disconnect()
        document.removeEventListener("click"  , hideIfTargetOutside)
        document.removeEventListener("focusin", hideIfTargetOutside)
        document.removeEventListener("keyup"  , hideOnEscape)
      } else if (isPopoverShown && activePopoverStateOpt != lastPopoverStateOpt) {
        showOrUpdatePopover(toggleIfShown = false)
      }
    )

    val selfOptions = new MutationObserverInit {
      attributes      = true
      attributeFilter = js.Array("class", "data-orbeon-title", "data-orbeon-content")
    }
    val containerOptions = new MutationObserverInit {
      childList       = true
      subtree         = true
      attributes      = true
      attributeFilter = js.Array("class")
    }
    val formOptions = new MutationObserverInit {
      attributes      = true
      attributeFilter = js.Array("class")
    }
    observer.observe(statusIconElem, selfOptions)
    statusIconElem.closestOpt(".fr-status-icons").foreach(observer.observe(_, containerOptions))
    statusIconElem.closestOpt("form").foreach(observer.observe(_, formOptions))
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
      val resizeObserver = new ResizeObserver((_, _) => {
        val documentElement = document.documentElementT
        val scrollPaddingWithMargin = htmlElement.clientHeight + 5;
        documentElement.style.setProperty(cssClass, s"${scrollPaddingWithMargin}px")
      })
      resizeObserver.observe(htmlElement)
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

      val resizeObserver = new ResizeObserver((_, _) => requestUpdate())
      Option(buttonsElem.parentElement).foreach(resizeObserver.observe)
      resizeObserver.observe(buttonsElem)

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

  private def initLandingQuickFilter(toolbarElem: html.Element): Unit = {

    val FilterDebounceMs = 120.0
    val HiddenClass      = "xforms-hidden"
    val FilteredOutClass = "fr-card-filtered-out"
    val AutoHiddenClass  = "fr-card-auto-hidden"
    val TabStorageKey    = "orbeon.fr.landing.tab"

    val tablistOpt        = toolbarElem.querySelectorOpt("#fr-landing-tablist")
    val tabButtons        = tablistOpt.toList.flatMap(_.querySelectorAllT("button[role='tab']"))
    val workspacePanelOpt = Option(document.getElementById("fr-landing-panel-workspace")).collect { case e: html.Element => e }
    val demosPanelOpt     = Option(document.getElementById("fr-landing-panel-demos")).collect { case e: html.Element => e }

    val filterBoxOpt = toolbarElem.querySelectorOpt(".fr-landing-filter-box")
    val inputOpt     = toolbarElem.querySelectorOpt(".fr-landing-filter-input").map(_.asInstanceOf[html.Input])
    val clearBtnOpt  = toolbarElem.querySelectorOpt(".fr-landing-filter-clear")
    val kbdHintOpt   = toolbarElem.querySelectorOpt(".fr-landing-filter-kbd")
    val countElemOpt = toolbarElem.querySelectorOpt(".fr-landing-filter-count")
    val srStatusOpt  = toolbarElem.querySelectorOpt(".fr-landing-filter-sr-status")
    val formElemOpt  = toolbarElem.closestOpt("form").map(_.asInstanceOf[html.Form])

    var lastSentQuery : String = ""
    var selectedTabKey: String =
      scala.util.Try(Option(window.sessionStorage.getItem(TabStorageKey))).toOption.flatten
        .filter(k => k == "workspace" || k == "demos")
        .getOrElse("workspace")

    def availableCardsInPanel(panelElem: html.Element): List[html.Element] =
      panelElem
        .querySelectorAllT(".fr-landing-grid .card")
        .iterator
        .filter { card =>
          !card.hasClass(HiddenClass) &&
            !card.hasClass(AutoHiddenClass) &&
            card.closestOpt(".xforms-disabled, .xforms-hidden, .xforms-repeat-template").isEmpty
        }
        .toList

    def confirmedNonEmptyDemoCards(panelElem: html.Element): List[html.Element] =
      availableCardsInPanel(panelElem).filter { card =>
        card.attValueOpt("data-has-loaded").contains("true") &&
          card.querySelectorOpt(".fr-card-state-error:not(.xforms-disabled)").isEmpty
      }

    def setPanelTabSemantics(panelElem: html.Element, enabled: Boolean): Unit =
      if (enabled) {
        panelElem.setAttribute("role", "tabpanel")
        panelElem.attValueOpt("data-tab-id").foreach(panelElem.setAttribute("aria-labelledby", _))
      } else {
        panelElem.removeAttribute("role")
        panelElem.removeAttribute("aria-labelledby")
      }

    def syncTabsAndPanels(): (Boolean, Option[html.Element]) = {
      val hasWorkspace = workspacePanelOpt.exists(p => !p.hasClass(HiddenClass) && availableCardsInPanel(p).nonEmpty)
      val hasDemos     = demosPanelOpt.exists(p => !p.hasClass(HiddenClass) && confirmedNonEmptyDemoCards(p).nonEmpty)
      val tabbedMode   = hasWorkspace && hasDemos

      tablistOpt.foreach(_.toggleClass(HiddenClass, !tabbedMode))

      if (tabbedMode) {
        tabButtons.foreach { btn =>
          val isSelected = btn.attValueOpt("data-tab-key").contains(selectedTabKey)
          btn.setAttribute("aria-selected", isSelected.toString)
          btn.setAttribute("tabindex", if (isSelected) "0" else "-1")
        }
        workspacePanelOpt.foreach { p =>
          setPanelTabSemantics(p, enabled = true)
          if (selectedTabKey == "workspace") p.removeAttribute("hidden") else p.setAttribute("hidden", "until-found")
        }
        demosPanelOpt.foreach { p =>
          setPanelTabSemantics(p, enabled = true)
          if (selectedTabKey == "demos") p.removeAttribute("hidden") else p.setAttribute("hidden", "until-found")
        }
        val activePanelOpt = if (selectedTabKey == "demos") demosPanelOpt else workspacePanelOpt
        (true, activePanelOpt)
      } else if (hasWorkspace) {
        workspacePanelOpt.foreach { p =>
          setPanelTabSemantics(p, enabled = false)
          p.removeAttribute("hidden")
        }
        demosPanelOpt.foreach { p =>
          setPanelTabSemantics(p, enabled = false)
          p.setAttribute("hidden", "")
        }
        (false, workspacePanelOpt)
      } else if (hasDemos) {
        workspacePanelOpt.foreach { p =>
          setPanelTabSemantics(p, enabled = false)
          p.setAttribute("hidden", "")
        }
        demosPanelOpt.foreach { p =>
          setPanelTabSemantics(p, enabled = false)
          p.removeAttribute("hidden")
        }
        (false, demosPanelOpt)
      } else {
        val fallbackPanelOpt = workspacePanelOpt.filterNot(_.hasClass(HiddenClass)).orElse(demosPanelOpt.filterNot(_.hasClass(HiddenClass)))
        workspacePanelOpt.foreach(setPanelTabSemantics(_, enabled = false))
        demosPanelOpt.foreach(setPanelTabSemantics(_, enabled = false))
        fallbackPanelOpt.foreach(_.removeAttribute("hidden"))
        (false, fallbackPanelOpt)
      }
    }

    def updateMatchSummary(): Unit = {
      val (tabbedMode, activePanelOpt) = syncTabsAndPanels()
      val noResultsOpt                 = document.querySelectorOpt(".orbeon .fr-landing-no-results")

      List(workspacePanelOpt, demosPanelOpt).flatten.foreach { panel =>
        panel.querySelectorOpt(".fr-landing-grid").foreach { grid =>
          val visibleInGrid = availableCardsInPanel(panel).count(!_.hasClass(FilteredOutClass))
          grid.toggleClass("fr-landing-grid-single", visibleInGrid == 1)
        }
      }

      // Update per-tab filter match counts when filtering in tabbed mode
      tabButtons.foreach { btn =>
        btn.querySelectorOpt(".fr-landing-tab-count").foreach { badge =>
          if (!tabbedMode || lastSentQuery.isEmpty) {
            badge.textContent = ""
            badge.classList.add(HiddenClass)
          } else {
            val panelOpt =
              if (btn.attValueOpt("data-tab-key").contains("demos")) demosPanelOpt else workspacePanelOpt
            val matches  = panelOpt.toList.flatMap(availableCardsInPanel).count(!_.hasClass(FilteredOutClass))
            badge.textContent = matches.toString
            badge.classList.remove(HiddenClass)
          }
        }
      }

      if (lastSentQuery.isEmpty) {
        countElemOpt.foreach { el =>
          el.textContent = ""
          el.removeAttribute("title")
          el.classList.add(HiddenClass)
        }
        srStatusOpt.foreach(_.textContent = "")
        noResultsOpt.foreach(_.classList.add(HiddenClass))
      } else {
        activePanelOpt.foreach { panel =>
          val availableCards = availableCardsInPanel(panel)
          val matchingCards  = availableCards.filterNot(_.hasClass(FilteredOutClass))
          val anyLoading     = availableCards.exists(c => c.querySelectorOpt(".fr-card-is-refreshing, .fr-card-skeleton:not(.xforms-disabled)").isDefined)
          val template       = toolbarElem.attValueOpt("data-matching-template").flatMap(_.trimAllToOpt).getOrElse("{0} / {1}")
          val fullSummary    =
            template
              .replace("{0}", matchingCards.size.toString)
              .replace("{1}", availableCards.size.toString)

          countElemOpt.foreach { el =>
            el.textContent = s"${matchingCards.size} / ${availableCards.size}"
            el.setAttribute("title", fullSummary)
            el.classList.remove(HiddenClass)
          }
          srStatusOpt.foreach(_.textContent = fullSummary)

          noResultsOpt.foreach(_.toggleClass(HiddenClass, matchingCards.nonEmpty || anyLoading))
        }
      }
    }

    def selectTab(key: String, focusTab: Boolean): Unit = {
      selectedTabKey = key
      scala.util.Try(window.sessionStorage.setItem(TabStorageKey, key))
      updateMatchSummary()
      if (focusTab) {
        tabButtons.find(_.attValueOpt("data-tab-key").contains(key)).foreach { btn =>
          btn.focus()
          scala.util.Try(btn.asInstanceOf[js.Dynamic].scrollIntoView(js.Dynamic.literal(block = "nearest", inline = "nearest")))
        }
      }
    }

    tabButtons.foreach { btn =>
      btn.addEventListener("click", (_: dom.MouseEvent) => {
        btn.attValueOpt("data-tab-key").foreach(selectTab(_, focusTab = false))
      })

      btn.addEventListener("keydown", (e: dom.KeyboardEvent) => {
        val isRtl = tablistOpt.exists(tl => window.getComputedStyle(tl).direction == "rtl")
        val nextDeltaOpt =
          e.key match {
            case "ArrowRight" => Some(if (isRtl) -1 else 1)
            case "ArrowLeft"  => Some(if (isRtl) 1 else -1)
            case _            => None
          }

        nextDeltaOpt match {
          case Some(delta) if tabButtons.nonEmpty =>
            e.preventDefault()
            val idx     = math.max(0, tabButtons.indexOf(btn))
            val nextIdx = (idx + delta + tabButtons.size) % tabButtons.size
            tabButtons(nextIdx).attValueOpt("data-tab-key").foreach(selectTab(_, focusTab = true))
          case None if e.key == "Home" && tabButtons.nonEmpty =>
            e.preventDefault()
            tabButtons.head.attValueOpt("data-tab-key").foreach(selectTab(_, focusTab = true))
          case None if e.key == "End" && tabButtons.nonEmpty =>
            e.preventDefault()
            tabButtons.last.attValueOpt("data-tab-key").foreach(selectTab(_, focusTab = true))
          case _ =>
        }
      })
    }

    // Sync tab state automatically when browser find-in-page reveals a `hidden="until-found"` panel
    workspacePanelOpt.foreach(_.addEventListener("beforematch", (_: dom.Event) => selectTab("workspace", focusTab = false)))
    demosPanelOpt    .foreach(_.addEventListener("beforematch", (_: dom.Event) => selectTab("demos",     focusTab = false)))

    AjaxClient.ajaxResponseProcessed.add(_ => updateMatchSummary())
    updateMatchSummary()

    inputOpt.foreach { inputElem =>

      var debounceTimerOpt: Option[timers.SetTimeoutHandle] = None

      def isComposingEvent(e: dom.Event): Boolean =
        js.DynamicImplicits.truthValue(e.asInstanceOf[js.Dynamic].isComposing)

      def syncInputChrome(): Unit = {
        val hasText   = inputElem.value.trimAllToOpt.isDefined
        val isFocused = document.activeElement == inputElem
        clearBtnOpt.foreach(_.toggleClass(HiddenClass, !hasText))
        kbdHintOpt .foreach(_.toggleClass(HiddenClass, hasText || isFocused))
      }

      def sendFilter(rawValue: String, immediate: Boolean): Unit = {
        debounceTimerOpt.foreach(timers.clearTimeout)
        debounceTimerOpt = None

        val trimmed = rawValue.trimAllToOpt.getOrElse("")
        if (immediate) {
          if (trimmed != lastSentQuery) {
            lastSentQuery = trimmed
            AjaxClient.fireEvent(
              AjaxEvent(
                eventName    = "fr-landing-filter",
                targetId     = "fr-form-model",
                form         = formElemOpt,
                properties   = Map("query" -> trimmed),
                showProgress = false
              )
            )
          }
          if (trimmed.isEmpty)
            updateMatchSummary()
        } else {
          debounceTimerOpt =
            Some(
              timers.setTimeout(FilterDebounceMs) {
                sendFilter(inputElem.value, immediate = true)
              }
            )
        }
      }

      def clearFilterAndFocus(): Unit = {
        inputElem.value = ""
        syncInputChrome()
        sendFilter("", immediate = true)
        inputElem.focus()
      }

      inputElem.addEventListener("focus", (_: dom.FocusEvent) => syncInputChrome())
      inputElem.addEventListener("blur",  (_: dom.FocusEvent) => syncInputChrome())

      inputElem.addEventListener("input", (e: dom.Event) => {
        syncInputChrome()
        if (!isComposingEvent(e))
          sendFilter(inputElem.value, immediate = inputElem.value.trimAllToOpt.isEmpty)
      })

      inputElem.addEventListener("compositionend", (_: dom.Event) => {
        syncInputChrome()
        sendFilter(inputElem.value, immediate = false)
      })

      inputElem.addEventListener("keydown", (e: dom.KeyboardEvent) => {
        if (e.key == "Enter") {
          e.preventDefault()
          if (!isComposingEvent(e) && e.keyCode != 229)
            sendFilter(inputElem.value, immediate = true)
        } else if (e.key == "Escape") {
          if (inputElem.value.nonEmpty) {
            e.preventDefault()
            e.stopPropagation()
            inputElem.value = ""
            syncInputChrome()
            sendFilter("", immediate = true)
          } else {
            inputElem.blur()
          }
        }
      })

      clearBtnOpt.foreach(_.addEventListener("click", (_: dom.MouseEvent) => clearFilterAndFocus()))

      filterBoxOpt.foreach(_.addEventListener("click", (e: dom.MouseEvent) => {
        val clickedInteractive =
          e.target match {
            case el: dom.Element => el.closestOpt("input, button").isDefined
            case _               => false
          }
        if (!clickedInteractive)
          inputElem.focus()
      }))

      document
        .querySelectorOpt(".orbeon .fr-landing-no-results .fr-landing-filter-reset")
        .foreach(_.addEventListener("click", (_: dom.MouseEvent) => clearFilterAndFocus()))

      document.addEventListener("keydown", (e: dom.KeyboardEvent) => {
        if (e.key == "/" && !e.ctrlKey && !e.metaKey && !e.altKey && document.querySelectorOpt("dialog[open]").isEmpty) {
          val inEditable =
            e.target match {
              case el: dom.Element =>
                el.closestOpt("input, textarea, select, [contenteditable='true'], [contenteditable='']").isDefined
              case _ =>
                false
            }
          if (!inEditable) {
            e.preventDefault()
            inputElem.focus()
            inputElem.select()
          }
        }
      })

      syncInputChrome()
    }
  }

  private def initQuickFilterShortcut(boxElem: html.Element): Unit =
    boxElem.querySelectorOpt("input").map(_.asInstanceOf[html.Input]).foreach { inputElem =>
      inputElem.addEventListener("keydown", (e: dom.KeyboardEvent) => {
        if (e.key == "Escape") {
          if (inputElem.value.nonEmpty) {
            e.preventDefault()
            e.stopPropagation()
            inputElem.closestOpt(".xforms-input") match {
              case Some(controlElem) =>
                DocumentAPI.setValue(controlElem, "")
              case None =>
                inputElem.value = ""
                inputElem.dispatchEvent(new dom.Event("input", new dom.EventInit { bubbles = true }))
            }
          } else {
            inputElem.blur()
          }
        }
      })

      document.addEventListener("keydown", (e: dom.KeyboardEvent) => {
        if (
          e.key == "/" &&
          !e.ctrlKey &&
          !e.metaKey &&
          !e.altKey &&
          boxElem.isConnected &&
          boxElem.offsetParent != null &&
          document.querySelectorOpt("dialog[open]").isEmpty
        ) {
          val inEditable =
            e.target match {
              case el: dom.Element =>
                el.closestOpt("input, textarea, select, [contenteditable='true'], [contenteditable='']").isDefined
              case _ =>
                false
            }
          if (!inEditable) {
            e.preventDefault()
            inputElem.focus()
            inputElem.select()
          }
        }
      })
    }

  private def initLandingCardExtraRows(gridElem: html.Element): Unit = {

    val ExtraRowClass       = "fr-card-extra-row"
    val ExtraRowHiddenClass = "fr-card-extra-row-hidden"
    val AllShownClass       = "fr-card-all-shown"
    val RowTopTolerancePx   = 8.0
    val FitTolerancePx      = 4.0

    case class CardMetrics(
      card         : html.Element,
      top          : Double,
      naturalHeight: Double,
      baseRows     : List[html.Element],
      extraRows    : List[html.Element],
      totalCountOpt: Option[Int]
    )

    var scheduled = false

    def visibleHeight(elemOpt: Option[html.Element]): Double =
      elemOpt.map(_.getBoundingClientRect().height).filter(_ > 0).getOrElse(0.0)

    def update(): Unit = {
      val cards =
        gridElem
          .querySelectorAllT(".card")
          .iterator
          .filter { card =>
            val r = card.getBoundingClientRect()
            r.width > 0 && r.height > 0
          }
          .toList

      if (cards.nonEmpty) {
        val metrics =
          cards.map { card =>
            val cardRect   = card.getBoundingClientRect()
            val borderV    = math.max(0.0, cardRect.height - card.clientHeight)
            val headerH    = visibleHeight(card.querySelectorOpt(":scope > .card-header"))
            val introH     = visibleHeight(card.querySelectorOpt(":scope > .fr-card-intro"))
            val quickLinkH = visibleHeight(card.querySelectorOpt(":scope > .fr-landing-quick-links-list"))
            val footerH    = visibleHeight(card.querySelectorOpt(":scope > .card-footer"))

            val listOpt =
              card
                .querySelectorOpt(":scope > ul.list-group:not(.xforms-hidden)")
                .filter(_.getBoundingClientRect().height > 0)

            val (baseListH, baseRows, extraRows) =
              listOpt match {
                case Some(list) =>
                  val dataRows      = list.childrenT.iterator.filter(el => el.hasClass("list-group-item") && !el.hasClass("fr-card-head")).toList
                  val base          = dataRows.filterNot(_.hasClass(ExtraRowClass))
                  val extra         = dataRows.filter(_.hasClass(ExtraRowClass))
                  val shownExtraSum = extra.iterator.filterNot(_.hasClass(ExtraRowHiddenClass)).map(_.getBoundingClientRect().height).sum
                  // Subtract 1px for `margin-block-end: -1px` on `ul.list-group`
                  val rawBaseListH  = math.max(0.0, list.getBoundingClientRect().height - shownExtraSum - 1.0)
                  (rawBaseListH, base, extra)
                case None =>
                  (0.0, Nil, Nil)
              }

            val naturalH      = borderV + headerH + introH + quickLinkH + baseListH + footerH
            val totalCountOpt = card.attValueOpt("data-total-count").flatMap(_.trimAllToOpt).flatMap(_.toIntOption)

            CardMetrics(
              card          = card,
              top           = cardRect.top,
              naturalHeight = naturalH,
              baseRows      = baseRows,
              extraRows     = extraRows,
              totalCountOpt = totalCountOpt
            )
          }

        val rows = scala.collection.mutable.ListBuffer.empty[scala.collection.mutable.ListBuffer[CardMetrics]]
        metrics.sortBy(_.top).foreach { cm =>
          rows.lastOption match {
            case Some(currentRow) if math.abs(cm.top - currentRow.head.top) <= RowTopTolerancePx =>
              currentRow += cm
            case _ =>
              rows += scala.collection.mutable.ListBuffer(cm)
          }
        }

        rows.foreach { rowCards =>
          val rowTargetHeight = rowCards.iterator.map(_.naturalHeight).max
          rowCards.foreach { cm =>
            if (cm.extraRows.nonEmpty && cm.baseRows.nonEmpty) {
              val availableSlack = rowTargetHeight - cm.naturalHeight
              val defaultRowH    = cm.baseRows.iterator.map(_.getBoundingClientRect().height).max
              var usedH          = 0.0
              var shownExtra     = 0

              cm.extraRows.foreach { row =>
                val rowH =
                  if (!row.hasClass(ExtraRowHiddenClass))
                    row.getBoundingClientRect().height
                  else
                    defaultRowH
                val canFit = (usedH + rowH) <= (availableSlack + FitTolerancePx)
                if (canFit) {
                  usedH      += rowH
                  shownExtra += 1
                }
                val shouldHide = !canFit
                if (row.hasClass(ExtraRowHiddenClass) != shouldHide)
                  row.toggleClass(ExtraRowHiddenClass, shouldHide)
              }

              val visibleCount = cm.baseRows.size + shownExtra
              val allShown     = cm.totalCountOpt.exists(total => total > 0 && visibleCount >= total)
              if (cm.card.hasClass(AllShownClass) != allShown)
                cm.card.toggleClass(AllShownClass, allShown)
            } else if (cm.card.hasClass(AllShownClass)) {
              cm.card.classList.remove(AllShownClass)
            }
          }
        }
      }
    }

    val scheduleUpdate: () => Unit = () =>
      if (!scheduled) {
        scheduled = true
        window.requestAnimationFrame { _ =>
          scheduled = false
          update()
        }
      }

    val resizeObserver = new ResizeObserver((_, _) => scheduleUpdate())
    resizeObserver.observe(gridElem)
    GlobalEventListenerSupport.addListener(window, "resize", (_: dom.Event) => scheduleUpdate())
    GlobalEventListenerSupport.addListener(gridElem, "load", (_: dom.Event) => scheduleUpdate(), useCapture = true)
    AjaxClient.ajaxResponseProcessed.add(_ => scheduleUpdate())

    scheduleUpdate()
  }

  def onPageContainsFormsMarkup(): Unit =
    XFormsApp.onPageContainsFormsMarkup()
}
