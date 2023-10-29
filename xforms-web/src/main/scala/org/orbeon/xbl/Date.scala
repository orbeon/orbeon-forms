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

import cats.syntax.option._
import org.log4s.Logger
import org.orbeon.date.JSDateUtils
import org.orbeon.facades.DatePicker._
import org.orbeon.oxf.util.CoreUtils.BooleanOps
import org.orbeon.oxf.util.LoggerFactory
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.web.DomEventNames
import org.orbeon.xforms.Constants.XFormsIosClass
import org.orbeon.xforms._
import org.orbeon.xforms.facade.XBL
import org.scalajs.dom
import org.scalajs.dom.html
import org.scalajs.jquery.JQueryPromise

import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import scala.scalajs.js.{Promise, UndefOr, |}


object Date {
  val logger: Logger = LoggerFactory.createLogger("org.orbeon.xbl.Date")
  XBL.declareCompanion("fr|date", js.constructorOf[DateCompanion])
}

private class DateCompanion(containerElem: html.Element) extends XBLCompanionWithState(containerElem) {

  companion =>

  import Date._
  import io.circe.generic.auto._
  import io.circe.{Decoder, Encoder}

  type State = DateExternalValue

  val stateEncoder: Encoder[State] = implicitly[Encoder[State]]
  val stateDecoder: Decoder[State] = implicitly[Decoder[State]]

  private def inputHtmlEl   : dom.html.Input = containerElem.querySelector("input").asInstanceOf[dom.html.Input]
  private var datePicker    : DatePicker     = _

  override def init(): Unit = {

    logger.debug("init")

    // Add `readonly` attribute on the input if the control is readonly
    val isReadonly = containerElem.classList.contains("xforms-readonly")
    updateReadonly(isReadonly)

    if (isNativePicker) {
      inputHtmlEl.`type` = "date"
      EventSupport.addListener(inputHtmlEl, DomEventNames.Change, (_: dom.raw.Event) => onDateSelectedUpdateStateAndSendValueToServer())

      // Also set `disabled` on iOS (see #5376)
      if (inputHtmlEl.readOnly)
        inputHtmlEl.disabled = true

    } else {
      createDatePicker(dateExternalValue = None)
    }
  }

  override def destroy(): Unit = {
    logger.debug("destroy")
    destroyImpl()
  }

  override def setUserValue(newValue: String): UndefOr[Promise[Unit] | JQueryPromise] =
    updateStateAndSendValueToServer(newValue)

  private def destroyImpl(): Unit =
    if (isNativePicker) {
      EventSupport.clearAllListeners()
    } else {
      Language.offLangChange(containerElem.id)
      EventSupport.clearAllListeners()
      datePicker.destroy()
    }

  def xformsUpdateState(previousStateOpt: Option[State], newState: State): Unit = {

    val DateExternalValue(newValue, newFormat, newExcludedDates, newWeekStart) = newState

    logger.debug(s"updateWithServerValues: `$newValue`, `$newFormat`, `$newExcludedDates`, `$newWeekStart`")

    if (isNativePicker) {
      if (! (previousStateOpt map (_.value) contains newValue))
        inputHtmlEl.value = newValue
    } else {
      if (! previousStateOpt.contains(newState)) {
        // Don't call `destroy()` directly because that causes the re-initialization of the companion in `xforms.js`!
        destroyImpl()
        createDatePicker(dateExternalValue = newState.some)
      }
    }
  }

  // Orbeon Forms format:         https://doc.orbeon.com/configuration/properties/xforms#for-xf-input
  // bootstrap-datepicker format: https://bootstrap-datepicker.readthedocs.io/en/latest/options.html#format
  private def orbeonFormatToBootstrapFormat(format: String): String =
    format
      .replace("[D]"  , "d"   )
      .replace("[D01]", "dd"  )
      .replace("[M]"  , "m"   )
      .replace("[M01]", "mm"  )
      .replace("[Y]"  , "yyyy")

  private def createDatePicker(dateExternalValue: Option[DateExternalValue]): Unit = {

    logger.debug(s"createDatePicker: `$dateExternalValue`")

    val options =  {
      val opts = new DatePickerOptions

      opts.autoclose        = true
      opts.enableOnReadonly = false
      opts.assumeNearbyYear = true
      opts.showOnFocus      = false
      opts.forceParse       = false
      opts.language         = Language.getLang()
      opts.container        = ".orbeon"

      dateExternalValue foreach { case DateExternalValue(_, format, excludedDates, weekStart) =>
        opts.format = orbeonFormatToBootstrapFormat(format)
        weekStart.foreach(opts.weekStart = _)
        if (excludedDates.nonEmpty)
          opts.datesDisabled = excludedDates.flatMap(JSDateUtils.parseIsoDateUsingLocalTimezone).toJSArray
      }

      opts
    }

    datePicker = $(inputHtmlEl).parent().datepicker(options)

    // Register listeners

    // DOM listeners
    EventSupport.addListener(containerElem.querySelector(".add-on, .input-group .input-group-addon"), DomEventNames.KeyDown,  onIconKeypress)
    EventSupport.addListener(inputHtmlEl,                                                             DomEventNames.KeyPress, onInputKeypress)
    EventSupport.addListener(inputHtmlEl,                                                             DomEventNames.Change,   onInputChangeUpdateDatePicker)

    // Date picker listeners
    enableDatePickerChangeListener()
    datePicker.onHide(() => { inputHtmlEl.focus() }) // Set focus back on field when done with the picker
    datePicker.onShow(() => { inputHtmlEl.focus() }) // For date picker to be usable with the keyboard

    // Global language listener
    Language.onLangChange(
      listenerId = containerElem.id,
      listener   = { _ =>
        destroy()
        createDatePicker(stateOpt)
      }
    )

    // When the focus leaves the input going to the date picker, we stop propagation, so our generic code doesn't take this as
    // the field loosing the focus, which might prematurely show the field as invalid, before users got a chance to select a value
    // in the date picker
    val inputElement = containerElem.querySelector("input")
    Support.stopFocusOutPropagationUseEventListenerSupport(inputElement, _.relatedTarget, "datepicker-dropdown", EventSupport)

    // Set date value if needed
    dateExternalValue foreach { case DateExternalValue(value, _, _, _) =>
      value.trimAllToOpt match {
        case Some(newValue) =>

          JSDateUtils.parseIsoDateUsingLocalTimezone(newValue) match {
            case Some(date) =>
              datePicker.setDate(date)
            case None       =>
              // https://github.com/orbeon/orbeon-forms/issues/4794
              // Issue: `clearDates()` itself sets the value of the field AND causes the `onChangeDate` listener
              // to run and send a blank value to the server. The listener for `clearDates()` runs synchronously
              // and so runs before what follows below here.
              // Now there is at least one issue, which is that when opening the date picker with a value like
              // `2021-11-33` in the field the picker shows a year of `1933`. We'd probably like to be something
              // closer to either a guess of the date entered, or the current date, but not `1933`. I tried a few
              // options, including `update()` on the picker, passing a `new js.Date()`, etc. but nothing does it.
              // I think we need a better date picker.
              disableDatePickerChangeListener()
              datePicker.clearDates()
              inputHtmlEl.value = newValue
              enableDatePickerChangeListener()
          }
        case None           =>
          datePicker.clearDates()
      }
    }
  }

  override def xformsUpdateReadonly(readonly: Boolean): Unit =
    updateReadonly(readonly)

  override def xformsFocus(): Unit =
    inputHtmlEl.focus()

  private object EventSupport extends EventListenerSupport

  private def isNativePicker: Boolean = {
    val always  = containerElem.querySelector(":scope > .fr-native-picker-always") != null
    val iOS     = dom.document.body.classList.contains(XFormsIosClass)
    always || iOS
  }

  private def enableDatePickerChangeListener(): Unit =
    datePicker.onChangeDate(        ()                     => onDateSelectedUpdateStateAndSendValueToServer())

  private def disableDatePickerChangeListener(): Unit =
    datePicker.offChangeDate()

  private def getInputFieldValue: String =
    inputHtmlEl.value

  private def updateReadonly(readonly: Boolean): Unit = {
    inputHtmlEl.readOnly = readonly
    if (isNativePicker)
      // Also set `disabled` on iOS (see #5376)
      inputHtmlEl.disabled = readonly
  }

  // Send the new value to the server when it changes
  private def onDateSelectedUpdateStateAndSendValueToServer(): Unit =
    stateOpt foreach { state =>

      val valueFromUIOpt =
        if (isNativePicker) {
          Some(getInputFieldValue)
        } else {
          Option(datePicker.getDate) match {
            case None       => Some(getInputFieldValue)
            case Some(uiDate) =>
              // Don't send UI date to server if it's the same as the state date
              val stateDateOpt = JSDateUtils.parseIsoDateUsingLocalTimezone(state.value)
              val dateChanged  = stateDateOpt.isEmpty || stateDateOpt.exists(_.getTime() != uiDate.getTime())
              dateChanged.option(JSDateUtils.dateToIsoStringUsingLocalTimezone(uiDate))
          }
        }

      valueFromUIOpt.foreach(updateStateAndSendValueToServer)
    }

  private def updateStateAndSendValueToServer(newValue: String): Unit =
    stateOpt foreach { state =>
      updateStateAndSendValueToServerIfNeeded(
        newState       = state.copy(value = newValue),
        valueFromState = _.value
      )
    }

  // Force an update of the date picker if we have a valid date, so when users type "1/2", the value
  // goes "1/2/2019" when the control looses the focus, or users press enter. (I would have thought that
  // the datepicker control would do this on it own, but apparently it doesn't.)
  private def onInputChangeUpdateDatePicker(e: dom.Event): Unit =
    Option(datePicker.getDate) match {
      case Some(date) => datePicker.setDate(date)
      case None       => datePicker.clearDates()
    }

  private def onInputKeypress(e: dom.KeyboardEvent): Unit =
    if (Set(10, 13)(e.keyCode)) {
      e.preventDefault()
      onDateSelectedUpdateStateAndSendValueToServer()
      AjaxClient.fireEvent(
        AjaxEvent(
          eventName = DomEventNames.DOMActivate,
          targetId  = containerElem.id
        )
      )
    }

  private def onIconKeypress(event: dom.KeyboardEvent): Unit =
    if (event.key == " " || event.key == "Enter") {
      event.preventDefault()
      datePicker.showDatepicker()
    }
}
