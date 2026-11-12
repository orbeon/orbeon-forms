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

  XFormsXbl.declareCompanion("fr|open-select", js.constructorOf[FrOpenSelectCompanion])
  XFormsXbl.declareCompanion("fr|tags-input",  js.constructorOf[FrOpenSelectCompanion])

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
    private var isEditingCustom  : Boolean                      = false
    private var isUpdatingDom    : Boolean                      = false

    private def isViewMode: Boolean =
      wrapperElemOpt.flatMap(_.dataset.get("frView")).contains("true")

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
            val removeBtnOpt = targetOpt.flatMap(el => Option(el.closest(".fr-open-select-pill-remove")).collect { case b: html.Element => b })
            removeBtnOpt match {
              case Some(btn) =>
                e.preventDefault()
                e.stopPropagation()
                if (isInteractive && ! XFormsUI.modalProgressPanelShown) {
                  btn.dataset.get("frToken").foreach { tokenToRemove =>
                    val nextTokens     = currentTokens.filterNot(_ == tokenToRemove)
                    val hasPendingText = tagsInputOpt.exists(_.value.trimAllToOpt.isDefined)
                    isEditingCustom    = nextTokens.nonEmpty || hasPendingText
                    updateTokens(nextTokens)
                    if (isEditingCustom)
                      tagsInputOpt.foreach(_.focus())
                  }
                }
              case None =>
                if (isInteractive && ! XFormsUI.modalProgressPanelShown)
                  tagsInputOpt.foreach(_.focus())
            }
          })
        }

        tagsInputOpt.foreach { inputElem =>

          EventSupport.addListener[dom.KeyboardEvent](inputElem, DomEventNames.KeyDown, e => {
            if (isInteractive && ! XFormsUI.modalProgressPanelShown && ! e.isComposing) {
              if (e.key == "Enter" || e.key == ",") {
                e.preventDefault()
                commitPendingInput(inputElem)
              } else if (e.key == "Escape") {
                e.preventDefault()
                inputElem.value = ""
                isEditingCustom = false
                updateInteractiveState()
                inputElem.blur()
              } else if (e.key == "Backspace" && inputElem.value.isEmpty && currentTokens.nonEmpty) {
                e.preventDefault()
                isEditingCustom = true
                updateTokens(currentTokens.init)
              }
            }
          })

          EventSupport.addListener[dom.FocusEvent](inputElem, DomEventNames.FocusOut, _ => {
            if (isInteractive && ! XFormsUI.modalProgressPanelShown) {
              commitPendingInput(inputElem)
              isEditingCustom = false
              updateInteractiveState()
            }
          })

          EventSupport.addListener[dom.ClipboardEvent](inputElem, "paste", e => {
            if (isInteractive && ! XFormsUI.modalProgressPanelShown) {
              val pastedText = Option(e.clipboardData).map(_.getData("text")).getOrElse("")
              if (pastedText.contains(",") || pastedText.contains("\n") || pastedText.contains("\r")) {
                e.preventDefault()
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
              attributeFilter = js.Array("data-fr-open-tokens", "data-fr-readonly", "class")
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
      if (! isViewMode)
        updateInteractiveState()
    }

    override def xformsFocus(): Unit =
      if (isInteractive) {
        isEditingCustom = true
        updateInteractiveState()
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

      currentTokens = datasetTokens
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
        val hasAddCustomBtn   = addCustomBtnOpt.isDefined
        val showTagsContainer = ! hasAddCustomBtn || currentTokens.nonEmpty || (isEditingCustom && enabled)
        val showAddCustomBtn  = hasAddCustomBtn && ! showTagsContainer && enabled

        addCustomBtnOpt.foreach { btn =>
          btn.disabled = ! enabled
          btn.classList.toggle("xforms-hidden", ! showAddCustomBtn)
        }
        tagsInputOpt.foreach(_.disabled = ! enabled)
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
          val enabled = isInteractive
          currentTokens.foreach { token =>
            val decoded = OpenSelectSupport.decodeToken(token)

            val pillSpan = dom.document.createElement("span").asInstanceOf[html.Span]
            pillSpan.className = "fr-open-select-pill"
            pillSpan.dataset("frToken") = token

            val labelSpan = dom.document.createElement("span").asInstanceOf[html.Span]
            labelSpan.className = "fr-open-select-pill-label"
            labelSpan.textContent = decoded
            pillSpan.appendChild(labelSpan)

            val removeBtn = dom.document.createElement("button").asInstanceOf[html.Button]
            removeBtn.`type` = "button"
            removeBtn.className = "fr-open-select-pill-remove"
            removeBtn.dataset("frToken") = token
            removeBtn.disabled = ! enabled
            removeBtn.setAttribute("aria-label", decoded)
            removeBtn.textContent = "×"
            pillSpan.appendChild(removeBtn)

            pillsContainer.appendChild(pillSpan)
          }
        } finally {
          isUpdatingDom = false
        }
      }
  }
}
