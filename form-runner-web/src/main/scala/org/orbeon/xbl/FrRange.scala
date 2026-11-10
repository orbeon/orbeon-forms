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


object FrRange {

  private val logger: Logger = LoggerFactory.createLogger("org.orbeon.xbl.FrRange")

  private val EmptyRangeClass  = "fr-range-empty"
  private val EmptyValueClass  = "fr-range-value-empty"
  private val HiddenClass      = "xforms-hidden"
  private val PointerDownEvent = "pointerdown"
  private val PointerUpEvent   = "pointerup"
  private val PointerCancelEvt = "pointercancel"

  private val MinCommitKeys: Set[String] = Set("ArrowLeft", "ArrowDown", "Home", "PageDown")
  private val ClearKeys    : Set[String] = Set("Delete", "Backspace")

  XFormsXbl.declareCompanion("fr|range", js.constructorOf[FrRangeCompanion])

  private class FrRangeCompanion(containerElem: html.Element) extends XBLCompanion {

    private object EventSupport extends EventListenerSupport

    private var innerContainerOpt    : Option[html.Element]         = None
    private var inputElemOpt         : Option[html.Input]           = None
    private var valueElemOpt         : Option[html.Element]         = None
    private var clearBtnElemOpt      : Option[html.Button]          = None
    private var observerOpt          : Option[dom.MutationObserver] = None
    private var currentValue         : String                       = ""
    private var isPointerInteracting : Boolean                      = false
    private var hadPointerDownOnInput: Boolean                      = false

    override def init(): Unit = {

      logger.debug("init")

      innerContainerOpt = containerElem.querySelectorOpt(".fr-range-container")
      inputElemOpt      = containerElem.queryNestedElems[html.Input]("input.fr-range-input").headOption
      valueElemOpt      = containerElem.querySelectorOpt(".fr-range-value")
      clearBtnElemOpt   = containerElem.queryNestedElems[html.Button]("button.fr-range-clear").headOption

      updateDom(currentValue)
      xformsUpdateReadonly(isMarkedReadonly)

      inputElemOpt.foreach { inputElem =>

        EventSupport.addListener[dom.Event](inputElem, PointerDownEvent, _ => {
          if (! isMarkedReadonly && ! XFormsUI.modalProgressPanelShown) {
            isPointerInteracting  = true
            hadPointerDownOnInput = true
          }
        })

        EventSupport.addListeners(
          dom.window,
          List(PointerUpEvent, PointerCancelEvt),
          (_: dom.Event) => {
            isPointerInteracting = false
          }
        )

        EventSupport.addListener[dom.Event](inputElem, DomEventNames.FocusOut, _ => {
          isPointerInteracting  = false
          hadPointerDownOnInput = false
        })

        EventSupport.addListener[dom.Event](inputElem, DomEventNames.Input, e => {
          if (XFormsUI.modalProgressPanelShown || isMarkedReadonly) {
            e.preventDefault()
            updateDom(currentValue)
          } else {
            commitUserValue(inputElem.value, incremental = true)
          }
        })

        EventSupport.addListener[dom.Event](inputElem, DomEventNames.Change, e => {
          if (XFormsUI.modalProgressPanelShown || isMarkedReadonly) {
            e.preventDefault()
            updateDom(currentValue)
          } else {
            // Flush final value non-incrementally when drag/step finishes
            commitUserValue(inputElem.value, incremental = false, forceFlushIfUnchanged = true)
          }
        })

        // When the slider starts unset (resting thumb at `min`), clicking directly on the resting thumb
        // does not fire `input` or `change` because `inputElem.value` is already `min`. Commit `min`
        // only if the pointer actually went down on the slider input (not via a `<label>` click).
        EventSupport.addListener[dom.MouseEvent](inputElem, DomEventNames.Click, _ => {
          val pointerPressedInput = hadPointerDownOnInput
          hadPointerDownOnInput = false
          if (pointerPressedInput && ! isMarkedReadonly && ! XFormsUI.modalProgressPanelShown && currentValue.isAllBlank)
            commitUserValue(inputElem.value, incremental = false)
        })

        EventSupport.addListener[dom.KeyboardEvent](inputElem, DomEventNames.KeyDown, e => {
          if (! isMarkedReadonly && ! XFormsUI.modalProgressPanelShown) {
            if (e.key == "Enter") {
              e.preventDefault()
              AjaxClient.fireEvent(
                AjaxEvent(
                  eventName = DomEventNames.DOMActivate,
                  targetId  = containerElem.id
                )
              )
            } else if (ClearKeys(e.key) && currentValue.nonAllBlank) {
              e.preventDefault()
              commitUserValue("", incremental = false)
            } else if (MinCommitKeys(e.key) && currentValue.isAllBlank) {
              // At `min`, pressing ArrowLeft/ArrowDown/Home/PageDown won't change `inputElem.value`,
              // so native `input` won't fire; commit `min` explicitly.
              commitUserValue(inputElem.value, incremental = true)
            }
          }
        })

        val observer = new dom.MutationObserver((_, _) => {
          updateDom(currentValue)
        })
        observer.observe(
          inputElem,
          new dom.MutationObserverInit {
            attributes      = true
            attributeFilter = js.Array(
              "min",
              "max",
              "step",
              "data-prefix",
              "data-suffix",
              "data-not-set-label"
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
            inputElemOpt.foreach(_.focus())
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
      inputElemOpt          = None
      valueElemOpt          = None
      clearBtnElemOpt       = None
      isPointerInteracting  = false
      hadPointerDownOnInput = false
    }

    override def xformsGetValue(): String =
      currentValue

    override def xformsUpdateValue(newValue: String): js.UndefOr[Nothing] = {
      val normalized = Option(newValue).flatMap(_.trimAllToOpt).getOrElse("")
      currentValue = normalized
      if (! isPointerInteracting)
        updateDom(normalized)
      js.undefined
    }

    override def xformsUpdateReadonly(readonly: Boolean): Unit = {
      logger.debug(s"xformsUpdateReadonly: $readonly")
      inputElemOpt.foreach(_.disabled = readonly)
      syncClearButton(isEmpty = currentValue.isAllBlank, readonly = readonly)
    }

    override def xformsFocus(): Unit = {
      logger.debug("xformsFocus")
      inputElemOpt.foreach(_.focus())
    }

    override def setUserValue(newValue: String): UndefOr[Promise[Unit] | JQueryPromise[js.Function1[js.Any, js.Any], js.Any]] = {
      val normalized = Option(newValue).flatMap(_.trimAllToOpt).getOrElse("")
      commitUserValue(normalized, incremental = false)
      js.undefined
    }

    private def syncClearButton(isEmpty: Boolean, readonly: Boolean): Unit =
      clearBtnElemOpt.foreach { clearBtn =>
        val hideClear = isEmpty || readonly
        clearBtn.toggleClass(HiddenClass, hideClear)
        clearBtn.disabled = readonly
      }

    private def updateDom(rawValue: String): Unit = {
      val trimmedOpt = Option(rawValue).flatMap(_.trimAllToOpt)
      val isEmpty    = trimmedOpt.isEmpty

      innerContainerOpt.foreach(_.toggleClass(EmptyRangeClass, isEmpty))

      inputElemOpt.foreach { inputElem =>
        val startAttr   = inputElem.attValueOpt("min").getOrElse("0")
        val endAttr     = inputElem.attValueOpt("max").getOrElse("100")
        val prefixAttr  = inputElem.dataset.get("prefix").getOrElse("")
        val suffixAttr  = inputElem.dataset.get("suffix").getOrElse("")
        val notSetLabel = inputElem.dataset.get("notSetLabel").flatMap(_.trimAllToOpt).getOrElse("Not set")

        val sliderVal = RangeSupport.initialSliderValue(rawValue, startAttr, endAttr)
        if (inputElem.value != sliderVal)
          inputElem.value = sliderVal

        val progressCss =
          if (isEmpty)
            "0%"
          else
            RangeSupport.progressCssPercent(rawValue, startAttr, endAttr)

        inputElem.style.setProperty("--slider-progress", progressCss)

        val formatted = RangeSupport.formatDisplayValue(rawValue, prefixAttr, suffixAttr)

        inputElem.setAttribute("aria-valuetext", if (isEmpty) notSetLabel else formatted)

        valueElemOpt.foreach { valueElem =>
          valueElem.textContent = if (isEmpty) "—" else formatted
          valueElem.toggleClass(EmptyValueClass, isEmpty)
        }
      }

      syncClearButton(isEmpty = isEmpty, readonly = isMarkedReadonly)
    }

    private def commitUserValue(
      newValue             : String,
      incremental          : Boolean,
      forceFlushIfUnchanged: Boolean = false
    ): Unit = {
      val normalized = Option(newValue).flatMap(_.trimAllToOpt).getOrElse("")
      val changed    = normalized != currentValue
      currentValue   = normalized
      updateDom(normalized)
      XFormsUI.fieldValueChanged(containerElem)
      if (changed || forceFlushIfUnchanged)
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
