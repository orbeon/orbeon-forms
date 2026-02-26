package org.orbeon.oxf.fr

import cats.syntax.option.*
import org.orbeon.oxf.fr.process.FormRunnerActionApi
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport, XFormsSupport}
import org.orbeon.oxf.util.{IndentedLogger, LoggerFactory}
import org.orbeon.oxf.xforms.control.XFormsValueControl
import org.orbeon.oxf.xforms.event.EventCollector
import org.orbeon.oxf.xforms.function.XFormsFunction
import org.scalatest.funspec.AnyFunSpecLike


class SectionTemplatesTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with FormRunnerSupport
     with XFormsSupport {

  private val Logger = LoggerFactory.createLogger(classOf[SectionTemplatesTest])

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
              ).get.getValue(EventCollector.Throw) == workflowStage
            )

            for (index <- 1 to 2)
              assert(
                resolveObjectInsideComponent[XFormsValueControl](
                  componentStaticOrAbsoluteId = "my-repeated-workflow-stage-section-content-control",
                  targetStaticOrAbsoluteId    = "current-workflow-stage-control",
                  targetIndexes               = List(index)
                ).get.getValue(EventCollector.Throw) == workflowStage
              )
          }
        }
      }
    }
  }

  describe("#7427: Section templates: value dependencies") {

    it("must update control values in all top-level and section template dependencies") {

      val (processorService, Some(xfcd), _) =
        runFormRunner("issue", "7427", "new", initialize = true)

      withTestExternalContext { _ =>
        withFormRunnerDocument(processorService, xfcd) {

          implicit val fnCtx: XFormsFunction.Context = XFormsFunction.Context(xfcd)
          implicit val logger: IndentedLogger        = new IndentedLogger(Logger)

          locally {

            val Sections =
              List(
                None,
                s"issue-7427-s2".some,
                s"issue-7427-s3".some,
                None,
              )

            for ((sectionNameOpt, sectionIndex0) <- Sections.zipWithIndex) {

              val sectionIndex1 = sectionIndex0 + 1
              val controlName   = s"s$sectionIndex1-in"
              val valueToSet    = s"v$sectionIndex1"

              document.withOutermostActionHandler {
                FormRunnerActionApi.controlSetvalue(
                  controlName    = controlName,
                  valueExpr      = s"'$valueToSet'",
                  sectionNameOpt = sectionNameOpt,
                  atOpt          = None,
                )
              }

              val TopLevelControls =
                List(
                  s"s1-s$sectionIndex1-out-control",
                  s"s4-s$sectionIndex1-out-control",
                )

              for (topLevelControlStaticId <- TopLevelControls)
                assert(
                  resolveObject[XFormsValueControl]( // TODO: would be good to have Form Runner API for that
                    staticOrAbsoluteId = topLevelControlStaticId
                  ).get.getValue(EventCollector.Throw) == valueToSet
                )

              val NestedControls =
                List(
                  "issue-7427-s2-content-control" -> s"s2-s$sectionIndex1-out-control",
                  "issue-7427-s3-content-control" -> s"s3-s$sectionIndex1-out-control"
                )

              for ((sectionTemplateComponentStaticId, controlStaticId) <- NestedControls)
                assert(
                  resolveObjectInsideComponent[XFormsValueControl]( // TODO: would be good to have Form Runner API for that
                    componentStaticOrAbsoluteId = sectionTemplateComponentStaticId,
                    targetStaticOrAbsoluteId    = controlStaticId,
                    targetIndexes               = Nil
                  ).get.getValue(EventCollector.Throw) == valueToSet
                )
            }
          }
        }
      }
    }
  }
}
