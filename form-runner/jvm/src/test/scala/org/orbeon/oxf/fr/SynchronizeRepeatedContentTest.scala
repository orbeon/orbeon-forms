/**
  * Copyright (C) 2018 Orbeon, Inc.
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

import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.oxf.xforms.control.XFormsControl
import org.scalatest.funspec.AnyFunSpecLike

class SynchronizeRepeatedContentTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with FormRunnerSupport {

  val BobValue   = "Bob"
  val AliceValue = "Alice"

  describe("Form Runner `<fr:synchronize-repeated-content>` component") {

    val (processorService, docOpt, _) =
      runFormRunner("tests", "synchronize-repeated-content", "new", document = "", initialize = true)

    val doc = docOpt.get

    it("must pass all synchronization checks") {
      withTestExternalContext { _ =>
        withFormRunnerDocument(processorService, doc) {

          val masterGridGrid     = resolveObject[XFormsControl]("master-grid-grid").get
          val gridAddEffectiveId = "fr-view-component≡master-section-section≡master-grid-grid≡fr-grid-add"

          def travelerControl(index: Int) =
            resolveObject[XFormsControl]("traveler-name-control", indexes = List(index)).get

          def travelerReadonlyControl(index: Int) =
            resolveObject[XFormsControl]("traveler-name-readonly-control", indexes = List(index)).get

          setControlValueWithEventSearchNested(travelerControl(index = 1).getEffectiveId, BobValue)

          def assertBothValues(index: Int, value: String): Unit = {
            assert(value === getControlValue(travelerControl(index).effectiveId))
            assert(value === getControlValue(travelerReadonlyControl(index).effectiveId))
          }

          assertBothValues(index = 1, BobValue)

          dispatch(name = "fr-insert-below", effectiveId = masterGridGrid.effectiveId, properties = Map("row" -> Some("1")))
          doc.synchronizeAndRefresh()
          assertBothValues(index = 1, BobValue)

          dispatch(name = "fr-insert-above", effectiveId = masterGridGrid.effectiveId, properties = Map("row" -> Some("1")))
          doc.synchronizeAndRefresh()
          assertBothValues(index = 2, BobValue)

          setControlValueWithEventSearchNested(controlEffectiveId = travelerControl(index = 3).getEffectiveId, value = AliceValue)

          assertBothValues(index = 2, BobValue)
          assertBothValues(index = 3, AliceValue)

          dispatch(name = "fr-remove", masterGridGrid.effectiveId, properties = Map("row" -> Some("1")))
          doc.synchronizeAndRefresh()

          assertBothValues(index = 1, BobValue)
          assertBothValues(index = 2, AliceValue)

          dispatch(name = "fr-move-down", effectiveId = masterGridGrid.effectiveId, properties = Map("row" -> Some("1")))
          doc.synchronizeAndRefresh()

          assertBothValues(index = 2, BobValue)
          assertBothValues(index = 1, AliceValue)

          dispatch(name = "fr-move-up", effectiveId = masterGridGrid.effectiveId, properties = Map("row" -> Some("2")))
          doc.synchronizeAndRefresh()

          assertBothValues(index = 1, BobValue)
          assertBothValues(index = 2, AliceValue)

          // NOTE: Index must be 1 now.
          dispatch(name = "DOMActivate", effectiveId = gridAddEffectiveId)
          doc.synchronizeAndRefresh()

          assertBothValues(index = 1, BobValue)
          assertBothValues(index = 3, AliceValue)
        }
      }
    }
  }

}
