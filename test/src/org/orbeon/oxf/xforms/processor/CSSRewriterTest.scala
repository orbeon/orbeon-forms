/**
 * Copyright (C) 2011 Orbeon, Inc.
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

package org.orbeon.oxf.xforms.processor

import org.orbeon.oxf.test.ResourceManagerTestBase
import org.scalatest.junit.AssertionsForJUnit
import org.orbeon.oxf.util.NetUtils
import org.orbeon.oxf.pipeline.api.ExternalContext.Response
import org.junit._

class CSSRewriterTest extends ResourceManagerTestBase with AssertionsForJUnit {

    var response: Response = _

    @Before def setup() {
        response = NetUtils.getExternalContext.getResponse
    }

    @Test def testURLs() {

        // Relative and absolute paths
        assert("""div { background-image: url(/orbeon/styles/a.png) }""" === rewriteCSS("""div { background-image: url(a.png) }"""))
        assert("""div { background-image: url(/orbeon/styles/common/a.png) }""" === rewriteCSS("""div { background-image: url(common/a.png) }"""))
        assert("""div { background-image: url(/orbeon/a.png) }""" === rewriteCSS("""div { background-image: url(../a.png) }"""))
        assert("""div { background-image: url(/orbeon/a.png) }""" === rewriteCSS("""div { background-image: url(/a.png) }"""))
        assert("""div { background-image: url(http://example.org/a.png) }""" === rewriteCSS("""div { background-image: url(http://example.org/a.png) }"""))

        // With and without quotes around URL
        val rewritten = "div { background-image: url(/orbeon/styles/a.png) }"

        assert(rewritten === rewriteCSS("""div { background-image: url(a.png) }"""))
        assert(rewritten === rewriteCSS("""div { background-image: url("a.png") }"""))
        assert(rewritten === rewriteCSS("""div { background-image: url('a.png') }"""))
    }

    @Test def testNamespaces() {
        // Multiple id rewrites
        assert("""div #_ns_foo.bar div #_ns_gaga.toto div {}""" === rewriteCSS("""div #foo.bar div #gaga.toto div {}"""))

        // Multiple pairs
        assert("""#_ns_foo.bar {} #_ns_gaga.toto {}""" === rewriteCSS("""#foo.bar {} #gaga.toto {}"""))
    }

    @Test def testBoth() {
        assert("div #_ns_foo.bar { background-image: url(/orbeon/styles/a.png) }" ===
            rewriteCSS("""div #foo.bar { background-image: url(a.png) }"""))
    }

    private def rewriteCSS(css: String) =
        XFormsResourceRewriter.rewriteCSS(css, "_ns_", "/styles/style.css", response, null)
}