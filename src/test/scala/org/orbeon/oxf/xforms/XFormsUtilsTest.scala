/**
 *  Copyright (C) 2012 Orbeon, Inc.
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
 */
package org.orbeon.oxf.xforms

import org.scalatest.junit.AssertionsForJUnit
import org.junit.Test
import XFormsUtils._
import org.orbeon.oxf.test.DocumentTestBase

class XFormsUtilsTest extends DocumentTestBase with AssertionsForJUnit {
  @Test def effectiveAndAbsolute(): Unit = {
    val pairs = Map(
      "foo"             → "|foo|",
      "foo≡bar"         → "|foo≡bar|",
      "foo≡bar≡baz⊙1-2" → "|foo≡bar≡baz⊙1-2|"
    )

    for ((effective, absolute) ← pairs) {
      assert(isAbsoluteId(absolute))
      assert(effective === absoluteIdToEffectiveId(absolute))
      assert(absolute  === effectiveIdToAbsoluteId(effective))
    }
  }

  @Test def absolute(): Unit = {
    assert(isAbsoluteId("|foo|"))
    assert(! isAbsoluteId("||"))
    assert(! isAbsoluteId("|"))
    assert(! isAbsoluteId(""))
    assert(! isAbsoluteId("≡≡"))
  }
}
