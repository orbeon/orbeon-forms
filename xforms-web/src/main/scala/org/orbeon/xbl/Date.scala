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
import org.orbeon.date.JSDateUtils
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.web.DomEventNames
import org.orbeon.xbl.DatePickerFacade._
import org.orbeon.xforms.Constants.XFormsIosClass
import org.orbeon.xforms.facade.XBL
import org.orbeon.xforms._
import org.scalajs.dom
import org.scalajs.jquery.JQuery

import scala.scalajs.js
import scala.scalajs.js.JSConverters._


object Date {
  XBL.declareCompanion("fr|date", new DateCompanion)
}

private class DateCompanion extends XBLCompanionWithState {

  companion =>

  import io.circe.generic.auto._
  import io.circe.{Decoder, Encoder}

  type State = DateExternalValue

  val stateEncoder: Encoder[State] = implicitly[Encoder[State]]
  val stateDecoder: Decoder[State] = implicitly[Decoder[State]]

  def inputEl    : JQuery     = $(containerElem).find("input").first()
  def iOS        : Boolean    = dom.document.body.classList.contains(XFormsIosClass)
  var datePicker : DatePicker = _

  override def init(): Unit = {

    scribe.debug("init")

    // Add `readonly` attribute on the input if the control is readonly
    val isReadonly = containerElem.classList.contains("xforms-readonly")
    updateReadonly(isReadonly)

    if (iOS) {

      // On iOS, use native date picker
      inputEl.attr("type", "date")
      EventSupport.addListener(inputEl(0), DomEventNames.Change, (_: dom.raw.Event) => onDateSelectedUpdateStateAndSendValueToServer())

      // Get around iOS bug where a touch shows the date picker even when the input is marked as readonly
      EventSupport.addListener(inputEl(0), DomEventNames.TouchStart, (event: dom.raw.Event) => {
        val targetInput = event.target.asInstanceOf[dom.html.Input]
        val isReadonly  = targetInput.readOnly
        if (isReadonly)
          event.preventDefault()
      })

    } else {
      createDatePicker(dateExternalValue = None)
    }
  }

  override def destroy(): Unit = {

    scribe.debug("destroy")

    if (iOS) {
      EventSupport.clearAllListeners()
    } else {
      Language.offLangChange(containerElem.id)
      EventSupport.clearAllListeners()
      datePicker.destroy()
    }
  }

  def xformsUpdateState(previousStateOpt: Option[State], newState: State): Unit = {

    val DateExternalValue(newValue, newFormat, newExcludedDates, newWeekStart) = newState

    scribe.debug(s"updateWithServerValues: `$newValue`, `$newFormat`, `$newExcludedDates`, `$newWeekStart`")

    if (iOS) {
      if (! (previousStateOpt map (_.value) contains newValue))
        inputEl.prop("value", newValue)
    } else {
      if (! previousStateOpt.contains(newState)) {
        destroy()
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

    scribe.debug(s"createDatePicker: `$dateExternalValue`")

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

    datePicker = inputEl.parent().datepicker(options)

    // Register listeners

    // DOM listeners
    EventSupport.addListener(containerElem.querySelector(".add-on"), DomEventNames.KeyDown,  onIconKeypress)
    EventSupport.addListener(inputEl(0),                             DomEventNames.KeyPress, onInputKeypress)
    EventSupport.addListener(inputEl(0),                             DomEventNames.Change,   onInputChangeUpdateDatePicker)

    // Date picker listeners
    enableDatePickerChangeListener()
    datePicker.onHide(              ()                     => { inputEl.focus() }) // Set focus back on field when done with the picker
    datePicker.onShow(              ()                     => { inputEl.focus() }) // For date picker to be usable with the keyboard

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
              inputEl.prop("value", newValue)
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
    inputEl.focus()

  private object EventSupport extends EventListenerSupport

  private def enableDatePickerChangeListener(): Unit =
    datePicker.onChangeDate(        ()                     => onDateSelectedUpdateStateAndSendValueToServer())

  private def disableDatePickerChangeListener(): Unit =
    datePicker.offChangeDate()

  private def getInputFieldValue: String =
    inputEl.prop("value").asInstanceOf[String]

  private def updateReadonly(readonly: Boolean): Unit =
    inputEl.prop("readonly", readonly)

  // Send the new value to the server when it changes
  private def onDateSelectedUpdateStateAndSendValueToServer(): Unit =
    stateOpt foreach { state =>

      val valueFromUI =
        if (iOS) {
          getInputFieldValue
        } else {
          Option(datePicker.getDate) match {
            case Some(date) => JSDateUtils.dateToIsoStringUsingLocalTimezone(date) // https://github.com/orbeon/orbeon-forms/issues/3907
            case None       => getInputFieldValue
          }
        }

      updateStateAndSendValueToServerIfNeeded(
        newState       = state.copy(value = valueFromUI),
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

private object DatePickerFacade {

  implicit def jQuery2DatePicker(jQuery: JQuery): JQueryDatePicker =
    jQuery.asInstanceOf[JQueryDatePicker]

  @js.native
  trait JQueryDatePicker extends JQuery {
    def datepicker(options: DatePickerOptions): DatePicker = js.native
  }

  @js.native
  trait DatePicker extends JQuery {
    def on(eventName: String, f: js.Function0[Unit]): Unit = js.native
    def datepicker(methodName: String, args: js.Any*): js.Any = js.native
  }

  class DatePickerOptions extends js.Object {
    var format           : String            = "mm/dd/yyyy"
    var autoclose        : Boolean           = false
    var enableOnReadonly : Boolean           = true
    var assumeNearbyYear : Boolean           = false
    var showOnFocus      : Boolean           = true
    var forceParse       : Boolean           = true
    var datesDisabled    : js.Array[js.Date] = _
    var language         : String            = "en"
    var container        : String            = _
    var weekStart        : js.UndefOr[Int]   = js.undefined
  }

  implicit class DatePickerOps(private val datePicker: DatePicker) extends AnyVal {
    def destroy()                           : Unit              = datePicker.datepicker("destroy")
    def onChangeDate(f: js.Function0[Unit]) : Unit              = datePicker.on("changeDate", f)
    def offChangeDate()                     : Unit              = datePicker.off("changeDate")
    def onHide      (f: js.Function0[Unit]) : Unit              = datePicker.on("hide", f)
    def onShow      (f: js.Function0[Unit]) : Unit              = datePicker.on("show", f)
    def getDate                             : js.Date           = datePicker.datepicker("getDate").asInstanceOf[js.Date]
    def setDate(date: js.Date)              : Unit              = datePicker.datepicker("setDate", date)
    def clearDates()                        : Unit              = datePicker.datepicker("clearDates", Nil)
    def update()                            : Unit              = datePicker.datepicker("update")
    def showDatepicker()                    : Unit              = datePicker.datepicker("show")
    def options                             : DatePickerOptions = datePicker.data("datepicker").o.asInstanceOf[DatePickerOptions]
  }
}
