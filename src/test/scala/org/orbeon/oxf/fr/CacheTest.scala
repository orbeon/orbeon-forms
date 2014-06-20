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

import org.orbeon.oxf.test.DocumentTestBase
import org.orbeon.oxf.xforms.state.XFormsStaticStateCache
import org.scalatest.junit.AssertionsForJUnit
import org.junit.Test
import org.orbeon.oxf.xforms.state.XFormsStaticStateCache.CacheTracer
import scala.collection.mutable.ListBuffer

class CacheTest extends DocumentTestBase with FormRunnerSupport with AssertionsForJUnit {

    @Test def formRunnerStaticCache(): Unit = {

        var events = ListBuffer[CacheEvent]()

        sealed trait CacheEvent
        case class DigestAndTemplate(digestIfFound: Option[String]) extends CacheEvent
        case class StaticState(found: Boolean, digest: String) extends CacheEvent

        implicit val tracer = new CacheTracer {
            override def digestAndTemplateStatus(digestIfFound: Option[String]) = events += DigestAndTemplate(digestIfFound)
            override def staticStateStatus(found: Boolean, digest: String)      = events += StaticState(found, digest)
        }

        val Id1 = "6578e2e0e7911fd9ba284aefaea671cbfb814851"
        val Id2 = "15c4a18428496faa1212d86f58c62d9d3c51cf0d"

        def staticStateHasTemplateOpt = (
            events
            collectFirst { case StaticState(_, digest) ⇒ digest}
            flatMap XFormsStaticStateCache.findDocument
            map (_.template.isDefined)
        )

        def runAndAssert(form: String, mode: String, noscript: Boolean = false)(expectedInitialHit: Boolean, staticStateHoldsTemplate: Boolean) = {

            // First time may or may not pass
            events.clear()
            runFormRunner("cache-test", form, mode, document = Id1, noscript = noscript, initialize = false)
            assert(Some(expectedInitialHit) === (events collectFirst { case StaticState(found, _) ⇒ found }))

            // Second time with different document must always pass
            events.clear()
            runFormRunner("cache-test", form, mode, document = Id2, noscript = noscript, initialize = false)
            assert(Some(true) === (events collectFirst { case StaticState(found, _) ⇒ found }))

            assert(Some(staticStateHoldsTemplate) === staticStateHasTemplateOpt)
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

            // NOTE: Need to run schema.xpl or FR PFC for this to work
            // See https://github.com/orbeon/orbeon-forms/issues/1731
            // runAndAssert(Form, "schema")(expectedFound = false)
        }

        locally {
            val Form = "noscript-false-pdf-template-wizard-true"
            val staticStateHoldsTemplate = false

            runAndAssert(Form, "new" )(expectedInitialHit = false, staticStateHoldsTemplate)
            runAndAssert(Form, "edit")(expectedInitialHit = true,  staticStateHoldsTemplate)
            runAndAssert(Form, "view")(expectedInitialHit = false, staticStateHoldsTemplate)
            runAndAssert(Form, "pdf" )(expectedInitialHit = true,  staticStateHoldsTemplate)

            runAndAssert(Form, "edit", noscript = true)(expectedInitialHit = true, staticStateHoldsTemplate)

            // NOTE: Need to run schema.xpl or FR PFC for this to work
            // See https://github.com/orbeon/orbeon-forms/issues/1731
            // runAndAssert(Form, "schema")(expectedFound = false)
        }
    }
}
