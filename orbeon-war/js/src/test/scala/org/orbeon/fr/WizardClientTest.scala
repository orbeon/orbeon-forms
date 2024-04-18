package org.orbeon.fr

import org.scalatest.funspec.AsyncFunSpec

import scala.async.Async._
import scala.scalajs.js


class WizardClientTest extends AsyncFunSpec with ClientTestSupport {

  describe("Wizard client tests") {
    it("must not allow focus on unreachable wizard page") {
      withFormReady("wizard") { case FormRunnerWindow(_, formRunnerApi) =>
        async {

          val form = formRunnerApi.getForm(js.undefined)

          // Initial test of control visibility
          assert(form.findControlsByName("control-1").nonEmpty)
          assert(form.findControlsByName("control-2").isEmpty)

          // This must work because the field is visible
          await(formRunnerApi.wizard.focus("control-1").toFuture)

          // NOTE: `document.activeElement` seems to remain at the `body` element, so we cannot test on that. This might
          // be a JSDOM-specific issue.

          // This must fail because the field is not visible and not reachable
          await(formRunnerApi.wizard.focus("control-2").toFuture)

          // `control-2` is not visible
          assert(form.findControlsByName("control-2").isEmpty)

          // After filling `control-1`, `control-2` will be reachable
          form.setControlValue("control-1", "value-1")
          await(formRunnerApi.wizard.focus("control-2").toFuture)

          // `control-2` is visible
          assert(form.findControlsByName("control-2").nonEmpty)
        }
      }
    }
  }
}
