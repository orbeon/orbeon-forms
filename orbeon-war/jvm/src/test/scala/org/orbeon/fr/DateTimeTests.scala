package org.orbeon.fr

import org.orbeon.fr.PlaywrightDomSupport.*
import org.scalatest.funspec.FixtureAnyFunSpecLike


trait DateTimeTests {
  this: FixtureAnyFunSpecLike & ClientTestSupport =>

  describe("DateTime control") {

    it("must not show validation error when tabbing from date input to calendar icon") { _ =>
      withFormReady(app = "orbeon-features", form = "controls", queryStringOpt = Some("fr-wizard-page=date-time-controls")) {
        case FormRunnerWindow(xformsWindow, formRunnerApi) =>
          val form            = formRunnerApi.getForm
          val datetimeControl = form.findControlsByName("datetime").head
          val dateUsControl   = form.findControlsByName("date-us").head
          val dateSubControl  = datetimeControl.querySelectorT(".xbl-fr-date")
          val timeSubControl  = datetimeControl.querySelectorT(".xbl-fr-time")
          val dateInput       = dateSubControl.querySelectorT("input[type='text']")
          val timeInput       = timeSubControl.querySelectorT("input[type='text']")
          val calendarIcon    = dateSubControl.querySelectorT(".add-on")
          val dateUsInput     = dateUsControl.querySelectorT("input[type='text']")

          dateInput.focus()
          dateInput.value = ""
          dateInput.dispatchEvent("change")
          calendarIcon.focus()
          xformsWindow.ajaxServer.waitForAllEventsProcessed()
          assert(
            datetimeControl.querySelectorOpt(".xforms-alert.xforms-active").isEmpty,
            "Clear the date, focus on the calendar icon; no alert shows since we're still within the datetime control"
          )

          dateUsInput.focus()
          xformsWindow.ajaxServer.waitForAllEventsProcessed()
          assert(
            datetimeControl.querySelectorOpt(".xforms-alert.xforms-active").isDefined,
            "Exit the datetime control; the alert shows"
          )

          dateInput.focus()
          dateInput.value = "2026-05-22"
          dateInput.dispatchEvent("change")
          timeInput.focus()
          xformsWindow.ajaxServer.waitForAllEventsProcessed()
          assert(
            datetimeControl.querySelectorOpt(".xforms-alert.xforms-active").isDefined,
            "Enter a valid date; alert still shows as we have exited the datetime"
          )

          dateUsInput.focus()
          xformsWindow.ajaxServer.waitForAllEventsProcessed()
          assert(
            datetimeControl.querySelectorOpt(".xforms-alert.xforms-active").isEmpty,
            "Exit the datetime control; the alert is hidden"
          )

          dateInput.focus()
          dateInput.value = ""
          dateInput.dispatchEvent("change")
          timeInput.focus()
          xformsWindow.ajaxServer.waitForAllEventsProcessed()
          assert(
            datetimeControl.querySelectorOpt(".xforms-alert.xforms-active").isEmpty,
            "Clear the date, focus on the time (same as earlier, but this time the datetime is visited); no alert"
          )
      }
    }

    it("must disable the correct excluded dates in the datepicker in positive timezones") { _ =>
      withFormReady(
        app            = "orbeon-features",
        form           = "controls",
        queryStringOpt = Some("fr-wizard-page=date-time-controls"),
        timezoneIdOpt  = Some("Europe/Helsinki")
      ) {
        case FormRunnerWindow(xformsWindow, formRunnerApi) =>
          val form          = formRunnerApi.getForm
          val dateEuControl = form.findControlsByName("date-eu").head
          val calendarIcon  = dateEuControl.querySelectorT(".add-on")

          calendarIcon.dispatchEvent("click")

          val day19Opt = xformsWindow.page.querySelectorAllT(".datepicker .day").find(el => el.textContent().trim == "19" && !el.hasClass("old"))
          val day20Opt = xformsWindow.page.querySelectorAllT(".datepicker .day").find(el => el.textContent().trim == "20" && !el.hasClass("old"))

          assert(day19Opt.isDefined && !day19Opt.get.hasClass("disabled"), "Day 19 must not be disabled")
          assert(day20Opt.isDefined && day20Opt.get.hasClass("disabled"), "Day 20 must be disabled")
      }
    }
  }
}
