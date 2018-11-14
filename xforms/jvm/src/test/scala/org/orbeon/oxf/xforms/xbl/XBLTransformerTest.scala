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

import org.junit.Test
import org.orbeon.oxf.test.XMLSupport
import org.orbeon.scaxon.NodeConversions._
import org.scalatest.junit.AssertionsForJUnit

class XBLTransformerTest extends AssertionsForJUnit with XMLSupport {

  @Test def testCSSToXPath(): Unit = {

    val data = List(
      "foo|a"                                     → "descendant-or-self::foo:a",
      "foo|a foo|b"                               → "descendant-or-self::foo:a//foo:b",
      "foo|a foo|b, bar|a bar|b"                  → "descendant-or-self::foo:a//foo:b|descendant-or-self::bar:a//bar:b",
      "foo|a > foo|b"                             → "descendant-or-self::foo:a/foo:b",
      "foo|a > foo|b, bar|a > bar|b"              → "descendant-or-self::foo:a/foo:b|descendant-or-self::bar:a/bar:b",
      "> foo|a"                                   → "./foo:a",
      ":root foo|a"                               → ".//foo:a",
      "*:root foo|a"                              → ".//foo:a",
      ":root > foo|a"                             → "./foo:a",
      ":root > xf|label, :root > xf|help"         → "./xf:label|./xf:help"
      // NOTE: We can't support this as we would need current() (root() won't work from XBLTransformer)
      // ":root :root foo|a"                         → "current()//current()//foo:a"
    )

    for ((css, xpath) ← data)
      assert(xpath === CSSParser.toXPath(css))
  }

  @Test def testIssue2519(): Unit = {

    val data = List(
      (<bound bar="baz"/>, <root><elem xbl:attr="bar"     bar="default" xmlns:xbl="http://www.w3.org/ns/xbl"/></root>, <root><elem bar="baz"/></root>),
      (<bound bar="baz"/>, <root><elem xbl:attr="bar=bar" bar="default" xmlns:xbl="http://www.w3.org/ns/xbl"/></root>, <root><elem bar="baz"/></root>),
      (<bound/>          , <root><elem xbl:attr="bar"     bar="default" xmlns:xbl="http://www.w3.org/ns/xbl"/></root>, <root><elem bar="default"/></root>),
      (<bound/>          , <root><elem xbl:attr="bar=bar" bar="default" xmlns:xbl="http://www.w3.org/ns/xbl"/></root>, <root><elem bar="default"/></root>)
    )

    for ((bound, shadow, expected) ← data) {
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
  }
}