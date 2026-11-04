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
import org.orbeon.web.DomEventNames
import org.orbeon.web.DomSupport.*
import org.orbeon.xforms.*
import org.orbeon.xforms.facade.XBLCompanion
import org.scalajs.dom
import org.scalajs.dom.html

import scala.scalajs.js
import scala.util.Try


object MaskedInput {

  private val logger: Logger = LoggerFactory.createLogger("org.orbeon.xbl.MaskedInput")

  XFormsXbl.declareCompanion("fr|masked-input", js.constructorOf[MaskedInputCompanion])

  private class MaskedInputCompanion(containerElem: html.Element) extends XBLCompanion {

    private object EventSupport extends EventListenerSupport

    private var inputElemOpt      : Option[html.Input]           = None
    private var maskObserverOpt   : Option[dom.MutationObserver] = None
    private var currentRawValue   : String                       = ""
    private var lastFormattedValue: String                       = ""

    private def currentMask(inputElem: html.Input): String =
      inputElem.dataset.get("mask").getOrElse("")

    private def syncInputAttributes(inputElem: html.Input, mask: String): Unit = {
      inputElem.setAttribute("autocorrect", "off")
      inputElem.setAttribute("autocapitalize", "off")
      inputElem.setAttribute("spellcheck", "false")
      val mode = MaskedInputSupport.inputMode(mask)
      if (inputElem.getAttribute("inputmode") != mode)
        inputElem.setAttribute("inputmode", mode)
    }

    override def init(): Unit = {

      logger.debug("init")

      inputElemOpt = containerElem.querySelectorOpt("input.fr-masked-input-field").map(_.asInstanceOf[html.Input])

      inputElemOpt.foreach { inputElem =>
        val mask = currentMask(inputElem)
        syncInputAttributes(inputElem, mask)
        updateDomFromRawValue(currentRawValue)
        xformsUpdateReadonly(isMarkedReadonly)

        EventSupport.addListener[dom.Event](inputElem, DomEventNames.Input, e => {
          if (! isMarkedReadonly) {
            val activeMask = currentMask(inputElem)
            syncInputAttributes(inputElem, activeMask)

            val inputType =
              e.asInstanceOf[js.Dynamic].inputType.asInstanceOf[js.UndefOr[String]].getOrElse("")
            val isDeleteForward = inputType == "deleteContentForward"
            val caretPos =
              Try(inputElem.selectionStart).toOption.map(_.toInt).getOrElse(inputElem.value.length)

            val result =
              MaskedInputSupport.applyMask(
                userInput         = inputElem.value,
                caretPos          = caretPos,
                previousFormatted = lastFormattedValue,
                mask              = activeMask,
                isDeleteForward   = isDeleteForward
              )

            lastFormattedValue = result.formattedValue
            if (inputElem.value != result.formattedValue)
              inputElem.value = result.formattedValue

            Try(inputElem.setSelectionRange(result.caretPos, result.caretPos))
          }
        })

        EventSupport.addListeners(inputElem, List(DomEventNames.Change, DomEventNames.FocusOut), (_: dom.Event) => {
          if (! isMarkedReadonly)
            commitCurrentInputValue(inputElem)
        })

        EventSupport.addListener[dom.KeyboardEvent](inputElem, DomEventNames.KeyPress, e => {
          if (! isMarkedReadonly && Set(10, 13)(e.keyCode)) {
            e.preventDefault()
            commitCurrentInputValue(inputElem)
            AjaxClient.fireEvent(
              AjaxEvent(
                eventName = DomEventNames.DOMActivate,
                targetId  = containerElem.id
              )
            )
          }
        })
        val observer = new dom.MutationObserver((_, _) => {
          updateDomFromRawValue(currentRawValue)
        })
        observer.observe(
          inputElem,
          new dom.MutationObserverInit {
            attributes      = true
            attributeFilter = js.Array("data-mask")
          }
        )
        maskObserverOpt = Some(observer)
      }
    }

    override def destroy(): Unit = {
      logger.debug("destroy")
      maskObserverOpt.foreach(_.disconnect())
      maskObserverOpt = None
      EventSupport.clearAllListeners()
      inputElemOpt = None
    }

    override def xformsGetValue(): String =
      currentRawValue

    override def xformsUpdateValue(newValue: String): js.UndefOr[Nothing] = {
      val mask       = inputElemOpt.map(currentMask).getOrElse("")
      val raw        = MaskedInputSupport.toRawValue(newValue, mask)
      val normalized = if (raw.isEmpty && newValue.nonEmpty) newValue else raw
      currentRawValue = normalized
      updateDomFromRawValue(normalized)
      js.undefined
    }

    override def xformsUpdateReadonly(readonly: Boolean): Unit = {
      logger.debug(s"xformsUpdateReadonly: $readonly")
      inputElemOpt.foreach(_.readOnly = readonly)
    }

    override def xformsFocus(): Unit = {
      logger.debug("xformsFocus")
      inputElemOpt.foreach(_.focus())
    }

    private def commitCurrentInputValue(inputElem: html.Input): Unit = {
      val mask      = currentMask(inputElem)
      val raw       = MaskedInputSupport.toRawValue(inputElem.value, mask)
      val formatted = MaskedInputSupport.formatDisplayValue(raw, mask)
      lastFormattedValue = formatted
      if (inputElem.value != formatted)
        inputElem.value = formatted
      sendValueToServerIfNeeded(raw)
    }

    private def updateDomFromRawValue(rawValue: String): Unit =
      inputElemOpt.foreach { inputElem =>
        val mask      = currentMask(inputElem)
        syncInputAttributes(inputElem, mask)
        val formatted = MaskedInputSupport.formatDisplayValue(rawValue, mask)
        lastFormattedValue = formatted
        if (inputElem.value != formatted)
          inputElem.value = formatted
      }

    private def sendValueToServerIfNeeded(newRawValue: String): Unit =
      if (newRawValue != currentRawValue) {
        currentRawValue = newRawValue
        AjaxClient.fireEvent(
          AjaxEvent(
            eventName  = EventNames.XXFormsValue,
            targetId   = containerElem.id,
            properties = Map("value" -> newRawValue)
          )
        )
      }
  }
}
