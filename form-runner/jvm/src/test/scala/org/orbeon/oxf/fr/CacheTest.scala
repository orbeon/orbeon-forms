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

import org.orbeon.oxf.test.TestHttpClient.{CacheEvent, StaticState}
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.oxf.xforms.state.XFormsStaticStateCache
import org.scalatest.funspec.AnyFunSpecLike

class CacheTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with FormRunnerSupport{

  describe("Form Runner static cache") {

    val Id1 = "6578e2e0e7911fd9ba284aefaea671cbfb814851"
    val Id2 = "15c4a18428496faa1212d86f58c62d9d3c51cf0d"

    def runAndAssert(form: String, mode: String)(expectedInitialHit: Boolean): Unit = {

      def staticStateFoundOpt(events: List[CacheEvent]) =
        events collectFirst { case StaticState(found, _) => found }

      def staticStateHasTemplateOpt(events: List[CacheEvent]) = (
        events
        collectFirst { case StaticState(_, digest) => digest}
        flatMap XFormsStaticStateCache.findDocument
        map (_._1.template.isDefined)
      )

      // First time may or may not pass
      val (_, _, events1) = runFormRunner("tests", form, mode, document = Id1, initialize = true)
      it(s"initial hit `$expectedInitialHit` for $form/$mode") {
        assert(Some(expectedInitialHit) === staticStateFoundOpt(events1))
        // NOTE: no XFCD because the form has `xxf:no-updates="true"`.
      }

      // Second time with different document must always pass
      val (_, _, events2) = runFormRunner("tests", form, mode, document = Id2, initialize = true)
      it(s"second hit `true` for $form/$mode") {
        assert(Some(true) === staticStateFoundOpt(events2))
        // NOTE: no XFCD because the form has `xxf:no-updates="true"`.
      }

      it(s"template exists for $form/$mode") {
        assert(staticStateHasTemplateOpt(events2) contains true)
      }
    }

    locally {
      val Form = "noscript-false-pdf-template-wizard-true"

      runAndAssert(Form, "new" )(expectedInitialHit = false)
      runAndAssert(Form, "edit")(expectedInitialHit = true)
      runAndAssert(Form, "view")(expectedInitialHit = false)
      runAndAssert(Form, "pdf" )(expectedInitialHit = true)

      // NOTE: Need to run schema.xpl or FR PFC for this to work
      // See https://github.com/orbeon/orbeon-forms/issues/1731
      // runAndAssert(Form, "schema")(expectedFound = false)
    }
  }
}
