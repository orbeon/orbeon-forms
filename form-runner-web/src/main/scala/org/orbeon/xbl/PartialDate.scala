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
import org.orbeon.date.{DateFormat, IsoTime}
import org.orbeon.facades.BoostrapDatepicker.*
import org.orbeon.oxf.util.LoggerFactory
import org.orbeon.web.DomSupport.*
import org.orbeon.web.{DomEventNames, JSDateUtils}
import org.orbeon.xforms.*
import org.orbeon.xforms.facade.XBLCompanion
import org.scalajs.dom
import org.scalajs.dom.html

import scala.scalajs.js


object PartialDate {

  private val logger: Logger = LoggerFactory.createLogger("org.orbeon.xbl.PartialDate")

  XFormsXbl.declareCompanion("fr|partial-date", js.constructorOf[PartialDateCompanion])

  private class PartialDateCompanion(containerElem: html.Element) extends XBLCompanion {

    private object EventSupport           extends EventListenerSupport
    private object DatePickerEventSupport extends EventListenerSupport

    private var inputElemOpt      : Option[html.Input]           = None
    private var datePickerOpt     : Option[DatePicker]           = None
    private var configObserverOpt : Option[dom.MutationObserver] = None
    private var datePickerShown   : Boolean                      = false
    private var currentRawValue   : String                       = ""

    private def readConfig(inputElem: html.Input): (DateFormat, Set[EdtfPrecision], Boolean, Boolean) = {
      val dateFormat      = PartialDateSupport.parseDateFormatOrDefault(inputElem.dataset.getOrElse("format", ""))
      val precisions      = PartialDateSupport.parsePrecisions(inputElem.dataset.getOrElse("precisions", ""))
      val allowQualifiers = PartialDateSupport.parseBooleanParam(inputElem.dataset.getOrElse("allowQualifiers", ""), default = false)
      val allowIntervals  = PartialDateSupport.parseBooleanParam(inputElem.dataset.getOrElse("allowIntervals", ""), default = false)
      (dateFormat, precisions, allowQualifiers, allowIntervals)
    }

    private def pickerModeOpt(precisions: Set[EdtfPrecision]): Option[Int] =
      if (precisions.contains(EdtfPrecision.Day))        Some(0)
      else if (precisions.contains(EdtfPrecision.Month)) Some(1)
      else                                               None

    override def init(): Unit = {
      logger.debug("init")

      inputElemOpt = containerElem.querySelectorOpt("input").collect { case i: html.Input => i }

      inputElemOpt.foreach { inputElem =>
        updateReadonly(isMarkedReadonly)
        updateDomFromRawValue(currentRawValue)

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

        createDatePickerIfEnabled(inputElem)

        val observer = new dom.MutationObserver((_, _) => {
          destroyDatePicker()
          createDatePickerIfEnabled(inputElem)
          updateDomFromRawValue(currentRawValue)
        })
        observer.observe(
          inputElem,
          new dom.MutationObserverInit {
            attributes      = true
            attributeFilter = js.Array("data-format", "data-precisions", "data-allow-qualifiers", "data-allow-intervals")
          }
        )
        configObserverOpt = Some(observer)
      }
    }

    override def destroy(): Unit = {
      logger.debug("destroy")
      configObserverOpt.foreach(_.disconnect())
      configObserverOpt = None
      destroyDatePicker()
      EventSupport.clearAllListeners()
      inputElemOpt = None
    }

    override def xformsGetValue(): String =
      currentRawValue

    override def xformsUpdateValue(newValue: String): js.UndefOr[Nothing] = {
      currentRawValue = Option(newValue).getOrElse("")
      updateDomFromRawValue(currentRawValue)
      syncDatePickerFromRawValue(currentRawValue)
      js.undefined
    }

    override def xformsUpdateReadonly(readonly: Boolean): Unit = {
      logger.debug(s"xformsUpdateReadonly: $readonly")
      updateReadonly(readonly)
    }

    override def xformsFocus(): Unit = {
      logger.debug("xformsFocus")
      inputElemOpt.foreach(_.focus())
    }

    private def updateReadonly(readonly: Boolean): Unit =
      inputElemOpt.foreach(_.readOnly = readonly)

    private def destroyDatePicker(): Unit = {
      Language.offLangChange(containerElem.id)
      DatePickerEventSupport.clearAllListeners()
      datePickerOpt.foreach(_.destroy())
      datePickerOpt = None
      datePickerShown = false
    }

    private def createDatePickerIfEnabled(inputElem: html.Input): Unit = {
      val (dateFormat, precisions, _, _) = readConfig(inputElem)
      pickerModeOpt(precisions).foreach { minViewMode =>
        val options = new DatePickerOptions
        options.autoclose        = true
        options.enableOnReadonly = false
        options.assumeNearbyYear = true
        options.showOnFocus      = false
        options.forceParse       = false
        options.language         = Language.fullLangOrDefault
        options.container        = containerElem.closestT("dialog, .orbeon")
        options.todayHighlight   = containerElem.querySelector(":scope > .fr-today-highlight-true") != null
        options.minViewMode      = minViewMode
        if (minViewMode == 1) {
          options.startView = 1
          options.format    = PartialDateSupport.generateBootstrapMonthFormat(dateFormat)
        } else {
          options.format    = dateFormat.generateBootstrapFormatString
        }

        val datePicker = $(inputElem).parent().datepicker(options)
        datePickerOpt = Some(datePicker)

        containerElem.querySelectorOpt(".add-on, .input-group .input-group-addon").foreach { addOnElem =>
          DatePickerEventSupport.addListener[dom.KeyboardEvent](addOnElem, DomEventNames.KeyDown, onIconKeypress)
        }

        enableDatePickerChangeListener(datePicker, inputElem, minViewMode)
        datePicker.onShow(() => {
          datePickerShown = true
          syncDatePickerFromRawValue(currentRawValue)
        })
        datePicker.onHide(() => {
          datePickerShown = false
          inputElem.focus()
        })

        Language.onLangChange(
          listenerId = containerElem.id,
          listener   = { _ =>
            destroyDatePicker()
            createDatePickerIfEnabled(inputElem)
          }
        )

        Support.stopFocusOutPropagationUseEventListenerSupport(inputElem, _.relatedTarget, "datepicker-dropdown", EventSupport)
        Support.stopFocusOutPropagationUseEventListenerSupport(inputElem, _.relatedTarget, "add-on",              EventSupport)

        syncDatePickerFromRawValue(currentRawValue)
      }
    }

    private def enableDatePickerChangeListener(datePicker: DatePicker, inputElem: html.Input, minViewMode: Int): Unit =
      datePicker.onChangeDate(() => {
        if (! isMarkedReadonly) {
          Option(datePicker.getDate).foreach { pickerJsDate =>
            val iso = JSDateUtils.jsDateToIsoDateUsingLocalTimezone(pickerJsDate)
            val canonical =
              if (minViewMode == 1)
                f"${iso.year}%04d-${IsoTime.pad2(iso.month)}"
              else
                iso.toIsoString
            updateDomFromRawValue(canonical)
            sendValueToServerIfNeeded(canonical)
          }
        }
      })

    private def syncDatePickerFromRawValue(rawValue: String): Unit =
      for {
        inputElem  <- inputElemOpt
        datePicker <- datePickerOpt
      } {
        val formatted = {
          val (dateFormat, _, _, _) = readConfig(inputElem)
          PartialDateSupport.formatForEdit(rawValue, dateFormat)
        }
        datePicker.offChangeDate()
        PartialDateSupport.earliestIsoDateOpt(rawValue) match {
          case Some(iso) =>
            datePicker.setUTCDate(new js.Date(js.Date.UTC(iso.year, iso.month - 1, iso.day)))
          case None =>
            datePicker.clearDates()
        }
        // Always restore the actual formatted partial-date text so bootstrap-datepicker doesn't overwrite e.g. "1961" or "192X"
        if (inputElem.value != formatted)
          inputElem.value = formatted
        val (_, precisions, _, _) = readConfig(inputElem)
        pickerModeOpt(precisions).foreach(enableDatePickerChangeListener(datePicker, inputElem, _))
      }

    private def commitCurrentInputValue(inputElem: html.Input): Unit = {
      val (dateFormat, precisions, allowQualifiers, allowIntervals) = readConfig(inputElem)
      val normalized = PartialDateSupport.normalizeUserInput(
        raw             = inputElem.value,
        dateFormat      = dateFormat,
        precisions      = precisions,
        allowQualifiers = allowQualifiers,
        allowIntervals  = allowIntervals
      )
      val formatted = PartialDateSupport.formatForEdit(normalized, dateFormat)
      if (inputElem.value != formatted)
        inputElem.value = formatted
      sendValueToServerIfNeeded(normalized)
      syncDatePickerFromRawValue(normalized)
    }

    private def updateDomFromRawValue(rawValue: String): Unit =
      inputElemOpt.foreach { inputElem =>
        val (dateFormat, _, _, _) = readConfig(inputElem)
        val formatted = PartialDateSupport.formatForEdit(rawValue, dateFormat)
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

    private def onIconKeypress(event: dom.KeyboardEvent): Unit = {
      if (! datePickerShown) {
        if (event.key == " " || event.key == "Enter") {
          event.preventDefault()
          event.stopPropagation()
          datePickerOpt.foreach(_.showDatepicker())
        }
      } else {
        datePickerOpt.foreach(_.data("datepicker").get.asInstanceOf[js.Dynamic].keydown(event))
      }
    }
  }
}
