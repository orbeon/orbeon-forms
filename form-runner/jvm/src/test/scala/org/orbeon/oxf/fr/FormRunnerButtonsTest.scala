package org.orbeon.oxf.fr

import org.orbeon.dom.QName
import org.orbeon.oxf.fr.XMLNames.{FR, FRPrefix}
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.xforms.control.Controls.ControlsIterator
import org.orbeon.oxf.xforms.control.XFormsSingleNodeControl
import org.orbeon.xforms.XFormsNames
import org.scalatest.funspec.AnyFunSpecLike


class FormRunnerButtonsTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with FormRunnerSupport
     with AnyFunSpecLike {

  describe("Form Runner buttons") {

    describe("#6542: `oxf.fr.detail.button.$button.visible` doesn't support `fr:control-string-value()`") {

      val (processorService, docOpt, _) =
        runFormRunner("issue", "6542", "new")

      // Before fix for #6544, this throws as the document initialization fails
      val doc = docOpt.get

      val ButtonComponentQNames = Set(
        QName("ladda-button", FRPrefix, FR),
        QName("trigger",      FRPrefix, FR),
      )

      def findButtonControl(name: String): Option[XFormsSingleNodeControl] =
        ControlsIterator(doc.controls.getCurrentControlTree)
          .collectFirst {
            case control: XFormsSingleNodeControl
              if ButtonComponentQNames(control.staticControl.qName) &&
                control.extensionAttributeValue(XFormsNames.CLASS_QNAME).exists(_.splitTo[List]().contains(s"fr-$name-button")) =>
              control
          }

      it("show/hide, disable/enable the `save-final` button depending on a control value") {
        withTestExternalContext { _ =>
          withFormRunnerDocument(processorService, doc) {
            assert(findButtonControl("save-final").isEmpty)
            setControlValueWithEventSearchNested("section-1-section≡grid-1-grid≡my-field-control", "show-enabled")
            assert(findButtonControl("save-final").nonEmpty)
            assert(findButtonControl("save-final").exists(! _.isReadonly))
            setControlValueWithEventSearchNested("section-1-section≡grid-1-grid≡my-field-control", "show-disabled")
            assert(findButtonControl("save-final").nonEmpty)
            assert(findButtonControl("save-final").exists(_.isReadonly))
            setControlValueWithEventSearchNested("section-1-section≡grid-1-grid≡my-field-control", "show-enabled")
            assert(findButtonControl("save-final").nonEmpty)
            assert(findButtonControl("save-final").exists(! _.isReadonly))
            setControlValueWithEventSearchNested("section-1-section≡grid-1-grid≡my-field-control", "")
            assert(findButtonControl("save-final").isEmpty)
          }
        }
      }
    }

    describe("#2238: form-level Detail Page buttons in `fr-form-metadata`") {

      val ButtonComponentQNames = Set(
        QName("ladda-button", FRPrefix, FR),
        QName("trigger",      FRPrefix, FR),
      )

      def findButtonControl(doc: org.orbeon.oxf.xforms.XFormsContainingDocument, name: String): Option[XFormsSingleNodeControl] =
        ControlsIterator(doc.controls.getCurrentControlTree)
          .collectFirst {
            case control: XFormsSingleNodeControl
              if ButtonComponentQNames(control.staticControl.qName) &&
                control.extensionAttributeValue(XFormsNames.CLASS_QNAME).exists(_.splitTo[List]().contains(s"fr-$name-button")) =>
              control
          }

      it("must honor overridden edit/inner bars, button labels, XPath visible/enabled formulas, loading indicators, and processes from metadata") {
        val (processorService, docOpt, _) = runFormRunner("issue", "2238", "new")
        val doc = docOpt.get

        withTestExternalContext { implicit ec =>
          withFormRunnerDocument(processorService, doc) {
            // 1. Overridden edit bar (`clear save-final approve`) + inner bar (`validate`), omitting default `summary` / `review`
            assert(findButtonControl(doc, "clear").isDefined)
            assert(findButtonControl(doc, "save-final").isDefined)
            assert(findButtonControl(doc, "validate").isDefined)
            assert(findButtonControl(doc, "review").isEmpty)

            // 2. `save-final` loading-indicator="" overrides property default `inline`, so it renders as `fr:trigger` instead of `fr:ladda-button`
            assert(findButtonControl(doc, "save-final").get.staticControl.qName.localName == "trigger")

            // 3. `approve` and `escalate` (whose `$amount gt 1000` references an initially-blank `xf:decimal` control)
            //    are initially hidden without breaking the rest of the button bar
            assert(findButtonControl(doc, "approve").isEmpty)
            assert(findButtonControl(doc, "escalate").isEmpty)

            // 4. Typed `xf:decimal` control (`amount`) transitions `escalate` from hidden -> disabled -> enabled -> hidden on invalid input
            val amountCtrl = resolveObject[org.orbeon.oxf.xforms.control.XFormsValueControl]("amount-control").get
            setControlValueWithEventSearchNested(amountCtrl.effectiveId, "2500")
            assert(findButtonControl(doc, "escalate").exists(_.isReadonly))
            setControlValueWithEventSearchNested(amountCtrl.effectiveId, "6000")
            assert(findButtonControl(doc, "escalate").exists(! _.isReadonly))
            setControlValueWithEventSearchNested(amountCtrl.effectiveId, "not-a-decimal")
            assert(findButtonControl(doc, "escalate").isEmpty)
            assert(findButtonControl(doc, "clear").isDefined)
            assert(findButtonControl(doc, "save-final").isDefined)

            // 5. Setting `my-field` to `locked` makes `approve` visible (`$my-field = ('ready', 'locked')`) but disabled (`$my-field = 'ready'`)
            val myFieldCtrl = resolveObject[org.orbeon.oxf.xforms.control.XFormsValueControl]("my-field-control").get
            setControlValueWithEventSearchNested(myFieldCtrl.effectiveId, "locked")
            val lockedApprove = findButtonControl(doc, "approve")
            assert(lockedApprove.isDefined)
            assert(lockedApprove.get.isReadonly)
            assert(lockedApprove.get.getLabel(org.orbeon.oxf.xforms.event.EventCollector.Throw).exists(_.contains("Approve Claim")))
            assert(lockedApprove.get.extensionAttributeValue(XFormsNames.CLASS_QNAME).exists(_.contains("xforms-trigger-appearance-modal")))

            // 5. Setting `my-field` to `ready` enables `approve`, and activating it runs the metadata process
            setControlValueWithEventSearchNested(myFieldCtrl.effectiveId, "ready")
            val readyApprove = findButtonControl(doc, "approve")
            assert(readyApprove.exists(! _.isReadonly))

            val innerTrigger =
              ControlsIterator(readyApprove.get, includeSelf = false)
                .collectFirst { case c: org.orbeon.oxf.xforms.control.XFormsControl if c.staticControl.localName == "trigger" => c }
                .getOrElse(readyApprove.get)
            activateControlWithEvent(innerTrigger.effectiveId)

            val resultCtrl = resolveObject[org.orbeon.oxf.xforms.control.XFormsValueControl]("action-result-control").get
            assert(resultCtrl.getValue(org.orbeon.oxf.xforms.event.EventCollector.Throw) == "approved")
          }
        }
      }

      it("must honor overridden view bar from metadata") {
        val (processorService, docOpt, _) = runFormRunner("issue", "2238", "view")
        val doc = docOpt.get

        withTestExternalContext { _ =>
          withFormRunnerDocument(processorService, doc) {
            // `view` bar is overridden to `edit` only (omitting `pdf`)
            assert(findButtonControl(doc, "pdf").isEmpty)
          }
        }
      }
    }
  }
}

