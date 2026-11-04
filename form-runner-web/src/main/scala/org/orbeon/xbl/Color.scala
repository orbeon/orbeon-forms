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


object Color {

  private val logger: Logger = LoggerFactory.createLogger("org.orbeon.xbl.Color")

  private val EmptyColorClass      = "fr-color-empty"
  private val DefaultPickerColor   = "#000000"
  private val Hex6WithHashRegex    = "^#([0-9a-fA-F]{6})$".r
  private val Hex6WithoutHashRegex = "^([0-9a-fA-F]{6})$".r
  private val Hex3WithHashRegex    = "^#([0-9a-fA-F]{3})$".r
  private val Hex3WithoutHashRegex = "^([0-9a-fA-F]{3})$".r

  private def expandHex3(h3: String): String = {
    val lower = h3.toLowerCase
    s"#${lower(0)}${lower(0)}${lower(1)}${lower(1)}${lower(2)}${lower(2)}"
  }

  def normalizeHexValue(raw: String): String =
    raw.trimAllToOpt match {
      case None                             => ""
      case Some(Hex6WithHashRegex(hex6))    => s"#${hex6.toLowerCase}"
      case Some(Hex6WithoutHashRegex(hex6)) => s"#${hex6.toLowerCase}"
      case Some(Hex3WithHashRegex(hex3))    => expandHex3(hex3)
      case Some(Hex3WithoutHashRegex(hex3)) => expandHex3(hex3)
      case Some(other)                      => other
    }

  def isValidHexColor(value: String): Boolean =
    Hex6WithHashRegex.matches(value)

  XFormsXbl.declareCompanion("fr|color", js.constructorOf[ColorCompanion])

  private class ColorCompanion(containerElem: html.Element) extends XBLCompanion {

    companion =>

    private object EventSupport extends EventListenerSupport

    private var swatchAddonElemOpt: Option[html.Element] = None
    private var colorInputElemOpt : Option[html.Input]   = None
    private var hexInputElemOpt   : Option[html.Input]   = None
    private var currentValue      : String               = ""

    override def init(): Unit = {

      logger.debug("init")

      swatchAddonElemOpt = containerElem.querySelectorOpt(".fr-color-swatch-addon")
      colorInputElemOpt  = containerElem.querySelectorOpt("input.fr-color-picker").map(_.asInstanceOf[html.Input])
      hexInputElemOpt    = containerElem.querySelectorOpt("input.fr-color-hex").map(_.asInstanceOf[html.Input])

      updateDom(currentValue)
      xformsUpdateReadonly(isMarkedReadonly)

      colorInputElemOpt.foreach { colorInputElem =>
        EventSupport.addListener[dom.Event](colorInputElem, DomEventNames.Input, e => {
          if (XFormsUI.modalProgressPanelShown || isMarkedReadonly) {
            e.preventDefault()
          } else {
            val picked = colorInputElem.value.toLowerCase
            hexInputElemOpt.foreach(_.value = picked)
            swatchAddonElemOpt.foreach(_.classList.remove(EmptyColorClass))
          }
        })

        EventSupport.addListener[dom.Event](colorInputElem, DomEventNames.Change, e => {
          if (XFormsUI.modalProgressPanelShown || isMarkedReadonly) {
            e.preventDefault()
          } else {
            val picked = colorInputElem.value.toLowerCase
            updateDom(picked)
            sendValueToServerIfNeeded(picked)
          }
        })
      }

      hexInputElemOpt.foreach { hexInputElem =>
        EventSupport.addListener[dom.Event](hexInputElem, DomEventNames.Input, _ => {
          if (! isMarkedReadonly) {
            val candidate = normalizeHexValue(hexInputElem.value)
            if (isValidHexColor(candidate)) {
              colorInputElemOpt.foreach(_.value = candidate)
              swatchAddonElemOpt.foreach(_.classList.remove(EmptyColorClass))
            } else {
              swatchAddonElemOpt.foreach(_.classList.add(EmptyColorClass))
            }
          }
        })

        EventSupport.addListeners(hexInputElem, List(DomEventNames.Change, DomEventNames.FocusOut), (_: dom.Event) => {
          if (! isMarkedReadonly) {
            val normalized = normalizeHexValue(hexInputElem.value)
            updateDom(normalized)
            sendValueToServerIfNeeded(normalized)
          }
        })

        EventSupport.addListener[dom.KeyboardEvent](hexInputElem, DomEventNames.KeyPress, e => {
          if (! isMarkedReadonly && Set(10, 13)(e.keyCode)) {
            e.preventDefault()
            val normalized = normalizeHexValue(hexInputElem.value)
            updateDom(normalized)
            sendValueToServerIfNeeded(normalized)
            AjaxClient.fireEvent(
              AjaxEvent(
                eventName = DomEventNames.DOMActivate,
                targetId  = containerElem.id
              )
            )
          }
        })
      }
    }

    override def destroy(): Unit = {
      logger.debug("destroy")
      EventSupport.clearAllListeners()
      swatchAddonElemOpt = None
      colorInputElemOpt  = None
      hexInputElemOpt    = None
    }

    override def xformsGetValue(): String =
      currentValue

    override def xformsUpdateValue(newValue: String): js.UndefOr[Nothing] = {
      val normalized = normalizeHexValue(newValue)
      currentValue = normalized
      updateDom(normalized)
      js.undefined
    }

    override def xformsUpdateReadonly(readonly: Boolean): Unit = {
      logger.debug(s"xformsUpdateReadonly: $readonly")
      colorInputElemOpt.foreach(_.disabled = readonly)
      hexInputElemOpt.foreach(_.readOnly = readonly)
    }

    override def xformsFocus(): Unit = {
      logger.debug("xformsFocus")
      hexInputElemOpt.foreach(_.focus())
    }

    private def updateDom(value: String): Unit = {
      hexInputElemOpt.foreach { hexInputElem =>
        if (hexInputElem.value != value)
          hexInputElem.value = value
      }
      if (isValidHexColor(value)) {
        colorInputElemOpt.foreach(_.value = value.toLowerCase)
        swatchAddonElemOpt.foreach(_.classList.remove(EmptyColorClass))
      } else {
        colorInputElemOpt.foreach(_.value = DefaultPickerColor)
        swatchAddonElemOpt.foreach(_.classList.add(EmptyColorClass))
      }
    }

    private def sendValueToServerIfNeeded(newValue: String): Unit =
      if (newValue != currentValue) {
        currentValue = newValue
        AjaxClient.fireEvent(
          AjaxEvent(
            eventName  = EventNames.XXFormsValue,
            targetId   = containerElem.id,
            properties = Map("value" -> newValue)
          )
        )
      }
  }
}
