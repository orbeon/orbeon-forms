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


object FrRating {

  private val logger: Logger = LoggerFactory.createLogger("org.orbeon.xbl.FrRating")

  private val EmptyRatingClass = "fr-rating-empty"
  private val EmptyValueClass  = "fr-rating-value-empty"
  private val HiddenClass      = "xforms-hidden"
  private val FilledStarClass  = "fr-rating-star-filled"
  private val PreviewStarClass = "fr-rating-star-preview"
  private val PointerDownEvent = "pointerdown"
  private val PointerOverEvent = "pointerover"
  private val PointerLeaveEvt  = "pointerleave"

  private val ClearKeys: Set[String] = Set("Delete", "Backspace")

  private final case class StarEntry(
    starNum  : Int,
    labelElem: html.Element,
    radioElem: html.Input,
    iconElem : html.Element
  )

  XFormsXbl.declareCompanion("fr|rating", js.constructorOf[FrRatingCompanion])

  private class FrRatingCompanion(containerElem: html.Element) extends XBLCompanion {

    private object EventSupport extends EventListenerSupport

    private var innerContainerOpt    : Option[html.Element]         = None
    private var groupElemOpt         : Option[html.Element]         = None
    private var valueElemOpt         : Option[html.Element]         = None
    private var clearBtnElemOpt      : Option[html.Button]          = None
    private var observerOpt          : Option[dom.MutationObserver] = None
    private var itemEntries          : Vector[StarEntry]            = Vector.empty
    private var renderedMax          : Int                          = 0
    private var renderedStarLabel    : String                       = ""
    private var renderedTabIndexOpt  : Option[String]               = None
    private var currentValue         : String                       = ""
    private var hoverValueOpt        : Option[Int]                  = None
    private var reclickClearCandidate: Boolean                      = false

    override def init(): Unit = {

      logger.debug("init")

      innerContainerOpt = containerElem.querySelectorOpt(".fr-rating-container")
      groupElemOpt      = containerElem.querySelectorOpt(".fr-rating-group")
      valueElemOpt      = containerElem.querySelectorOpt(".fr-rating-value")
      clearBtnElemOpt   = containerElem.queryNestedElems[html.Button]("button.fr-rating-clear").headOption

      updateDom(currentValue)
      xformsUpdateReadonly(isMarkedReadonly)

      groupElemOpt.foreach { groupElem =>

        EventSupport.addListener[dom.PointerEvent](groupElem, PointerOverEvent, e => {
          if (! isMarkedReadonly && ! XFormsUI.modalProgressPanelShown && e.pointerType != "touch") {
            val hoveredOpt =
              e.targetOpt
                .flatMap(_.closestOpt("label.fr-rating-item"))
                .flatMap(_.attValueOpt("data-item-value"))
                .flatMap(_.toIntOption)
            if (hoveredOpt != hoverValueOpt) {
              hoverValueOpt = hoveredOpt
              syncStarsVisual(currentValue)
            }
          }
        })

        EventSupport.addListener[dom.PointerEvent](groupElem, PointerLeaveEvt, _ => {
          if (hoverValueOpt.isDefined) {
            hoverValueOpt = None
            syncStarsVisual(currentValue)
          }
        })

        EventSupport.addListener[dom.PointerEvent](groupElem, PointerDownEvent, e => {
          if (! isMarkedReadonly && ! XFormsUI.modalProgressPanelShown) {
            val clickedValOpt =
              e.targetOpt
                .flatMap(_.closestOpt("label.fr-rating-item"))
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
              hoverValueOpt = None
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
              hoverValueOpt = None
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
              "data-max",
              "data-allow-clear",
              "data-star-label",
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
            hoverValueOpt = None
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
      valueElemOpt          = None
      clearBtnElemOpt       = None
      itemEntries           = Vector.empty
      renderedMax           = 0
      renderedStarLabel     = ""
      renderedTabIndexOpt   = None
      hoverValueOpt         = None
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
      if (readonly)
        hoverValueOpt = None
      itemEntries.foreach(_.radioElem.disabled = readonly)
      syncStarsVisual(currentValue)
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

    private def ensureItemsBuilt(groupElem: html.Element): Int = {
      val maxAttr     = groupElem.dataset.get("max").getOrElse("")
      val effectiveMax = RatingScaleSupport.effectiveRatingMax(maxAttr)
      val starLabelTpl = groupElem.dataset.get("starLabel").flatMap(_.trimAllToOpt).getOrElse("{0} / {1}")
      val tabIndexOpt  = groupElem.dataset.get("tabindex").flatMap(_.trimAllToOpt)

      if (effectiveMax != renderedMax || starLabelTpl != renderedStarLabel || tabIndexOpt != renderedTabIndexOpt) {
        groupElem.innerHTML = ""
        val readonly = isMarkedReadonly
        itemEntries =
          (1 to effectiveMax).map { starNum =>
            val radioId   = s"${containerElem.id}-radio-$starNum"
            val labelElem = dom.document.createElementT("label")
            labelElem.className = "fr-rating-item"
            labelElem.setAttribute("for", radioId)
            labelElem.setAttribute("data-item-value", starNum.toString)

            val radioElem = dom.document.createInputElement
            radioElem.id        = radioId
            radioElem.`type`    = "radio"
            radioElem.className = "fr-rating-radio visually-hidden"
            radioElem.name      = s"${containerElem.id}-radio"
            radioElem.value     = starNum.toString
            radioElem.disabled  = readonly
            val ariaLabel = starLabelTpl.replace("{0}", starNum.toString).replace("{1}", effectiveMax.toString)
            radioElem.setAttribute("aria-label", ariaLabel)
            tabIndexOpt.foreach(ti => radioElem.setAttribute("tabindex", ti))

            val iconElem = dom.document.createElementT("i")
            iconElem.className = "fa-regular fa-star fr-rating-star"
            iconElem.setAttribute("aria-hidden", "true")

            labelElem.appendChildT(radioElem)
            labelElem.appendChildT(iconElem)
            groupElem.appendChildT(labelElem)

            StarEntry(starNum, labelElem, radioElem, iconElem)
          }.toVector

        renderedMax         = effectiveMax
        renderedStarLabel   = starLabelTpl
        renderedTabIndexOpt = tabIndexOpt
      }

      effectiveMax
    }

    private def syncStarsVisual(rawValue: String): Unit = {
      val currentIntOpt = Option(rawValue).flatMap(_.trimAllToOpt).flatMap(_.toIntOption)
      itemEntries.foreach { entry =>
        entry.radioElem.checked = currentIntOpt.contains(entry.starNum)
        hoverValueOpt match {
          case Some(hoverVal) =>
            val inPreview = entry.starNum <= hoverVal
            entry.iconElem.toggleClass("fa-solid", inPreview)
            entry.iconElem.toggleClass("fa-regular", ! inPreview)
            entry.iconElem.toggleClass(PreviewStarClass, inPreview)
            entry.iconElem.toggleClass(FilledStarClass, inPreview && currentIntOpt.exists(entry.starNum <= _))
          case None =>
            val isFilled = currentIntOpt.exists(c => c >= 1 && entry.starNum <= c)
            entry.iconElem.toggleClass("fa-solid", isFilled)
            entry.iconElem.toggleClass("fa-regular", ! isFilled)
            entry.iconElem.toggleClass(FilledStarClass, isFilled)
            entry.iconElem.toggleClass(PreviewStarClass, add = false)
        }
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

      innerContainerOpt.foreach(_.toggleClass(EmptyRatingClass, isEmpty))

      groupElemOpt.foreach { groupElem =>
        ensureItemsBuilt(groupElem)
        syncStarsVisual(rawValue)

        val maxAttr   = groupElem.dataset.get("max").getOrElse("")
        val formatted = RatingScaleSupport.formatRatingDisplayValue(rawValue, maxAttr)
        valueElemOpt.foreach { valueElem =>
          valueElem.textContent = if (isEmpty) "—" else formatted
          valueElem.toggleClass(EmptyValueClass, isEmpty)
        }
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
