/**
 * Copyright (C) 2026 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.fr

import cats.syntax.option.*
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.oxf.xforms.control.XFormsValueControl
import org.orbeon.scaxon.SimplePath.*
import org.scalatest.funspec.AnyFunSpecLike


class NonRelevantClearTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with FormRunnerSupport {

  describe("#2760: Form Runner 5-level `nonrelevant` hierarchy") {

    it("must cascade `oxf.fr.detail.nonrelevant` through section, grid, repeat iteration, and control overrides") {
      val (processorService, Some(doc), _) =
        runFormRunner("issue", "2760-prop-empty", "new")

      withTestExternalContext { _ =>
        withFormRunnerDocument(processorService, doc) {
          val root = doc.findInstance("fr-form-instance").get.rootElement

          // Hide iteration 2 of repeated grid in section-1 -> row 1 kept, row 2 cleared (inherits `empty` from property)
          val showRow2 = resolveObject[XFormsValueControl]("show-row2-control").get
          setControlValueWithEventSearchNested(showRow2.effectiveId, "false")

          val rows = (root / "section-1" / "rep-grid" / "rep-grid-iteration" / "rc").map(_.stringValue).toList
          assert(rows == List("row-1", ""))

          // Hide section-1: `c11` cleared (inherits `empty`), `c12` kept (control override `keep`), row 1 of `rep-grid` cleared
          val showS1 = resolveObject[XFormsValueControl]("show-s1-control").get
          setControlValueWithEventSearchNested(showS1.effectiveId, "false")
          assert((root / "section-1" / "grid-1" / "c11").stringValue == "")
          assert((root / "section-1" / "grid-1" / "c12").stringValue == "v12")
          assert((root / "section-1" / "rep-grid" / "rep-grid-iteration" / "rc").map(_.stringValue).toList == List("", ""))

          // Hide section-2 (`keep` at section level):
          // - grid-21 inherits `keep` -> `c211` kept, `c212` cleared (control override `empty`)
          // - grid-22 overrides to `empty` -> `c221` cleared, `c222` kept (control override `keep`)
          val showS2 = resolveObject[XFormsValueControl]("show-s2-control").get
          setControlValueWithEventSearchNested(showS2.effectiveId, "false")
          assert((root / "section-2" / "grid-21" / "c211").stringValue == "v211")
          assert((root / "section-2" / "grid-21" / "c212").stringValue == "")
          assert((root / "section-2" / "grid-22" / "c221").stringValue == "")
          assert((root / "section-2" / "grid-22" / "c222").stringValue == "v222")
        }
      }
    }

    it("must let `<nonrelevant>keep</nonrelevant>` in form metadata override property `empty`, and skip clearing when `disable-calculate` is active in readonly mode") {
      // 1. In `edit` mode (`disable-calculate` is false): metadata `<nonrelevant>keep</nonrelevant>` keeps `hidden-keep`,
      //    while control-level `xxf:nonrelevant="empty"` clears `hidden-empty` and `summary` recalculates to `"saved-keep|"`
      locally {
        val (processorService, Some(doc), _) =
          runFormRunner("issue", "2760-meta-keep", "edit", documentId = "1111111111111111111111111111111111111111".some)

        withTestExternalContext { _ =>
          withFormRunnerDocument(processorService, doc) {
            val root = doc.findInstance("fr-form-instance").get.rootElement
            assert((root / "section-1" / "grid-1" / "hidden-keep").stringValue == "saved-keep")
            assert((root / "section-1" / "grid-1" / "hidden-empty").stringValue == "")
            assert(getControlValue(resolveObject[XFormsValueControl]("summary-control").get.effectiveId) == "saved-keep|")
          }
        }
      }

      // 2. In `view` mode with `readonly-disable-calculate = true`: neither `calculate` nor `xxf:nonrelevant="empty"` mutates saved data
      locally {
        val (processorService, Some(doc), _) =
          runFormRunner("issue", "2760-meta-keep", "view", documentId = "1111111111111111111111111111111111111111".some)

        withTestExternalContext { _ =>
          withFormRunnerDocument(processorService, doc) {
            val root = doc.findInstance("fr-form-instance").get.rootElement
            assert((root / "section-1" / "grid-1" / "hidden-keep").stringValue == "saved-keep")
            assert((root / "section-1" / "grid-1" / "hidden-empty").stringValue == "saved-empty")
            assert(getControlValue(resolveObject[XFormsValueControl]("summary-control").get.effectiveId) == "saved-summary")
          }
        }
      }
    }
  }
}
