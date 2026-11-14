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
import scala.scalajs.js.UndefOr


object FrCodeInput {

  private val logger: Logger = LoggerFactory.createLogger("org.orbeon.xbl.FrCodeInput")

  XFormsXbl.declareCompanion("fr|code-input", js.constructorOf[FrCodeInputCompanion])

  private class FrCodeInputCompanion(containerElem: html.Element) extends XBLCompanion {

    private object OuterEventSupport extends EventListenerSupport
    private object BoxEventSupport   extends EventListenerSupport

    private var innerContainerOpt   : Option[html.Element]         = None
    private var boxesContainerOpt   : Option[html.Element]         = None
    private var observerOpt         : Option[dom.MutationObserver] = None
    private var inputs              : Vector[html.Input]           = Vector.empty
    private var slots               : Array[Option[Char]]          = Array.fill(CodeInputSupport.DefaultLength)(None)
    private var currentValue        : String                       = ""
    private var lastCompositionEndAt: Option[Double]               = None

    private def activeLength: Int =
      CodeInputSupport.normalizeLength(
        innerContainerOpt
          .flatMap(_.dataset.get("frLength"))
          .getOrElse("")
      )

    private def activeCharType: String =
      CodeInputSupport.normalizeCharacterType(
        innerContainerOpt
          .flatMap(_.dataset.get("frCharacterType"))
          .getOrElse("")
      )

    private def activeLabel: String =
      innerContainerOpt
        .flatMap(_.dataset.get("frLabel"))
        .flatMap(_.trimAllToOpt)
        .getOrElse("Code")

    override def init(): Unit = {

      logger.debug("init")

      innerContainerOpt = containerElem.querySelectorOpt(".fr-code-input-container")
      boxesContainerOpt = containerElem.querySelectorOpt(".fr-code-input-boxes")

      ensureInputsBuilt()
      syncSlotsFromCurrentValue()
      renderSlotsToInputs()
      xformsUpdateReadonly(isMarkedReadonly)

      containerElem.querySelectorOpt(".xforms-label").foreach { labelElem =>
        OuterEventSupport.addListener[dom.MouseEvent](labelElem, DomEventNames.Click, _ => {
          if (! isMarkedReadonly && ! XFormsUI.modalProgressPanelShown)
            xformsFocus()
        })
      }

      innerContainerOpt.foreach { inner =>
        val observer = new dom.MutationObserver((_, _) => {
          ensureInputsBuilt()
          syncSlotsFromCurrentValue()
          renderSlotsToInputs()
        })
        observer.observe(
          inner,
          new dom.MutationObserverInit {
            attributes      = true
            attributeFilter = js.Array("data-fr-length", "data-fr-character-type", "data-fr-label")
          }
        )
        observerOpt = Some(observer)
      }
    }

    override def destroy(): Unit = {
      logger.debug("destroy")
      observerOpt.foreach(_.disconnect())
      observerOpt = None
      OuterEventSupport.clearAllListeners()
      BoxEventSupport.clearAllListeners()
      innerContainerOpt = None
      boxesContainerOpt = None
      inputs            = Vector.empty
    }

    override def xformsGetValue(): String =
      currentValue

    override def xformsUpdateValue(newValue: String): UndefOr[Nothing] = {
      val normalized = Option(newValue).getOrElse("")
      currentValue = normalized
      ensureInputsBuilt()
      syncSlotsFromCurrentValue()
      renderSlotsToInputs()
      js.undefined
    }

    override def xformsUpdateReadonly(readonly: Boolean): Unit = {
      logger.debug(s"xformsUpdateReadonly: $readonly")
      inputs.foreach(_.disabled = readonly)
      innerContainerOpt.foreach(_.classList.toggle("xforms-readonly", readonly))
    }

    override def xformsFocus(): Unit = {
      val targetIdx = slots.indexWhere(_.isEmpty) match {
        case -1  => math.max(0, inputs.length - 1)
        case idx => idx
      }
      focusBox(targetIdx)
    }

    private def ensureInputsBuilt(): Unit = {
      val len      = activeLength
      val charType = activeCharType
      val label    = activeLabel

      if (inputs.length != len) {
        BoxEventSupport.clearAllListeners()
        boxesContainerOpt.foreach { boxesContainer =>
          boxesContainer.innerHTML = ""
          val built = Vector.tabulate(len) { idx =>
            val inputElem = dom.document.createElement("input").asInstanceOf[html.Input]
            inputElem.`type`    = "text"
            inputElem.className = "fr-code-input-box xforms-input-input"
            inputElem.id        = s"${containerElem.id}-box-$idx"
            attachBoxListeners(inputElem, idx)
            boxesContainer.appendChild(inputElem)
            inputElem
          }
          inputs = built
          slots  = CodeInputSupport.storedValueToSlots(currentValue, len, charType).toArray
        }
      }

      inputs.zipWithIndex.foreach { case (inputElem, idx) =>
        inputElem.setAttribute("inputmode",      if (charType == "digits") "numeric" else "text")
        if (charType == "digits")
          inputElem.setAttribute("pattern", "[0-9]*")
        else
          inputElem.removeAttribute("pattern")
        inputElem.setAttribute("autocomplete",   if (idx == 0) "one-time-code" else "off")
        inputElem.setAttribute("autocorrect",    "off")
        inputElem.setAttribute("autocapitalize", if (charType == "digits") "none" else "characters")
        inputElem.setAttribute("spellcheck",     "false")
        inputElem.setAttribute("aria-label",     s"$label (${idx + 1} / $len)")
        inputElem.disabled = isMarkedReadonly
      }

      updateLabelFor()
    }

    private def updateLabelFor(): Unit =
      for {
        firstInput <- inputs.headOption
        if firstInput.id.nonAllBlank
        labelElem  <- containerElem.queryNestedElems[html.Label]("label.xforms-label").headOption
      } locally {
        labelElem.htmlFor = firstInput.id
      }

    private def isImeComposing(e: dom.KeyboardEvent): Boolean =
      e.isComposing ||
      e.keyCode == 229 ||
      lastCompositionEndAt.exists(ts => math.abs(e.timeStamp - ts) < 50.0)

    private def attachBoxListeners(inputElem: html.Input, idx: Int): Unit = {

      BoxEventSupport.addListener[dom.Event](inputElem, "compositionend", e => {
        lastCompositionEndAt = Some(e.timeStamp)
      })

      BoxEventSupport.addListener[dom.FocusEvent](inputElem, "focus", _ => {
        if (! isMarkedReadonly)
          inputElem.select()
      })

      BoxEventSupport.addListener[dom.KeyboardEvent](inputElem, DomEventNames.KeyDown, e => {
        if (! isMarkedReadonly && ! XFormsUI.modalProgressPanelShown) {
          val composing = isImeComposing(e)
          e.key match {
            case "Enter" =>
              e.preventDefault()
              if (! composing) {
                commitSlots()
                AjaxClient.fireEvent(
                  AjaxEvent(
                    eventName = DomEventNames.DOMActivate,
                    targetId  = containerElem.id
                  )
                )
              }
            case "Backspace" if ! composing =>
              e.preventDefault()
              if (slots(idx).isDefined) {
                slots(idx) = None
                commitSlots()
              } else if (idx > 0) {
                slots(idx - 1) = None
                commitSlots()
                focusBox(idx - 1)
              }
            case "Delete" if ! composing =>
              e.preventDefault()
              if (slots(idx).isDefined) {
                slots(idx) = None
                commitSlots()
              }
            case "ArrowLeft" if ! composing =>
              e.preventDefault()
              if (idx > 0)
                focusBox(idx - 1)
            case "ArrowRight" if ! composing =>
              e.preventDefault()
              if (idx + 1 < inputs.length)
                focusBox(idx + 1)
            case "Home" if ! composing =>
              e.preventDefault()
              focusBox(0)
            case "End" if ! composing =>
              e.preventDefault()
              if (inputs.nonEmpty)
                focusBox(inputs.length - 1)
            case key if ! composing && key.length == 1 && ! e.ctrlKey && ! e.metaKey && ! e.altKey =>
              e.preventDefault()
              CodeInputSupport.normalizeChar(key.charAt(0), activeCharType).foreach { normChar =>
                slots(idx) = Some(normChar)
                commitSlots()
                if (idx + 1 < inputs.length)
                  focusBox(idx + 1)
                else
                  inputElem.select()
              }
            case _ =>
          }
        }
      })

      BoxEventSupport.addListener[dom.ClipboardEvent](inputElem, "paste", e => {
        e.preventDefault()
        if (! isMarkedReadonly && ! XFormsUI.modalProgressPanelShown) {
          val pasted = Option(e.clipboardData).map(_.getData("text")).getOrElse("")
          distributeText(pasted, idx)
        }
      })

      BoxEventSupport.addListener[dom.Event](inputElem, DomEventNames.Input, e => {
        if (isMarkedReadonly || XFormsUI.modalProgressPanelShown) {
          e.preventDefault()
          renderSlotsToInputs()
        } else {
          val raw = inputElem.value
          if (raw.isEmpty) {
            if (slots(idx).isDefined) {
              slots(idx) = None
              commitSlots()
            }
          } else if (raw.length > 1) {
            distributeText(raw, idx)
          } else {
            CodeInputSupport.normalizeChar(raw.charAt(0), activeCharType) match {
              case Some(normChar) =>
                val changed = ! slots(idx).contains(normChar)
                slots(idx) = Some(normChar)
                commitSlots()
                if (changed && idx + 1 < inputs.length)
                  focusBox(idx + 1)
              case None =>
                renderSlotsToInputs()
            }
          }
        }
      })
    }

    private def distributeText(rawText: String, startIndex: Int): Unit = {
      val charType       = activeCharType
      val len            = inputs.length
      val effectiveStart = if (slots.forall(_.isEmpty)) 0 else math.max(0, math.min(startIndex, len - 1))
      val filtered       = CodeInputSupport.filterAndNormalize(rawText, charType, len - effectiveStart)
      if (filtered.nonEmpty) {
        var i = 0
        while (i < filtered.length && (effectiveStart + i) < len) {
          slots(effectiveStart + i) = Some(filtered.charAt(i))
          i += 1
        }
        commitSlots()
        val nextIdx = math.min(effectiveStart + filtered.length, len - 1)
        focusBox(nextIdx)
      } else {
        renderSlotsToInputs()
      }
    }

    private def focusBox(idx: Int): Unit =
      if (idx >= 0 && idx < inputs.length) {
        val el = inputs(idx)
        el.focus()
        el.select()
      }

    private def syncSlotsFromCurrentValue(): Unit =
      slots = CodeInputSupport.storedValueToSlots(currentValue, activeLength, activeCharType).toArray

    private def renderSlotsToInputs(): Unit =
      inputs.zipWithIndex.foreach { case (inputElem, idx) =>
        val expected = slots.lift(idx).flatten.map(_.toString).getOrElse("")
        if (inputElem.value != expected)
          inputElem.value = expected
      }

    private def commitSlots(): Unit = {
      val newStored = CodeInputSupport.slotsToStoredValue(slots.toIndexedSeq)
      val changed   = newStored != currentValue
      currentValue  = newStored
      renderSlotsToInputs()
      if (changed)
        DocumentAPI.setValue(containerElem.id, newStored)
    }
  }
}
