/**
 * Copyright (C) 2018 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.xbl

import cats.syntax.option.*
import io.udash.wrappers.jquery.JQueryPromise
import org.log4s.Logger
import org.orbeon.date.JSDateUtils.todayAsIsoDate
import org.orbeon.date.{IsoDate, JSDateUtils}
import org.orbeon.facades.DatePicker.*
import org.orbeon.oxf.util.CoreUtils.*
import org.orbeon.oxf.util.LoggerFactory
import org.orbeon.web.DomEventNames
import org.orbeon.xforms.*
import org.orbeon.xforms.Constants.XFormsIosClass
import org.orbeon.xforms.facade.XBL
import org.scalajs.dom
import org.scalajs.dom.html
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.*

import scala.scalajs.js
import scala.scalajs.js.JSConverters.*
import scala.scalajs.js.{Promise, UndefOr, |}


object Date {

  val logger: Logger = LoggerFactory.createLogger("org.orbeon.xbl.Date")

  XBL.declareCompanion("fr|date", js.constructorOf[DateCompanion])

  // TODO: Can we reduce duplication with `TimeCompanion`?
  private class DateCompanion(containerElem: html.Element) extends XBLCompanionWithState(containerElem) {

    companion =>

    import Private.*
    import io.circe.generic.auto.*
    import io.circe.{Decoder, Encoder}

    type State = DateExternalValue

    val stateEncoder: Encoder[State] = implicitly[Encoder[State]]
    val stateDecoder: Decoder[State] = implicitly[Decoder[State]]

    private object EventSupport           extends EventListenerSupport
    private object DatePickerEventSupport extends EventListenerSupport

    private var visibleInputElemOpt: Option[html.Input] = None
    private var datePickerOpt: Option[DatePicker] = None

    override def init(): Unit = {

      logger.debug("init")

      val visibleInputElem = containerElem.querySelector("input").asInstanceOf[html.Input]
      companion.visibleInputElemOpt = Some(visibleInputElem)

      // Add `readonly` attribute on the input if the control is readonly
      val isReadonly = containerElem.classList.contains("xforms-readonly")
      updateReadonly(isReadonly)

      if (isNativePicker) {
        visibleInputElem.`type` = "date"
        EventSupport.addListener(visibleInputElem, DomEventNames.Change, (_: dom.Event) => onDateSelectedUpdateStateAndSendValueToServer())

        // Also set `disabled` on iOS (see #5376)
        if (visibleInputElem.readOnly)
          visibleInputElem.disabled = true

      } else {

        // 2024-12-06: Use `focusout` like for `fr:time`
        EventSupport.addListener(visibleInputElem, DomEventNames.FocusOut,
          (e: dom.Event) => {
            if (! isMarkedReadonly) {
              logger.debug(s"reacting to event ${e.`type`}")

              updateStateAndSendValueToServer(readValue)

              // Always update visible value with XForms value:
              //
              // - relying on just value change event from server is not enough
              // - value change is not dispatched if the server value hasn't changed
              // - if the visible changed, but XForms hasn't, we still need to show XForms value
              // - see: https://github.com/orbeon/orbeon-forms/issues/1026
              //
              // So either:
              //
              // - the Ajax response called `updateWithServerValues()` and then `updateVisibleValue()`
              // - or it didn't, and we force `updateVisibleValue()` after the response is processed
              //
              // We also call `updateVisibleValue()` immediately in `updateStateAndSendValueToServer()` if the
              // edit value hasn't changed.
              //
  //            val formId = $(containerElem).parents("form").attr("id").get
              AjaxClient.allEventsProcessedF("date").foreach { _ =>
                stateOpt.foreach(updateVisibleValue)
              }
            }
          }
        )

        EventSupport.addListener(visibleInputElem, DomEventNames.KeyPress,
          (e: dom.KeyboardEvent) => {
            if (! isMarkedReadonly) {

              logger.debug(s"reacting to event ${e.`type`}")

              if (Set(10, 13)(e.keyCode)) {
                e.preventDefault()
                updateStateAndSendValueToServer(readValue)
                AjaxClient.fireEvent(
                  AjaxEvent(
                    eventName = DomEventNames.DOMActivate,
                    targetId  = containerElem.id
                  )
                )
              }
            }
          }
        )

        createDatePicker(visibleInputElem, dateExternalValue = None)
      }
    }

    override def destroy(): Unit = {
      logger.debug("destroy")
      EventSupport.clearAllListeners()
      if (! isNativePicker)
        destroyDatePicker()
    }

    private def destroyDatePicker(): Unit = {
      Language.offLangChange(containerElem.id)
      DatePickerEventSupport.clearAllListeners()
      datePickerOpt.foreach(_.destroy())
      datePickerOpt = None
    }

    override def xformsUpdateReadonly(readonly: Boolean): Unit = {
      logger.debug(s"xformsUpdateReadonly: $readonly")
      updateReadonly(readonly)
    }

    def xformsUpdateState(previousStateOpt: Option[State], newState: State): Unit = {

      val DateExternalValue(newValue, newFormat, newExcludedDates, newWeekStart) = newState

      logger.debug(s"updateWithServerValues: `$newValue`, `$newFormat`, `$newExcludedDates`, `$newWeekStart`")

      if (isNativePicker) {
        if (! previousStateOpt.map(_.stringValue).contains(newState.stringValue)) // test probably not needed as we just set the input value? might avoid focus issues?
          updateVisibleValue(newState)
      } else {
        if (! previousStateOpt.contains(newState)) {
          // Don't call `destroy()` directly because that causes the re-initialization of the companion in `xforms.js`!
          destroyDatePicker()
          companion.visibleInputElemOpt.foreach(createDatePicker(_, dateExternalValue = newState.some))
          updateVisibleValue(newState)
        }
      }

      // Also update disabled because this might be called upon an iteration being moved, in which
      // case all the control properties must be updated.
      updateReadonly(companion.isMarkedReadonly)
    }

    override def xformsFocus(): Unit = {
      logger.debug(s"xformsFocus")
      companion.visibleInputElemOpt.foreach(_.focus())
    }

    override def setUserValue(newValue: String): UndefOr[Promise[Unit] | JQueryPromise[js.Function1[js.Any, js.Any], js.Any]] =
      updateStateAndSendValueToServer(_ => newValue)

    private object Private {

      def updateReadonly(readonly: Boolean): Unit =
        visibleInputElemOpt.foreach { visibleInputElem =>
          visibleInputElem.readOnly = readonly
          if (isNativePicker)
            // Also set `disabled` on iOS (see #5376)
            visibleInputElem.disabled = readonly
        }

      def updateStateAndSendValueToServer(read: html.Input => String): Unit =
        visibleInputElemOpt.foreach { visibleInputElem =>
          stateOpt.foreach { state =>

            val visibleInputElemValue = read(visibleInputElem)

            val newState =
              state.copy(
                isoOrUnrecognizedValue =
                  IsoDate.findMagicDateAsIsoDateWithNow(state.format, visibleInputElemValue, todayAsIsoDate).toLeft(visibleInputElemValue)
              )

            val stateUpdated =
              updateStateAndSendValueToServerIfNeeded(
                newState       = newState,
                valueFromState = _.isoOrUnrecognizedValue,
              )

            if (! stateUpdated)
              updateVisibleValue(newState)
          }
        }

      def updateVisibleValue(state: State): Unit =
        companion.visibleInputElemOpt.foreach { visibleInputElem =>
            writeValue(
              visibleInputElem,
              if (isNativePicker)
                state.isoOrUnrecognizedValue.fold(_.toIsoString, identity)
              else
                state.stringValue // date picker will clear field if the date is not an HTML "date string" (ISO-like)
            )
        }

      def readValue(input: html.Input): String =
        input.value

      def writeValue(input: html.Input, value: String): Unit =
        input.value = value

      def createDatePicker(visibleInputElem: html.Input, dateExternalValue: Option[DateExternalValue]): Unit = {

        logger.debug(s"createDatePicker: `$dateExternalValue`")

        val options =  {
          val opts = new DatePickerOptions

          opts.autoclose        = true
          opts.enableOnReadonly = false
          opts.assumeNearbyYear = true
          opts.showOnFocus      = false
          opts.forceParse       = false
          opts.language         = Language.getLang()
          opts.container        = containerElem.closest("dialog, .orbeon")

          dateExternalValue.foreach { case DateExternalValue(_, format, excludedDates, weekStart) =>
            opts.format = format.generateBootstrapFormatString
            weekStart.foreach(opts.weekStart = _)
            if (excludedDates.nonEmpty)
              opts.datesDisabled = excludedDates.flatMap(JSDateUtils.parseIsoDateUsingLocalTimezone).toJSArray
          }

          opts
        }

        val datePicker = $(visibleInputElem).parent().datepicker(options)
        datePickerOpt = Some(datePicker)

        // Register listeners

        // DOM listeners
        DatePickerEventSupport.addListener(containerElem.querySelector(".add-on, .input-group .input-group-addon"), DomEventNames.KeyDown,  onIconKeypress)

        // Date picker listeners
        enableDatePickerChangeListener(datePicker)
        datePicker.onHide(() => { visibleInputElem.focus() }) // Set focus back on field when done with the picker
        datePicker.onShow(() => { visibleInputElem.focus() }) // For date picker to be usable with the keyboard

        // Global language listener
        Language.onLangChange(
          listenerId = containerElem.id,
          listener   = { _ =>
            destroy()
            createDatePicker(visibleInputElem, stateOpt)
          }
        )

        // When the focus leaves the input going to the date picker, we stop propagation, so our generic code doesn't take this as
        // the field losing the focus, which might prematurely show the field as invalid, before users got a chance to select a value
        // in the date picker
        val inputElement = containerElem.querySelector("input")
        Support.stopFocusOutPropagationUseEventListenerSupport(inputElement, _.relatedTarget, "datepicker-dropdown", EventSupport)

        // Set date value
        dateExternalValue.foreach { case DateExternalValue(value, _, _, _) =>
          value match {
            case Left(newIsoDate) =>
              datePicker.setDate(JSDateUtils.isoDateToJsDate(newIsoDate))
              // TODO: set field value?
            case Right(unrecognizedValue) =>
              disableDatePickerChangeListener(datePicker)
              datePicker.clearDates()
              writeValue(visibleInputElem, unrecognizedValue)
              enableDatePickerChangeListener(datePicker)
          }
        }
      }

      def isNativePicker: Boolean = {
        val always  = containerElem.querySelector(":scope > .fr-native-picker-always") != null
        val iOS     = dom.document.body.classList.contains(XFormsIosClass)
        always || iOS
      }

      def enableDatePickerChangeListener(datePicker: DatePicker): Unit =
        datePicker.onChangeDate(() => onDateSelectedUpdateStateAndSendValueToServer())

      def disableDatePickerChangeListener(datePicker: DatePicker): Unit =
        datePicker.offChangeDate()

      // Send the new value to the server when it changes
      def onDateSelectedUpdateStateAndSendValueToServer(): Unit =
        visibleInputElemOpt.foreach { visibleInputElem =>
          stateOpt.foreach { state =>

            val valueFromUIOpt =
              if (isNativePicker) {
                Some(readValue(visibleInputElem))
              } else {
                datePickerOpt.flatMap(datePicker => Option(datePicker.getDate)) match {
                  case None       =>
                    Some(readValue(visibleInputElem))
                  case Some(pickerJsDate) =>

                    val pickerIsoDate =
                      JSDateUtils.jsDateToIsoDateUsingLocalTimezone(pickerJsDate)

                    val stateIsoDateOpt =
                      state.isoOrUnrecognizedValue.left.toOption

                    val dateChanged =
                      stateIsoDateOpt.isEmpty || stateIsoDateOpt.exists(_ != pickerIsoDate)

                    // Don't send UI date to server if it's the same as the state date
                    dateChanged.option(IsoDate.formatDate(pickerIsoDate, state.format))
                }
              }

            valueFromUIOpt.foreach(valueFromUI => updateStateAndSendValueToServer(_ => valueFromUI))
          }
        }

      // Force an update of the date picker if we have a valid date, so when users type "1/2", the value
      // goes "1/2/2019" when the control looses the focus, or users press enter. (I would have thought that
      // the datepicker control would do this on it own, but apparently it doesn't.)
//      def onInputChangeUpdateDatePicker(e: dom.Event): Unit =
//        Option(datePicker.getDate) match {
//          case Some(date) =>
//            println(s"xxx onInputChangeUpdateDatePicker Some($date)")
//            datePicker.setDate(date)
//          case None       =>
//            println(s"xxx onInputChangeUpdateDatePicker: none")
//            datePicker.clearDates()
//        }

      def onIconKeypress(event: dom.KeyboardEvent): Unit =
        if (event.key == " " || event.key == "Enter") {
          event.preventDefault()
          datePickerOpt.foreach(_.showDatepicker())
        }
    }
  }
}