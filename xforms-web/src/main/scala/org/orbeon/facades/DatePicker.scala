package org.orbeon.facades

import org.scalajs.jquery.JQuery
import scala.scalajs.js

object DatePicker {

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
