/**
 *  Copyright (C) 2007 Orbeon, Inc.
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
package org.orbeon.oxf.util

import org.scalatest.funspec.AnyFunSpec

class DynamicVariableTest extends AnyFunSpec {

  describe("Basic usage") {

    val v1 =  new DynamicVariable[String]
    it("must be initially empty") {
      assert(v1.value.isEmpty)
    }

    it("must scope nested values") {
      v1.withValue("foo") {
        assert(v1.value.contains("foo"))

        v1.withValue("bar") {
          assert(v1.value.contains("bar"))
        }

        assert(v1.value.contains("foo"))
      }
    }

    it("must be finally empty") {
      assert(v1.value.isEmpty)
    }
  }
}