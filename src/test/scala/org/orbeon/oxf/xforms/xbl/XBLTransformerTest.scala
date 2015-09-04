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

import org.scalatest.junit.AssertionsForJUnit
import org.junit.Test

class XBLTransformerTest extends AssertionsForJUnit {

  @Test def testCSSToXPath(): Unit = {

    val expected = Seq(
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

    for ((css, xpath) ← expected)
      assert(xpath === CSSParser.toXPath(css))
  }
}