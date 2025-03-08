package org.orbeon.oxf.fr

import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.oxf.xforms.control.XFormsValueControl
import org.orbeon.oxf.xforms.event.EventCollector
import org.scalatest.funspec.AnyFunSpecLike


class ControlsTest
  extends DocumentTestBase
    with ResourceManagerSupport
    with AnyFunSpecLike
    with FormRunnerSupport {

  describe("Form Runner form controls") {

    describe("#5699: Improve radio buttons `xxf:group` support") {
      val (processorService, docOpt, _) =
        runFormRunner("issue", "5699", "new", initialize = true)

      val doc = docOpt.get

      it("must pass deselect items in the same selection group") {
        withTestExternalContext { _ =>
          withFormRunnerDocument(processorService, doc) {

            val LineCount = 4

            for (_ <- 1 to 2) // do it twice to check that the last select1 control is also cleared
              for (index <- 1 to LineCount) {

                val buttonControl = resolveObject[XFormsValueControl](s"button-$index-control").get

                setControlValueWithEventSearchNested(buttonControl.effectiveId, "0")
                assert(buttonControl.getValue(EventCollector.Throw) == (if (index == LineCount) "one" else "true")) // last select1 has multiple items

                (1 to LineCount)
                  .filter(_ != index)
                  .map(i => resolveObject[XFormsValueControl](s"control-$i-control").get)
                  .foreach { control =>
                    assert(control.getValue(EventCollector.Throw) == "")
                  }
              }
          }
        }
      }
    }
  }
}
