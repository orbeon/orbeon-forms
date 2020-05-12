/**
  * Copyright (C) 2017 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  *  2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.oxf.fr

import org.orbeon.oxf.fr.Names._
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport, XFormsSupport}
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.oxf.xforms.control.controls.XFormsInputControl
import org.orbeon.scaxon.SimplePath._
import org.orbeon.xbl.Wizard
import org.scalatest.funspec.AnyFunSpecLike

class WizardTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with FormRunnerSupport
     with XFormsSupport {

  describe("Wizard") {

    it("must control strict navigation") {

      val (processorService, Some(doc), _) =
        runFormRunner("tests", "wizard", "new", document = "", initialize = true)

      withTestExternalContext { _ =>
        withFormRunnerDocument(processorService, doc) {

          val dataRootElem = instance(FormInstance).get.rootElement

          val section1Holder = dataRootElem / "section-1" head
          val section2Holder = dataRootElem / "section-2" head
          val section3Holder = dataRootElem / "section-3" head

          def resolveControlStaticId(staticId: String) =
            resolveObject[XFormsInputControl](staticId, container = doc).get.effectiveId

          def doNext() =
            XFormsAPI.dispatch(name = "fr-next", targetId = Names.ViewComponent)

          def doPrev() =
            XFormsAPI.dispatch(name = "fr-prev", targetId = Names.ViewComponent)

          def assertIsFirstPage() = {
            assert(  Wizard.isWizardFirstPage)
            assert(! Wizard.isWizardLastPage)
          }

          def assertIsLastPage() = {
            assert(! Wizard.isWizardFirstPage)
            assert(  Wizard.isWizardLastPage)
          }

          locally {

            def assertFirstPageStatus(section1HasVisibleIncomplete: Boolean) = {

              assert(Set("section-1") === Wizard.wizardAvailableSections)
              assertIsFirstPage()

              val expectedSectionStatus =
                Set("incomplete") ++ (section1HasVisibleIncomplete list "visible-incomplete")

              assert(expectedSectionStatus ===  (section1Holder attTokens "*:section-status"))
              assert(! (section2Holder hasAtt "*:section-status"))
              assert(! (section3Holder hasAtt "*:section-status"))
            }

            // Initial state
            assertFirstPageStatus(section1HasVisibleIncomplete = false)

            // Next must fail
            locally {
              doNext()
              assertFirstPageStatus(section1HasVisibleIncomplete = true)
            }
          }

          // Next must still fail after invalid value
          locally {
            setControlValue(resolveControlStaticId("control-11-control"), "M")

            doNext()

            assert(Set("changed", "invalid", "visible-invalid") ===  (section1Holder attTokens "*:section-status"))
            assert(! (section2Holder hasAtt "*:section-status"))
            assert(! (section3Holder hasAtt "*:section-status"))
          }

          locally {

            def assertSecondPage(section2HasVisibleIncomplete: Boolean) = {

              assert(Set("section-1", "section-2") === Wizard.wizardAvailableSections)

              assert(! Wizard.isWizardFirstPage)
              assert(! Wizard.isWizardLastPage)

              val expectedSection2Status =
                Set("incomplete") ++ (section2HasVisibleIncomplete list "visible-incomplete")

              assert("changed"              === (section1Holder attValue "*:section-status"))
              assert(expectedSection2Status === (section2Holder attTokens  "*:section-status"))
              assert(! (section3Holder hasAtt "*:section-status"))
            }

            // Next must succeed after valid value
            locally {
              setControlValue(resolveControlStaticId("control-11-control"), "Mickey")

              doNext()
              assertSecondPage(section2HasVisibleIncomplete = false)
            }

            // Next must fail
            locally {
              doNext()
              assertSecondPage(section2HasVisibleIncomplete = true)
            }
          }

          def assertAllSectionsAvailable() =
            assert(Set("section-1", "section-2", "section-3") === Wizard.wizardAvailableSections)

          locally {

            def assertThirdPage(section3HasVisibleIncomplete: Boolean) = {

              assertAllSectionsAvailable()

              assertIsLastPage()

              val expectedSection3Status =
                Set("incomplete") ++ (section3HasVisibleIncomplete list "visible-incomplete")

              assert("changed"              === (section1Holder attValue "*:section-status"))
              assert("changed"              === (section2Holder attValue "*:section-status"))
              assert(expectedSection3Status === (section3Holder attTokens "*:section-status"))
            }

            // Next must succeed after valid value
            locally {
              setControlValue(resolveControlStaticId("control-21-control"), "Minnie")

              doNext()
              assertThirdPage(section3HasVisibleIncomplete = false)
            }

            // Next must fail
            locally {
              doNext()
              assertThirdPage(section3HasVisibleIncomplete = true)
            }
          }

          locally {

            def assertComplete() = {

              assertAllSectionsAvailable()

              assert("changed" === (section1Holder attValue "*:section-status"))
              assert("changed" === (section2Holder attValue "*:section-status"))
              assert("changed" === (section3Holder attValue "*:section-status"))
            }

            // Change value on last page
            locally {
              setControlValue(resolveControlStaticId("control-31-control"), "Goofy")

              doNext()

              assertIsLastPage()
              assertComplete()
            }

            // Back to first page
            locally {

              doPrev()
              doPrev()

              assertIsFirstPage()
              assertComplete()
            }
          }

          locally {

            def assertFirstPageStatus() = {

              assert(Set("section-1") === Wizard.wizardAvailableSections)
              assertIsFirstPage()

              assert(Set("changed", "incomplete", "visible-incomplete") === (section1Holder attTokens "*:section-status"))
              assert("changed"                                          === (section2Holder attValue  "*:section-status"))
              assert("changed"                                          === (section3Holder attValue  "*:section-status"))
            }

            // Clear first value
            locally {
              setControlValue(resolveControlStaticId("control-11-control"), "")
              assertFirstPageStatus()
            }

            // Next must fail
            locally {
              doNext()
              assertFirstPageStatus()
            }
          }
        }
      }
    }

    ignore("must handle section relevance") {
      // TODO: Would be great to have a test for this!
    }

    ignore("must handle separate TOC") {
      // TODO: Would be great to have a test for this!
    }

    ignore("must handle subsection navigation") {
      // TODO: Would be great to have a test for this!
    }
  }
}
