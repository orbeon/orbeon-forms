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
import org.orbeon.xbl.DurationSupport.{DurationFieldValues, DurationUnit}
import org.orbeon.xforms.*
import org.orbeon.xforms.facade.XBLCompanion
import org.scalajs.dom
import org.scalajs.dom.html

import scala.scalajs.js
import scala.scalajs.js.UndefOr


object FrDuration {

  private val logger: Logger = LoggerFactory.createLogger("org.orbeon.xbl.FrDuration")

  private val HiddenClass = "xforms-hidden"

  XFormsXbl.declareCompanion("fr|duration", js.constructorOf[FrDurationCompanion])

  private class FrDurationCompanion(containerElem: html.Element) extends XBLCompanion {

    private object EventSupport extends EventListenerSupport

    private var innerContainerOpt: Option[html.Element]         = None
    private var daysInputOpt     : Option[html.Input]           = None
    private var hoursInputOpt    : Option[html.Input]           = None
    private var minutesInputOpt  : Option[html.Input]           = None
    private var secondsInputOpt  : Option[html.Input]           = None
    private var observerOpt      : Option[dom.MutationObserver] = None
    private var currentValue     : String                       = ""

    private def activeFields: List[DurationUnit] =
      DurationSupport.parseFields(
        innerContainerOpt
          .flatMap(_.dataset.get("frFields"))
          .getOrElse(DurationSupport.DefaultFieldsParam)
      )

    private def inputForUnit(unit: DurationUnit): Option[html.Input] = unit match {
      case DurationUnit.Days    => daysInputOpt
      case DurationUnit.Hours   => hoursInputOpt
      case DurationUnit.Minutes => minutesInputOpt
      case DurationUnit.Seconds => secondsInputOpt
    }

    private def allInputsWithUnits: List[(DurationUnit, html.Input)] =
      DurationUnit.AllInOrder.flatMap(u => inputForUnit(u).map(u -> _))

    override def init(): Unit = {

      logger.debug("init")

      innerContainerOpt = containerElem.querySelectorOpt(".fr-duration-container")
      daysInputOpt      = containerElem.queryNestedElems[html.Input]("input.fr-duration-days").headOption
      hoursInputOpt     = containerElem.queryNestedElems[html.Input]("input.fr-duration-hours").headOption
      minutesInputOpt   = containerElem.queryNestedElems[html.Input]("input.fr-duration-minutes").headOption
      secondsInputOpt   = containerElem.queryNestedElems[html.Input]("input.fr-duration-seconds").headOption

      updateDom(currentValue)
      xformsUpdateReadonly(isMarkedReadonly)

      containerElem.querySelectorOpt(".xforms-label").foreach { labelElem =>
        EventSupport.addListener[dom.MouseEvent](labelElem, DomEventNames.Click, _ => {
          if (! isMarkedReadonly && ! XFormsUI.modalProgressPanelShown)
            xformsFocus()
        })
      }

      allInputsWithUnits.foreach { case (_, inputElem) =>

        EventSupport.addListener[dom.Event](inputElem, DomEventNames.Input, e => {
          if (XFormsUI.modalProgressPanelShown || isMarkedReadonly) {
            e.preventDefault()
            updateDom(currentValue)
          } else {
            commitFromInputs(normalizeDom = false)
          }
        })

        EventSupport.addListener[dom.Event](inputElem, DomEventNames.Change, e => {
          if (XFormsUI.modalProgressPanelShown || isMarkedReadonly) {
            e.preventDefault()
            updateDom(currentValue)
          } else {
            commitFromInputs(normalizeDom = false)
          }
        })

        EventSupport.addListener[dom.FocusEvent](inputElem, DomEventNames.FocusOut, e => {
          val relatedNodeOpt = Option(e.relatedTarget).collect { case n: dom.Node => n }
          val focusStayedInside = relatedNodeOpt.exists(containerElem.contains)
          if (! focusStayedInside && ! isMarkedReadonly) {
            if (DurationSupport.parseIsoDuration(currentValue).isDefined || currentValue.isAllBlank)
              updateDom(currentValue)
          }
        })

        EventSupport.addListener[dom.KeyboardEvent](inputElem, DomEventNames.KeyDown, e => {
          if (! isMarkedReadonly && ! XFormsUI.modalProgressPanelShown && ! e.isComposing) {
            if (e.key == "ArrowUp" || e.key == "ArrowDown") {
              e.preventDefault()
              val delta = if (e.key == "ArrowUp") 1 else -1
              inputElem.value = DurationSupport.stepUnitValue(inputElem.value, delta)
              commitFromInputs(normalizeDom = true)
            } else if (e.key == "Enter") {
              e.preventDefault()
              commitFromInputs(normalizeDom = true)
              AjaxClient.fireEvent(
                AjaxEvent(
                  eventName = DomEventNames.DOMActivate,
                  targetId  = containerElem.id
                )
              )
            }
          }
        })
      }

      innerContainerOpt.foreach { inner =>
        val observer = new dom.MutationObserver((_, _) => {
          updateDom(currentValue)
        })
        observer.observe(
          inner,
          new dom.MutationObserverInit {
            attributes      = true
            attributeFilter = js.Array("data-fr-fields")
          }
        )
        observerOpt = Some(observer)
      }
    }

    override def destroy(): Unit = {
      logger.debug("destroy")
      observerOpt.foreach(_.disconnect())
      observerOpt = None
      EventSupport.clearAllListeners()
      innerContainerOpt = None
      daysInputOpt      = None
      hoursInputOpt     = None
      minutesInputOpt   = None
      secondsInputOpt   = None
    }

    override def xformsGetValue(): String =
      currentValue

    override def xformsUpdateValue(newValue: String): UndefOr[Nothing] = {
      val normalized = Option(newValue).getOrElse("")
      currentValue = normalized
      updateDom(normalized)
      js.undefined
    }

    override def xformsUpdateReadonly(readonly: Boolean): Unit = {
      logger.debug(s"xformsUpdateReadonly: $readonly")
      allInputsWithUnits.foreach { case (_, inputElem) =>
        inputElem.disabled = readonly
      }
      innerContainerOpt.foreach(_.classList.toggle("xforms-readonly", readonly))
    }

    override def xformsFocus(): Unit =
      activeFields.flatMap(inputForUnit).headOption.foreach(_.focus())

    private def readDomFieldValues(): DurationFieldValues =
      DurationFieldValues(
        days    = daysInputOpt.map(_.value).getOrElse(""),
        hours   = hoursInputOpt.map(_.value).getOrElse(""),
        minutes = minutesInputOpt.map(_.value).getOrElse(""),
        seconds = secondsInputOpt.map(_.value).getOrElse("")
      )

    private def commitFromInputs(normalizeDom: Boolean): Unit = {
      val fields = activeFields
      val newIso = DurationSupport.fromFieldValues(readDomFieldValues(), fields)
      val changed = newIso != currentValue
      currentValue = newIso
      if (normalizeDom && (DurationSupport.parseIsoDuration(newIso).isDefined || newIso.isAllBlank))
        updateDom(newIso)
      if (changed)
        DocumentAPI.setValue(containerElem.id, newIso)
    }

    private def updateLabelFor(fields: List[DurationUnit]): Unit =
      for {
        firstUnit  <- fields.headOption
        firstInput <- inputForUnit(firstUnit)
        if firstInput.id.nonAllBlank
        labelElem  <- containerElem.queryNestedElems[html.Label]("label.xforms-label").headOption
      } locally {
        labelElem.htmlFor = firstInput.id
      }

    private def updateDom(isoValue: String): Unit = {
      val fields    = activeFields
      val activeSet = fields.toSet
      val fv        = DurationSupport.toFieldValues(isoValue, fields)

      DurationUnit.AllInOrder.foreach { unit =>
        val isUnitActive = activeSet.contains(unit)
        containerElem
          .querySelectorOpt(s".fr-duration-field-${unit.name}")
          .foreach(_.classList.toggle(HiddenClass, ! isUnitActive))
      }

      daysInputOpt.foreach(_.value    = fv.days)
      hoursInputOpt.foreach(_.value   = fv.hours)
      minutesInputOpt.foreach(_.value = fv.minutes)
      secondsInputOpt.foreach(_.value = fv.seconds)

      updateLabelFor(fields)
    }
  }
}
