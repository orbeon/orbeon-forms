package org.orbeon.fr

import org.orbeon.oxf.util.CoreUtils.*
import org.orbeon.web.DomSupport.*
import org.scalatest.funspec.FixtureAsyncFunSpecLike

import scala.async.Async.*
import scala.scalajs.js


trait FormRunnerApiTests {
  this: FixtureAsyncFunSpecLike with ClientTestSupport =>

  describe("Form Runner API client tests") {
    it("must find form controls by name, set values, get values, and activate") { _ =>
      withFormReady(app = "tests", form = "control-names") { case FormRunnerWindow(_, formRunnerApi) =>
        async {

          val form = formRunnerApi.getForm(js.undefined)

          assert(form.findControlsByName("first-name").head.classList.contains("xforms-control"))

          assert(form.findControlsByName("last-name").head.classList.contains("xforms-control"))
          assert(form.findControlsByName("i-dont-exist").isEmpty)

          assert(form.findControlsByName("comments").length == 1)
          assert(form.findControlsByName("comments").forall(_.classList.contains("xforms-textarea")))

          await(form.setControlValue("comments", "Hello world!").map(_.toFuture).get)

          // Use same value to make sure that we get a resolving `Promise` in this case as well
          await(form.setControlValue("comments", "Hello world!").map(_.toFuture).get)
          await(form.setControlValue("comments", "Hello world!").map(_.toFuture).get)

          assert(form.getControlValue("comments").contains("Hello world!"))

          await(form.activateControl("add-comment").map(_.toFuture).get)

          // Only one "Add Comment" button
          assert(form.activateControl("add-comment", 1).isEmpty)

          assert(form.findControlsByName("comments").length == 2)
          assert(form.findControlsByName("comments").forall(_.classList.contains("xforms-textarea")))

          await(form.setControlValue("comments", "Hello world, again!", 1).map(_.toFuture).get)
          assert(form.getControlValue("comments", 1).contains("Hello world, again!"))

          assert(form.findControlsByName("comments").map(_.id).sameElements(List("message-section≡grid-3-grid≡comments-control⊙1", "message-section≡grid-3-grid≡comments-control⊙2")))

          // Single selection control with `Int`
          assert(form.getControlValue("topic").contains("0"))
          await(form.setControlValue("topic", 3).map(_.toFuture).get)
          assert(form.getControlValue("topic").contains("3"))
        }
      }
    }

    it("must pass the strict Wizard focus rules") { _ =>
      withFormReady(app = "tests", form = "wizard") { case FormRunnerWindow(_, formRunnerApi) =>
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
