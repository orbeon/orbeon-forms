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
package org.orbeon.builder

import org.orbeon.builder.ToolboxSearch.{SearchEntry, ToolboxItem}
import org.orbeon.builder.rpc.FormBuilderRpcClient
import org.orbeon.fr.FormRunnerUtils
import org.orbeon.oxf.fr.ControlOps
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.web.DomEventNames
import org.orbeon.web.DomSupport.*
import org.orbeon.xforms.AjaxClient
import org.scalajs.dom
import org.scalajs.dom.html
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.*

import scala.scalajs.js


object QuickInsert {

  import Private.*

  sealed trait PaletteTab
  case object TabAll    extends PaletteTab
  case object TabGoTo   extends PaletteTab
  case object TabInsert extends PaletteTab

  sealed trait Mode
  case class Palette(tab: PaletteTab, preferInsert: Boolean)          extends Mode
  case class CellPopover(cellEl: html.Element, needsSelect: Boolean)  extends Mode

  case class FormControlItem(
    controlId   : String,
    controlName : String,
    label       : String,
    sectionLabel: String,
    cellElOpt   : Option[html.Element]
  ) {
    def displayLabel: String =
      if (label.nonEmpty) label else controlName

    def toSearchEntry: SearchEntry =
      SearchEntry(
        label      = displayLabel,
        category   = sectionLabel,
        extraTerms = controlName,
        enabled    = true
      )
  }

  sealed trait PaletteRow {
    def enabled: Boolean
  }
  case class GoToControlRow(item: FormControlItem) extends PaletteRow {
    def enabled: Boolean = true
  }
  case class InsertControlRow(item: ToolboxItem) extends PaletteRow {
    def enabled: Boolean = item.enabled
  }

  case class PopoverCoords(left: Double, top: Double, placedAbove: Boolean)

  private val PopoverWidth       = 290.0
  private val PopoverMaxHeight   = 320.0
  private val PopoverMargin      = 8.0
  private val PopoverGap         = 6.0
  private val AllTabSectionLimit = 8

  private var initialized       : Boolean               = false
  private var activeModeOpt     : Option[Mode]          = None
  private var overlayElOpt      : Option[html.Element]  = None
  private var panelElOpt        : Option[html.Element]  = None
  private var inputElOpt        : Option[html.Input]    = None
  private var tabsElOpt         : Option[html.Element]  = None
  private var listElOpt         : Option[html.Element]  = None
  private var toolboxItems      : List[ToolboxItem]     = Nil
  private var formControls      : List[FormControlItem] = Nil
  private var renderedRows      : List[PaletteRow]      = Nil
  private var selectedIndex     : Int                   = -1

  // In-canvas "+" trigger state
  private var hoveredCellOpt    : Option[html.Element]  = None
  private var activeCellElOpt   : Option[html.Element]  = None
  private var cellButtonElOpt   : Option[html.Button]   = None

  /**
   * Computes clamped viewport coordinates for an anchored cell popover,
   * flipping above the anchor when there isn't enough room below.
   */
  def computePopoverPosition(
    anchorLeft    : Double,
    anchorTop     : Double,
    anchorWidth   : Double,
    anchorHeight  : Double,
    viewportWidth : Double,
    viewportHeight: Double,
    popoverWidth  : Double = PopoverWidth,
    popoverHeight : Double = PopoverMaxHeight
  ): PopoverCoords = {
    val anchorBottom = anchorTop + anchorHeight
    val spaceBelow   = viewportHeight - anchorBottom - PopoverMargin
    val spaceAbove   = anchorTop - PopoverMargin
    val placedAbove  = spaceBelow < popoverHeight && spaceAbove > spaceBelow

    val rawTop =
      if (placedAbove) anchorTop - popoverHeight - PopoverGap
      else             anchorBottom + PopoverGap
    val clampedTop = Math.max(PopoverMargin, Math.min(rawTop, viewportHeight - popoverHeight - PopoverMargin))

    val centeredLeft = anchorLeft + (anchorWidth - popoverWidth) / 2.0
    val clampedLeft  = Math.max(PopoverMargin, Math.min(centeredLeft, viewportWidth - popoverWidth - PopoverMargin))

    PopoverCoords(left = clampedLeft, top = clampedTop, placedAbove = placedAbove)
  }

  /**
   * Collects all existing controls rendered in the Form Builder canvas.
   */
  def collectFormControls(): List[FormControlItem] =
    dom.document.querySelectorOpt(".fb-main").map { mainEl =>
      mainEl.querySelectorAllT(".fr-grid.fr-editable .fr-grid-td").flatMap { cellEl =>
        val sectionLabel =
          cellEl
            .closestOpt(".xbl-fr-section")
            .flatMap(_.querySelectorOpt(".fr-section-label .btn-link, .fr-section-label .xforms-output-output"))
            .flatMap(el => Option(el.textContent))
            .flatMap(_.trimAllToOpt)
            .getOrElse("")

        cellEl.childrenT.flatMap { elem =>
          if (
            ! elem.hasClass("gu-transit") &&
            ! elem.hasClass("fb-control-editor-left") &&
            ! elem.hasClass("fb-control-editor-right") &&
            ! elem.hasClass("fb-control-editor-top") &&
            elem.hasAttribute("id")
          ) {
            ControlOps.controlNameFromIdOpt(elem.id).map { controlName =>
              val label =
                elem.querySelectorOpt(".xforms-label")
                  .flatMap(el => Option(el.textContent))
                  .flatMap(_.trimAllToOpt)
                  .getOrElse("")
              FormControlItem(
                controlId    = elem.id,
                controlName  = controlName,
                label        = label,
                sectionLabel = sectionLabel,
                cellElOpt    = Some(cellEl)
              )
            }
          } else {
            None
          }
        }
      }.toList
    }.getOrElse(Nil)

  /**
   * Filters and ranks form controls against a query using `ToolboxSearch.matchScore`.
   */
  def filterAndRankFormControls(
    items: List[FormControlItem],
    query: String
  ): List[FormControlItem] = {
    val trimmed = query.trimAllToEmpty
    if (trimmed.isEmpty) {
      items
    } else {
      items.zipWithIndex
        .flatMap { case (item, idx) =>
          ToolboxSearch.matchScore(item.toSearchEntry, trimmed).map(score => (item, (score, idx)))
        }
        .sortBy(_._2)
        .map(_._1)
    }
  }

  def isOpen: Boolean = activeModeOpt.isDefined

  def activePaletteTabOpt: Option[PaletteTab] =
    activeModeOpt.collect { case Palette(tab, _) => tab }

  def openPalette(initialTab: PaletteTab = TabAll): Unit = {
    if (isBlockedByDialogOrViewMode())
      return

    close()

    val collectedToolbox  = ToolboxSearch.collectItems()
    val collectedControls = collectFormControls()
    if (collectedToolbox.isEmpty && collectedControls.isEmpty)
      return

    val preferInsert =
      dom.document
        .querySelectorOpt(".fb-main .fr-grid-td.fb-selected")
        .exists(isEmptyEditableCell)

    toolboxItems  = collectedToolbox
    formControls  = collectedControls
    activeModeOpt = Some(Palette(tab = initialTab, preferInsert = preferInsert))
    hideCellButton()

    val labels   = ToolboxSearch.uiLabels()
    val backdrop = createDiv("fb-quick-insert-backdrop")
    val panel    = createPanel(labels, isPalette = true)
    backdrop.appendChild(panel)

    backdrop.addEventListener("mousedown", (e: dom.MouseEvent) => {
      if (e.target == backdrop) {
        e.preventDefault()
        close()
      }
    })

    overlayParent().appendChild(backdrop)
    overlayElOpt = Some(backdrop)
    panelElOpt   = Some(panel)

    syncPaletteTabUi()
    renderList("")
    inputElOpt.foreach(_.focus())

    // Asynchronously load the complete list of form controls (including controls inside collapsed sections)
    val paletteMarker = overlayElOpt
    scala.util.Try(FormBuilderRpcClient.api.listFormControls()).foreach { future =>
      future.foreach { serverControls =>
        if (overlayElOpt.isDefined && (overlayElOpt eq paletteMarker) && serverControls.nonEmpty) {
          val domByName = collectedControls.map(c => c.controlName -> c).toMap
          formControls = serverControls.map { sc =>
            val domOpt = domByName.get(sc.controlName)
            FormControlItem(
              controlId    = domOpt.map(_.controlId).getOrElse(s"${sc.controlName}-control"),
              controlName  = sc.controlName,
              label        = sc.label,
              sectionLabel = sc.sectionLabel,
              cellElOpt    = domOpt.flatMap(_.cellElOpt)
            )
          }
          tabsElOpt
            .flatMap(_.querySelectorOpt("[data-fb-qi-tab='goto'] .fb-quick-insert-tab-count"))
            .foreach(_.textContent = formControls.size.toString)
          renderList(inputElOpt.map(_.value).getOrElse(""))
        }
      }
    }
  }

  def switchPaletteTab(tab: PaletteTab): Unit =
    activeModeOpt match {
      case Some(p: Palette) if p.tab != tab =>
        activeModeOpt = Some(p.copy(tab = tab))
        syncPaletteTabUi()
        renderList(inputElOpt.map(_.value).getOrElse(""))
        inputElOpt.foreach(_.focus())
      case _ =>
    }

  def openCellPopover(cellEl: html.Element): Unit = {
    if (isBlockedByDialogOrViewMode())
      return

    val needsSelect = ! cellEl.classList.contains("fb-selected")
    if (needsSelect)
      cellEl.click()

    close()

    val items =
      ToolboxSearch
        .collectItems()
        .filter(_.isControl)
        .map(item => if (needsSelect) item.copy(enabled = true) else item)
    if (items.isEmpty)
      return

    toolboxItems  = items
    formControls  = Nil
    activeModeOpt = Some(CellPopover(cellEl, needsSelect))
    hideCellButton()

    val labels = ToolboxSearch.uiLabels()
    val panel  = createPanel(labels, isPalette = false)
    positionPopover(panel, cellEl)

    overlayParent().appendChild(panel)
    overlayElOpt = Some(panel)
    panelElOpt   = Some(panel)

    renderList("")
    inputElOpt.foreach(_.focus())
  }

  def close(refreshCellButton: Boolean = true): Unit = {
    overlayElOpt.foreach(_.remove())
    overlayElOpt  = None
    panelElOpt    = None
    inputElOpt    = None
    tabsElOpt     = None
    listElOpt     = None
    activeModeOpt = None
    toolboxItems  = Nil
    formControls  = Nil
    renderedRows  = Nil
    selectedIndex = -1
    if (refreshCellButton)
      updateCellButton()
    else
      hideCellButton()
  }

  def init(): Unit = {
    if (initialized)
      return
    initialized = true

    // Track hovered cell via Form Builder's BlockCache
    Position.currentContainerChanged(
      containerCache = BlockCache.cellCache,
      wasCurrent     = _ => {
        hoveredCellOpt = None
        updateCellButton()
      },
      becomesCurrent = (cell: Block) => {
        hoveredCellOpt = Some(cell.el)
        updateCellButton()
      }
    )

    // Global keyboard shortcuts:
    // - Cmd/Ctrl + K: Unified Command Palette (All)
    // - Cmd/Ctrl + J: Unified Command Palette pre-filtered to Go to Control (replaces legacy dialog-search)
    // - Esc: Close active palette / popover
    dom.document.addEventListener(
      DomEventNames.KeyDown,
      (e: dom.KeyboardEvent) => {
        if (
          (e.metaKey || e.ctrlKey) &&
          ! e.altKey               &&
          ! e.shiftKey
        ) {
          e.key.toLowerCase match {
            case "k" if ! isBlockedByDialogOrViewMode() =>
              e.preventDefault()
              e.stopPropagation()
              activeModeOpt match {
                case Some(Palette(TabAll, _)) => close()
                case Some(_: Palette)         => switchPaletteTab(TabAll)
                case _                        => openPalette(TabAll)
              }
            case "j" if ! isBlockedByDialogOrViewMode() =>
              e.preventDefault()
              e.stopPropagation()
              activeModeOpt match {
                case Some(Palette(TabGoTo, _)) => close()
                case Some(_: Palette)          => switchPaletteTab(TabGoTo)
                case _                         => openPalette(TabGoTo)
              }
            case _ =>
          }
        } else if (e.key == "Escape" && isOpen) {
          e.preventDefault()
          e.stopPropagation()
          close()
        }
      },
      useCapture = true
    )

    // Close cell popover on outside mousedown
    dom.document.addEventListener(
      "mousedown",
      (e: dom.MouseEvent) => {
        activeModeOpt match {
          case Some(_: CellPopover) =>
            val insidePanel =
              Option(e.target)
                .collect { case el: html.Element => el }
                .exists(el => panelElOpt.exists(_.contains(el)) || cellButtonElOpt.exists(_.contains(el)))
            if (! insidePanel)
              close()
          case _ =>
        }
      }
    )

    // Re-sync empty-cell "+" button after clicks and Ajax updates
    dom.document.addEventListener(
      "click",
      (_: dom.Event) => {
        if (! isOpen)
          dom.window.setTimeout(() => updateCellButton(), 50)
      }
    )

    AjaxClient.ajaxResponseProcessed.add { _ =>
      if (! isOpen)
        updateCellButton()
    }

    // Reposition or hide "+" button when .fb-main scrolls or window resizes
    Position.onUnderPointerChange(() => {
      if (! isOpen)
        updateCellButton()
    })

    dom.window.addEventListener("resize", (_: dom.Event) => {
      if (isOpen) {
        activeModeOpt.foreach {
          case CellPopover(cellEl, _) =>
            panelElOpt.foreach(positionPopover(_, cellEl))
          case _: Palette =>
        }
      } else {
        updateCellButton()
      }
    })
  }

  private object Private {

    def overlayParent(): html.Element =
      dom.document.querySelectorOpt(".orbeon").getOrElse(dom.document.body)

    def positionPopover(panel: html.Element, cellEl: html.Element): Unit = {
      val rect           = cellEl.getBoundingClientRect()
      val viewportWidth  = dom.window.innerWidth
      val viewportHeight = dom.window.innerHeight
      val coords = computePopoverPosition(
        anchorLeft     = rect.left,
        anchorTop      = rect.top,
        anchorWidth    = rect.width,
        anchorHeight   = rect.height,
        viewportWidth  = viewportWidth,
        viewportHeight = viewportHeight
      )
      panel.style.left = s"${coords.left}px"
      if (coords.placedAbove) {
        val bottomPx = Math.max(PopoverMargin, viewportHeight - rect.top + PopoverGap)
        panel.style.top    = "auto"
        panel.style.bottom = s"${bottomPx}px"
      } else {
        panel.style.top    = s"${coords.top}px"
        panel.style.bottom = "auto"
      }
    }

    def createDiv(className: String): html.Div = {
      val div = dom.document.createElement("div").asInstanceOf[html.Div]
      div.className = className
      div
    }

    def isAppleOs(): Boolean =
      Option(dom.window.navigator.platform).exists(_.toLowerCase.contains("mac"))

    def isBlockedByDialogOrViewMode(): Boolean = {
      val hasOpenDialog = dom.document.querySelectorAll("dialog[open], .modal.in").length > 0
      val isViewMode    = dom.document.querySelectorOpt(".fr-mode-view").isDefined
      val hasToolbox    = dom.document.querySelectorOpt(".fb-toolbox").isDefined
      hasOpenDialog || isViewMode || ! hasToolbox
    }

    def isEmptyEditableCell(cellEl: html.Element): Boolean = {
      if (! cellEl.isConnected || FormRunnerUtils.isViewMode(cellEl))
        return false

      if (cellEl.closestOpt(".fr-grid.fr-editable").isEmpty)
        return false

      val hasControl = cellEl.childrenT.exists { elem =>
        ! elem.hasClass("gu-transit") &&
        ! elem.hasClass("fb-control-editor-left") &&
        ! elem.hasClass("fb-control-editor-right") &&
        ! elem.hasClass("fb-control-editor-top") &&
        elem.hasAttribute("id") &&
        ControlOps.controlNameFromIdOpt(elem.id).isDefined
      }

      ! hasControl
    }

    def ensureCellButton(): html.Button =
      cellButtonElOpt match {
        case Some(btn) if btn.isConnected => btn
        case _ =>
          val labels = ToolboxSearch.uiLabels()
          val btn    = dom.document.createElement("button").asInstanceOf[html.Button]
          btn.`type` = "button"
          btn.className = "fb-cell-quick-insert-btn xforms-hidden"
          btn.setAttribute("title", labels.insertControl)
          btn.setAttribute("aria-label", labels.insertControl)
          btn.innerHTML = """<i class="fa fa-plus fa-fw" aria-hidden="true"></i>"""

          btn.addEventListener("mousedown", (e: dom.MouseEvent) => {
            e.stopPropagation()
          })

          btn.addEventListener("click", (e: dom.MouseEvent) => {
            e.preventDefault()
            e.stopPropagation()
            activeCellElOpt.foreach(openCellPopover)
          })

          overlayParent().appendChild(btn)
          cellButtonElOpt = Some(btn)
          btn
      }

    def hideCellButton(): Unit = {
      activeCellElOpt = None
      cellButtonElOpt.foreach(_.classList.add("xforms-hidden"))
    }

    def updateCellButton(): Unit = {
      if (isOpen || isBlockedByDialogOrViewMode()) {
        hideCellButton()
        return
      }

      val candidateCellOpt =
        hoveredCellOpt
          .filter(isEmptyEditableCell)
          .orElse(
            dom.document
              .querySelectorOpt(".fb-main .fr-grid-td.fb-selected")
              .filter(isEmptyEditableCell)
          )

      candidateCellOpt match {
        case Some(cellEl) =>
          val rect = cellEl.getBoundingClientRect()
          val mainRectOpt = dom.document.querySelectorOpt(".fb-main").map(_.getBoundingClientRect())
          val inMainBounds = mainRectOpt.forall { mainRect =>
            val centerY = rect.top + rect.height / 2.0
            centerY >= mainRect.top + 8 && centerY <= mainRect.bottom - 8 &&
            rect.width >= 28 && rect.height >= 24
          }

          if (inMainBounds) {
            val btn = ensureCellButton()
            activeCellElOpt = Some(cellEl)
            btn.classList.remove("xforms-hidden")
            val btnSize = 24.0
            val left = rect.left + (rect.width - btnSize) / 2.0
            val top  = rect.top + (rect.height - btnSize) / 2.0
            btn.style.left = s"${left}px"
            btn.style.top  = s"${top}px"
          } else {
            hideCellButton()
          }
        case None =>
          hideCellButton()
      }
    }

    def createPanel(labels: ToolboxSearch.UiLabels, isPalette: Boolean): html.Div = {
      val panel = createDiv(
        if (isPalette) "fb-quick-insert-panel fb-quick-insert-palette"
        else           "fb-quick-insert-panel fb-quick-insert-popover"
      )
      panel.setAttribute("role", "dialog")
      panel.setAttribute("aria-label", if (isPalette) labels.palettePlaceholder else labels.insertControl)

      // Header with search input
      val header = createDiv("fb-quick-insert-header")
      val icon   = dom.document.createElement("i").asInstanceOf[html.Element]
      icon.className = "fa fa-search fa-fw fb-quick-insert-search-icon"
      icon.setAttribute("aria-hidden", "true")

      val input = dom.document.createElement("input").asInstanceOf[html.Input]
      input.`type`       = "text"
      input.className    = "fb-quick-insert-input"
      input.placeholder  = if (isPalette) labels.palettePlaceholder else labels.placeholder
      input.autocomplete = "off"
      input.setAttribute("spellcheck", "false")
      input.setAttribute("aria-label", input.placeholder)

      header.appendChild(icon)
      header.appendChild(input)

      if (isPalette) {
        val escHint = dom.document.createElement("kbd").asInstanceOf[html.Element]
        escHint.className   = "fb-quick-insert-esc"
        escHint.textContent = "Esc"
        header.appendChild(escHint)
      }

      panel.appendChild(header)

      // Mode pill tabs for unified Command Palette
      if (isPalette) {
        val tabsBar = createDiv("fb-quick-insert-tabs")
        tabsBar.setAttribute("role", "tablist")

        def makeTabBtn(tab: PaletteTab, tabKey: String, text: String, countOpt: Option[Int]): html.Button = {
          val btn = dom.document.createElement("button").asInstanceOf[html.Button]
          btn.`type`    = "button"
          btn.className = "fb-quick-insert-tab"
          btn.setAttribute("role", "tab")
          btn.setAttribute("data-fb-qi-tab", tabKey)

          val labelSpan = dom.document.createElement("span").asInstanceOf[html.Span]
          labelSpan.textContent = text
          btn.appendChild(labelSpan)

          countOpt.foreach { count =>
            val badge = dom.document.createElement("span").asInstanceOf[html.Span]
            badge.className   = "fb-quick-insert-tab-count"
            badge.textContent = count.toString
            btn.appendChild(badge)
          }

          btn.addEventListener("click", (e: dom.MouseEvent) => {
            e.preventDefault()
            switchPaletteTab(tab)
          })
          btn
        }

        tabsBar.appendChild(makeTabBtn(TabAll,    "all",    labels.tabAll,    None))
        tabsBar.appendChild(makeTabBtn(TabGoTo,   "goto",   labels.tabGoTo,   Some(formControls.size)))
        tabsBar.appendChild(makeTabBtn(TabInsert, "insert", labels.tabInsert, None))

        panel.appendChild(tabsBar)
        tabsElOpt = Some(tabsBar)
      }

      val list = createDiv("fb-quick-insert-list")
      list.setAttribute("role", "listbox")
      panel.appendChild(list)

      if (isPalette) {
        val modSym = if (isAppleOs()) "⌘" else "Ctrl+"
        val footer = createDiv("fb-quick-insert-footer")
        footer.innerHTML =
          s"""<span><kbd>↑</kbd><kbd>↓</kbd></span>""" +
          s"""<span><kbd>↵</kbd></span>""" +
          s"""<span><kbd>${modSym}↵</kbd> ${labels.openSettings}</span>""" +
          s"""<span><kbd>Tab</kbd></span>"""
        panel.appendChild(footer)
      }

      inputElOpt = Some(input)
      listElOpt  = Some(list)

      input.addEventListener("input", (_: dom.Event) => {
        renderList(input.value)
      })

      input.addEventListener("keydown", (e: dom.KeyboardEvent) => {
        e.key match {
          case "ArrowDown" =>
            e.preventDefault()
            moveSelection(1)
          case "ArrowUp" =>
            e.preventDefault()
            moveSelection(-1)
          case "Tab" if isPalette =>
            e.preventDefault()
            cyclePaletteTab(backward = e.shiftKey)
          case "Enter" =>
            e.preventDefault()
            activateSelected(openSettings = e.metaKey || e.ctrlKey || e.shiftKey)
          case "Escape" =>
            e.preventDefault()
            e.stopPropagation()
            close()
          case _ =>
        }
      })

      panel
    }

    def cyclePaletteTab(backward: Boolean): Unit =
      activeModeOpt.foreach {
        case Palette(currentTab, _) =>
          val order = List(TabAll, TabGoTo, TabInsert)
          val idx   = order.indexOf(currentTab)
          val next  = order(Math.floorMod(idx + (if (backward) -1 else 1), order.length))
          switchPaletteTab(next)
        case _ =>
      }

    def syncPaletteTabUi(): Unit = {
      val labels = ToolboxSearch.uiLabels()
      activeModeOpt.foreach {
        case Palette(activeTab, _) =>
          val activeKey = activeTab match {
            case TabAll    => "all"
            case TabGoTo   => "goto"
            case TabInsert => "insert"
          }
          tabsElOpt.foreach { tabsBar =>
            tabsBar.querySelectorAllT(".fb-quick-insert-tab").foreach { tabBtn =>
              val isActive = Option(tabBtn.getAttribute("data-fb-qi-tab")).contains(activeKey)
              tabBtn.classList.toggle("active", isActive)
              tabBtn.setAttribute("aria-selected", isActive.toString)
            }
          }
          inputElOpt.foreach { input =>
            input.placeholder = activeTab match {
              case TabAll    => labels.palettePlaceholder
              case TabGoTo   => labels.goToPlaceholder
              case TabInsert => labels.placeholder
            }
          }
        case _ =>
      }
    }

    def renderList(query: String): Unit =
      listElOpt.foreach { listEl =>
        listEl.innerHTML = ""

        val labels  = ToolboxSearch.uiLabels()
        val trimmed = query.trimAllToEmpty
        val recent  = ToolboxSearch.recentItemIds

        var flatBuilder = List.newBuilder[PaletteRow]
        var rowIdx      = 0

        def appendGroupHeader(title: String): Unit =
          if (title.nonEmpty) {
            val header = createDiv("fb-quick-insert-group-title")
            header.textContent = title
            listEl.appendChild(header)
          }

        def appendGoToRow(item: FormControlItem): Unit = {
          val idx = rowIdx
          rowIdx += 1
          flatBuilder += GoToControlRow(item)

          val row = dom.document.createElement("div").asInstanceOf[html.Div]
          row.className = "fb-quick-insert-item fb-quick-insert-goto-item"
          row.setAttribute("role", "option")
          row.setAttribute("data-fb-qi-index", idx.toString)

          val leftSpan = dom.document.createElement("span").asInstanceOf[html.Span]
          leftSpan.className = "fb-quick-insert-item-main"

          val iconWrap = dom.document.createElement("span").asInstanceOf[html.Span]
          iconWrap.className = "fb-quick-insert-item-icon"
          iconWrap.innerHTML = """<i class="fa fa-crosshairs fa-fw" aria-hidden="true"></i>"""
          leftSpan.appendChild(iconWrap)

          val labelSpan = dom.document.createElement("span").asInstanceOf[html.Span]
          labelSpan.className   = "fb-quick-insert-item-label"
          labelSpan.textContent = item.displayLabel
          leftSpan.appendChild(labelSpan)

          if (item.label.nonEmpty && item.label != item.controlName) {
            val nameBadge = dom.document.createElement("code").asInstanceOf[html.Element]
            nameBadge.className   = "fb-quick-insert-control-name"
            nameBadge.textContent = item.controlName
            leftSpan.appendChild(nameBadge)
          }

          row.appendChild(leftSpan)

          val rightSpan = dom.document.createElement("span").asInstanceOf[html.Span]
          rightSpan.className = "fb-quick-insert-item-meta"

          if (item.sectionLabel.nonEmpty) {
            val secSpan = dom.document.createElement("span").asInstanceOf[html.Span]
            secSpan.className   = "fb-quick-insert-item-category"
            secSpan.textContent = item.sectionLabel
            rightSpan.appendChild(secSpan)
          }

          val settingsBtn = dom.document.createElement("button").asInstanceOf[html.Button]
          settingsBtn.`type`    = "button"
          settingsBtn.className = "fb-quick-insert-settings-btn"
          val modHint = if (isAppleOs()) "⌘↵" else "Ctrl+↵"
          settingsBtn.setAttribute("title", s"${labels.openSettings} ($modHint)")
          settingsBtn.setAttribute("aria-label", labels.openSettings)
          settingsBtn.innerHTML = """<i class="fa fa-cog fa-fw" aria-hidden="true"></i>"""

          settingsBtn.addEventListener("click", (e: dom.MouseEvent) => {
            e.preventDefault()
            e.stopPropagation()
            activateGoToControl(item, openSettings = true)
          })
          rightSpan.appendChild(settingsBtn)

          row.appendChild(rightSpan)

          row.addEventListener("mouseenter", (_: dom.MouseEvent) => {
            setSelectedIndex(idx, scrollIntoView = false)
          })

          row.addEventListener("click", (e: dom.MouseEvent) => {
            e.preventDefault()
            activateGoToControl(item, openSettings = e.metaKey || e.ctrlKey || e.shiftKey)
          })

          listEl.appendChild(row)
        }

        def appendInsertRow(item: ToolboxItem, showCategory: Boolean): Unit = {
          val idx = rowIdx
          rowIdx += 1
          flatBuilder += InsertControlRow(item)

          val row = dom.document.createElement("button").asInstanceOf[html.Button]
          row.`type` = "button"
          row.className =
            if (item.enabled) "fb-quick-insert-item"
            else              "fb-quick-insert-item fb-quick-insert-item-disabled"
          row.setAttribute("role", "option")
          row.setAttribute("data-fb-qi-index", idx.toString)
          if (! item.enabled)
            row.disabled = true

          val leftSpan = dom.document.createElement("span").asInstanceOf[html.Span]
          leftSpan.className = "fb-quick-insert-item-main"

          if (item.iconHtml.nonEmpty) {
            val iconWrap = dom.document.createElement("span").asInstanceOf[html.Span]
            iconWrap.className = "fb-quick-insert-item-icon"
            iconWrap.innerHTML = item.iconHtml
            leftSpan.appendChild(iconWrap)
          }

          val labelSpan = dom.document.createElement("span").asInstanceOf[html.Span]
          labelSpan.className   = "fb-quick-insert-item-label"
          labelSpan.textContent = item.label
          leftSpan.appendChild(labelSpan)

          row.appendChild(leftSpan)

          val rightSpan = dom.document.createElement("span").asInstanceOf[html.Span]
          rightSpan.className = "fb-quick-insert-item-meta"

          if (showCategory && item.category.nonEmpty) {
            val catSpan = dom.document.createElement("span").asInstanceOf[html.Span]
            catSpan.className   = "fb-quick-insert-item-category"
            catSpan.textContent = item.category
            rightSpan.appendChild(catSpan)
          }

          item.shortcut.foreach { sc =>
            val kbd = dom.document.createElement("kbd").asInstanceOf[html.Element]
            kbd.textContent = sc
            rightSpan.appendChild(kbd)
          }

          if (rightSpan.childElementCount > 0)
            row.appendChild(rightSpan)

          row.addEventListener("mouseenter", (_: dom.MouseEvent) => {
            if (item.enabled)
              setSelectedIndex(idx, scrollIntoView = false)
          })

          row.addEventListener("click", (e: dom.MouseEvent) => {
            e.preventDefault()
            if (item.enabled)
              activateInsertItem(item)
          })

          listEl.appendChild(row)
        }

        def renderInsertOnlyList(): Unit = {
          val filtered = ToolboxSearch.filterAndRankItems(toolboxItems, trimmed, recent)
          if (filtered.nonEmpty) {
            if (trimmed.isEmpty) {
              val recentItems = recent.flatMap(id => filtered.find(_.id == id))
              if (recentItems.nonEmpty) {
                appendGroupHeader(labels.recent)
                recentItems.foreach(item => appendInsertRow(item, showCategory = false))
              }
              var lastCategory = ""
              filtered.foreach { item =>
                if (item.category != lastCategory) {
                  lastCategory = item.category
                  appendGroupHeader(item.category)
                }
                appendInsertRow(item, showCategory = false)
              }
            } else {
              filtered.foreach(item => appendInsertRow(item, showCategory = true))
            }
          }
        }

        def renderGoToOnlyList(): Unit = {
          val filtered = filterAndRankFormControls(formControls, trimmed)
          if (filtered.nonEmpty) {
            if (trimmed.isEmpty) {
              var lastSection = ""
              filtered.foreach { item =>
                val sec = if (item.sectionLabel.nonEmpty) item.sectionLabel else labels.goToGroup
                if (sec != lastSection) {
                  lastSection = sec
                  appendGroupHeader(sec)
                }
                appendGoToRow(item)
              }
            } else {
              filtered.foreach(appendGoToRow)
            }
          }
        }

        activeModeOpt match {
          case Some(Palette(TabAll, preferInsert)) =>
            val matchedGoTo   = filterAndRankFormControls(formControls, trimmed)
            val matchedInsert = ToolboxSearch.filterAndRankItems(toolboxItems, trimmed, recent)

            val goToToShow =
              if (trimmed.isEmpty) matchedGoTo.take(AllTabSectionLimit)
              else                 matchedGoTo

            val insertToShow =
              if (trimmed.isEmpty) {
                val recentItems = recent.flatMap(id => matchedInsert.find(_.id == id))
                val rest        = matchedInsert.filterNot(i => recentItems.exists(_.id == i.id))
                (recentItems ++ rest).take(AllTabSectionLimit)
              } else {
                matchedInsert
              }

            def renderGoToSection(): Unit =
              if (goToToShow.nonEmpty) {
                appendGroupHeader(labels.goToGroup)
                goToToShow.foreach(appendGoToRow)
              }

            def renderInsertSection(): Unit =
              if (insertToShow.nonEmpty) {
                appendGroupHeader(labels.insertGroup)
                insertToShow.foreach(item => appendInsertRow(item, showCategory = true))
              }

            if (preferInsert) {
              renderInsertSection()
              renderGoToSection()
            } else {
              renderGoToSection()
              renderInsertSection()
            }

          case Some(Palette(TabGoTo, _)) =>
            renderGoToOnlyList()

          case Some(Palette(TabInsert, _)) | Some(_: CellPopover) =>
            renderInsertOnlyList()

          case None =>
        }

        renderedRows = flatBuilder.result()
        if (renderedRows.isEmpty) {
          selectedIndex = -1
          val empty = createDiv("fb-quick-insert-empty")
          empty.textContent = labels.noResults
          listEl.appendChild(empty)
        } else {
          val firstEnabledIdx = renderedRows.indexWhere(_.enabled)
          setSelectedIndex(if (firstEnabledIdx >= 0) firstEnabledIdx else 0, scrollIntoView = false)
        }
      }

    def setSelectedIndex(idx: Int, scrollIntoView: Boolean): Unit =
      listElOpt.foreach { listEl =>
        listEl.querySelectorOpt(".fb-quick-insert-item-selected").foreach { prev =>
          prev.classList.remove("fb-quick-insert-item-selected")
          prev.setAttribute("aria-selected", "false")
        }
        selectedIndex = idx
        if (idx >= 0 && idx < renderedRows.length) {
          listEl.querySelectorOpt(s"[data-fb-qi-index='$idx']").foreach { el =>
            el.classList.add("fb-quick-insert-item-selected")
            el.setAttribute("aria-selected", "true")
            if (scrollIntoView)
              el.asInstanceOf[js.Dynamic].scrollIntoView(js.Dynamic.literal(block = "nearest"))
          }
        }
      }

    def moveSelection(delta: Int): Unit = {
      if (renderedRows.isEmpty)
        return

      val enabledIndices = renderedRows.zipWithIndex.collect { case (row, idx) if row.enabled => idx }
      val pool           = if (enabledIndices.nonEmpty) enabledIndices else renderedRows.indices.toList

      val currentPos = pool.indexOf(selectedIndex)
      val nextPos =
        if (currentPos < 0)
          if (delta >= 0) 0 else pool.length - 1
        else
          Math.floorMod(currentPos + delta, pool.length)

      setSelectedIndex(pool(nextPos), scrollIntoView = true)
    }

    def activateSelected(openSettings: Boolean): Unit =
      if (selectedIndex >= 0 && selectedIndex < renderedRows.length) {
        renderedRows(selectedIndex) match {
          case GoToControlRow(item) =>
            activateGoToControl(item, openSettings)
          case InsertControlRow(item) if item.enabled =>
            activateInsertItem(item)
          case _ =>
        }
      }

    def activateGoToControl(item: FormControlItem, openSettings: Boolean): Unit = {
      close()

      // If Section Focus is active, clear it so the target control's section is visible
      if (SectionFocus.focusedSectionStaticIdOpt.isDefined)
        SectionFocus.clearFocus()

      FormBuilderRpcClient.api.selectControlByName(
        controlName  = item.controlName,
        openSettings = openSettings
      ).failed.foreach { _ =>
        item.cellElOpt.foreach { cellEl =>
          cellEl.asInstanceOf[js.Dynamic].scrollIntoView(js.Dynamic.literal(block = "center"))
          cellEl.click()
        }
      }
    }

    def activateInsertItem(item: ToolboxItem): Unit = {
      val modeOpt = activeModeOpt
      ToolboxSearch.recordRecentItem(item.id)
      close(refreshCellButton = false)

      modeOpt match {
        case Some(CellPopover(_, needsSelect)) if needsSelect =>
          AjaxClient.allEventsProcessedF("quickInsertCell").foreach { _ =>
            item.button.click()
          }
        case _ =>
          item.button.click()
      }
    }
  }
}
