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

import org.orbeon.oxf.util.StringUtils._
import org.orbeon.xforms.$
import org.orbeon.xforms.facade.{Document, XBL, XBLCompanion}
import org.scalajs.jquery.JQuery

import scala.scalajs.js
import DatePickerFacade._
import org.orbeon.xforms.DocumentAPI
import org.scalajs.dom

object Date {
  XBL.declareCompanion("fr|date", new DateCompanion())
}

private class DateCompanion extends XBLCompanion {

  def inputEl    : JQuery     = $(containerElem).find("input")
  def iOS        : Boolean    = $(dom.document.body).hasClass("xforms-ios")
  var datePicker : DatePicker = null

  override def init(): Unit = {

    // Add `readonly` attribute on the input if the control is readonly
    val isReadonly = $(containerElem).is(".xforms-readonly")
    inputEl.prop("readonly", isReadonly)

    if (iOS) {
      // On iOS, use native date picker
      inputEl.attr("type", "date")
      inputEl.on("change", () ⇒ onChangeDate())
    } else {
      // Initialize bootstrap-datepicker
      val options              = new DatePickerOptions
      options.autoclose        = true
      options.enableOnReadonly = false
      datePicker = inputEl.datepicker(options)
      datePicker.onChangeDate(() ⇒ onChangeDate())
    }
  }

  override def xformsGetValue(): String = {
    if (iOS) {
      inputEl.prop("value").asInstanceOf[String]
    } else {
      Option(datePicker.getDate) match {
        case Some(date) ⇒ date.toISOString.substring(0, 10)
        case None       ⇒ ""
      }
    }
  }

  override def xformsUpdateValue(newValue: String): Unit = {
    if (xformsGetValue() != newValue) {
      if (iOS) {
        inputEl.prop("value", newValue)
      } else {
        try {
          val dateParts = newValue.splitTo[List]("-")
          // Parse as a local date (see https://stackoverflow.com/a/33909265/5295)
          val jsDate = new js.Date(
            year  = dateParts(0).toInt,
            month = dateParts(1).toInt - 1,
            date  = dateParts(2).toInt,
            0, 0, 0, 0)
          datePicker.setDate(jsDate)
        } catch {
          case _: Exception ⇒ // Ignore values we can't parse as dates
        }
      }
    }
  }

  override def xformsUpdateReadonly(readonly: Boolean): Unit =
    inputEl.prop("disabled", readonly)

  def setFormat(format: String): Unit = {
    // On iOS, ignore the format as the native widget uses its own format
    if (! iOS) {
      val date = datePicker.getDate
      datePicker.options.format = format
        .replaceAllLiterally("[D]", "d")
        .replaceAllLiterally("[M]", "m")
        .replaceAllLiterally("[Y]", "yyyy")
      datePicker.setDate(date)
    }
  }

  def onChangeDate(): Unit = {
    DocumentAPI.setValue(containerElem.id, xformsGetValue())
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
    var format           : String  = "mm/dd/yyyy"
    var autoclose        : Boolean = false
    var enableOnReadonly : Boolean = true
  }

  implicit class DatePickerOps(val datePicker: DatePicker) extends AnyVal {
    def onChangeDate(f: js.Function0[Unit]): Unit = datePicker.on("changeDate", f)
    def getDate: js.Date = datePicker.datepicker("getDate").asInstanceOf[js.Date]
    def setDate(date: js.Date): Unit = datePicker.datepicker("setDate", date)
    def options: DatePickerOptions = datePicker.data("datepicker").o.asInstanceOf[DatePickerOptions]
  }
}

