package org.orbeon.oxf.fr

import org.orbeon.io.CharsetNames
import org.orbeon.oxf.fr.FormRunnerBaseOps.MessageAppearance
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.oxf.xforms.control.XFormsControl
import org.orbeon.scaxon.SimplePath.*
import org.scalatest.funspec.AnyFunSpecLike


class FormRunnerMessagesTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with FormRunnerSupport
     with AnyFunSpecLike {

  describe("#6227: Messages appearance configurable via property") {

    describe("HTML output markup") {

      it("generates Bootstrap 5 toast markup when appearance is toast") {
        val (_, content, _) = runFormRunnerReturnContent("issue", "6227-toast", "new")
        val html = new String(content.body, CharsetNames.Utf8)

        assert(html.contains("toast-container"))
        assert(html.contains("toast"))
        assert(html.contains("btn-close"))
        assert(html.contains("fr-messages-toast-container"))
        assert(html.contains("fr-message-success"))
        assert(html.contains("fr-message-error"))
      }

      it("generates inline alert markup when appearance is `inline`") {
        val (_, content, _) = runFormRunnerReturnContent("issue", "6227-inline", "new")
        val html = new String(content.body, CharsetNames.Utf8)

        assert(!html.contains("fr-messages-toast-container"))
        assert(!html.contains("data-orbeon-bs-dismiss=\"toast\""))
        assert(html.contains("alert alert-success"))
        assert(html.contains("alert alert-error"))
      }
    }

    describe("Runtime behavior") {

      it("handles messages with `toast` appearance: increments seq, ignores `DOMFocusIn`, clears on `fr-clear-message`") {
        val (processorService, docOpt, _) = runFormRunner("issue", "6227-toast", "new")
        val doc = docOpt.get

        withTestExternalContext { _ =>
          withFormRunnerDocument(processorService, doc) {
            def getMessageValue: String =
              (FormRunner.persistenceInstance.rootElement / "message").stringValue

            def getMessageSeq: String =
              (FormRunner.persistenceInstance.rootElement / "message").headOption.flatMap(_.attValueOpt("seq")).getOrElse("")

            // Initial state: empty message, seq 0
            assert(getMessageValue == "")
            assert(getMessageSeq == "0")

            // Show success message
            FormRunner.successMessage("Form saved successfully!")
            assert(getMessageValue == "Form saved successfully!")
            assert(getMessageSeq == "1")

            // Dispatch DOMFocusIn to field: message must NOT be cleared for toasts
            val fieldControl = resolveObject[XFormsControl]("my-field-control").get
            dispatch(name = "DOMFocusIn", effectiveId = fieldControl.effectiveId)
            assert(getMessageValue == "Form saved successfully!")

            // Stale seq dismissal must be ignored
            XFormsAPI.dispatch(
              name       = "fr-clear-message",
              targetId   = "fr-persistence-model",
              properties = Map("seq" -> Some("0"))
            )
            assert(getMessageValue == "Form saved successfully!")

            // Matching seq dismissal clears message
            XFormsAPI.dispatch(
              name       = "fr-clear-message",
              targetId   = "fr-persistence-model",
              properties = Map("seq" -> Some("1"))
            )
            assert(getMessageValue == "")

            // Ephemeral error message increments seq
            FormRunner.errorMessage("An ephemeral error occurred", MessageAppearance.Ephemeral)
            assert(getMessageValue == "An ephemeral error occurred")
            assert(getMessageSeq == "2")

            // Immediate dismissal (e.g. from close button without seq) clears message
            XFormsAPI.dispatch(
              name     = "fr-clear-message",
              targetId = "fr-persistence-model"
            )
            assert(getMessageValue == "")
          }
        }
      }

      it("handles messages with `inline` appearance: clears message on `DOMFocusIn`") {
        val (processorService, docOpt, _) = runFormRunner("issue", "6227-inline", "new")
        val doc = docOpt.get

        withTestExternalContext { _ =>
          withFormRunnerDocument(processorService, doc) {
            def getMessageValue: String =
              (FormRunner.persistenceInstance.rootElement / "message").stringValue

            assert(getMessageValue == "")

            FormRunner.successMessage("Saved inline!")
            assert(getMessageValue == "Saved inline!")

            // Dispatch DOMFocusIn: message must be cleared for inline appearance
            val fieldControl = resolveObject[XFormsControl]("my-field-control").get
            dispatch(name = "DOMFocusIn", effectiveId = fieldControl.effectiveId)
            assert(getMessageValue == "")
          }
        }
      }
    }
  }
}
