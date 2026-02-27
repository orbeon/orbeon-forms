package org.orbeon.oxf.fr.process

import org.orbeon.oxf.fr.FormRunnerSupport
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport, XFormsSupport, XMLSupport}
import org.orbeon.oxf.xforms.control.XFormsValueControl
import org.orbeon.oxf.xforms.event.EventCollector
import org.scalatest.funspec.AnyFunSpecLike


class FormRunnerProcessActionTest
extends DocumentTestBase
   with ResourceManagerSupport // access to resources is needed because `XPathCache` needs the default cache size
   with AnyFunSpecLike
   with FormRunnerSupport
   with XMLSupport
   with XFormsSupport {

  describe("The `control-setvalue()` process action") {

    it("must get value set by process actions during form initialization") {
      val (processorService, Some(xfcd), _) = runFormRunner(
        app        = "issue",
        form       = "7509",
        mode       = "new",
      )
      withTestExternalContext { _ =>
        withFormRunnerDocument(processorService, xfcd) {

          // Top-level control
          assert(
            resolveObject[XFormsValueControl](
              staticOrAbsoluteId = "contact-information-first-name-control"
            ).get.getValue(EventCollector.Throw) == "Homer"
          )

          // Top-level repeated controls
          val ValuesForRepeatedControls = List(
            ("A", "X"),
            ("B", "X"),
            ("Z", "X"),
          )

          for (((expectedValue1, expectedValue2), index0) <- ValuesForRepeatedControls.zipWithIndex) {
            assert(
              resolveObject[XFormsValueControl](
                staticOrAbsoluteId = "top-level-repeated-control-1-control",
                indexes            = List(index0 + 1)
              ).get.getValue(EventCollector.Throw) == expectedValue1
            )
            assert(
              resolveObject[XFormsValueControl](
                staticOrAbsoluteId = "top-level-repeated-control-2-control",
                indexes            = List(index0 + 1)
              ).get.getValue(EventCollector.Throw) == expectedValue2
            )
          }

          // Section template controls
          val SectionTemplateControlId = "address-us-content-control"

          assert(
            resolveObjectInsideComponent[XFormsValueControl](
              componentStaticOrAbsoluteId = SectionTemplateControlId,
              targetStaticOrAbsoluteId    = "us-address-street-1-control",
              targetIndexes               = Nil
            ).get.getValue(EventCollector.Throw) == "742 Evergreen Terrace"
          )
          assert(
            resolveObjectInsideComponent[XFormsValueControl](
              componentStaticOrAbsoluteId = SectionTemplateControlId,
              targetStaticOrAbsoluteId    = "us-address-city-control",
              targetIndexes               = Nil
            ).get.getValue(EventCollector.Throw) == "Springfield"
          )
          assert(
            resolveObjectInsideComponent[XFormsValueControl](
              componentStaticOrAbsoluteId = SectionTemplateControlId,
              targetStaticOrAbsoluteId    = "us-address-state-control",
              targetIndexes               = Nil
            ).get.getValue(EventCollector.Throw) == "OR"
          )
          assert(
            resolveObjectInsideComponent[XFormsValueControl](
              componentStaticOrAbsoluteId = SectionTemplateControlId,
              targetStaticOrAbsoluteId    = "us-address-zip-control",
              targetIndexes               = Nil
            ).get.getValue(EventCollector.Throw) == "99999"
          )
        }
      }
    }
  }
}
