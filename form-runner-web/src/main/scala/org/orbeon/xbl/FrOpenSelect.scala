/**
  * Copyright (C) 2026 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  *  2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.xbl

import org.log4s.Logger
import org.orbeon.oxf.util.LoggerFactory
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.web.DomEventNames
import org.orbeon.web.DomSupport.*
import org.orbeon.xforms.*
import org.orbeon.xforms.facade.XBLCompanion
import org.scalajs.dom
import org.scalajs.dom.html

import scala.scalajs.js


object FrOpenSelect {

  private val logger: Logger = LoggerFactory.createLogger("org.orbeon.xbl.FrOpenSelect")

  XFormsXbl.declareCompanion("fr|tags-input", js.constructorOf[FrOpenSelectCompanion])

  private class FrOpenSelectCompanion(containerElem: html.Element) extends XBLCompanion {

    private object EventSupport extends EventListenerSupport

    private var wrapperElemOpt   : Option[html.Element]         = None
    private var addCustomBtnOpt  : Option[html.Button]          = None
    private var tagsContainerOpt : Option[html.Element]         = None
    private var pillsContainerOpt: Option[html.Element]         = None
    private var tagsInputOpt     : Option[html.Input]           = None
    private var hiddenInputOpt   : Option[html.Element]         = None
    private var observerOpt      : Option[dom.MutationObserver] = None
    private var currentTokens    : List[String]                 = Nil
    private var selectedPillIndex: Option[Int]                  = None
    private var editingPillIndex : Option[Int]                  = None
    private var isEditingCustom  : Boolean                      = false
    private var isUpdatingDom    : Boolean                      = false

    private def isViewMode: Boolean =
      wrapperElemOpt.flatMap(_.dataset.get("frView")).contains("true")

    private def isCollapsible: Boolean =
      wrapperElemOpt.flatMap(_.dataset.get("frCollapsible")).contains("true")

    private def isComponentReadonly: Boolean =
      isMarkedReadonly || wrapperElemOpt.flatMap(_.dataset.get("frReadonly")).contains("true")

    private def isInteractive: Boolean =
      ! isViewMode && ! isComponentReadonly

    override def init(): Unit = {

      logger.debug("init")

      wrapperElemOpt    = containerElem.querySelectorOpt(".fr-open-select-tags-wrapper")
      addCustomBtnOpt   = containerElem.queryNestedElems[html.Button]("button.fr-open-select-add-custom").headOption
      tagsContainerOpt  = containerElem.querySelectorOpt(".fr-open-select-tags-container")
      pillsContainerOpt = containerElem.querySelectorOpt(".fr-open-select-pills")
      tagsInputOpt      = containerElem.queryNestedElems[html.Input]("input.fr-open-select-tags-input").headOption
      hiddenInputOpt    = containerElem.querySelectorOpt(".fr-open-select-hidden-input")

      if (! isViewMode) {
        syncFromDataset()

        addCustomBtnOpt.foreach { addBtn =>
          EventSupport.addListener[dom.MouseEvent](addBtn, DomEventNames.Click, e => {
            e.preventDefault()
            if (isInteractive && ! XFormsUI.modalProgressPanelShown) {
              isEditingCustom = true
              updateInteractiveState()
              tagsInputOpt.foreach(_.focus())
            }
          })
        }

        tagsContainerOpt.foreach { tagsContainer =>
          EventSupport.addListener[dom.MouseEvent](tagsContainer, DomEventNames.Click, e => {
            val targetOpt    = Option(e.target).collect { case el: html.Element => el }
            val editInputOpt = targetOpt.flatMap(el => Option(el.closest(".fr-open-select-pill-edit-input")).collect { case inp: html.Input => inp })
            val removeBtnOpt = targetOpt.flatMap(el => Option(el.closest(".fr-open-select-pill-remove")).collect { case b: html.Element => b })
            val pillElemOpt  = targetOpt.flatMap(el => Option(el.closest(".fr-open-select-pill")).collect { case p: html.Element => p })

            if (editInputOpt.isDefined) {
              // Keep focus inside the inline edit input
            } else {
              removeBtnOpt match {
                case Some(btn) =>
                  e.preventDefault()
                  e.stopPropagation()
                  if (isInteractive && ! XFormsUI.modalProgressPanelShown) {
                    btn.dataset.get("frToken").foreach { tokenToRemove =>
                      val nextTokens     = currentTokens.filterNot(_ == tokenToRemove)
                      val hasPendingText = tagsInputOpt.exists(_.value.trimAllToOpt.isDefined)
                      selectedPillIndex  = None
                      editingPillIndex   = None
                      isEditingCustom    = nextTokens.nonEmpty || hasPendingText
                      updateTokens(nextTokens)
                      if (isEditingCustom || ! isCollapsible)
                        tagsInputOpt.foreach(_.focus())
                    }
                  }
                case None =>
                  if (isInteractive && ! XFormsUI.modalProgressPanelShown) {
                    pillElemOpt.flatMap(_.dataset.get("frPillIndex")).flatMap(_.toIntOption) match {
                      case Some(idx) =>
                        if (e.detail >= 2) {
                          startEditingPill(idx)
                        } else {
                          selectedPillIndex = Some(idx)
                          updateSelectedPillClasses()
                          tagsInputOpt.foreach(_.focus())
                        }
                      case None =>
                        if (selectedPillIndex.isDefined) {
                          selectedPillIndex = None
                          updateSelectedPillClasses()
                        }
                        tagsInputOpt.foreach(_.focus())
                    }
                  }
              }
            }
          })

          EventSupport.addListener[dom.MouseEvent](tagsContainer, "dblclick", e => {
            if (isInteractive && ! XFormsUI.modalProgressPanelShown) {
              val targetOpt   = Option(e.target).collect { case el: html.Element => el }
              val pillElemOpt = targetOpt.flatMap(el => Option(el.closest(".fr-open-select-pill")).collect { case p: html.Element => p })
              pillElemOpt.flatMap(_.dataset.get("frPillIndex")).flatMap(_.toIntOption).foreach { idx =>
                e.preventDefault()
                e.stopPropagation()
                startEditingPill(idx)
              }
            }
          })
        }

        tagsInputOpt.foreach { inputElem =>

          EventSupport.addListener[dom.KeyboardEvent](inputElem, DomEventNames.KeyDown, e => {
            if (isInteractive && ! XFormsUI.modalProgressPanelShown && ! e.isComposing) {
              val caretAtStart = inputElem.selectionStart == 0 && inputElem.selectionEnd == 0

              if (e.key == "ArrowLeft" && caretAtStart && currentTokens.nonEmpty) {
                e.preventDefault()
                selectedPillIndex = Some(selectedPillIndex.fold(currentTokens.length - 1)(idx => math.max(0, idx - 1)))
                updateSelectedPillClasses()
              } else if (e.key == "ArrowRight" && selectedPillIndex.isDefined) {
                e.preventDefault()
                selectedPillIndex = selectedPillIndex.flatMap(idx => if (idx + 1 < currentTokens.length) Some(idx + 1) else None)
                updateSelectedPillClasses()
              } else if ((e.key == "Backspace" || e.key == "Delete") && selectedPillIndex.isDefined) {
                e.preventDefault()
                val idx        = selectedPillIndex.get
                val nextTokens = currentTokens.patch(idx, Nil, 1)
                selectedPillIndex =
                  if (nextTokens.isEmpty)
                    None
                  else if (e.key == "Backspace")
                    if (idx > 0) Some(idx - 1) else None
                  else
                    if (idx < nextTokens.length) Some(idx) else None
                isEditingCustom = true
                updateTokens(nextTokens)
              } else if (e.key == "Enter" && selectedPillIndex.isDefined && inputElem.value.isEmpty) {
                e.preventDefault()
                val idx = selectedPillIndex.get
                startEditingPill(idx)
              } else if (e.key == "Enter" || e.key == ",") {
                e.preventDefault()
                selectedPillIndex = None
                commitPendingInput(inputElem)
              } else if (e.key == "Escape") {
                e.preventDefault()
                if (selectedPillIndex.isDefined) {
                  selectedPillIndex = None
                  updateSelectedPillClasses()
                } else {
                  inputElem.value = ""
                  isEditingCustom = false
                  updateInteractiveState()
                  inputElem.blur()
                }
              } else if (e.key == "Backspace" && inputElem.value.isEmpty && currentTokens.nonEmpty) {
                e.preventDefault()
                selectedPillIndex = None
                isEditingCustom   = true
                updateTokens(currentTokens.init)
              } else if (selectedPillIndex.isDefined && e.key.length == 1 && ! e.ctrlKey && ! e.metaKey && ! e.altKey) {
                selectedPillIndex = None
                updateSelectedPillClasses()
              }
            }
          })

          EventSupport.addListener[dom.FocusEvent](inputElem, DomEventNames.FocusOut, _ => {
            if (isInteractive && ! XFormsUI.modalProgressPanelShown && editingPillIndex.isEmpty) {
              selectedPillIndex = None
              commitPendingInput(inputElem)
              isEditingCustom = false
              renderPills()
              updateInteractiveState()
            }
          })

          EventSupport.addListener[dom.ClipboardEvent](inputElem, "paste", e => {
            if (isInteractive && ! XFormsUI.modalProgressPanelShown) {
              val pastedText = Option(e.clipboardData).map(_.getData("text")).getOrElse("")
              if (pastedText.contains(",") || pastedText.contains("\n") || pastedText.contains("\r")) {
                e.preventDefault()
                selectedPillIndex = None
                val newTokens =
                  pastedText
                    .split("[,\\r\\n]+")
                    .toList
                    .flatMap(OpenSelectSupport.encodeToken)
                if (newTokens.nonEmpty)
                  updateTokens((currentTokens ++ newTokens).distinct)
              }
            }
          })
        }

        wrapperElemOpt.foreach { wrapper =>
          val observer = new dom.MutationObserver((_, _) => {
            if (! isUpdatingDom)
              syncFromDataset()
          })
          observer.observe(
            wrapper,
            new dom.MutationObserverInit {
              attributes      = true
              subtree         = true
              attributeFilter = js.Array(
                "data-fr-open-tokens",
                "data-fr-readonly",
                "data-fr-validation-type",
                "data-fr-pattern",
                "data-fr-max-tags",
                "data-fr-placeholder",
                "class"
              )
            }
          )
          observerOpt = Some(observer)
        }
      }
    }

    override def destroy(): Unit = {
      logger.debug("destroy")
      observerOpt.foreach(_.disconnect())
      observerOpt       = None
      EventSupport.clearAllListeners()
      wrapperElemOpt    = None
      addCustomBtnOpt   = None
      tagsContainerOpt  = None
      pillsContainerOpt = None
      tagsInputOpt      = None
      hiddenInputOpt    = None
    }

    override def xformsUpdateReadonly(readonly: Boolean): Unit = {
      logger.debug(s"xformsUpdateReadonly: $readonly")
      if (! isViewMode) {
        if (readonly) {
          selectedPillIndex = None
          editingPillIndex  = None
        }
        renderPills()
        updateInteractiveState()
      }
    }

    override def xformsFocus(): Unit =
      if (isInteractive) {
        isEditingCustom = true
        updateInteractiveState()
        tagsInputOpt.foreach(_.focus())
      }

    private def startEditingPill(idx: Int): Unit =
      if (idx >= 0 && idx < currentTokens.length && ! editingPillIndex.contains(idx)) {
        selectedPillIndex = None
        editingPillIndex  = Some(idx)
        renderPills()
      }

    private def updateSelectedPillClasses(): Unit =
      pillsContainerOpt.foreach { pillsContainer =>
        isUpdatingDom = true
        try {
          pillsContainer.queryNestedElems[html.Element](".fr-open-select-pill").foreach { pillElem =>
            val idxOpt = pillElem.dataset.get("frPillIndex").flatMap(_.toIntOption)
            pillElem.classList.toggle("fr-open-select-pill-selected", idxOpt.exists(selectedPillIndex.contains))
          }
        } finally {
          isUpdatingDom = false
        }
      }

    private def commitEditedPill(idx: Int, rawValue: String): Unit =
      if (editingPillIndex.contains(idx)) {
        editingPillIndex = None
        val replacementTokens =
          rawValue
            .split("[,\\r\\n]+")
            .toList
            .flatMap(OpenSelectSupport.encodeToken)
        val nextTokens = currentTokens.patch(idx, replacementTokens, 1).distinct
        isEditingCustom = nextTokens.nonEmpty
        updateTokens(nextTokens)
        if (isEditingCustom || ! isCollapsible)
          tagsInputOpt.foreach(_.focus())
      }

    private def cancelEditingPill(): Unit =
      if (editingPillIndex.isDefined) {
        val prevIdx       = editingPillIndex
        editingPillIndex  = None
        selectedPillIndex = prevIdx
        renderPills()
        tagsInputOpt.foreach(_.focus())
      }

    private def commitPendingInput(inputElem: html.Input): Unit = {
      val raw = inputElem.value
      val newTokens =
        raw
          .split("[,\\r\\n]+")
          .toList
          .flatMap(OpenSelectSupport.encodeToken)
      inputElem.value = ""
      if (newTokens.nonEmpty)
        updateTokens((currentTokens ++ newTokens).distinct)
    }

    private def syncFromDataset(): Unit = {
      val datasetTokens =
        wrapperElemOpt
          .flatMap(_.dataset.get("frOpenTokens"))
          .getOrElse("")
          .splitTo[List]()
          .distinct

      currentTokens     = datasetTokens
      selectedPillIndex = selectedPillIndex.filter(_ < currentTokens.length)
      editingPillIndex  = editingPillIndex.filter(_ < currentTokens.length)
      renderPills()
      updateInteractiveState()
    }

    private def updateTokens(nextTokens: List[String]): Unit = {
      val deduplicated = nextTokens.distinct
      val changed      = deduplicated != currentTokens
      currentTokens    = deduplicated
      renderPills()
      updateInteractiveState()
      if (changed) {
        val joined = deduplicated.mkString(" ")
        isUpdatingDom = true
        try {
          wrapperElemOpt.foreach(_.dataset("frOpenTokens") = joined)
        } finally {
          isUpdatingDom = false
        }
        hiddenInputOpt.foreach { hiddenElem =>
          DocumentAPI.setValue(hiddenElem.id, joined)
        }
      }
    }

    private def updateInteractiveState(): Unit = {
      isUpdatingDom = true
      try {
        val enabled           = isInteractive
        val collapsible       = isCollapsible
        val showTagsContainer = ! collapsible || currentTokens.nonEmpty || (isEditingCustom && enabled)
        val showAddCustomBtn  = collapsible && ! showTagsContainer && enabled
        val placeholderText   = wrapperElemOpt.flatMap(_.dataset.get("frPlaceholder")).getOrElse("")

        addCustomBtnOpt.foreach { btn =>
          btn.disabled = ! enabled
          btn.classList.toggle("xforms-hidden", ! showAddCustomBtn)
        }
        tagsInputOpt.foreach { inp =>
          inp.disabled    = ! enabled
          inp.placeholder = if (currentTokens.isEmpty && enabled) placeholderText else ""
        }
        tagsContainerOpt.foreach { tc =>
          tc.classList.toggle("xforms-hidden", ! showTagsContainer)
          tc.classList.toggle("fr-open-select-tags-disabled", ! enabled)
        }
        wrapperElemOpt.foreach { wrapper =>
          wrapper.classList.toggle("xforms-hidden", ! showTagsContainer && ! showAddCustomBtn)
        }
        pillsContainerOpt.foreach { pc =>
          pc.queryNestedElems[html.Button]("button.fr-open-select-pill-remove").foreach { btn =>
            btn.disabled = ! enabled
          }
        }
      } finally {
        isUpdatingDom = false
      }
    }

    private def renderPills(): Unit =
      pillsContainerOpt.foreach { pillsContainer =>
        isUpdatingDom = true
        try {
          pillsContainer.innerHTML = ""
          val enabled        = isInteractive
          val validationType = wrapperElemOpt.flatMap(_.dataset.get("frValidationType")).getOrElse("")
          val pattern        = wrapperElemOpt.flatMap(_.dataset.get("frPattern")).getOrElse("")
          val maxTagsOpt     = wrapperElemOpt.flatMap(_.dataset.get("frMaxTags")).flatMap(OpenSelectSupport.parseMaxTags)

          var inputToFocusOpt: Option[html.Input] = None

          currentTokens.zipWithIndex.foreach { case (token, idx) =>
            val decoded       = OpenSelectSupport.decodeToken(token)
            val isValidFormat = OpenSelectSupport.isValidTag(decoded, validationType, pattern)
            val isWithinMax   = maxTagsOpt.forall(idx < _)
            val isInvalid     = ! (isValidFormat && isWithinMax)
            val isSelected    = selectedPillIndex.contains(idx)
            val isEditing     = editingPillIndex.contains(idx) && enabled

            val pillSpan = dom.document.createElement("span").asInstanceOf[html.Span]
            val classes  = List(
              Some("fr-open-select-pill"),
              Option.when(isInvalid)("fr-open-select-pill-invalid"),
              Option.when(isSelected)("fr-open-select-pill-selected"),
              Option.when(isEditing)("fr-open-select-pill-editing")
            ).flatten
            pillSpan.className            = classes.mkString(" ")
            pillSpan.dataset("frToken")     = token
            pillSpan.dataset("frPillIndex") = idx.toString

            if (isEditing) {
              val editInput = dom.document.createElement("input").asInstanceOf[html.Input]
              editInput.`type`    = "text"
              editInput.className = "fr-open-select-pill-edit-input"
              editInput.value     = decoded
              editInput.size      = math.max(decoded.length + 1, 4)

              editInput.addEventListener(DomEventNames.KeyDown, (e: dom.KeyboardEvent) => {
                if (! e.isComposing) {
                  if (e.key == "Enter") {
                    e.preventDefault()
                    e.stopPropagation()
                    commitEditedPill(idx, editInput.value)
                  } else if (e.key == "Escape") {
                    e.preventDefault()
                    e.stopPropagation()
                    cancelEditingPill()
                  }
                }
              })

              editInput.addEventListener(DomEventNames.FocusOut, (_: dom.FocusEvent) => {
                commitEditedPill(idx, editInput.value)
              })

              pillSpan.appendChild(editInput)
              inputToFocusOpt = Some(editInput)
            } else {
              val labelSpan = dom.document.createElement("span").asInstanceOf[html.Span]
              labelSpan.className   = "fr-open-select-pill-label"
              labelSpan.textContent = decoded
              pillSpan.appendChild(labelSpan)

              val removeBtn = dom.document.createElement("button").asInstanceOf[html.Button]
              removeBtn.`type`            = "button"
              removeBtn.className         = "fr-open-select-pill-remove"
              removeBtn.dataset("frToken") = token
              removeBtn.disabled          = ! enabled
              removeBtn.setAttribute("aria-label", decoded)
              removeBtn.textContent       = "×"
              pillSpan.appendChild(removeBtn)
            }

            pillsContainer.appendChild(pillSpan)
          }

          inputToFocusOpt.foreach { editInput =>
            editInput.focus()
            editInput.select()
          }
        } finally {
          isUpdatingDom = false
        }
      }
  }
}
