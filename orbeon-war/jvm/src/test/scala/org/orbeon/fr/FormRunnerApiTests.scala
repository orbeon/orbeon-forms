package org.orbeon.fr

import org.orbeon.fr.PlaywrightDomSupport.*
import org.scalatest.funspec.FixtureAnyFunSpecLike


trait FormRunnerApiTests {
  this: FixtureAnyFunSpecLike & ClientTestSupport =>

  describe("Form Runner API client tests") {
    it("must find form controls by name, set values, get values, and activate") { _ =>
      withFormReady(app = "tests", form = "control-names") { case FormRunnerWindow(_, formRunnerApi) =>

        val form = formRunnerApi.getForm
        assert(formRunnerApi.isSameFormReference)

        assert(form.findControlsByName("first-name").head.hasClass("xforms-control"))

        assert(form.findControlsByName("last-name").head.hasClass("xforms-control"))
        assert(form.findControlsByName("i-dont-exist").isEmpty)

        assert(form.findControlsByName("comments").length == 1)
        assert(form.findControlsByName("comments").forall(_.hasClass("xforms-textarea")))

        form.setControlValue("comments", "Hello world!").get

        // Use same value to make sure that we get a resolving `Promise` in this case as well
        form.setControlValue("comments", "Hello world!").get
        form.setControlValue("comments", "Hello world!").get

        assert(form.getControlValue("comments").contains("Hello world!"))

        form.activateControl("add-comment").get

        // Only one "Add Comment" button
        assert(form.activateControl("add-comment", 1).isEmpty)

        assert(form.findControlsByName("comments").length == 2)
        assert(form.findControlsByName("comments").forall(_.hasClass("xforms-textarea")))

        form.setControlValue("comments", "Hello world, again!", 1).get
        assert(form.getControlValue("comments", 1).contains("Hello world, again!"))

        assert(form.findControlsByName("comments").map(_.id).sameElements(List("message-section≡grid-3-grid≡comments-control⊙1", "message-section≡grid-3-grid≡comments-control⊙2")))

        // Single selection control with `Int`
        assert(form.getControlValue("topic").contains("0"))
        form.setControlValue("topic", 3).get
        assert(form.getControlValue("topic").contains("3"))
      }
    }

    it("must pass the strict Wizard focus rules") { _ =>
      withFormReady(app = "tests", form = "wizard") { case FormRunnerWindow(_, formRunnerApi) =>

        val form = formRunnerApi.getForm

        // Initial test of control visibility
        assert(form.findControlsByName("control-1").nonEmpty)
        assert(form.findControlsByName("control-2").isEmpty)

        // This must work because the field is visible
        formRunnerApi.wizard.focus("control-1")

        // This must fail because the field is not visible and not reachable
        formRunnerApi.wizard.focus("control-2")

        // `control-2` is not visible
        assert(form.findControlsByName("control-2").isEmpty)

        // After filling `control-1`, `control-2` will be reachable
        form.setControlValue("control-1", "value-1")
        formRunnerApi.wizard.focus("control-2")

        // `control-2` is visible
        assert(form.findControlsByName("control-2").nonEmpty)
      }
    }
  }
}
