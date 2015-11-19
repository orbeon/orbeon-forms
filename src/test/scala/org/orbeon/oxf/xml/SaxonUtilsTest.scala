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
package org.orbeon.oxf.xml

import org.scalatest.junit.AssertionsForJUnit
import org.junit.Test

class SaxonUtilsTest extends AssertionsForJUnit {

  @Test def makeNCName(): Unit = {

    intercept[IllegalArgumentException] {
      SaxonUtils.makeNCName("", keepFirstIfPossible = true)
    }

    intercept[IllegalArgumentException] {
      SaxonUtils.makeNCName("  ", keepFirstIfPossible = true)
    }

    assert("foo"      === SaxonUtils.makeNCName("foo",      keepFirstIfPossible = true))
    assert("_foo_"    === SaxonUtils.makeNCName(" foo ",    keepFirstIfPossible = true))
    assert("_42foos"  === SaxonUtils.makeNCName("42foos",   keepFirstIfPossible = true))
    assert("_2foos"   === SaxonUtils.makeNCName("42foos",   keepFirstIfPossible = false))
    assert("foo_bar_" === SaxonUtils.makeNCName("foo(bar)", keepFirstIfPossible = true))
  }
}