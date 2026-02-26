package org.orbeon.oxf.fr

import cats.syntax.option.*
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport, XFormsSupport}
import org.orbeon.oxf.xforms.control.{XFormsComponentControl, XFormsValueControl}
import org.orbeon.oxf.xforms.event.EventCollector
import org.scalatest.funspec.AnyFunSpecLike


class SectionTemplatesTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with FormRunnerSupport
     with XFormsSupport {

  describe("#7492: form with repeated section templates with dependent values") {

    it("must update the top-level and section template controls when the workflow stage changes") {

      val (processorService, Some(xfcd), _) =
        runFormRunner("issue", "7492", "new", initialize = true)

      withTestExternalContext { _ =>
        withFormRunnerDocument(processorService, xfcd) {
          for (workflowStage <- List("approved", "rejected")) {
            FormRunner.documentWorkflowStage = workflowStage.some
            xfcd.synchronizeAndRefresh()

            assert(
              resolveObject[XFormsValueControl](
                staticOrAbsoluteId = "top-level-current-workflow-stage-control"
              ).get.getValue(EventCollector.Throw) == workflowStage)

            for (index <- 1 to 2)
              assert(
                resolveObjectInsideComponent[XFormsValueControl](
                  componentStaticOrAbsoluteId = "my-repeated-workflow-stage-section-content-control",
                  targetStaticOrAbsoluteId    = "current-workflow-stage-control",
                  targetIndexes      = List(index)
                ).get.getValue(EventCollector.Throw) == workflowStage
              )
          }
        }
      }
    }
  }
}
