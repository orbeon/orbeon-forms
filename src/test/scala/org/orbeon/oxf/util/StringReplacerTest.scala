/**
 *  Copyright (C) 2013 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 *  package org.orbeon.oxf.util
 */
package org.orbeon.oxf.util

import org.junit.Test
import org.orbeon.oxf.test.ResourceManagerTestBase
import org.scalatestplus.junit.AssertionsForJUnit

class StringReplacerTest extends AssertionsForJUnit {

  implicit def logger = ResourceManagerTestBase.newIndentedLogger

  @Test def simpleReplacements(): Unit = {

    val TestString = "abcdabcd"

    // Valid replacements
    assert("AbCdAbCd"             === StringReplacer("""{ "a": "A", "c": "C" }""")     (logger)(TestString))
    assert("BcdBcd"               === StringReplacer("""{ "a": "aaa", "aaab": "B" }""")(logger)(TestString))
    assert("dcbadcba"             === StringReplacer("""{ "abcd": "dcba" }""")         (logger)(TestString))
    assert("""between "quotes"""" === StringReplacer("""{ "'": "\"" }""")              (logger)("between 'quotes'"))

    // Empty map
    assert(TestString === StringReplacer("""{}""")(logger)(TestString))

    // Not a map
    assert(TestString === StringReplacer("""[]""")(logger)(TestString))

    // Map not to a string
    assert(TestString === StringReplacer("""{ "a": 1 }""")(logger)(TestString))

    // Invalid JSON
    assert(TestString === StringReplacer("""{ 'a': 'A' }""")(logger)(TestString))
  }
}
