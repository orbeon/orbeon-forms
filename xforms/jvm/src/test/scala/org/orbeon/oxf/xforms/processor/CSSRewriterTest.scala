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

import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.scalatest.funspec.AnyFunSpecLike

class CSSRewriterTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike {

  describe("Rewriting CSS") {

    it ("rewrites relative and absolute paths") {
      assert("""div { background-image: url(/orbeon/styles/a.png) }"""        === rewriteCSS("""div { background-image: url(a.png) }"""))
      assert("""div { background-image: url(/orbeon/styles/common/a.png) }""" === rewriteCSS("""div { background-image: url(common/a.png) }"""))
      assert("""div { background-image: url(/orbeon/a.png) }"""               === rewriteCSS("""div { background-image: url(../a.png) }"""))
      assert("""div { background-image: url(/orbeon/a.png) }"""               === rewriteCSS("""div { background-image: url(/a.png) }"""))
      assert("""div { background-image: url(http://example.org/a.png) }"""    === rewriteCSS("""div { background-image: url(http://example.org/a.png) }"""))
    }

    it("rewrites with and without quotes around URL") {

      val rewritten = "div { background-image: url(/orbeon/styles/a.png) }"

      assert(rewritten === rewriteCSS("""div { background-image: url(a.png) }"""))
      assert(rewritten === rewriteCSS("""div { background-image: url("a.png") }"""))
      assert(rewritten === rewriteCSS("""div { background-image: url('a.png') }"""))
    }
  }

  describe("Namespaces") {

    it("handles multiple id rewrites") {
      assert("""div #_ns_foo.bar div #_ns_gaga.toto div {}""" === rewriteCSS("""div #foo.bar div #gaga.toto div {}"""))
    }

    it("handles multiple pairs") {
      assert("""#_ns_foo.bar {} #_ns_gaga.toto {}""" === rewriteCSS("""#foo.bar {} #gaga.toto {}"""))
    }
  }

  describe("Both") {
    it("rewrites ids and URLs in the same CSS") {
      assert("div #_ns_foo.bar { background-image: url(/orbeon/styles/a.png) }" ===
        rewriteCSS("""div #foo.bar { background-image: url(a.png) }"""))
    }
  }

  describe("Backslashes") {

    val rule = """input[type="radio"],
           |input[type="checkbox"] {
           |  margin: 4px 0 0;
           |  margin-top: 1px\9;
           |  *margin-top: 0;
           |  line-height: normal;
           |  cursor: pointer;
           |}
           |""".stripMargin

    it("keeps them") {
      assert(rule === rewriteCSS(rule).stripMargin)
    }
  }

  private def rewriteCSS(css: String) =
    withTestExternalContext { ec =>
      XFormsResourceRewriter.rewriteCSS(css, "/styles/style.css", Some("_ns_"), ec.getResponse, isCssNoPrefix = false)(null)
    }
}