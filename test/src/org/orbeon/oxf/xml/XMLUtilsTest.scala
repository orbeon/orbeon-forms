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

class XMLUtilsTest extends AssertionsForJUnit {

    @Test def makeNCName() {

        intercept[IllegalArgumentException] {
            XMLUtils.makeNCName("")
        }

        intercept[IllegalArgumentException] {
            XMLUtils.makeNCName("  ")
        }

        assert("foo" === XMLUtils.makeNCName("foo"))
        assert("_foo_" === XMLUtils.makeNCName(" foo "))
        assert("_2foos" === XMLUtils.makeNCName("42foos"))
        assert("foo_bar_" === XMLUtils.makeNCName("foo(bar)"))
    }
}