/**
  * Copyright (C) 2017 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  *  2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.oxf.xforms.function.xxforms

import org.orbeon.saxon.om.NodeInfo
import org.scalatest.FunSpec

import org.orbeon.scaxon.XML._

class XXFormsResourceTest extends FunSpec {

  describe("The `pathFromTokens()` function") {

    val xml1: NodeInfo =
      <resources>
        <components>
          <grid>
            <insert-above>42</insert-above>
          </grid>
        </components>
        <first-name>
          <label>First Name</label>
          <alert>Default Alert</alert>
          <alert>Invalid Length</alert>
        </first-name>
      </resources>

    val Expected = List(
      "components.grid.insert-above" → "42",
      "first-name.alert.0"           → "Default Alert",
      "first-name.alert.1"           → "Invalid Length"
    )

    for ((path, expected) ← Expected)
      it(s"must find the node with path `$path`") {

        val result = XXFormsResource.pathFromTokens(xml1.rootElement, XXFormsResource.splitResourceName(path))

        assert(1 === result.size)
        assert(expected === result.stringValue)
      }
  }

  describe("The `flattenResourceName()` function") {

     val Expected = List(
      "components.grid.insert-above" → List("components", "grid", "insert-above"),
      "first-name.alert.0"           → List("first-name", "alert"),
      "first-name.alert.1"           → List("first-name", "alert")
    )

    for ((path, expected) ← Expected)
      it(s"must flatten path `$path`") {
        assert(expected === XXFormsResource.flattenResourceName(path))
      }
  }

}
