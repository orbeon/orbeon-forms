package org.orbeon.fr

import org.orbeon.web.DomSupport.*
import org.scalajs.dom.html
import org.scalatest.funspec.FixtureAsyncFunSpecLike

import scala.async.Async.*
import scala.scalajs.js


trait NumberControlTests {
  this: FixtureAsyncFunSpecLike with ClientTestSupport =>

  describe("Number control") {
    it("must round value to 2 decimal places on blur") { _ =>
      withFormReady(app = "tests", form = "number") { case FormRunnerWindow(xformsWindow, formRunnerApi) =>
        async {

          val form          = formRunnerApi.getForm(js.undefined)
          val controls      = form.findControlsByName("my-number")
          val numberControl = controls.head
          val visibleInput  = numberControl.querySelectorT(".xbl-fr-number-visible-input").asInstanceOf[html.Input]

          visibleInput.focus()
          visibleInput.value = "1.234"
          visibleInput.blur()
          await(xformsWindow.ajaxServer.allEventsProcessedP().toFuture)
          assert(visibleInput.value == "1.23")

          visibleInput.focus()
          visibleInput.value = "1.2345"
          visibleInput.blur()
          await(xformsWindow.ajaxServer.allEventsProcessedP().toFuture)
          assert(visibleInput.value == "1.23")
        }
      }
    }
  }
}
