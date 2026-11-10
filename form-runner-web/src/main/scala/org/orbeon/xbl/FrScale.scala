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

import io.udash.wrappers.jquery.JQueryPromise
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
import scala.scalajs.js.{Promise, UndefOr, |}


object FrScale {

  private val logger: Logger = LoggerFactory.createLogger("org.orbeon.xbl.FrScale")

  private val EmptyScaleClass   = "fr-scale-empty"
  private val SelectedItemClass = "fr-scale-item-selected"
  private val HiddenClass       = "xforms-hidden"
  private val PointerDownEvent  = "pointerdown"

  private val ClearKeys: Set[String] = Set("Delete", "Backspace")

  private final case class ScaleEntry(
    itemValue: Int,
    labelElem: html.Element,
    radioElem: html.Input
  )

  private final case class ScaleConfig(
    start     : Int,
    end       : Int,
    startLabel: String,
    endLabel  : String,
    tabIndex  : Option[String]
  )

  XFormsXbl.declareCompanion("fr|scale", js.constructorOf[FrScaleCompanion])

  private class FrScaleCompanion(containerElem: html.Element) extends XBLCompanion {

    private object EventSupport extends EventListenerSupport

    private var innerContainerOpt    : Option[html.Element]         = None
    private var groupElemOpt         : Option[html.Element]         = None
    private var clearBtnElemOpt      : Option[html.Button]          = None
    private var observerOpt          : Option[dom.MutationObserver] = None
    private var itemEntries          : Vector[ScaleEntry]           = Vector.empty
    private var renderedConfigOpt    : Option[ScaleConfig]          = None
    private var currentValue         : String                       = ""
    private var reclickClearCandidate: Boolean                      = false

    override def init(): Unit = {

      logger.debug("init")

      innerContainerOpt = containerElem.querySelectorOpt(".fr-scale-container")
      groupElemOpt      = containerElem.querySelectorOpt(".fr-scale-group")
      clearBtnElemOpt   = containerElem.queryNestedElems[html.Button]("button.fr-scale-clear").headOption

      updateDom(currentValue)
      xformsUpdateReadonly(isMarkedReadonly)

      groupElemOpt.foreach { groupElem =>

        EventSupport.addListener[dom.PointerEvent](groupElem, PointerDownEvent, e => {
          if (! isMarkedReadonly && ! XFormsUI.modalProgressPanelShown) {
            val clickedValOpt =
              e.targetOpt
                .flatMap(_.closestOpt("label.fr-scale-item"))
                .flatMap(_.attValueOpt("data-item-value"))
            reclickClearCandidate = allowClear(groupElem) && currentValue.nonAllBlank && clickedValOpt.contains(currentValue)
          } else {
            reclickClearCandidate = false
          }
        })

        EventSupport.addListener[dom.MouseEvent](groupElem, DomEventNames.Click, e => {
          e.targetOpt.collect { case inp: html.Input if inp.`type` == "radio" => inp }.foreach { radioElem =>
            val shouldClear = reclickClearCandidate
            reclickClearCandidate = false
            if (XFormsUI.modalProgressPanelShown || isMarkedReadonly) {
              e.preventDefault()
              updateDom(currentValue)
            } else if (shouldClear && allowClear(groupElem) && radioElem.value == currentValue) {
              radioElem.checked = false
              commitUserValue("", incremental = false)
            }
          }
        })

        EventSupport.addListener[dom.Event](groupElem, DomEventNames.Change, e => {
          e.targetOpt.collect { case inp: html.Input if inp.`type` == "radio" => inp }.foreach { radioElem =>
            reclickClearCandidate = false
            if (XFormsUI.modalProgressPanelShown || isMarkedReadonly) {
              e.preventDefault()
              updateDom(currentValue)
            } else if (radioElem.checked) {
              commitUserValue(radioElem.value, incremental = false)
            }
          }
        })

        EventSupport.addListener[dom.KeyboardEvent](groupElem, DomEventNames.KeyDown, e => {
          reclickClearCandidate = false
          if (! isMarkedReadonly && ! XFormsUI.modalProgressPanelShown) {
            if (e.key == "Enter") {
              e.preventDefault()
              AjaxClient.fireEvent(
                AjaxEvent(
                  eventName = DomEventNames.DOMActivate,
                  targetId  = containerElem.id
                )
              )
            } else if (ClearKeys(e.key) && allowClear(groupElem) && currentValue.nonAllBlank) {
              e.preventDefault()
              commitUserValue("", incremental = false)
            }
          }
        })

        val observer = new dom.MutationObserver((_, _) => {
          updateDom(currentValue)
        })
        observer.observe(
          groupElem,
          new dom.MutationObserverInit {
            attributes      = true
            attributeFilter = js.Array(
              "data-start",
              "data-end",
              "data-start-label",
              "data-end-label",
              "data-allow-clear",
              "data-tabindex"
            )
          }
        )
        observerOpt = Some(observer)
      }

      clearBtnElemOpt.foreach { clearBtn =>
        EventSupport.addListener[dom.MouseEvent](clearBtn, DomEventNames.Click, e => {
          e.preventDefault()
          if (! isMarkedReadonly && ! XFormsUI.modalProgressPanelShown) {
            commitUserValue("", incremental = false)
            xformsFocus()
          }
        })
      }
    }

    override def destroy(): Unit = {
      logger.debug("destroy")
      observerOpt.foreach(_.disconnect())
      observerOpt           = None
      EventSupport.clearAllListeners()
      innerContainerOpt     = None
      groupElemOpt          = None
      clearBtnElemOpt       = None
      itemEntries           = Vector.empty
      renderedConfigOpt     = None
      reclickClearCandidate = false
    }

    override def xformsGetValue(): String =
      currentValue

    override def xformsUpdateValue(newValue: String): js.UndefOr[Nothing] = {
      val normalized = Option(newValue).flatMap(_.trimAllToOpt).getOrElse("")
      currentValue = normalized
      updateDom(normalized)
      js.undefined
    }

    override def xformsUpdateReadonly(readonly: Boolean): Unit = {
      logger.debug(s"xformsUpdateReadonly: $readonly")
      itemEntries.foreach(_.radioElem.disabled = readonly)
      syncClearButton(isEmpty = currentValue.isAllBlank, readonly = readonly)
    }

    override def xformsFocus(): Unit = {
      logger.debug("xformsFocus")
      itemEntries
        .find(_.radioElem.checked)
        .orElse(itemEntries.headOption)
        .foreach(_.radioElem.focus())
    }

    override def setUserValue(newValue: String): UndefOr[Promise[Unit] | JQueryPromise[js.Function1[js.Any, js.Any], js.Any]] = {
      val normalized = Option(newValue).flatMap(_.trimAllToOpt).getOrElse("")
      commitUserValue(normalized, incremental = false)
      js.undefined
    }

    private def allowClear(groupElem: html.Element): Boolean =
      groupElem.dataset.get("allowClear").contains("true")

    private def ensureItemsBuilt(groupElem: html.Element): Unit = {
      val startAttr  = groupElem.dataset.get("start").getOrElse("")
      val endAttr    = groupElem.dataset.get("end").getOrElse("")
      val (effectiveStart, effectiveEnd) = RatingScaleSupport.effectiveScaleBounds(startAttr, endAttr)
      val startLabel = groupElem.dataset.get("startLabel").flatMap(_.trimAllToOpt).getOrElse("")
      val endLabel   = groupElem.dataset.get("endLabel").flatMap(_.trimAllToOpt).getOrElse("")
      val tabIndex   = groupElem.dataset.get("tabindex").flatMap(_.trimAllToOpt)

      val config = ScaleConfig(effectiveStart, effectiveEnd, startLabel, endLabel, tabIndex)
      if (! renderedConfigOpt.contains(config)) {
        groupElem.innerHTML = ""
        val readonly = isMarkedReadonly
        itemEntries =
          (effectiveStart to effectiveEnd).map { itemVal =>
            val radioId   = s"${containerElem.id}-radio-$itemVal"
            val labelElem = dom.document.createElementT("label")
            labelElem.className = "fr-scale-item"
            labelElem.setAttribute("for", radioId)
            labelElem.setAttribute("data-item-value", itemVal.toString)

            val radioElem = dom.document.createInputElement
            radioElem.id        = radioId
            radioElem.`type`    = "radio"
            radioElem.className = "fr-scale-radio visually-hidden"
            radioElem.name      = s"${containerElem.id}-radio"
            radioElem.value     = itemVal.toString
            radioElem.disabled  = readonly
            tabIndex.foreach(ti => radioElem.setAttribute("tabindex", ti))

            // WCAG 2.5.3 Label in Name: endpoint accessible names start with the visible number
            if (itemVal == effectiveStart && startLabel.nonEmpty)
              radioElem.setAttribute("aria-label", s"$itemVal — $startLabel")
            else if (itemVal == effectiveEnd && endLabel.nonEmpty)
              radioElem.setAttribute("aria-label", s"$itemVal — $endLabel")

            val numberSpan = dom.document.createElementT("span")
            numberSpan.className   = "fr-scale-number"
            numberSpan.textContent = itemVal.toString

            labelElem.appendChildT(radioElem)
            labelElem.appendChildT(numberSpan)
            groupElem.appendChildT(labelElem)

            ScaleEntry(itemVal, labelElem, radioElem)
          }.toVector

        renderedConfigOpt = Some(config)
      }
    }

    private def syncItemsVisual(rawValue: String): Unit = {
      val currentIntOpt = Option(rawValue).flatMap(_.trimAllToOpt).flatMap(_.toIntOption)
      itemEntries.foreach { entry =>
        val isSelected = currentIntOpt.contains(entry.itemValue)
        entry.radioElem.checked = isSelected
        entry.labelElem.toggleClass(SelectedItemClass, isSelected)
      }
    }

    private def syncClearButton(isEmpty: Boolean, readonly: Boolean): Unit =
      clearBtnElemOpt.foreach { clearBtn =>
        val canClear  = groupElemOpt.exists(allowClear)
        val hideClear = isEmpty || readonly || ! canClear
        clearBtn.toggleClass(HiddenClass, hideClear)
        clearBtn.disabled = readonly
      }

    private def updateDom(rawValue: String): Unit = {
      val trimmedOpt = Option(rawValue).flatMap(_.trimAllToOpt)
      val isEmpty    = trimmedOpt.isEmpty

      innerContainerOpt.foreach(_.toggleClass(EmptyScaleClass, isEmpty))

      groupElemOpt.foreach { groupElem =>
        ensureItemsBuilt(groupElem)
        syncItemsVisual(rawValue)
      }

      syncClearButton(isEmpty = isEmpty, readonly = isMarkedReadonly)
    }

    private def commitUserValue(
      newValue   : String,
      incremental: Boolean
    ): Unit = {
      val normalized = Option(newValue).flatMap(_.trimAllToOpt).getOrElse("")
      val changed    = normalized != currentValue
      currentValue   = normalized
      updateDom(normalized)
      XFormsUI.fieldValueChanged(containerElem)
      if (changed)
        AjaxClient.fireEvent(
          AjaxEvent(
            eventName   = EventNames.XXFormsValue,
            targetId    = containerElem.id,
            properties  = Map("value" -> normalized),
            incremental = incremental
          )
        )
    }
  }
}
