package org.orbeon.oxf.fr

import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.oxf.xforms.control.XFormsValueControl
import org.orbeon.oxf.xforms.event.EventCollector
import org.scalatest.funspec.AnyFunSpecLike


class LegacyActionsTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with FormRunnerSupport {

  describe("Form Runner actions in legacy format") {

    describe("#6544: Legacy action causes error with `$fr-mode` variable") {

      val (processorService, docOpt, _) =
        runFormRunner("issue", "6544", "new", initialize = true)

      // Before fix for #6544, this throws as the document initialization fails
      val doc = docOpt.get

      it("must initialize, call the service, and set the correct control value") {
        withTestExternalContext { _ =>
          withFormRunnerDocument(processorService, doc) {
            assert(resolveObject[XFormsValueControl]("title-control").map(_.getValue(EventCollector.Throw)).contains("Reminder"))
          }
        }
      }
    }
  }
}

