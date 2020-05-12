/**
 * Copyright (C) 2010 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.xbl

import org.orbeon.oxf.test.XMLSupport
import org.orbeon.scaxon.NodeConversions._
import org.scalatest.funspec.AnyFunSpec

import scala.xml.Elem

class XBLTransformerTest extends AnyFunSpec with XMLSupport {

  describe("Conversion of CSS to XPath") {

    val data = List(
      "foo|a"                                     -> "descendant-or-self::foo:a",
      "foo|a foo|b"                               -> "descendant-or-self::foo:a//foo:b",
      "foo|a foo|b, bar|a bar|b"                  -> "descendant-or-self::foo:a//foo:b|descendant-or-self::bar:a//bar:b",
      "foo|a > foo|b"                             -> "descendant-or-self::foo:a/foo:b",
      "foo|a > foo|b, bar|a > bar|b"              -> "descendant-or-self::foo:a/foo:b|descendant-or-self::bar:a/bar:b",
      "> foo|a"                                   -> "./foo:a",
      ":root foo|a"                               -> ".//foo:a",
      "*:root foo|a"                              -> ".//foo:a",
      ":root > foo|a"                             -> "./foo:a",
      ":root > xf|label, :root > xf|help"         -> "./xf:label|./xf:help"
      // NOTE: We can't support this as we would need current() (root() won't work from XBLTransformer)
      // ":root :root foo|a"                         -> "current()//current()//foo:a"
    )

    for ((css, xpath) <- data)
      it(s"must convert `$css`") {
        assert(xpath === CSSParser.toXPath(css))
      }
  }

  def assertTransforms(data: List[(String, Elem, Elem, Elem)]): Unit =
    for ((description, bound, shadow, expected) <- data)
      it(description) {
        assertXMLDocumentsIgnoreNamespacesInScope(
          elemToDom4j(expected),
          XBLTransformer.transform(
            partAnalysis          = null, // just for tests, we assume it's not going to be used
            xblSupport            = None,
            shadowTreeDocument    = elemToDom4j(shadow),
            boundElement          = elemToDom4jElem(bound),
            abstractBindingOpt    = None,
            excludeNestedHandlers = false,
            excludeNestedLHHA     = false,
            supportAVTs           = true
          )
        )
      }

  describe("Issue #2519") {
    assertTransforms(
      List(
        (
          "must copy attribute with simple syntax",
          <bound bar="baz"/>,
          <root><elem xbl:attr="bar"     bar="default" xmlns:xbl="http://www.w3.org/ns/xbl"/></root>,
          <root><elem bar="baz"/></root>
        ),
        (
          "must copy attribute with `=` syntax",
          <bound bar="baz"/>,
          <root><elem xbl:attr="bar=bar" bar="default" xmlns:xbl="http://www.w3.org/ns/xbl"/></root>,
          <root><elem bar="baz"/></root>
        ),
        (
          "must keep original attribute with simple syntax",
          <bound/>,
          <root><elem xbl:attr="bar"     bar="default" xmlns:xbl="http://www.w3.org/ns/xbl"/></root>,
          <root><elem bar="default"/></root>
        ),
        (
          "must keep original attribute with `=` syntax",
          <bound/>,
          <root><elem xbl:attr="bar=bar" bar="default" xmlns:xbl="http://www.w3.org/ns/xbl"/></root>,
          <root><elem bar="default"/></root>
        )
      )
    )
  }

  describe("`xxbl:use-if-attr` attribute") {
     assertTransforms(
      List(
        (
          "keep with non-blank attribute",
          <bound bar="baz"/>,
          <root xmlns:xxbl="http://orbeon.org/oxf/xml/xbl"><div id="keep"><span/></div><div id="keep-too" xxbl:use-if-attr="bar"><span/></div></root>,
          <root xmlns:xxbl="http://orbeon.org/oxf/xml/xbl"><div id="keep"><span/></div><div id="keep-too" xxbl:use-if-attr="bar"><span/></div></root>
        ),
        (
          "prune with blank attribute",
          <bound bar=""/>,
          <root xmlns:xxbl="http://orbeon.org/oxf/xml/xbl"><div id="keep"><span/></div><div id="prune" xxbl:use-if-attr="bar"><span/></div></root>,
          <root><div id="keep"><span/></div></root>
        ),
        (
          "prune with missing attribute",
          <bound/>,
          <root xmlns:xxbl="http://orbeon.org/oxf/xml/xbl"><div id="keep"><span/></div><div id="prune" xxbl:use-if-attr="bar"><span/></div></root>,
          <root><div id="keep"><span/></div></root>
        )
      )
    )
  }
}