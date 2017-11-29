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
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.oxf.xforms.control.controls.XFormsInputControl
import org.orbeon.scaxon.SimplePath._
import org.orbeon.xbl.Wizard
import org.scalatest.FunSpecLike

class WizardTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with FunSpecLike
     with FormRunnerSupport
     with XFormsSupport {

  describe("Wizard") {

    it("must control strict navigation") {

      val (processorService, Some(doc), _) =
        runFormRunner("tests", "wizard", "new", document = "", noscript = false, initialize = true)

      withFormRunnerDocument(processorService, doc) {

        val dataRootElem = instance(FormInstance).get.rootElement

        val section1Holder = dataRootElem / "section-1" head
        val section2Holder = dataRootElem / "section-2" head
        val section3Holder = dataRootElem / "section-3" head

        def resolveControlStaticId(staticId: String) =
          resolveObject[XFormsInputControl](staticId, container = doc).get.effectiveId

        def doNext() =
          XFormsAPI.dispatch(name = "fr-next", targetId = "fr-view-component")

        def doPrev() =
          XFormsAPI.dispatch(name = "fr-prev", targetId = "fr-view-component")

        def assertIsFirstPage() = {
          assert(  Wizard.isWizardFirstPage)
          assert(! Wizard.isWizardLastPage)
        }

        def assertIsLastPage() = {
          assert(! Wizard.isWizardFirstPage)
          assert(  Wizard.isWizardLastPage)
        }

        locally {

          def assertFirstPageStatus() = {

            assert(Set("section-1") === Wizard.wizardAvailableSections)
            assertIsFirstPage()

            assert("incomplete" ===  (section1Holder attValue "*:section-status"))
            assert(! (section2Holder hasAtt "*:section-status"))
            assert(! (section3Holder hasAtt "*:section-status"))
          }

          // Initial state
          assertFirstPageStatus()

          // Next must fail
          locally {
            doNext()
            assertFirstPageStatus()
          }
        }

        // Next must still fail after invalid value
        locally {
          setControlValue(resolveControlStaticId("control-11-control"), "M")

          doNext()

          assert(Set("changed", "invalid") ===  (section1Holder attTokens "*:section-status"))
          assert(! (section2Holder hasAtt "*:section-status"))
          assert(! (section3Holder hasAtt "*:section-status"))
        }

        locally {

          def assertSecondPage() = {

            assert(Set("section-1", "section-2") === Wizard.wizardAvailableSections)

            assert(! Wizard.isWizardFirstPage)
            assert(! Wizard.isWizardLastPage)

            assert("changed"    === (section1Holder attValue "*:section-status"))
            assert("incomplete" === (section2Holder attValue "*:section-status"))
            assert(! (section3Holder hasAtt "*:section-status"))
          }

          // Next must succeed after valid value
          locally {
            setControlValue(resolveControlStaticId("control-11-control"), "Mickey")

            doNext()
            assertSecondPage()
          }

          // Next must fail
          locally {
            doNext()
            assertSecondPage()
          }
        }

        def assertAllSectionsAvailable() =
          assert(Set("section-1", "section-2", "section-3") === Wizard.wizardAvailableSections)

        locally {

          def assertThirdPage() = {

            assertAllSectionsAvailable()

            assertIsLastPage()

            assert("changed"    === (section1Holder attValue "*:section-status"))
            assert("changed"    === (section2Holder attValue "*:section-status"))
            assert("incomplete" === (section3Holder attValue "*:section-status"))
          }

          // Next must succeed after valid value
          locally {
            setControlValue(resolveControlStaticId("control-21-control"), "Minnie")

            doNext()
            assertThirdPage()
          }

          // Next must fail
          locally {
            doNext()
            assertThirdPage()
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

            assert(Set("changed", "incomplete") === (section1Holder attTokens "*:section-status"))
            assert("changed"                    === (section2Holder attValue  "*:section-status"))
            assert("changed"                    === (section3Holder attValue  "*:section-status"))
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
