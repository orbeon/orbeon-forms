package org.orbeon.fr

import org.orbeon.fr.PlaywrightDomSupport.*
import org.scalatest.funspec.FixtureAnyFunSpecLike


trait NumberControlTests {
  this: FixtureAnyFunSpecLike & ClientTestSupport =>

  describe("Number control") {
    it("must round value to 2 decimal places on blur") { _ =>
      withFormReady(app = "tests", form = "number") { case FormRunnerWindow(xformsWindow, formRunnerApi) =>

        val form          = formRunnerApi.getForm
        val controls      = form.findControlsByName("my-number")
        val numberControl = controls.head
        val visibleInput  = numberControl.querySelectorT(".xbl-fr-number-visible-input")

        visibleInput.focus()
        visibleInput.value = "1.234"
        visibleInput.blur()
        xformsWindow.ajaxServer.waitForAllEventsProcessed()
        assert(visibleInput.value == "1.23")

        visibleInput.focus()
        visibleInput.value = "1.2345"
        visibleInput.blur()
        xformsWindow.ajaxServer.waitForAllEventsProcessed()
        assert(visibleInput.value == "1.23")
      }
    }
  }
}
