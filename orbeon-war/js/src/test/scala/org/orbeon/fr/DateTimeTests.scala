package org.orbeon.fr

import org.orbeon.web.DomSupport.*
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
            val dateSubControl  = datetimeControl.querySelectorT(".xbl-fr-date")
            val dateInput       = dateSubControl.querySelectorT("input[type='text']").asInstanceOf[html.Input]
            val calendarIcon    = dateSubControl.querySelectorT(".add-on")

            // Clear the date input, then move focus to the calendar icon (simulating Tab).
            // We don't call dateInput.blur() explicitly; focusing another element automatically
            // blurs the previous one with the correct relatedTarget on the focusout event.
            dateInput.focus()
            dateInput.value = ""
            calendarIcon.focus()
            await(xformsWindow.ajaxServer.allEventsProcessedP().toFuture)

            // No validation alert should be visible since focus is still within the datetime control
            val alert = datetimeControl.querySelectorOpt(".xforms-alert.xforms-active")
            assert(alert.isEmpty, "Validation error should not show when focus is still within the datetime control")
          }
      }
    }
  }
}
