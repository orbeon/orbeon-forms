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

import org.orbeon.oxf.test.ResourceManagerSupport
import org.orbeon.saxon.function.ProcessTemplateSupport
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.NodeConversions.*
import org.orbeon.scaxon.SimplePath.*
import org.scalatest.funspec.AnyFunSpec


class XXFormsResourceTest extends AnyFunSpec with ResourceManagerSupport {

  describe("The `pathFromTokens()` function") {

    val resources: NodeInfo =
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
      "components.grid.insert-above" -> "42",
      "first-name.alert.0"           -> "Default Alert",
      "first-name.alert.1"           -> "Invalid Length"
    )

    for ((path, expected) <- Expected)
      it(s"must find the node with path `$path`") {

        val result = XXFormsResourceSupport.pathFromTokens(resources.rootElement, XXFormsResourceSupport.splitResourceName(path))

        assert(1 === result.size)
        assert(expected === result.stringValue)
      }
  }

  describe("The `flattenResourceName()` function") {

     val Expected = List(
      "components.grid.insert-above" -> List("components", "grid", "insert-above"),
      "first-name.alert.0"           -> List("first-name", "alert"),
      "first-name.alert.1"           -> List("first-name", "alert")
    )

    for ((path, expected) <- Expected)
      it(s"must flatten path `$path`") {
        assert(expected === XXFormsResourceSupport.flattenResourceName(path))
      }
  }

  describe("Template replacement") {

    val Params = List(
      "simple"                                      -> "42",
      "hyphenated-word"                             -> "43"
    )
    val TestCases = List(
      "Thank you {$simple}."                        -> "Thank you 42.",
      "Thank you {$hyphenated-word}."               -> "Thank you 43.",
      "Thank you {$simple} and {$hyphenated-word}." -> "Thank you 42 and 43.",
      "{$simple} div { color: red }"                -> "42 div { color: red }",
      "{$missing}"                                  -> "{$missing}"
    )

    for ((template, expected) <- TestCases)
      it(s"must replace template to `$expected`") {
        assert(
          expected === ProcessTemplateSupport.processTemplateWithNames(template, Params)
        )
      }
  }
}
