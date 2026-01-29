package org.orbeon.oxf.fr

import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.oxf.xforms.control.{XFormsControl, XFormsValueControl}
import org.orbeon.oxf.xforms.event.EventCollector
import org.orbeon.oxf.xforms.itemset.ItemsetSupport
import org.scalatest.funspec.AnyFunSpecLike


class SimpleActionsTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with FormRunnerSupport {

  describe("Form Runner Simple Actions (legacy format)") {

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

    describe("#7053: calling service asynchronously") {

      val (processorService, docOpt, _) =
        runFormRunner("issue", "7053", "new", initialize = true)

      // Before fix for #6544, this throws as the document initialization fails
      val doc = docOpt.get

      it("must call the async service, wait, and find the correct itemset") {
        withTestExternalContext { implicit ec =>
          withFormRunnerDocument(processorService, doc) {
            // The triggered action calls an async service, but the external event dispatch will wait for all async
            // submissions to terminate with `Duration.Inf` by default, so we can just test for the itemset right after
            // the event dispatch.
            activateControlWithEvent(resolveObject[XFormsControl]("button-control").get.effectiveId)

            val jsonOpt =
              resolveObject[XFormsControl]("regular-dropdown-control")
                .flatMap(getItemsetSearchNested)
                .map(ItemsetSupport.asJSON(_, controlValue = None, encode = false, excludeWhitespaceTextNodes = false, locationData = null))

            assert(jsonOpt.exists(_.contains("Switzerland")))
          }
        }
      }
    }

    describe("#7464: Action targeting repeated iteration on same line doesn't work") {

      val (processorService, docOpt, _) =
        runFormRunner("issue", "7464", "new", initialize = true)

      val doc = docOpt.get

      it("must resolve the correct repeated iterations and set the control values") {
        withTestExternalContext { implicit ec =>
          withFormRunnerDocument(processorService, doc) {

            def assertOne(value: String, index: Int): Unit = {
              setControlValueWithEventSearchNested(resolveObject[XFormsControl]("source-control-control", indexes = List(index)).get.effectiveId, value)
              assert(resolveObject[XFormsValueControl]("destination-control-control", indexes = List(index)).map(_.getValue(EventCollector.Throw)).contains(s"$value result!"))
            }

            assertOne("one", 1)
            assertOne("two", 2)
          }
        }
      }
    }
  }
}

