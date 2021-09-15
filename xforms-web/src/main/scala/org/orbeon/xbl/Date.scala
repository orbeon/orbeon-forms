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

import org.orbeon.date.JSDateUtils
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.xbl.DatePickerFacade._
import org.orbeon.xforms.Constants.XFormsIosClass
import org.orbeon.xforms.facade.XBL
import org.orbeon.xforms.{$, AjaxClient, AjaxEvent, EventNames, Language, Support}
import org.scalajs.dom
import org.scalajs.dom.FocusEvent
import org.scalajs.jquery.{JQuery, JQueryEventObject}

import scala.scalajs.js
import scala.scalajs.js.JSConverters._

object Date {
  XBL.declareCompanion("fr|date", new DateCompanion)
}

private class DateCompanion extends XBLCompanionWithState {

  companion =>

  import Private._
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
      inputEl.on("change", () => onDateSelectedUpdateStateAndSendValueToServer())
    } else {

      val options              = new DatePickerOptions
      options.autoclose        = true
      options.enableOnReadonly = false
      options.assumeNearbyYear = true
      options.showOnFocus      = false
      options.forceParse       = false
      options.language         = Language.getLang()
      options.container        = ".orbeon"
      datePicker = inputEl.parent().datepicker(options)

      // Register listeners
      containerElem.querySelector(".add-on").addEventListener(EventNames.KeyDown, onIconKeypress)
      inputEl.on(EventNames.KeyPress, (e: JQueryEventObject) => onInputKeypress(e))
      inputEl.on(EventNames.Change,   ()                     => onInputChangeUpdateDatePicker())
      datePicker.onChangeDate(        ()                     => onDateSelectedUpdateStateAndSendValueToServer())
      datePicker.onHide(              ()                     => { inputEl.focus() }) // Set focus back on field when done with the picker
      datePicker.onShow(              ()                     => { inputEl.focus() }) // For date picker to be usable with the keyboard
      Language.onLangChange(
        listenerId = containerElem.id,
        listener   = { newLang =>
          datePicker.options.language = newLang
          datePicker.update()
        }
      )

      // When the focus leaves the input going to the date picker, we stop propagation, so our generic code doesn't take this as
      // the field loosing the focus, which might prematurely show the field as invalid, before users got a chance to select a value
      // in the date picker
      val inputElement = containerElem.querySelector("input")
      Support.stopFocusOutPropagation(inputElement, _.relatedTarget, "datepicker-dropdown")
    }
  }

  override def destroy(): Unit =
    Language.offLangChange(containerElem.id)

  def xformsUpdateState(previousStateOpt: Option[State], newState: State): Unit = {

    val DateExternalValue(newValue, newFormat, newExcludedDates) = newState

    scribe.debug(s"updateWithServerValues: `$newValue`, `$newFormat`, `$newExcludedDates`")

    if (! (previousStateOpt map (_.value) contains newValue)) {
      if (iOS) {
        inputEl.prop("value", newValue)
      } else {
        newValue.trimAllToOpt match {
          case Some(newValue) =>
            JSDateUtils.isoDateToStringUsingLocalTimezone(newValue) match {
              case Some(date) =>
                datePicker.setDate(date)
              case None       =>
                // https://github.com/orbeon/orbeon-forms/issues/4828
                datePicker.clearDates()
                inputEl.prop("value", newValue)
            }
          case None           =>
            datePicker.clearDates()
        }
      }
    }

    if (! (previousStateOpt map (_.format) contains newFormat)) {
      // On iOS, ignore the format as the native widget uses its own format
      if (! iOS) {
        val jsDate = datePicker.getDate

        // Orbeon Forms format:         https://doc.orbeon.com/configuration/properties/xforms#for-xf-input
        // bootstrap-datepicker format: https://bootstrap-datepicker.readthedocs.io/en/latest/options.html#format
        datePicker.options.format = newFormat
          .replace("[D]"  , "d"   )
          .replace("[D01]", "dd"  )
          .replace("[M]"  , "m"   )
          .replace("[M01]", "mm"  )
          .replace("[Y]"  , "yyyy")

        // Don't set if `null` as that means we have a value which is not a date, and if we set it to `null`
        // we will cause the value to be emptied.
        // https://github.com/orbeon/orbeon-forms/issues/4828
        if (jsDate ne null)
          datePicker.setDate(jsDate)
      }
    }

    if (! (previousStateOpt map (_.excludedDates) contains newExcludedDates)) {
      if (! iOS) {
        datePicker.options.datesDisabled = newExcludedDates.toJSArray.map(new js.Date(_))
        datePicker.update()
      }
    }
  }

  override def xformsUpdateReadonly(readonly: Boolean): Unit =
    updateReadonly(readonly)

  override def xformsFocus(): Unit =
    inputEl.focus()

  private object Private {

    private def getInputFieldValue: String =
      inputEl.prop("value").asInstanceOf[String]

    def updateReadonly(readonly: Boolean): Unit =
      inputEl.prop("readonly", readonly)

    // Send the new value to the server when it changes
    def onDateSelectedUpdateStateAndSendValueToServer(): Unit =
      stateOpt foreach { state =>

        val valueFromUI =
          if (iOS) {
            getInputFieldValue
          } else {
            Option(datePicker.getDate) match {
              case Some(date) => JSDateUtils.dateToISOStringUsingLocalTimezone(date) // https://github.com/orbeon/orbeon-forms/issues/3907
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
    def onInputChangeUpdateDatePicker(): Unit =
      Option(datePicker.getDate) match {
        case Some(date) => datePicker.setDate(date)
        case None       => datePicker.clearDates()
      }

    def onInputKeypress(e: JQueryEventObject): Unit =
      if (Set(10, 13)(e.which)) {
        e.preventDefault()
        onDateSelectedUpdateStateAndSendValueToServer()
        AjaxClient.fireEvent(
          AjaxEvent(
            eventName = EventNames.DOMActivate,
            targetId  = containerElem.id
          )
        )
      }

    def onIconKeypress(event: dom.KeyboardEvent): Unit =
      if (event.key == " " || event.key == "Enter") {
        event.preventDefault()
        datePicker.showDatepicker()
      }
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
  }

  implicit class DatePickerOps(private val datePicker: DatePicker) extends AnyVal {
    def onChangeDate(f: js.Function0[Unit]) : Unit              = datePicker.on("changeDate", f)
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
