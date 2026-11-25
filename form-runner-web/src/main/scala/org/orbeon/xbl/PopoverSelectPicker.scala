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
package org.orbeon.xbl

import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.web.DomSupport
import org.orbeon.web.DomSupport.*
import org.orbeon.xforms.XFormsUiEvents.ValueChangeInternalEvent
import org.orbeon.xforms.*
import org.orbeon.xforms.facade.XBLCompanion
import org.scalajs.dom
import org.scalajs.dom.{MutationObserver, html}

import scala.scalajs.js


object PopoverSelectPicker {

  import Private.*

  case class Category(
    id         : String,
    shortLabel : String,
    fullLabel  : String
  )

  case class Item(
    value         : String,
    label         : String,
    badgeOpt      : Option[String] = None,
    hintOpt       : Option[String] = None,
    categoryId    : String         = "",
    categoryLabel : String         = "",
    extraKeywords : String         = "",
    cssClass      : String         = "",
    isFreeEntry   : Boolean        = false
  ) {
    val normLabel    : String = Select1Search.normalizeText(stripLeadingEmoji(label))
    val normBadge    : String = badgeOpt.map(Select1Search.normalizeText).getOrElse("")
    val normHint     : String = hintOpt.map(Select1Search.normalizeText).getOrElse("")
    val normKeywords : String = Select1Search.normalizeText(extraKeywords)
    val normCat      : String = Select1Search.normalizeText(categoryLabel)
    val searchText   : String = s"$normLabel $normBadge $normHint $normKeywords $normCat"
  }

  case class Config(
    triggerBtn         : html.Element,
    hostContainer      : html.Element,
    currentValue       : String,
    searchPlaceholder  : String,
    emptyMessage       : String,
    allLabel           : String,
    categories         : List[Category],
    items              : List[Item],
    allowOpenSelection : Boolean = false,
    minWidthPx         : Double  = 300.0,
    extraPopoverClass  : String  = "",
    onSelect           : Item => Unit
  )

  def isOpenFor(triggerBtn: html.Element): Boolean =
    activeStateOpt.exists(_.config.triggerBtn == triggerBtn)

  def close(restoreFocus: Boolean): Unit =
    closePopover(restoreFocus)

  def open(config: Config): Unit = {
    installGlobalListenersOnce()
    openPopover(config)
  }

  def stripLeadingEmoji(s: String): String =
    s.dropWhile(c => c.isSurrogate || c.isWhitespace)

  // Register XBL companions for static and open single-selection searchable dropdowns
  def init(): Unit = {
    installGlobalListenersOnce()
    XFormsXbl.declareCompanion(name = "fr|dropdown-select1-search", js.constructorOf[DropdownSelect1SearchCompanion])
    XFormsXbl.declareCompanion(name = "fr|open-select1-search"    , js.constructorOf[DropdownSelect1SearchCompanion])
  }

  private object Private {

    val ViewportMarginPx = 8.0
    val TriggerGapPx     = 4.0
    val UnitSeparator    = "\u001f"

    var listenersInstalled: Boolean                    = false
    var activeStateOpt    : Option[ActivePopoverState] = None

    lazy val hasNativePopover: Boolean = {
      val proto: js.Dynamic = js.Dynamic.global.HTMLElement.prototype
      if (js.isUndefined(proto))
        false
      else
        proto.asInstanceOf[js.Object].hasOwnProperty("popover")
    }

    class ActivePopoverState(
      val config        : Config,
      val popoverEl     : html.Element,
      val searchInput   : html.Input,
      val clearBtn      : html.Button,
      val pillsBarOpt   : Option[html.Element],
      val listEl        : html.Element,
      var selectedCat   : String,
      var filteredItems : List[Item],
      var activeIndex   : Int
    )

    def installGlobalListenersOnce(): Unit =
      if (! listenersInstalled) {
        listenersInstalled = true

        AjaxClient.ajaxResponseProcessed.add { _ =>
          activeStateOpt.foreach { state =>
            if (! dom.document.contains(state.config.triggerBtn))
              closePopover(restoreFocus = false)
          }
        }

        dom.document.addEventListener(
          "click",
          (event: dom.MouseEvent) => {
            activeStateOpt.foreach { state =>
              event.targetOpt.foreach { target =>
                if (! state.popoverEl.contains(target) && ! state.config.triggerBtn.contains(target))
                  closePopover(restoreFocus = false)
              }
            }
          }
        )

        dom.window.addEventListener(
          "resize",
          (_: dom.Event) => activeStateOpt.foreach(positionPopover)
        )

        dom.document.addEventListener(
          "scroll",
          (event: dom.Event) => {
            activeStateOpt.foreach { state =>
              val scrolledInsidePopover =
                event.targetOpt.exists(t => state.popoverEl.contains(t))
              if (! scrolledInsidePopover) {
                if (state.config.triggerBtn.isVisible)
                  positionPopover(state)
                else
                  closePopover(restoreFocus = false)
              }
            }
          },
          useCapture = true
        )
      }

    def openPopover(config: Config): Unit = {
      closePopover(restoreFocus = false)

      val popoverEl = dom.document.createElement("div").asInstanceOf[html.Element]
      popoverEl.className =
        "fr-process-action-popover fr-select1-picker-popover" +
          (if (config.extraPopoverClass.nonEmpty) s" ${config.extraPopoverClass}" else "")

      if (hasNativePopover)
        popoverEl.setAttribute("popover", "auto")

      // Search header
      val searchRow = dom.document.createElement("div").asInstanceOf[html.Element]
      searchRow.className = "fr-process-picker-search-row"

      val searchIcon = dom.document.createElement("i").asInstanceOf[html.Element]
      searchIcon.className = "fa fa-fw fa-search fr-process-picker-search-icon"
      searchIcon.setAttribute("aria-hidden", "true")

      val searchInput = dom.document.createElement("input").asInstanceOf[html.Input]
      searchInput.`type`      = "search"
      searchInput.className   = "fr-process-picker-search-input"
      searchInput.placeholder = config.searchPlaceholder
      searchInput.setAttribute("aria-label", config.searchPlaceholder)
      searchInput.setAttribute("autocomplete", "off")
      searchInput.setAttribute("spellcheck", "false")

      val clearBtn = dom.document.createElement("button").asInstanceOf[html.Button]
      clearBtn.`type`    = "button"
      clearBtn.className = "fr-process-picker-clear-btn xforms-hidden"
      clearBtn.tabIndex  = -1
      clearBtn.setAttribute("aria-label", "Clear")
      val clearIcon = dom.document.createElement("i").asInstanceOf[html.Element]
      clearIcon.className = "fa fa-fw fa-times"
      clearIcon.setAttribute("aria-hidden", "true")
      clearBtn.appendChild(clearIcon)

      searchRow.appendChild(searchIcon)
      searchRow.appendChild(searchInput)
      searchRow.appendChild(clearBtn)
      popoverEl.appendChild(searchRow)

      // Category pills bar (shown automatically only when >= 2 non-empty categories exist)
      val nonEmptyCategories =
        config.categories.filter(c => config.items.exists(_.categoryId == c.id))

      val pillsBarOpt =
        if (nonEmptyCategories.size >= 2) {
          val pillsBar = dom.document.createElement("div").asInstanceOf[html.Element]
          pillsBar.className = "fr-process-picker-pills"
          popoverEl.appendChild(pillsBar)
          Some(pillsBar)
        } else {
          None
        }

      // Scrollable options list
      val listEl = dom.document.createElement("div").asInstanceOf[html.Element]
      listEl.className = "fr-process-picker-list"
      listEl.setAttribute("role", "listbox")
      popoverEl.appendChild(listEl)

      config.hostContainer.appendChild(popoverEl)

      val initialIndex = math.max(0, config.items.indexWhere(_.value == config.currentValue))

      val state = new ActivePopoverState(
        config        = config,
        popoverEl     = popoverEl,
        searchInput   = searchInput,
        clearBtn      = clearBtn,
        pillsBarOpt   = pillsBarOpt,
        listEl        = listEl,
        selectedCat   = "",
        filteredItems = config.items,
        activeIndex   = if (config.items.isEmpty) -1 else initialIndex
      )

      activeStateOpt = Some(state)
      config.triggerBtn.setAttribute("aria-expanded", "true")
      config.triggerBtn.classList.add("fr-process-action-trigger-open")
      config.triggerBtn.classList.add("fr-select1-picker-trigger-open")

      initPills(state, nonEmptyCategories)
      renderList(state, scrollActiveIntoView = true)

      if (hasNativePopover) {
        popoverEl.addEventListener(
          "toggle",
          (ev: dom.Event) => {
            val newState = ev.asInstanceOf[js.Dynamic].newState.asInstanceOf[js.UndefOr[String]].getOrElse("")
            if (newState == "closed" && activeStateOpt.contains(state))
              closePopover(restoreFocus = false)
          }
        )
        popoverEl.asInstanceOf[js.Dynamic].showPopover()
      }

      positionPopover(state)

      clearBtn.addEventListener("mousedown", (ev: dom.MouseEvent) => ev.preventDefault())
      clearBtn.addEventListener(
        "click",
        (ev: dom.MouseEvent) => {
          ev.stopPropagation()
          searchInput.value = ""
          clearBtn.classList.add("xforms-hidden")
          updateFilter(state)
          searchInput.focus()
        }
      )

      searchInput.addEventListener(
        "input",
        (_: dom.Event) => {
          val hasQuery = searchInput.value.nonAllBlank
          if (hasQuery) {
            clearBtn.classList.remove("xforms-hidden")
            if (state.selectedCat.nonEmpty) {
              state.selectedCat = ""
              syncPillsState(state)
            }
          } else {
            clearBtn.classList.add("xforms-hidden")
          }
          updateFilter(state)
        }
      )

      popoverEl.addEventListener(
        "keydown",
        (ev: dom.KeyboardEvent) => {
          ev.key match {
            case "ArrowDown" =>
              ev.preventDefault()
              ev.stopPropagation()
              if (state.filteredItems.nonEmpty) {
                state.activeIndex = (state.activeIndex + 1) % state.filteredItems.size
                updateActiveHighlight(state)
              }

            case "ArrowUp" =>
              ev.preventDefault()
              ev.stopPropagation()
              if (state.filteredItems.nonEmpty) {
                state.activeIndex = (state.activeIndex - 1 + state.filteredItems.size) % state.filteredItems.size
                updateActiveHighlight(state)
              }

            case "Enter" =>
              ev.preventDefault()
              ev.stopPropagation()
              if (state.activeIndex >= 0 && state.activeIndex < state.filteredItems.size) {
                val chosen = state.filteredItems(state.activeIndex)
                closePopover(restoreFocus = true)
                state.config.onSelect(chosen)
              }

            case "Escape" =>
              ev.preventDefault()
              ev.stopPropagation()
              closePopover(restoreFocus = true)

            case "Tab" =>
              closePopover(restoreFocus = false)

            case _ =>
          }
        }
      )

      dom.window.requestAnimationFrame(_ => {
        if (activeStateOpt.contains(state)) {
          searchInput.focus()
          if (config.currentValue.nonEmpty)
            updateActiveHighlight(state)
        }
      })
    }

    def initPills(state: ActivePopoverState, categories: List[Category]): Unit =
      state.pillsBarOpt.foreach { pillsBar =>
        pillsBar.innerHTML = ""

        def addPill(catId: String, label: String): Unit = {
          val btn = dom.document.createElement("button").asInstanceOf[html.Button]
          btn.`type`   = "button"
          btn.tabIndex = -1
          btn.setAttribute("data-cat-pill", catId)
          btn.textContent = label
          btn.addEventListener("mousedown", (ev: dom.MouseEvent) => ev.preventDefault())
          btn.addEventListener(
            "click",
            (ev: dom.MouseEvent) => {
              ev.stopPropagation()
              state.selectedCat = if (state.selectedCat == catId && catId.nonEmpty) "" else catId
              if (state.selectedCat.nonEmpty && state.searchInput.value.nonAllBlank) {
                state.searchInput.value = ""
                state.clearBtn.classList.add("xforms-hidden")
              }
              syncPillsState(state)
              updateFilter(state)
              state.searchInput.focus()
            }
          )
          pillsBar.appendChild(btn)
        }

        addPill("", state.config.allLabel)
        categories.foreach(c => addPill(c.id, c.shortLabel))
        syncPillsState(state)
      }

    def syncPillsState(state: ActivePopoverState): Unit =
      state.pillsBarOpt.foreach { pillsBar =>
        pillsBar.querySelectorAllT("[data-cat-pill]").foreach { btn =>
          val catId    = btn.attValueOpt("data-cat-pill").getOrElse("")
          val isActive = state.selectedCat == catId
          btn.className = if (isActive) "fr-process-picker-pill fr-process-picker-pill-active" else "fr-process-picker-pill"
          btn.setAttribute("aria-pressed", isActive.toString)
        }
      }

    def scoreItem(item: Item, normQuery: String, tokens: List[String]): Option[Int] =
      if (! tokens.forall(t => item.searchText.contains(t) || (t.startsWith("+") && item.searchText.contains(t.stripPrefix("+"))))) {
        None
      } else {
        val qNoPlus = normQuery.stripPrefix("+")
        val score =
          if (item.normBadge == normQuery || item.normLabel == normQuery || (qNoPlus.nonEmpty && item.normKeywords.splitTo[List]().contains(qNoPlus))) 0
          else if (item.normBadge.nonEmpty && item.normBadge.startsWith(normQuery))                                                                   1
          else if (item.normLabel.startsWith(normQuery))                                                                                              2
          else if (item.normLabel.split("[\\s:\\-()/]+").exists(_.startsWith(normQuery)))                                                             3
          else if (item.normBadge.nonEmpty && item.normBadge.contains(normQuery))                                                                     4
          else if (item.normLabel.contains(normQuery))                                                                                                5
          else if (item.normHint.nonEmpty && item.normHint.contains(normQuery))                                                                       6
          else                                                                                                                                        7
        Some(score)
      }

    def updateFilter(state: ActivePopoverState): Unit = {
      val rawQuery  = state.searchInput.value.trimAllToEmpty
      val normQuery = Select1Search.normalizeText(rawQuery)
      val tokens    = normQuery.splitTo[List]()

      val baseList =
        if (state.selectedCat.isEmpty)
          state.config.items
        else
          state.config.items.filter(_.categoryId == state.selectedCat)

      val matched =
        if (tokens.isEmpty) {
          baseList
        } else {
          baseList
            .zipWithIndex
            .flatMap { case (item, idx) => scoreItem(item, normQuery, tokens).map(sc => (item, sc, idx)) }
            .sortBy { case (_, sc, idx) => (sc, idx) }
            .map(_._1)
        }

      val withFreeEntry =
        if (state.config.allowOpenSelection && rawQuery.nonEmpty) {
          val alreadyExact =
            state.config.items.exists { i =>
              i.normLabel == normQuery ||
              (i.normBadge.nonEmpty && i.normBadge == normQuery) ||
              Select1Search.normalizeText(i.value) == normQuery
            }
          if (alreadyExact)
            matched
          else
            matched :+ Item(
              value       = rawQuery,
              label       = rawQuery,
              badgeOpt    = Some("+"),
              isFreeEntry = true
            )
        } else {
          matched
        }

      state.filteredItems = withFreeEntry
      state.activeIndex =
        if (withFreeEntry.isEmpty) -1
        else if (tokens.isEmpty) math.max(0, withFreeEntry.indexWhere(_.value == state.config.currentValue))
        else 0

      renderList(state, scrollActiveIntoView = true)
      positionPopover(state)
    }

    def appendHighlightedText(parent: html.Element, text: String, query: String): Unit = {
      val trimmedQuery = query.trimAllToEmpty
      if (trimmedQuery.isEmpty) {
        parent.textContent = text
      } else {
        val lowerText  = text.toLowerCase
        val lowerQuery = trimmedQuery.toLowerCase
        val directIdx  = lowerText.indexOf(lowerQuery)
        if (directIdx >= 0) {
          if (directIdx > 0)
            parent.appendChild(dom.document.createTextNode(text.substring(0, directIdx)))
          val mark = dom.document.createElement("mark")
          mark.textContent = text.substring(directIdx, directIdx + trimmedQuery.length)
          parent.appendChild(mark)
          val rest = text.substring(directIdx + trimmedQuery.length)
          if (rest.nonEmpty)
            parent.appendChild(dom.document.createTextNode(rest))
        } else {
          // Diacritic-insensitive fallback highlight when normalized lengths match 1-to-1
          val normText  = Select1Search.normalizeText(text)
          val normQuery = Select1Search.normalizeText(trimmedQuery)
          val normIdx   = if (normText.length == text.length && normQuery.nonEmpty) normText.indexOf(normQuery) else -1
          if (normIdx >= 0 && normIdx + normQuery.length <= text.length) {
            if (normIdx > 0)
              parent.appendChild(dom.document.createTextNode(text.substring(0, normIdx)))
            val mark = dom.document.createElement("mark")
            mark.textContent = text.substring(normIdx, normIdx + normQuery.length)
            parent.appendChild(mark)
            val rest = text.substring(normIdx + normQuery.length)
            if (rest.nonEmpty)
              parent.appendChild(dom.document.createTextNode(rest))
          } else {
            parent.textContent = text
          }
        }
      }
    }

    def renderList(state: ActivePopoverState, scrollActiveIntoView: Boolean): Unit = {
      state.listEl.innerHTML = ""
      val rawQuery    = state.searchInput.value.trimAllToEmpty
      val isSearching = rawQuery.nonEmpty

      if (state.filteredItems.isEmpty) {
        val emptyDiv = dom.document.createElement("div").asInstanceOf[html.Element]
        emptyDiv.className   = "fr-process-picker-empty"
        emptyDiv.textContent = state.config.emptyMessage
        state.listEl.appendChild(emptyDiv)
      } else {
        var lastCategory = ""

        state.filteredItems.zipWithIndex.foreach { case (item, idx) =>
          if (! isSearching && ! item.isFreeEntry && item.categoryLabel.nonEmpty && item.categoryId != lastCategory) {
            lastCategory = item.categoryId
            val header = dom.document.createElement("div").asInstanceOf[html.Element]
            header.className   = "fr-process-picker-cat-header"
            header.textContent = item.categoryLabel
            state.listEl.appendChild(header)
          } else if (! isSearching && ! item.isFreeEntry && item.categoryLabel.isEmpty) {
            lastCategory = ""
          }

          val btn = dom.document.createElement("button").asInstanceOf[html.Button]
          btn.`type`   = "button"
          btn.tabIndex = -1
          btn.setAttribute("role", "option")
          btn.setAttribute("data-item-index", idx.toString)

          val isSelected = ! item.isFreeEntry && item.value == state.config.currentValue
          val isActive   = idx == state.activeIndex
          btn.setAttribute("aria-selected", isSelected.toString)
          btn.className =
            "fr-process-picker-item" +
              (if (isSelected)            " is-selected"              else "") +
              (if (isActive)              " is-active"                else "") +
              (if (item.hintOpt.nonEmpty) " has-hint"                 else "") +
              (if (item.isFreeEntry)      " is-free-entry"            else "") +
              (if (item.cssClass.nonEmpty) s" ${item.cssClass}"       else "")

          val checkSpan = dom.document.createElement("span").asInstanceOf[html.Element]
          checkSpan.className = "fr-process-picker-check"
          checkSpan.setAttribute("aria-hidden", "true")
          if (isSelected) {
            val checkIcon = dom.document.createElement("i").asInstanceOf[html.Element]
            checkIcon.className = "fa fa-check"
            checkSpan.appendChild(checkIcon)
          } else if (item.isFreeEntry) {
            val plusIcon = dom.document.createElement("i").asInstanceOf[html.Element]
            plusIcon.className = "fa fa-plus"
            checkSpan.appendChild(plusIcon)
          }
          btn.appendChild(checkSpan)

          val textCol = dom.document.createElement("span").asInstanceOf[html.Element]
          textCol.className = "fr-process-picker-item-text"

          val labelSpan = dom.document.createElement("span").asInstanceOf[html.Element]
          labelSpan.className = "fr-process-picker-item-label"
          if (item.isFreeEntry)
            labelSpan.textContent = item.label
          else
            appendHighlightedText(labelSpan, item.label, rawQuery)
          textCol.appendChild(labelSpan)

          item.hintOpt.filter(_.nonAllBlank).foreach { hint =>
            val hintSpan = dom.document.createElement("span").asInstanceOf[html.Element]
            hintSpan.className = "fr-process-picker-item-hint"
            appendHighlightedText(hintSpan, hint, rawQuery)
            textCol.appendChild(hintSpan)
          }

          btn.appendChild(textCol)

          item.badgeOpt.filter(_.nonAllBlank).foreach { badge =>
            if (! item.isFreeEntry) {
              val tokenSpan = dom.document.createElement("span").asInstanceOf[html.Element]
              tokenSpan.className = "fr-process-picker-item-token"
              appendHighlightedText(tokenSpan, badge, rawQuery)
              btn.appendChild(tokenSpan)
            }
          }

          btn.addEventListener("mousedown", (ev: dom.MouseEvent) => ev.preventDefault())
          btn.addEventListener(
            "mousemove",
            (_: dom.MouseEvent) => {
              if (state.activeIndex != idx) {
                state.activeIndex = idx
                updateActiveHighlight(state, scrollIntoView = false)
              }
            }
          )
          btn.addEventListener(
            "click",
            (ev: dom.MouseEvent) => {
              ev.stopPropagation()
              closePopover(restoreFocus = true)
              state.config.onSelect(item)
            }
          )

          state.listEl.appendChild(btn)
        }

        if (scrollActiveIntoView)
          updateActiveHighlight(state)
      }
    }

    def updateActiveHighlight(state: ActivePopoverState, scrollIntoView: Boolean = true): Unit =
      state.listEl.querySelectorAllT(".fr-process-picker-item").foreach { el =>
        val idx = el.attValueOpt("data-item-index").flatMap(_.toIntOption).getOrElse(-1)
        if (idx == state.activeIndex) {
          el.classList.add("is-active")
          if (scrollIntoView)
            el.asInstanceOf[js.Dynamic].scrollIntoView(js.Dynamic.literal(block = "nearest"))
        } else {
          el.classList.remove("is-active")
        }
      }

    def positionPopover(state: ActivePopoverState): Unit = {
      val triggerRect = state.config.triggerBtn.getBoundingClientRect()
      val vw          = dom.window.innerWidth
      val vh          = dom.window.innerHeight

      val minWidth = math.max(state.config.minWidthPx, triggerRect.width)
      val width    = math.min(minWidth, vw - ViewportMarginPx * 2)
      state.popoverEl.style.width = s"${width}px"

      val popHeight  = state.popoverEl.offsetHeight.toDouble
      val spaceBelow = vh - triggerRect.bottom - TriggerGapPx - ViewportMarginPx
      val spaceAbove = triggerRect.top - TriggerGapPx - ViewportMarginPx

      val placeAbove = popHeight > spaceBelow && spaceAbove > spaceBelow
      val maxAvailH  = math.max(180.0, if (placeAbove) spaceAbove else spaceBelow)

      val headerH  = state.popoverEl.offsetHeight - state.listEl.offsetHeight
      val maxListH = math.max(110.0, math.min(280.0, maxAvailH - headerH))
      state.listEl.style.maxHeight = s"${maxListH}px"

      val finalPopHeight = state.popoverEl.offsetHeight.toDouble
      val top =
        if (placeAbove)
          math.max(ViewportMarginPx, triggerRect.top - finalPopHeight - TriggerGapPx)
        else
          math.min(vh - finalPopHeight - ViewportMarginPx, triggerRect.bottom + TriggerGapPx)

      val left =
        math.max(ViewportMarginPx, math.min(triggerRect.left, vw - width - ViewportMarginPx))

      state.popoverEl.style.position = "fixed"
      state.popoverEl.style.setProperty("inset", "auto")
      state.popoverEl.style.margin   = "0"
      state.popoverEl.style.top      = s"${top}px"
      state.popoverEl.style.left     = s"${left}px"
    }

    def closePopover(restoreFocus: Boolean): Unit =
      activeStateOpt.foreach { state =>
        activeStateOpt = None
        state.config.triggerBtn.setAttribute("aria-expanded", "false")
        state.config.triggerBtn.classList.remove("fr-process-action-trigger-open")
        state.config.triggerBtn.classList.remove("fr-select1-picker-trigger-open")

        if (hasNativePopover && dom.document.contains(state.popoverEl))
          scala.util.Try(state.popoverEl.asInstanceOf[js.Dynamic].hidePopover())

        if (state.popoverEl.parentNode != null)
          state.popoverEl.parentNode.removeChild(state.popoverEl)

        if (restoreFocus && dom.document.contains(state.config.triggerBtn))
          state.config.triggerBtn.focus()
      }

    // Companion for `fr|dropdown-select1-search` and `fr|open-select1-search`
    class DropdownSelect1SearchCompanion(containerElem: html.Element) extends XBLCompanion {

      private val DataPlaceholder     = "data-placeholder"
      private val DataIsOpenSelection = "data-is-open-selection"
      private val DataShowValue       = "data-show-value"
      private val DataSearchPreset    = "data-search-preset"
      private val DataSelectionFormat = "data-selection-format"
      private val DataItemset         = "data-itemset"
      private val DataRawValues       = "data-raw-values"
      private val DataSearchLabel     = "data-search-placeholder"
      private val DataNoResultsLabel  = "data-no-results"
      private val DataAllLabel        = "data-all-label"

      private var wrapperOpt                    : Option[html.Element]                    = None
      private var triggerBtnOpt                 : Option[html.Button]                     = None
      private var labelSpanOpt                  : Option[html.Element]                    = None
      private var badgeSpanOpt                  : Option[html.Element]                    = None
      private var clearBtnOpt                   : Option[html.Button]                     = None
      private var mutationObservers             : List[MutationObserver]                  = Nil
      private var onXFormsSelect1ValueChangeOpt : Option[ValueChangeInternalEvent => Unit] = None

      private def querySelect1ElemOpt: Option[html.Element] =
        containerElem.querySelectorOpt(".xforms-select1")

      private def queryDataElemOpt: Option[html.Element] =
        containerElem.querySelectorOpt(".fr-select1-picker-data").orElse(querySelect1ElemOpt)

      private def queryNativeSelectOpt: Option[html.Select] =
        containerElem.querySelectorOpt("select").map(_.asInstanceOf[html.Select])

      private def isOpenSelection: Boolean =
        querySelect1ElemOpt.flatMap(_.attValueOpt(DataIsOpenSelection)).contains("true")

      private def isShowValue: Boolean =
        querySelect1ElemOpt.flatMap(_.attValueOpt(DataShowValue)).contains("true")

      private def isCountryPreset: Boolean =
        querySelect1ElemOpt.flatMap(_.attValueOpt(DataSearchPreset)).contains("country") ||
          containerElem.closestOpt(".xbl-fr-country, .xbl-fr-phone").isDefined

      private def isPhoneFormat: Boolean =
        querySelect1ElemOpt.flatMap(_.attValueOpt(DataSelectionFormat)).contains("phone") ||
          containerElem.closestOpt(".xbl-fr-phone").isDefined

      private def isReadonly: Boolean =
        containerElem.hasClass("xforms-readonly") ||
          querySelect1ElemOpt.exists(_.hasClass("xforms-readonly")) ||
          queryNativeSelectOpt.exists(_.disabled)

      private def placeholderText: String =
        querySelect1ElemOpt.flatMap(_.attValueOpt(DataPlaceholder)).getOrElse("")

      override def init(): Unit = {
        val isStaticReadonly = containerElem.querySelector(".xforms-static") != null
        if (! isStaticReadonly) {
          for {
            select1Elem <- querySelect1ElemOpt
            select      <- queryNativeSelectOpt
          } {
            select.classList.add("fr-picker-native-select-hidden")
            select.tabIndex = -1

            val wrapper = dom.document.createElement("span").asInstanceOf[html.Element]
            wrapper.className = "fr-select1-picker xforms-help-popover-control"

            val triggerBtn = dom.document.createElement("button").asInstanceOf[html.Button]
            triggerBtn.`type`    = "button"
            triggerBtn.className = "fr-select1-picker-trigger"
            triggerBtn.setAttribute("aria-expanded", "false")

            val labelSpan = dom.document.createElement("span").asInstanceOf[html.Element]
            labelSpan.className = "fr-select1-picker-trigger-label"

            val badgeSpan = dom.document.createElement("span").asInstanceOf[html.Element]
            badgeSpan.className = "fr-select1-picker-trigger-badge xforms-hidden"

            val clearBtn = dom.document.createElement("button").asInstanceOf[html.Button]
            clearBtn.`type`    = "button"
            clearBtn.className = "fr-select1-picker-clear xforms-hidden"
            clearBtn.setAttribute("aria-label", "Clear")
            val clearIcon = dom.document.createElement("i").asInstanceOf[html.Element]
            clearIcon.className = "fa fa-fw fa-times"
            clearIcon.setAttribute("aria-hidden", "true")
            clearBtn.appendChild(clearIcon)

            val caretIcon = dom.document.createElement("i").asInstanceOf[html.Element]
            caretIcon.className = "fa fa-fw fa-chevron-down fr-select1-picker-caret"
            caretIcon.setAttribute("aria-hidden", "true")

            triggerBtn.appendChild(labelSpan)
            triggerBtn.appendChild(badgeSpan)
            triggerBtn.appendChild(caretIcon)

            wrapper.appendChild(triggerBtn)
            wrapper.appendChild(clearBtn)
            select.parentNode.insertBefore(wrapper, select.nextSibling)

            wrapperOpt    = Some(wrapper)
            triggerBtnOpt = Some(triggerBtn)
            labelSpanOpt  = Some(labelSpan)
            badgeSpanOpt  = Some(badgeSpan)
            clearBtnOpt   = Some(clearBtn)

            syncAriaAttributes()
            syncTriggerDisplay()

            triggerBtn.addEventListener(
              "click",
              (ev: dom.MouseEvent) => {
                ev.preventDefault()
                ev.stopPropagation()
                if (! isReadonly) {
                  if (PopoverSelectPicker.isOpenFor(triggerBtn))
                    PopoverSelectPicker.close(restoreFocus = true)
                  else
                    openPickerForControl()
                }
              }
            )

            triggerBtn.addEventListener(
              "keydown",
              (ev: dom.KeyboardEvent) => {
                if (! isReadonly && ! PopoverSelectPicker.isOpenFor(triggerBtn) && Set("ArrowDown", "ArrowUp")(ev.key)) {
                  ev.preventDefault()
                  ev.stopPropagation()
                  openPickerForControl()
                }
              }
            )

            clearBtn.addEventListener(
              "click",
              (ev: dom.MouseEvent) => {
                ev.preventDefault()
                ev.stopPropagation()
                if (! isReadonly) {
                  clearSelection()
                  triggerBtn.focus()
                }
              }
            )

            val listener: ValueChangeInternalEvent => Unit = { ev =>
              if (containerElem.contains(ev.control))
                syncTriggerDisplay()
            }
            onXFormsSelect1ValueChangeOpt = Some(listener)
            XFormsUiEvents.afterValueChangeCB.add(listener)

            List(DataPlaceholder, DataShowValue, "class").foreach { attr =>
              mutationObservers = DomSupport.onAttributeChange(select1Elem, attr, () => syncTriggerDisplay()) :: mutationObservers
            }
            queryDataElemOpt.foreach { dataElem =>
              List(DataItemset, DataRawValues).foreach { attr =>
                mutationObservers = DomSupport.onAttributeChange(dataElem, attr, () => syncTriggerDisplay()) :: mutationObservers
              }
            }
            mutationObservers = DomSupport.onAttributeChange(containerElem, "class", () => syncTriggerDisplay()) :: mutationObservers
          }
        }
      }

      override def destroy(): Unit = {
        triggerBtnOpt.foreach { btn =>
          if (PopoverSelectPicker.isOpenFor(btn))
            PopoverSelectPicker.close(restoreFocus = false)
        }
        onXFormsSelect1ValueChangeOpt.foreach(XFormsUiEvents.afterValueChangeCB.remove)
        onXFormsSelect1ValueChangeOpt = None
        mutationObservers.foreach(_.disconnect())
        mutationObservers = Nil
        wrapperOpt.foreach { w =>
          if (w.parentNode != null)
            w.parentNode.removeChild(w)
        }
        wrapperOpt    = None
        triggerBtnOpt = None
        labelSpanOpt  = None
        badgeSpanOpt  = None
        clearBtnOpt   = None
      }

      override def xformsFocus(): Unit =
        triggerBtnOpt.foreach(_.focus())

      private def syncAriaAttributes(): Unit =
        triggerBtnOpt.foreach { triggerBtn =>
          val LhhaSelectorAriaAttrs = List(
            ".xforms-label"              -> "aria-labelledby",
            ".xforms-help, .xforms-hint" -> "aria-describedby"
          )
          LhhaSelectorAriaAttrs.foreach { case (selector, ariaAttr) =>
            val lhhaElems = containerElem.querySelectorAllT(selector).toList
            if (lhhaElems.nonEmpty) {
              val lhhaIds = lhhaElems.map(DomSupport.generateIdIfNeeded).mkString(" ")
              triggerBtn.setAttribute(ariaAttr, lhhaIds)
            }
          }
        }

      private def findPlaceholderOptionValue(select: html.Select): Option[String] =
        Option(select.options)
          .filter(_.length > 0)
          .map(_(0))
          .filter(_.text.isEmpty)
          .map(_.value)

      private def readCategoriesAndItems(): (List[Category], List[Item], Option[String]) =
        (queryDataElemOpt, queryNativeSelectOpt) match {
          case (Some(dataElem), Some(select)) =>
            val placeholderValOpt = findPlaceholderOptionValue(select)
            val showValue         = isShowValue
            val isCountry         = isCountryPreset
            val rawValuesList     =
              if (showValue)
                dataElem.attValueOpt(DataRawValues).filter(_.nonAllBlank).flatMap { jsonStr =>
                  scala.util.Try(js.JSON.parse(jsonStr).asInstanceOf[js.Array[String]].toList).toOption
                }.getOrElse(Nil)
              else
                Nil

            val categoriesBuf = scala.collection.mutable.ListBuffer.empty[Category]
            val itemsBuf      = scala.collection.mutable.ListBuffer.empty[Item]
            var rawIdx        = 0

            def nextRawValueOpt(fallbackVal: String, isPlaceholder: Boolean): Option[String] =
              if (isPlaceholder) {
                if (rawIdx < rawValuesList.size && rawValuesList(rawIdx).isEmpty)
                  rawIdx += 1
                None
              } else {
                val res =
                  if (showValue) {
                    val v = rawValuesList.lift(rawIdx).getOrElse(fallbackVal)
                    rawIdx += 1
                    v.trimAllToOpt
                  } else {
                    None
                  }
                res
              }

            def countryKeywordsFor(codeOrVal: String, label: String): String =
              if (! isCountry) {
                ""
              } else {
                val codeUpper = codeOrVal.toUpperCase
                val aliases   = Select1Search.aliasesForCountryCode(codeUpper)
                val callCode  = Select1Search.callingCodeForLabel(label).toList
                (codeUpper.toLowerCase :: aliases ::: callCode).mkString(" ")
              }

            def unescapeItemsetHtml(s: String): String =
              s.replace("&lt;", "<")
               .replace("&gt;", ">")
               .replace("&quot;", "\"")
               .replace("&apos;", "'")
               .replace("&#39;", "'")
               .replace("&amp;", "&")

            dataElem.attValueOpt(DataItemset).filter(_.nonAllBlank).flatMap { jsonStr =>
              scala.util.Try(js.JSON.parse(jsonStr).asInstanceOf[js.Array[js.Dynamic]]).toOption
            } match {
              case Some(jsonArr) =>
                def visitNodes(nodes: js.Array[js.Dynamic], catId: String, catLabel: String): Unit =
                  nodes.foreach { node =>
                    val label    = unescapeItemsetHtml(node.label.asInstanceOf[js.UndefOr[String]].getOrElse(""))
                    val children = node.children.asInstanceOf[js.UndefOr[js.Array[js.Dynamic]]].toOption
                    val valOpt   = node.value.asInstanceOf[js.UndefOr[String]].toOption
                    if (children.exists(_.nonEmpty) && valOpt.isEmpty) {
                      val cid = s"cat-${categoriesBuf.size}"
                      val short =
                        if (label.length <= 18) label
                        else label.take(16).trim + "…"
                      categoriesBuf += Category(id = cid, shortLabel = short, fullLabel = label)
                      visitNodes(children.get, cid, label)
                    } else {
                      val value         = valOpt.getOrElse("")
                      val isPlaceholder = placeholderValOpt.contains(value) && label.isEmpty
                      val rawCodeOpt    = nextRawValueOpt(value, isPlaceholder)
                      if (! isPlaceholder && (label.nonEmpty || value.nonEmpty)) {
                        val hintOpt = node.hint.asInstanceOf[js.UndefOr[String]].toOption.map(unescapeItemsetHtml).flatMap(_.trimAllToOpt)
                        val cssCls  =
                          node.attributes.asInstanceOf[js.UndefOr[js.Dynamic]].toOption
                            .flatMap(a => a.selectDynamic("class").asInstanceOf[js.UndefOr[String]].toOption)
                            .getOrElse("")
                        itemsBuf += Item(
                          value         = value,
                          label         = label,
                          badgeOpt      = rawCodeOpt,
                          hintOpt       = hintOpt,
                          categoryId    = catId,
                          categoryLabel = catLabel,
                          extraKeywords = countryKeywordsFor(rawCodeOpt.getOrElse(value), label),
                          cssClass      = cssCls
                        )
                      }
                    }
                  }

                visitNodes(jsonArr, "", "")

              case None =>
                // Fallback: read directly from `<select>` DOM (`<optgroup>` + `<option>`)
                var optIdx = 0
                while (optIdx < select.children.length) {
                  val child = select.children(optIdx)
                  if (child.tagName.equalsIgnoreCase("OPTGROUP")) {
                    val grpLabel = Option(child.getAttribute("label")).getOrElse("")
                    val cid      = s"cat-${categoriesBuf.size}"
                    val short    = if (grpLabel.length <= 18) grpLabel else grpLabel.take(16).trim + "…"
                    categoriesBuf += Category(id = cid, shortLabel = short, fullLabel = grpLabel)
                    var cIdx = 0
                    while (cIdx < child.children.length) {
                      val opt = child.children(cIdx).asInstanceOf[html.Option]
                      val isPlaceholder = placeholderValOpt.contains(opt.value) && opt.text.isEmpty
                      val rawCodeOpt    = nextRawValueOpt(opt.value, isPlaceholder)
                      if (! isPlaceholder && (opt.text.nonEmpty || opt.value.nonEmpty)) {
                        itemsBuf += Item(
                          value         = opt.value,
                          label         = opt.text,
                          badgeOpt      = rawCodeOpt,
                          categoryId    = cid,
                          categoryLabel = grpLabel,
                          extraKeywords = countryKeywordsFor(rawCodeOpt.getOrElse(opt.value), opt.text),
                          cssClass      = opt.className
                        )
                      }
                      cIdx += 1
                    }
                  } else if (child.tagName.equalsIgnoreCase("OPTION")) {
                    val opt           = child.asInstanceOf[html.Option]
                    val isPlaceholder = placeholderValOpt.contains(opt.value) && opt.text.isEmpty
                    val rawCodeOpt    = nextRawValueOpt(opt.value, isPlaceholder)
                    if (! isPlaceholder && (opt.text.nonEmpty || opt.value.nonEmpty)) {
                      itemsBuf += Item(
                        value         = opt.value,
                        label         = opt.text,
                        badgeOpt      = rawCodeOpt,
                        extraKeywords = countryKeywordsFor(rawCodeOpt.getOrElse(opt.value), opt.text),
                        cssClass      = opt.className
                      )
                    }
                  }
                  optIdx += 1
                }
            }

            (categoriesBuf.toList, itemsBuf.toList, placeholderValOpt)

          case _ =>
            (Nil, Nil, None)
        }

      private def setClassPresent(el: html.Element, token: String, present: Boolean): Unit =
        if (present) el.classList.add(token) else el.classList.remove(token)

      private def syncTriggerDisplay(): Unit =
        for {
          wrapper    <- wrapperOpt
          triggerBtn <- triggerBtnOpt
          labelSpan  <- labelSpanOpt
          badgeSpan  <- badgeSpanOpt
          clearBtn   <- clearBtnOpt
          select     <- queryNativeSelectOpt
        } {
          val (_, items, placeholderValOpt) = readCategoriesAndItems()
          val currentVal                    = Option(select.value).getOrElse("")
          val isPlaceholderSelected         = placeholderValOpt.contains(currentVal) || currentVal.isEmpty
          val selectedItemOpt               = if (isPlaceholderSelected) None else items.find(_.value == currentVal)
          val ro                            = isReadonly
          val ph                            = placeholderText

          setClassPresent(wrapper, "is-readonly", ro)
          triggerBtn.disabled = ro

          selectedItemOpt match {
            case Some(item) =>
              triggerBtn.classList.remove("is-placeholder")
              labelSpan.textContent =
                if (isPhoneFormat)
                  Select1Search.formatPhoneCountryLabel(item.label)
                else
                  item.label

              item.badgeOpt.filter(_ => isShowValue && ! isPhoneFormat) match {
                case Some(code) =>
                  badgeSpan.textContent = code
                  badgeSpan.classList.remove("xforms-hidden")
                case None =>
                  badgeSpan.textContent = ""
                  badgeSpan.classList.add("xforms-hidden")
              }

              val canClear = ! ro && ph.nonEmpty
              setClassPresent(clearBtn, "xforms-hidden", ! canClear)
              setClassPresent(wrapper, "has-clear", canClear)

            case None =>
              val fallbackDomText =
                if (! isPlaceholderSelected && select.selectedIndex >= 0 && select.selectedIndex < select.options.length)
                  Option(select.options(select.selectedIndex).text).filter(_.nonEmpty)
                else
                  None

              fallbackDomText match {
                case Some(txt) =>
                  triggerBtn.classList.remove("is-placeholder")
                  labelSpan.textContent = if (isPhoneFormat) Select1Search.formatPhoneCountryLabel(txt) else txt
                  badgeSpan.textContent = ""
                  badgeSpan.classList.add("xforms-hidden")
                  val canClear = ! ro && ph.nonEmpty
                  setClassPresent(clearBtn, "xforms-hidden", ! canClear)
                  setClassPresent(wrapper, "has-clear", canClear)

                case None =>
                  triggerBtn.classList.add("is-placeholder")
                  labelSpan.textContent = ph
                  badgeSpan.textContent = ""
                  badgeSpan.classList.add("xforms-hidden")
                  clearBtn.classList.add("xforms-hidden")
                  wrapper.classList.remove("has-clear")
              }
          }
        }

      private def openPickerForControl(): Unit =
        for {
          dataElem   <- queryDataElemOpt
          triggerBtn <- triggerBtnOpt
          select     <- queryNativeSelectOpt
        } {
          val (categories, items, placeholderValOpt) = readCategoriesAndItems()
          val currentVal =
            if (placeholderValOpt.contains(select.value)) "" else Option(select.value).getOrElse("")

          val hostContainer =
            containerElem.closestOpt("dialog.xforms-dialog, .xforms-dialog").getOrElse(containerElem)

          val searchPlaceholder =
            dataElem.attValueOpt(DataSearchLabel).filter(_.nonAllBlank).getOrElse("Search…")
          val emptyMessage =
            dataElem.attValueOpt(DataNoResultsLabel).filter(_.nonAllBlank).getOrElse("No results found")
          val allLabel =
            dataElem.attValueOpt(DataAllLabel).filter(_.nonAllBlank).getOrElse("All")

          val minWidth =
            if (isPhoneFormat) 280.0
            else if (categories.size >= 2 || items.exists(i => i.hintOpt.nonEmpty || i.badgeOpt.nonEmpty)) 340.0
            else 240.0

          PopoverSelectPicker.open(
            Config(
              triggerBtn         = triggerBtn,
              hostContainer      = hostContainer,
              currentValue       = currentVal,
              searchPlaceholder  = searchPlaceholder,
              emptyMessage       = emptyMessage,
              allLabel           = allLabel,
              categories         = categories,
              items              = items,
              allowOpenSelection = isOpenSelection,
              minWidthPx         = minWidth,
              extraPopoverClass  = if (isPhoneFormat) "fr-phone-dropdown" else "",
              onSelect           = commitItemSelection
            )
          )
        }

      private def commitItemSelection(item: Item): Unit =
        for (select <- queryNativeSelectOpt) {
          if (item.isFreeEntry && isOpenSelection) {
            labelSpanOpt.foreach(_.textContent = item.label)
            triggerBtnOpt.foreach(_.classList.remove("is-placeholder"))
            AjaxClient.fireEvent(
              AjaxEvent(
                eventName  = "fr-change",
                targetId   = containerElem.id,
                properties = Map("fr-value" -> item.value)
              )
            )
          } else {
            select.value = item.value
            syncTriggerDisplay()
            DomSupport.dispatchChange(select)
          }
        }

      private def clearSelection(): Unit =
        for (select <- queryNativeSelectOpt) {
          if (isOpenSelection) {
            findPlaceholderOptionValue(select).foreach(select.value = _)
            syncTriggerDisplay()
            AjaxClient.fireEvent(
              AjaxEvent(
                eventName  = "fr-change",
                targetId   = containerElem.id,
                properties = Map("fr-value" -> "")
              )
            )
          } else {
            findPlaceholderOptionValue(select).foreach { phVal =>
              select.value = phVal
              syncTriggerDisplay()
              DomSupport.dispatchChange(select)
            }
          }
        }
    }
  }
}
