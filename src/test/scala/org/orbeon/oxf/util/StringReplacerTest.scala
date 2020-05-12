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

import org.orbeon.oxf.test.ResourceManagerTestBase
import org.scalatest.funspec.AnyFunSpecLike

class StringReplacerTest extends AnyFunSpecLike {

  implicit def logger = ResourceManagerTestBase.newIndentedLogger

  describe("Simple replacements") {

    val TestString = "abcdabcd"

    it("must pass valid replacements") {
      assert("AbCdAbCd" == StringReplacer("""{ "a": "A", "c": "C" }""")     (logger)(TestString))
      assert("BcdBcd"   == StringReplacer("""{ "a": "aaa", "aaab": "B" }""")(logger)(TestString))
      assert("dcbadcba" == StringReplacer("""{ "abcd": "dcba" }""")         (logger)(TestString))
    }

    it("must pass between quotes") {
      assert("""between "quotes"""" == StringReplacer("""{ "'": "\"" }""")(logger)("between 'quotes'"))
    }

    it("must pass with an empty map") {
      assert(TestString == StringReplacer("""{}""")(logger)(TestString))
    }

    it("must ignore a non-map") {
      assert(TestString == StringReplacer("""[]""")(logger)(TestString))
    }

    it("must ignore a non-string") {
      assert(TestString == StringReplacer("""{ "a": 1 }""")(logger)(TestString))
    }

    it("must ignore invalid JSON") {
      assert(TestString == StringReplacer("""{ 'a': 'A' }""")(logger)(TestString))
    }
  }
}
