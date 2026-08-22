package org.orbeon.fr

import org.orbeon.web.DomSupport.*
import org.scalajs.dom
import org.scalajs.dom.html
import org.scalatest.funspec.FixtureAsyncFunSpecLike

import scala.async.Async.*
import scala.scalajs.js


trait DateTimeTests {
  this: FixtureAsyncFunSpecLike & ClientTestSupport =>

  describe("DateTime control") {

    it("must not show validation error when tabbing from date input to calendar icon") { _ =>
      withFormReady(app = "orbeon-features", form = "controls", queryStringOpt = Some("fr-wizard-page=date-time-controls")) {
        case FormRunnerWindow(xformsWindow, formRunnerApi) =>
          async {
            val form            = formRunnerApi.getForm(js.undefined)
            val datetimeControl = form.findControlsByName("datetime").head
            val dateUsControl   = form.findControlsByName("date-us").head
            val dateSubControl  = datetimeControl.querySelectorT(".xbl-fr-date")
            val timeSubControl  = datetimeControl.querySelectorT(".xbl-fr-time")
            val dateInput       = dateSubControl.querySelectorT("input[type='text']").asInstanceOf[html.Input]
            val timeInput       = timeSubControl.querySelectorT("input[type='text']").asInstanceOf[html.Input]
            val calendarIcon    = dateSubControl.querySelectorT(".add-on")
            val dateUsInput     = dateUsControl.querySelectorT("input[type='text']").asInstanceOf[html.Input]

            dateInput.focus()
            dateInput.value = ""
            dateInput.dispatchEvent(new dom.Event("change", new dom.EventInit { bubbles = true }))
            calendarIcon.focus()
            await(xformsWindow.ajaxServer.allEventsProcessedP().toFuture)
            assert(
              datetimeControl.querySelectorOpt(".xforms-alert.xforms-active").isEmpty,
              "Clear the date, focus on the calendar icon; no alert shows since we're still within the datetime control"
            )

            dateUsInput.focus()
            await(xformsWindow.ajaxServer.allEventsProcessedP().toFuture)
            assert(
              datetimeControl.querySelectorOpt(".xforms-alert.xforms-active").isDefined,
              "Exit the datetime control; the alert shows"
            )

            dateInput.focus()
            dateInput.value = "2026-05-22"
            dateInput.dispatchEvent(new dom.Event("change", new dom.EventInit { bubbles = true }))
            timeInput.focus()
            await(xformsWindow.ajaxServer.allEventsProcessedP().toFuture)
            assert(
              datetimeControl.querySelectorOpt(".xforms-alert.xforms-active").isDefined,
              "Enter a valid date; alert still shows as we have exited the datetime"
            )

            dateUsInput.focus()
            await(xformsWindow.ajaxServer.allEventsProcessedP().toFuture)
            assert(
              datetimeControl.querySelectorOpt(".xforms-alert.xforms-active").isEmpty,
              "Exit the datetime control; the alert is hidden"
            )

            dateInput.focus()
            dateInput.value = ""
            dateInput.dispatchEvent(new dom.Event("change", new dom.EventInit { bubbles = true }))
            timeInput.focus()
            await(xformsWindow.ajaxServer.allEventsProcessedP().toFuture)
            assert(
              datetimeControl.querySelectorOpt(".xforms-alert.xforms-active").isEmpty,
              "Clear the date, focus on the time (same as earlier, but this time the datetime is visited); no alert"
            )
          }
      }
    }

    it("must disable the correct excluded dates in the datepicker in positive timezones") { _ =>
      withTimezone("Europe/Helsinki") {
        withFormReady(app = "orbeon-features", form = "controls", queryStringOpt = Some("fr-wizard-page=date-time-controls")) {
          case FormRunnerWindow(xformsWindow, formRunnerApi) =>
            async {
              val form          = formRunnerApi.getForm(js.undefined)
              val dateEuControl = form.findControlsByName("date-eu").head
              val calendarIcon  = dateEuControl.querySelectorT(".add-on")

              calendarIcon.dispatchEvent(new dom.MouseEvent("click", new dom.MouseEventInit {
                bubbles    = true
                cancelable = true
              }))

              val day19Opt = xformsWindow.window.document.documentElement.querySelectorAllT(".datepicker .day").find(el => el.textContent.trim == "19" && !el.hasClass("old"))
              val day20Opt = xformsWindow.window.document.documentElement.querySelectorAllT(".datepicker .day").find(el => el.textContent.trim == "20" && !el.hasClass("old"))

              assert(day19Opt.isDefined && !day19Opt.get.hasClass("disabled"), "Day 19 must not be disabled")
              assert(day20Opt.isDefined && day20Opt.get.hasClass("disabled"), "Day 20 must be disabled")
            }
        }
      }
    }
  }

  private def withTimezone[T](tz: String)(f: => scala.concurrent.Future[T]): scala.concurrent.Future[T] = {
    val originalTz = js.Dynamic.global.process.env.TZ
    js.Dynamic.global.process.env.TZ = tz
    f.andThen { case _ =>
      if (js.isUndefined(originalTz))
        js.Dynamic.global.process.env.TZ = js.undefined
      else
        js.Dynamic.global.process.env.TZ = originalTz
    }
  }
}
