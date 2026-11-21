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

import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.web.DomEventNames
import org.orbeon.web.DomSupport.*
import org.orbeon.xforms.AjaxClient
import org.scalajs.dom
import org.scalajs.dom.html

import scala.scalajs.js


object ToolboxSearch {

  import Private.*

  case class SearchEntry(
    label     : String,
    category  : String,
    extraTerms: String,
    enabled   : Boolean
  )

  private val SearchContainerSelector = ".fb-toolbox-search"
  private val SearchToggleSelector    = ".fb-toolbox-search-toggle"
  private val SearchInputSelector     = ".fb-toolbox-search-input"
  private val SearchClearSelector     = ".fb-toolbox-search-clear"
  private val SearchEmptySelector     = ".fb-toolbox-search-empty"
  private val ControlsTabSelector     = "#fb-toolbox-controls-tab"
  private val ToolboxTabsNavSelector  = "#fb-toolbox-tabs .nav-tabs > li:first-child > a"

  private val OpenClass               = "fb-toolbox-search-open"
  private val ToolHiddenClass         = "fb-tool-search-hidden"
  private val GroupHiddenClass        = "fb-tools-search-hidden"
  private val ToolSelectedClass       = "fb-tool-search-selected"

  private var selectedToolOpt: Option[html.Element] = None

  /**
   * Normalizes text for case-insensitive, diacritic-insensitive matching.
   */
  def normalizeForSearch(s: String): String =
    s.asInstanceOf[js.Dynamic]
      .normalize("NFD")
      .asInstanceOf[String]
      .replaceAll("[\\u0300-\\u036f]", "")
      .toLowerCase

  /**
   * Returns a match rank if all whitespace-separated query tokens match the entry
   * (lower rank = better match), or `None` if the entry does not match.
   *
   * Rank ordering:
   * - Enabled entries always rank ahead of disabled entries.
   * - Within enabled/disabled: label prefix (0) < label word-start (1) < label substring (2) < extra terms / category (3).
   */
  def matchScore(entry: SearchEntry, query: String): Option[Int] = {
    val tokens = normalizeForSearch(query).splitTo[List]()
    if (tokens.isEmpty) {
      Some(if (entry.enabled) 0 else 10)
    } else {
      val normLabel    = normalizeForSearch(entry.label)
      val normCategory = normalizeForSearch(entry.category)
      val normExtra    = normalizeForSearch(entry.extraTerms)
      val combined     = s"$normLabel $normCategory $normExtra"
      val labelWords   = normLabel.splitTo[List]()

      if (tokens.forall(combined.contains)) {
        val labelRank =
          if (tokens.forall(t => normLabel.startsWith(t) || labelWords.exists(_.startsWith(t)))) {
            if (normLabel.startsWith(tokens.head)) 0 else 1
          } else if (tokens.forall(normLabel.contains)) {
            2
          } else {
            3
          }
        val enabledOffset = if (entry.enabled) 0 else 10
        Some(enabledOffset + labelRank)
      } else {
        None
      }
    }
  }

  def openSearch(): Unit = {
    switchToControlsTabIfNeeded()
    findSearchContainer().foreach { container =>
      container.classList.add(OpenClass)
      container.querySelectorOpt(SearchToggleSelector).foreach(_.setAttribute("aria-expanded", "true"))
      findSearchInput().foreach { input =>
        input.focus()
        input.select()
        applyFilter(input.value)
      }
    }
  }

  def closeSearch(): Unit =
    findSearchContainer().foreach { container =>
      container.classList.remove(OpenClass)
      container.querySelectorOpt(SearchToggleSelector).foreach(_.setAttribute("aria-expanded", "false"))
      findSearchInput().foreach { input =>
        input.value = ""
        input.blur()
      }
      applyFilter("")
    }

  def applyFilter(query: String): Unit =
    findControlsTab().foreach { controlsTab =>
      val trimmedQuery = query.trimAllToEmpty
      val isFiltering  = trimmedQuery.nonEmpty
      val groups       = controlsTab.querySelectorAllT(".fb-tools")

      var matchedToolsWithScore = List.empty[(html.Element, Int)]

      groups.foreach { group =>
        val categoryLabel =
          group.querySelectorOpt(".fb-tools-title, legend")
            .map(el => cleanLabelText(el))
            .getOrElse("")

        val tools = group.querySelectorAllT(".fb-tool")
        var anyToolVisible = false

        tools.foreach { tool =>
          val entry = extractEntry(tool, categoryLabel)
          val scoreOpt =
            if (isFiltering) matchScore(entry, trimmedQuery)
            else             Some(if (entry.enabled) 0 else 10)

          scoreOpt match {
            case Some(score) =>
              tool.classList.remove(ToolHiddenClass)
              anyToolVisible = true
              if (isFiltering)
                matchedToolsWithScore = (tool, score) :: matchedToolsWithScore
            case None =>
              tool.classList.add(ToolHiddenClass)
          }
        }

        if (isFiltering && ! anyToolVisible)
          group.classList.add(GroupHiddenClass)
        else
          group.classList.remove(GroupHiddenClass)
      }

      val matchedInDomOrder = matchedToolsWithScore.reverse

      controlsTab.querySelectorOpt(SearchEmptySelector).foreach { emptyEl =>
        if (isFiltering && matchedInDomOrder.isEmpty)
          emptyEl.classList.remove("xforms-hidden")
        else
          emptyEl.classList.add("xforms-hidden")
      }

      if (isFiltering && matchedInDomOrder.nonEmpty) {
        val currentStillValid = selectedToolOpt.exists(sel => matchedInDomOrder.exists(_._1 eq sel))
        if (! currentStillValid) {
          val bestTool = matchedInDomOrder.minBy(_._2)._1
          setSelectedTool(Some(bestTool), scrollIntoView = false)
        }
      } else {
        setSelectedTool(None, scrollIntoView = false)
      }
    }

  locally {
    dom.document.addEventListener(
      "click",
      (e: dom.Event) => {
        for (targetEl <- Option(e.target).collect { case el: html.Element => el }) {
          if (targetEl.closestOpt(SearchToggleSelector).isDefined) {
            e.preventDefault()
            openSearch()
          } else if (targetEl.closestOpt(SearchClearSelector).isDefined) {
            e.preventDefault()
            findSearchInput() match {
              case Some(input) if input.value.nonEmpty =>
                input.value = ""
                applyFilter("")
                input.focus()
              case _ =>
                closeSearch()
            }
          }
        }
      }
    )

    dom.document.addEventListener(
      "input",
      (e: dom.Event) => {
        for {
          targetEl <- Option(e.target).collect { case el: html.Input => el }
          if targetEl.matches(SearchInputSelector)
        } locally {
          applyFilter(targetEl.value)
        }
      }
    )

    dom.document.addEventListener(
      DomEventNames.KeyDown,
      (e: dom.KeyboardEvent) => {
        val targetInputOpt =
          Option(e.target)
            .collect { case el: html.Input => el }
            .filter(_.matches(SearchInputSelector))

        targetInputOpt match {
          case Some(_) =>
            e.key match {
              case "Escape" =>
                e.preventDefault()
                e.stopPropagation()
                closeSearch()
              case "ArrowDown" =>
                e.preventDefault()
                moveSelection(delta = 1)
              case "ArrowUp" =>
                e.preventDefault()
                moveSelection(delta = -1)
              case "Enter" =>
                e.preventDefault()
                activateSelectedTool()
              case _ =>
            }
          case None =>
            if (
              e.key == "/" &&
              ! e.ctrlKey  &&
              ! e.metaKey  &&
              ! e.altKey   &&
              ! isEditingTextOrInDialog()
            ) {
              if (findSearchContainer().isDefined) {
                e.preventDefault()
                openSearch()
              }
            }
        }
      }
    )

    // Re-apply active filter after Ajax updates (e.g. reloading toolbox or switching library versions)
    AjaxClient.ajaxResponseReceived.add { _ =>
      findSearchInput().foreach { input =>
        if (input.value.trimAllToEmpty.nonEmpty)
          applyFilter(input.value)
      }
    }
  }

  private object Private {

    def findControlsTab(): Option[html.Element] =
      findSearchContainer()
        .flatMap(_.closestOpt(".tab-pane"))
        .orElse(dom.document.querySelectorOpt(ControlsTabSelector))

    def findSearchContainer(): Option[html.Element] =
      dom.document.querySelectorOpt(SearchContainerSelector)

    def findSearchInput(): Option[html.Input] =
      dom.document.querySelectorOpt(SearchInputSelector).collect { case input: html.Input => input }

    def switchToControlsTabIfNeeded(): Unit =
      findControlsTab().foreach { controlsTab =>
        if (! controlsTab.classList.contains("active")) {
          dom.document.querySelectorOpt(ToolboxTabsNavSelector).foreach(_.click())
        }
      }

    def isEditingTextOrInDialog(): Boolean = {
      val hasOpenDialog =
        dom.document.querySelectorAll("dialog[open], .modal.in").length > 0

      val isEditingInput =
        Option(dom.document.activeElement)
          .collect { case el: html.Element => el }
          .exists(_.matches("input, textarea, select, [contenteditable='true']"))

      val isViewMode =
        dom.document.querySelectorOpt(".fr-mode-view").isDefined

      hasOpenDialog || isEditingInput || isViewMode
    }

    def cleanLabelText(el: html.Element): String = {
      val clone = el.cloneNode(deep = true).asInstanceOf[html.Element]
      clone.querySelectorAllT("kbd").foreach(_.remove())
      Option(clone.textContent).getOrElse("").trimAllToEmpty
    }

    def extractEntry(tool: html.Element, categoryLabel: String): SearchEntry = {
      val buttonOpt = tool.querySelectorOpt("button")
      val label =
        buttonOpt
          .map(cleanLabelText)
          .filter(_.nonEmpty)
          .orElse(buttonOpt.flatMap(b => Option(b.getAttribute("title"))))
          .getOrElse("")

      val extraTerms = Option(tool.getAttribute("data-fb-search-terms")).getOrElse("")
      val enabled = buttonOpt.exists { btn =>
        ! btn.hasAttribute("disabled") &&
        ! btn.classList.contains("disabled") &&
        btn.closestOpt(".xforms-disabled, .xforms-readonly").isEmpty
      }

      SearchEntry(
        label      = label,
        category   = categoryLabel,
        extraTerms = extraTerms,
        enabled    = enabled
      )
    }

    def visibleMatchingTools(): List[html.Element] =
      findControlsTab()
        .map { controlsTab =>
          controlsTab.querySelectorAllT(s".fb-tool:not(.$ToolHiddenClass)").toList
        }
        .getOrElse(Nil)

    def setSelectedTool(toolOpt: Option[html.Element], scrollIntoView: Boolean): Unit = {
      selectedToolOpt.foreach(_.classList.remove(ToolSelectedClass))
      selectedToolOpt = toolOpt
      toolOpt.foreach { tool =>
        tool.classList.add(ToolSelectedClass)
        if (scrollIntoView)
          tool.asInstanceOf[js.Dynamic].scrollIntoView(js.Dynamic.literal(block = "nearest"))
      }
    }

    def moveSelection(delta: Int): Unit = {
      val candidates = {
        val allVisible = visibleMatchingTools()
        val enabledOnly = allVisible.filter { tool =>
          tool.querySelectorOpt("button").exists { btn =>
            ! btn.hasAttribute("disabled") &&
            ! btn.classList.contains("disabled") &&
            btn.closestOpt(".xforms-disabled, .xforms-readonly").isEmpty
          }
        }
        if (enabledOnly.nonEmpty) enabledOnly else allVisible
      }

      if (candidates.nonEmpty) {
        val currentIdx = selectedToolOpt.map(sel => candidates.indexWhere(_ eq sel)).getOrElse(-1)
        val nextIdx =
          if (currentIdx < 0)
            if (delta >= 0) 0 else candidates.length - 1
          else
            Math.floorMod(currentIdx + delta, candidates.length)
        setSelectedTool(Some(candidates(nextIdx)), scrollIntoView = true)
      }
    }

    def activateSelectedTool(): Unit =
      selectedToolOpt
        .orElse(visibleMatchingTools().headOption)
        .flatMap(_.querySelectorOpt("button"))
        .foreach { btn =>
          val isDisabled =
            btn.hasAttribute("disabled") ||
            btn.classList.contains("disabled") ||
            btn.closestOpt(".xforms-disabled, .xforms-readonly").isDefined
          if (! isDisabled) {
            btn.click()
            closeSearch()
          }
        }
  }
}
