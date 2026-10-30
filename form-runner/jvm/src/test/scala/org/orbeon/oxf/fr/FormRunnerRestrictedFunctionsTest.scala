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

import cats.syntax.option._
import org.orbeon.oxf.externalcontext.{Credentials, SimpleRole, UserAndGroup}
import org.orbeon.oxf.http.HttpMethod.GET
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport, TestHttpClient}
import org.orbeon.oxf.xforms.control.XFormsValueControl
import org.orbeon.oxf.xforms.event.EventCollector
import org.scalatest.funspec.AnyFunSpecLike


class FormRunnerRestrictedFunctionsTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with FormRunnerSupport {

  describe("Restricted XPath Functions in Form Runner (#7266)") {

    it("must allow standard whitelisted functions (concat, math:pow, string-length)") {
      val (processorService, docOpt, _) =
        runFormRunner("tests", "restricted-functions-allowed", "new")

      assert(docOpt.isDefined)
      val doc = docOpt.get

      withTestExternalContext { _ =>
        withFormRunnerDocument(processorService, doc) {
          val greetingControl = resolveObject[XFormsValueControl]("greeting-control").get
          assert(greetingControl.elementAnalysis.isFunctionsRestricted)
          assert(greetingControl.getValue(EventCollector.Throw) == "Hello, World")

          val mathControl = resolveObject[XFormsValueControl]("math-result-control").get
          assert(mathControl.elementAnalysis.isFunctionsRestricted)
          assert(mathControl.getValue(EventCollector.Throw) == "8")

          val lengthControl = resolveObject[XFormsValueControl]("length-result-control").get
          assert(lengthControl.elementAnalysis.isFunctionsRestricted)
          assert(lengthControl.getValue(EventCollector.Throw) == "5")

          val amountControl = resolveObject[XFormsValueControl]("amount-control").get
          assert(amountControl.elementAnalysis.isFunctionsRestricted)
          assert(amountControl.getValue(EventCollector.Throw) == "1234.56")
          assert(amountControl.getFormattedValue(EventCollector.Throw).contains("1,234.56"))
          assert(amountControl.getExternalValue(EventCollector.Throw).nonEmpty)
        }
      }
    }

    it("must reject disallowed standard functions like fn:doc") {
      val (_, docOpt, _) =
        runFormRunner("tests", "restricted-functions-disallowed-doc", "new")

      assert(docOpt.isEmpty)
    }

    it("must reject disallowed Java extension function calls") {
      val (_, docOpt, _) =
        runFormRunner("tests", "restricted-functions-disallowed-java", "new")

      assert(docOpt.isEmpty)
    }

    it("must not allow form definitions to bypass restriction with author xxf:functions attributes") {
      val (_, docOpt, _) =
        runFormRunner("tests", "restricted-functions-bypass-attempt", "new")

      assert(docOpt.isEmpty)
    }

    it("must allow whitelisted functions in user actions") {
      val (processorService, docOpt, _) =
        runFormRunner("tests", "restricted-functions-action-allowed", "new")

      assert(docOpt.isDefined)
      val doc = docOpt.get

      withTestExternalContext { _ =>
        withFormRunnerDocument(processorService, doc) {
          val targetControl = resolveObject[XFormsValueControl]("target-control").get
          assert(targetControl.getValue(EventCollector.Throw) == "Action Executed")

          val target20182Control = resolveObject[XFormsValueControl]("target-20182-control").get
          assert(target20182Control.getValue(EventCollector.Throw) == "Input Processed")
        }
      }
    }

    it("must reject disallowed functions in user actions") {
      val (_, docOpt, _) =
        runFormRunner("tests", "restricted-functions-action-disallowed", "new")

      assert(docOpt.isEmpty)
    }

    it("must reject non-whitelisted internal helper functions (like frf:bindId) in 2018.2 user actions") {
      val (_, docOpt, _) =
        runFormRunner("tests", "restricted-functions-action-20182-disallowed-internal", "new")

      assert(docOpt.isEmpty)
    }

    it("must allow Summary page to load without restrictions") {
      val (_, docOpt, _) =
        runFormRunner("tests", "restricted-functions-allowed", "summary")

      assert(docOpt.isDefined)
    }

    it("must allow Landing page to load without restrictions") {
      val (_, response, _, _) =
        TestHttpClient.connect(
          url     = "/fr/",
          method  = GET,
          headers = Map.empty,
          content = None
        )

      assert(response.statusCode == 200)
    }

    it("must allow Forms and Admin pages to load without restrictions") {
      val (_, formsResponse, _, _) =
        TestHttpClient.connect(
          url     = "/fr/forms",
          method  = GET,
          headers = Map.empty,
          content = None
        )

      assert(formsResponse.statusCode == 200)

      // Admin page runs the same home/home.xhtml model, executing frf:* extension functions (including frf:sendError(403))
      val (_, adminResponse, _, _) =
        TestHttpClient.connect(
          url     = "/fr/admin",
          method  = GET,
          headers = Map.empty,
          content = None
        )

      assert(adminResponse.statusCode == 403)
    }
  }
}
