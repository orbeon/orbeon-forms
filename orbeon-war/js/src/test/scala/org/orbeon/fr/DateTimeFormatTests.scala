package org.orbeon.fr

import org.orbeon.web.DomSupport.*
import org.scalajs.dom.html
import org.scalatest.funspec.FixtureAsyncFunSpecLike

import scala.async.Async.*
import scala.scalajs.js


trait DateTimeFormatTests {
  this: FixtureAsyncFunSpecLike & ClientTestSupport =>

  describe("Date, Time, and DateTime output format (issue #7850)") {

    it("must format values correctly in new mode") { _ =>
      withFormReady(app = "tests", form = "date-time-output-format", mode = "new") {
        case FormRunnerWindow(_, formRunnerApi) =>
          async {
            val form = formRunnerApi.getForm(js.undefined)

            // Date controls in new/edit mode
            val dateMetadataVal = getInputValue(form, "date-metadata")
            val dateCustomVal   = getInputValue(form, "date-custom")
            val dateDotsVal     = getInputValue(form, "date-dots")

            assert(dateMetadataVal == "25/10/2026", "date-metadata must use format from metadata ([D01]/[M01]/[Y])")
            assert(dateCustomVal   == "2026-10-25", "date-custom must use format from control ([Y]-[M01]-[D01])")
            assert(dateDotsVal     == "25.10.2026", "date-dots must use format from control ([D01].[M01].[Y])")

            // Time controls in new/edit mode
            val timeMetadataVal = getInputValue(form, "time-metadata")
            val timeCustomVal   = getInputValue(form, "time-custom")
            val time24hVal      = getInputValue(form, "time-24h")

            assert(timeMetadataVal == "14:35:45", "time-metadata must use format from metadata ([H01]:[m]:[s])")
            assert(timeCustomVal   == "2:35 pm",  "time-custom must use format from control ([h]:[m] [P,2-2])")
            assert(time24hVal      == "14:35",    "time-24h must use format from control ([H01]:[m])")

            // DateTime controls in new/edit mode
            val (dtMetadataDateVal, dtMetadataTimeVal) = getDateTimeSubcontrolValues(form, "datetime-metadata")
            assert(dtMetadataDateVal == "25/10/2026", "datetime-metadata date subcontrol must use date format from metadata")
            assert(dtMetadataTimeVal == "14:35:45",   "datetime-metadata time subcontrol must use time format from metadata")

            val (dtCustomDateVal, dtCustomTimeVal) = getDateTimeSubcontrolValues(form, "datetime-custom")
            assert(dtCustomDateVal == "25/10/2026", "datetime-custom date subcontrol uses date format from metadata")
            assert(dtCustomTimeVal == "14:35:45",   "datetime-custom time subcontrol uses time format from metadata")

            val (dtDotsDateVal, dtDotsTimeVal) = getDateTimeSubcontrolValues(form, "datetime-dots")
            assert(dtDotsDateVal == "25/10/2026", "datetime-dots date subcontrol uses date format from metadata")
            assert(dtDotsTimeVal == "14:35:45",   "datetime-dots time subcontrol uses time format from metadata")
          }
      }
    }

    it("must format values correctly in view mode") { _ =>
      withFormReady(app = "tests", form = "date-time-output-format", mode = "view") {
        case FormRunnerWindow(_, formRunnerApi) =>
          async {
            val form = formRunnerApi.getForm(js.undefined)

            // Date controls in view mode
            val dateMetadataVal = getReadonlyText(form, "date-metadata")
            val dateCustomVal   = getReadonlyText(form, "date-custom")
            val dateDotsVal     = getReadonlyText(form, "date-dots")

            assert(dateMetadataVal == "25/10/2026", "date-metadata must show metadata format in view mode")
            assert(dateCustomVal   == "2026-10-25", "date-custom must show control format in view mode")
            assert(dateDotsVal     == "25.10.2026", "date-dots must show control format in view mode")

            // Time controls in view mode
            val timeMetadataVal = getReadonlyText(form, "time-metadata")
            val timeCustomVal   = getReadonlyText(form, "time-custom")
            val time24hVal      = getReadonlyText(form, "time-24h")

            assert(timeMetadataVal == "14:35:45", "time-metadata must show metadata format in view mode")
            assert(timeCustomVal   == "2:35 pm",  "time-custom must show control format in view mode")
            assert(time24hVal      == "14:35",    "time-24h must show control format in view mode")

            // DateTime controls in view mode
            val dtMetadataVal = getReadonlyText(form, "datetime-metadata")
            val dtCustomVal   = getReadonlyText(form, "datetime-custom")
            val dtDotsVal     = getReadonlyText(form, "datetime-dots")

            assert(dtMetadataVal == "25/10/2026 14:35:45", "datetime-metadata must show metadata format in view mode")
            assert(dtCustomVal   == "2026-10-25 2:35 pm",  "datetime-custom must show control format in view mode")
            assert(dtDotsVal     == "25.10.2026 14:35",    "datetime-dots must show control format in view mode")
          }
      }
    }
  }

  private def getInputValue(form: FormRunnerFormTrait, controlName: String): String = {
    val controls = form.findControlsByName(controlName)
    assert(controls.nonEmpty, s"Expected control for `$controlName`")
    val input = controls.head.querySelectorT("input[type='text']").asInstanceOf[html.Input]
    input.value
  }

  private def getDateTimeSubcontrolValues(form: FormRunnerFormTrait, controlName: String): (String, String) = {
    val controls = form.findControlsByName(controlName)
    assert(controls.nonEmpty, s"Expected control for `$controlName`")
    val datetimeControl = controls.head
    val dateSubControl  = datetimeControl.querySelectorT(".xbl-fr-date")
    val timeSubControl  = datetimeControl.querySelectorT(".xbl-fr-time")
    val dateInput       = dateSubControl.querySelectorT("input[type='text']").asInstanceOf[html.Input]
    val timeInput       = timeSubControl.querySelectorT("input[type='text']").asInstanceOf[html.Input]
    (dateInput.value, timeInput.value)
  }

  private def getReadonlyText(form: FormRunnerFormTrait, controlName: String): String = {
    val controls = form.findControlsByName(controlName)
    assert(controls.nonEmpty, s"Expected control for `$controlName`")
    val control = controls.head
    control.querySelectorOpt(".xforms-field")
      .map(_.textContent.trim)
      .getOrElse(control.textContent.trim)
  }
}
