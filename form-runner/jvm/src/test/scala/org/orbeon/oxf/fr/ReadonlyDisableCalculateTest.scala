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

import cats.implicits.catsSyntaxOptionId
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.oxf.xforms.control.XFormsControl
import org.scalatest.funspec.AnyFunSpecLike


class ReadonlyDisableCalculateTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with FormRunnerSupport {

  describe("Calculations disabled in readonly modes") {

    val Expected = List(
      ("7921-true",  "true",  List("edit" -> "calculated", "view" -> "saved")),      // readonly-disable-calculate = true
      ("7921-false", "false", List("edit" -> "calculated", "view" -> "calculated")), // readonly-disable-calculate = false
    )

    for {
      (form, readonlyDisableCalculate, expectedByMode) <- Expected
      (mode, expected)                                 <- expectedByMode
    }
      it(s"must return `$expected` for mode `$mode` with `readonly-disable-calculate` set to `$readonlyDisableCalculate`") {

        val (processorService, Some(doc), _) =
          runFormRunner("issue", form, mode, documentId = "d58bf2707df15bd0889ea0ff641420ec3416845b".some)

        withTestExternalContext { _ =>
          withFormRunnerDocument(processorService, doc) {
            assert(getControlValue(resolveObject[XFormsControl]("value-control").get.effectiveId) == expected)
          }
        }
      }
  }
}
