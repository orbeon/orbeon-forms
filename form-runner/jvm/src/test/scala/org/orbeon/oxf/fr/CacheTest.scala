/**
 * Copyright (C) 2014 Orbeon, Inc.
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

import org.junit.Test
import org.orbeon.oxf.test.DocumentTestBase
import org.orbeon.oxf.test.TestHttpClient.{CacheEvent, StaticState}
import org.orbeon.oxf.xforms.state.XFormsStaticStateCache
import org.scalatest.junit.AssertionsForJUnit

class CacheTest extends DocumentTestBase with FormRunnerSupport with AssertionsForJUnit {

  @Test def formRunnerStaticCache(): Unit = {

    val Id1 = "6578e2e0e7911fd9ba284aefaea671cbfb814851"
    val Id2 = "15c4a18428496faa1212d86f58c62d9d3c51cf0d"

    def runAndAssert(form: String, mode: String, noscript: Boolean = false)(expectedInitialHit: Boolean, staticStateHoldsTemplate: Boolean) = {

      def staticStateFoundOpt(events: List[CacheEvent]) =
        events collectFirst { case StaticState(found, _) ⇒ found }

      def staticStateHasTemplateOpt(events: List[CacheEvent]) = (
        events
        collectFirst { case StaticState(_, digest) ⇒ digest}
        flatMap XFormsStaticStateCache.findDocument
        map (_.template.isDefined)
      )

      // First time may or may not pass
      val (_, docOpt1, events1) = runFormRunner("tests", form, mode, document = Id1, noscript = noscript, initialize = true)
      assert(Some(expectedInitialHit) === staticStateFoundOpt(events1))
      assert(docOpt1.nonEmpty || mode == "pdf")

      // Second time with different document must always pass
      val (_, docOpt2, events2) = runFormRunner("tests", form, mode, document = Id2, noscript = noscript, initialize = true)
      assert(Some(true) === staticStateFoundOpt(events2))
      assert(docOpt2.nonEmpty || mode == "pdf")

      assert(Some(staticStateHoldsTemplate) === staticStateHasTemplateOpt(events2))
    }

    locally {
      val Form = "noscript-true-pdf-auto-wizard-false"
      val staticStateHoldsTemplate = true

      runAndAssert(Form, "new")(expectedInitialHit = false, staticStateHoldsTemplate)

      for (mode ← Seq("edit", "view", "pdf"))
        runAndAssert(Form, mode)(expectedInitialHit = true, staticStateHoldsTemplate)

      // Once #1712 is fixed, should return true
      // See https://github.com/orbeon/orbeon-forms/issues/1712
      runAndAssert(Form, "edit", noscript = true)(expectedInitialHit = false, staticStateHoldsTemplate)
    }

    locally {
      val Form = "noscript-false-pdf-template-wizard-true"
      val staticStateHoldsTemplate = false

      runAndAssert(Form, "new" )(expectedInitialHit = false, staticStateHoldsTemplate)
      runAndAssert(Form, "edit")(expectedInitialHit = true,  staticStateHoldsTemplate)
      runAndAssert(Form, "view")(expectedInitialHit = false, staticStateHoldsTemplate)
      runAndAssert(Form, "pdf" )(expectedInitialHit = true,  staticStateHoldsTemplate)

      runAndAssert(Form, "edit", noscript = true)(expectedInitialHit = true, staticStateHoldsTemplate)
    }
  }
}
