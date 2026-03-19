package org.orbeon.oxf.fr

import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.oxf.xforms.control.{XFormsControl, XFormsValueControl}
import org.orbeon.oxf.xforms.event.EventCollector
import org.orbeon.oxf.xforms.itemset.ItemsetSupport
import org.scalatest.Assertion
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

    describe("#6199: HTTP Service Editor to support HTTP headers") {

      val (processorService, docOpt, _) =
        runFormRunner("issue", "6199", "new", initialize = true)

      val doc = docOpt.get

      it("must set a control value as request header, send to echo service, and read back correctly") {
        withTestExternalContext { implicit ec =>
          withFormRunnerDocument(processorService, doc) {
            val testValue = "my-test-header-value"

            // The form defines an HTTP service and actions, so that the test value should be propagated from a source
            // control to a destination control: source-control -> X-Test-Header -> httpbin -> body -> destination-control

            setControlValueWithEventSearchNested(resolveObject[XFormsControl]("source-control").get.effectiveId, testValue)

            assert(resolveObject[XFormsValueControl]("destination-control").map(_.getValue(EventCollector.Throw)).contains(testValue))
          }
        }
      }
    }
  }

  describe("#7550: Service with variable in URL AVT fails") {

    val (processorService, docOpt, _) =
      runFormRunner("issue", "7550", "new", initialize = true)

    val doc = docOpt.get

    it("must conditionally run the action and set the result in the correct iteration") {
      withTestExternalContext { implicit ec =>
        withFormRunnerDocument(processorService, doc) {

          def makeEffectiveServiceUrl(v: String) =
            s"http://localhost:8080/orbeon/fr/service/custom/orbeon/echo?foo=$v"

          def assertOne(index1: Int, inputValue: String, other: String): Assertion = {

            val button1    = resolveObject[XFormsControl]     ("my-button-control",    indexes = List(index1)).get
            val condition1 = resolveObject[XFormsValueControl]("my-condition-control", indexes = List(index1)).get
            val input1     = resolveObject[XFormsValueControl]("my-value-control",     indexes = List(index1)).get

            // This must not run the action
            activateControlWithEvent(button1.effectiveId)
            assert(resolveObject[XFormsValueControl]("out-control", indexes = List(index1)).map(_.getValue(EventCollector.Throw)).contains(""))

            setControlValue(input1.effectiveId, inputValue)
            setControlValue(condition1.effectiveId, "true")

            activateControlWithEvent(button1.effectiveId)
            // TODO: This is incorrect: the value of the control should be the one matching the iteration, not the combination of both.
            //  See: https://github.com/orbeon/orbeon-forms/issues/7551
            assert(resolveObject[XFormsValueControl]("out-control", indexes = List(index1))                   .map(_.getValue(EventCollector.Throw)).contains(makeEffectiveServiceUrl("bar%20baz")))
            assert(resolveObject[XFormsValueControl]("out-control", indexes = List(if (index1 == 1) 2 else 1)).map(_.getValue(EventCollector.Throw)).contains(other))
          }

          assertOne(1, "bar", "")
          assertOne(2, "baz", makeEffectiveServiceUrl("bar%20baz"))
        }
      }
    }
  }
}
