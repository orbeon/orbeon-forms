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

import org.orbeon.builder.ToolboxSearch.ToolboxItem
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

  sealed trait Mode
  case object Palette                                                  extends Mode
  case class  CellPopover(cellEl: html.Element, needsSelect: Boolean)  extends Mode

  case class PopoverCoords(left: Double, top: Double, placedAbove: Boolean)

  private val PopoverWidth       = 290.0
  private val PopoverMaxHeight   = 320.0
  private val PopoverMargin      = 8.0
  private val PopoverGap         = 6.0

  private var activeModeOpt     : Option[Mode]          = None
  private var overlayElOpt      : Option[html.Element]  = None
  private var panelElOpt        : Option[html.Element]  = None
  private var inputElOpt        : Option[html.Input]    = None
  private var listElOpt         : Option[html.Element]  = None
  private var allItems          : List[ToolboxItem]     = Nil
  private var renderedItems     : List[ToolboxItem]     = Nil
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

  def isOpen: Boolean = activeModeOpt.isDefined

  def openPalette(): Unit = {
    if (isBlockedByDialogOrViewMode())
      return

    close()
    hideCellButton()

    val items = ToolboxSearch.collectItems()
    if (items.isEmpty)
      return

    allItems      = items
    activeModeOpt = Some(Palette)

    val labels = ToolboxSearch.uiLabels()

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

    renderList("")
    inputElOpt.foreach(_.focus())
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

    allItems      = items
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

  def close(): Unit = {
    overlayElOpt.foreach(_.remove())
    overlayElOpt  = None
    panelElOpt    = None
    inputElOpt    = None
    listElOpt     = None
    activeModeOpt = None
    allItems      = Nil
    renderedItems = Nil
    selectedIndex = -1
    updateCellButton()
  }

  private var initialized       : Boolean               = false

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

    // Global keyboard shortcut: Cmd/Ctrl + K opens Command Palette; Esc closes active overlay
    dom.document.addEventListener(
      DomEventNames.KeyDown,
      (e: dom.KeyboardEvent) => {
        if (
          (e.metaKey || e.ctrlKey) &&
          ! e.altKey               &&
          ! e.shiftKey             &&
          e.key.toLowerCase == "k"
        ) {
          if (! isBlockedByDialogOrViewMode()) {
            e.preventDefault()
            e.stopPropagation()
            if (activeModeOpt.contains(Palette)) close()
            else                                 openPalette()
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

    // Re-sync empty-cell "+" button after clicks and Ajax updates (e.g., selecting a cell or inserting/deleting a control)
    dom.document.addEventListener(
      "click",
      (_: dom.Event) => {
        if (! isOpen)
          dom.window.setTimeout(() => updateCellButton(), 50)
      }
    )

    AjaxClient.ajaxResponseReceived.add { _ =>
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
          case Palette =>
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
            // Prevent losing focus / dragging interference
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
      panel.setAttribute("aria-label", labels.insertControl)

      // Header with search input
      val header = createDiv("fb-quick-insert-header")
      val icon   = dom.document.createElement("i").asInstanceOf[html.Element]
      icon.className = "fa fa-search fa-fw fb-quick-insert-search-icon"
      icon.setAttribute("aria-hidden", "true")

      val input = dom.document.createElement("input").asInstanceOf[html.Input]
      input.`type`       = "text"
      input.className    = "fb-quick-insert-input"
      input.placeholder  = labels.placeholder
      input.autocomplete = "off"
      input.setAttribute("spellcheck", "false")
      input.setAttribute("aria-label", labels.placeholder)

      header.appendChild(icon)
      header.appendChild(input)

      if (isPalette) {
        val escHint = dom.document.createElement("kbd").asInstanceOf[html.Element]
        escHint.className   = "fb-quick-insert-esc"
        escHint.textContent = "Esc"
        header.appendChild(escHint)
      }

      val list = createDiv("fb-quick-insert-list")
      list.setAttribute("role", "listbox")

      panel.appendChild(header)
      panel.appendChild(list)

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
          case "Enter" =>
            e.preventDefault()
            activateSelected()
          case "Escape" =>
            e.preventDefault()
            e.stopPropagation()
            close()
          case _ =>
        }
      })

      panel
    }

    def renderList(query: String): Unit =
      listElOpt.foreach { listEl =>
        listEl.innerHTML = ""

        val labels   = ToolboxSearch.uiLabels()
        val trimmed  = query.trimAllToEmpty
        val recent   = ToolboxSearch.recentItemIds
        val filtered = ToolboxSearch.filterAndRankItems(allItems, trimmed, recent)

        if (filtered.isEmpty) {
          renderedItems = Nil
          selectedIndex = -1
          val empty = createDiv("fb-quick-insert-empty")
          empty.textContent = labels.noResults
          listEl.appendChild(empty)
        } else {
          var flatBuilder = List.newBuilder[ToolboxItem]
          var rowIdx      = 0

          def appendGroupHeader(title: String): Unit =
            if (title.nonEmpty) {
              val header = createDiv("fb-quick-insert-group-title")
              header.textContent = title
              listEl.appendChild(header)
            }

          def appendItemRow(item: ToolboxItem, showCategory: Boolean): Unit = {
            val idx = rowIdx
            rowIdx += 1
            flatBuilder += item

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
                activateItem(item)
            })

            listEl.appendChild(row)
          }

          if (trimmed.isEmpty) {
            // Show Recent section at top when available
            val recentItems = recent.flatMap(id => filtered.find(_.id == id))
            if (recentItems.nonEmpty) {
              appendGroupHeader(labels.recent)
              recentItems.foreach(item => appendItemRow(item, showCategory = false))
            }

            // Group remaining items by category in DOM order
            var lastCategory = ""
            filtered.foreach { item =>
              if (item.category != lastCategory) {
                lastCategory = item.category
                appendGroupHeader(item.category)
              }
              appendItemRow(item, showCategory = false)
            }
          } else {
            // Ranked flat list with category badges
            filtered.foreach(item => appendItemRow(item, showCategory = true))
          }

          renderedItems = flatBuilder.result()
          val firstEnabledIdx = renderedItems.indexWhere(_.enabled)
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
        if (idx >= 0 && idx < renderedItems.length) {
          listEl.querySelectorOpt(s"[data-fb-qi-index='$idx']").foreach { el =>
            el.classList.add("fb-quick-insert-item-selected")
            el.setAttribute("aria-selected", "true")
            if (scrollIntoView)
              el.asInstanceOf[js.Dynamic].scrollIntoView(js.Dynamic.literal(block = "nearest"))
          }
        }
      }

    def moveSelection(delta: Int): Unit = {
      if (renderedItems.isEmpty)
        return

      val enabledIndices = renderedItems.zipWithIndex.collect { case (item, idx) if item.enabled => idx }
      val pool           = if (enabledIndices.nonEmpty) enabledIndices else renderedItems.indices.toList

      val currentPos = pool.indexOf(selectedIndex)
      val nextPos =
        if (currentPos < 0)
          if (delta >= 0) 0 else pool.length - 1
        else
          Math.floorMod(currentPos + delta, pool.length)

      setSelectedIndex(pool(nextPos), scrollIntoView = true)
    }

    def activateSelected(): Unit =
      if (selectedIndex >= 0 && selectedIndex < renderedItems.length) {
        val item = renderedItems(selectedIndex)
        if (item.enabled)
          activateItem(item)
      }

    def activateItem(item: ToolboxItem): Unit = {
      val modeOpt = activeModeOpt
      ToolboxSearch.recordRecentItem(item.id)
      close()

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
