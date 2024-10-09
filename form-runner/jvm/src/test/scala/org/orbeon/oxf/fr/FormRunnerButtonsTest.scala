package org.orbeon.oxf.fr

import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.xforms.control.Controls.ControlsIterator
import org.orbeon.oxf.xforms.control.XFormsSingleNodeControl
import org.orbeon.xforms.XFormsNames
import org.scalatest.funspec.AnyFunSpecLike


class FormRunnerButtonsTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with FormRunnerSupport {

  describe("Form Runner buttons") {

    describe("#6542: `oxf.fr.detail.button.$button.visible` doesn't support `fr:control-string-value()`") {

      val (processorService, docOpt, _) =
        runFormRunner("issue", "6542", "new", initialize = true)

      // Before fix for #6544, this throws as the document initialization fails
      val doc = docOpt.get

      val ButtonComponentNames = Set("ladda-button", "trigger")

      def findButtonControl(name: String): Option[XFormsSingleNodeControl] =
        ControlsIterator(doc.controls.getCurrentControlTree)
          .collectFirst {
            case control: XFormsSingleNodeControl
              if ButtonComponentNames(control.staticControl.localName) &&
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
  }
}

