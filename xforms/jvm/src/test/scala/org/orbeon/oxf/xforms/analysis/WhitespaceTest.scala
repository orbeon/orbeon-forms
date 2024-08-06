/**
 * Copyright (C) 2016 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.analysis

import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.scalatest.funspec.AnyFunSpecLike


class WhitespaceTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike {

  describe("The `xxf:whitespace` MIP") {

    val ControlIds = List(
      "with-trimming-control",
      "without-trimming-control",
      "calculated-value-with-trimming-control",
      "calculated-value-without-trimming-control"
    )

    def assertValues(expectedValues: List[String]): Unit =
      for ((expectedValue, controlId) <- expectedValues.zip(ControlIds))
        assert(expectedValue == getControlValue(controlId))

    def assertInitialState(): Unit =
      assertValues(
          List(
            "",
            " " * 4,
            "",
            " " * 12
          )
        )

    it("must pass initial and value changes") {
      withTestExternalContext { _ =>
        withActionAndDoc("oxf:/org/orbeon/oxf/xforms/analysis/whitespace.xhtml") {
          assertInitialState()

          // After value change to trimmed value
          setControlValue("with-trimming-control", "  Laniakea  ")

          assertValues(
            List(
              "Laniakea",
              " " * 4,
              "Laniakea",
              " " * 12
            )
          )

          // After value change to non-trimmed value
          setControlValue("without-trimming-control", "  Andromeda  ")

          assertValues(
            List(
              "Laniakea",
              "  Andromeda  ",
              "Laniakea",
              "      Andromeda      "
            )
          )
        }
      }
    }

    it("must pass instance replacement") {
      withTestExternalContext { _ =>
        withActionAndDoc("oxf:/org/orbeon/oxf/xforms/analysis/whitespace.xhtml") {
          assertInitialState()

          XFormsAPI.sendThrowOnError("replace-submission")
          XFormsAPI.refresh("model")

          assertValues(
            List(
              "Mercury",
              "  Venus  ",
              "Mercury",
              "      Venus      "
            )
          )
        }
      }
    }
  }
}