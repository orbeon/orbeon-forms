package org.orbeon.oxf.fr

import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.control.XFormsValueControl
import org.orbeon.oxf.xforms.event.EventCollector
import org.scalatest.funspec.AnyFunSpecLike


class StandardCodesValidationTest
  extends DocumentTestBase
    with ResourceManagerSupport
    with AnyFunSpecLike
    with FormRunnerSupport {

  private val ControlValueIsValid = List(
    ("iban-control", "BE68539007547034"    , true ),
    ("iban-control", "BE68539007547035"    , false),
    ("iban-control", "BE010000000081"      , false), // ISO 7064 MOD 97-10 excludes check-digit pairs 00, 01 and 99
    ("isin-control", "US35953D1046"        , true ),
    ("isin-control", "US0378331005"        , true ),
    ("isin-control", "US0378331006"        , false),
    ("lei-control" , "F50EOCWSQFAUVO9Q8Z97", true ),
    ("lei-control" , "F50EOCWSQFAUVO9Q8Z98", false),
    ("lei-control" , "00000000000000000001", false) // ISO 17442 excludes check-digit pairs 00, 01 and 99
  )

  private val (processorService, docOpt, _) =
    runFormRunner("tests", "standard-codes-validation", "new")
  private val doc = docOpt.get

  ControlValueIsValid.foreach { case (controlId, value, isValid) =>
    it(s"must ${if (isValid) "accept" else "reject"} value $value for control $controlId") {
      withTestExternalContext { _ =>
        withFormRunnerDocument(processorService, doc) {
          val control = resolveObject[XFormsValueControl](controlId).get
          setControlValueWithEventSearchNested(control.effectiveId, value)
          if (isValid) {
            assert(control.getAlert(EventCollector.Throw).isEmpty  , s"Valid value $value should be accepted for control $controlId")
          } else {
            assert(control.getAlert(EventCollector.Throw).isDefined, s"Invalid value $value should be rejected for control $controlId")
          }
        }
      }
    }
  }
}
