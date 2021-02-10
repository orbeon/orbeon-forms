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

import org.orbeon.saxon.function.ProcessTemplateSupport
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.NodeConversions._
import org.orbeon.scaxon.SimplePath._
import org.scalatest.funspec.AnyFunSpec


class XXFormsResourceTest extends AnyFunSpec {

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

    // NOTE: We don't want to support Java's `MessageFormat` `choice` anymore, see
    // https://github.com/orbeon/orbeon-forms/issues/3078.
    val PublishTemplate =
      """Version {
        $form-version
      } with {
        $attachments,choice,0#no attachments|1#1 attachment|1<{$attachments} attachments}."""

    val Expected = List(
      (
        PublishTemplate,
        List("form-version" -> 42, "attachments" -> 0)
      ) -> """Version 42 with no attachments.""",
      (
        PublishTemplate,
        List("form-version" -> 42, "attachments" -> 1)
      ) -> """Version 42 with 1 attachment.""",
      (
        PublishTemplate,
        List("form-version" -> 42, "attachments" -> 3)
      ) -> """Version 42 with 3 attachments."""
    )

    for (((template, params), expected) <- Expected)
      it(s"must replace template to `$expected`") {
        assert(
          expected === ProcessTemplateSupport.processTemplateWithNames(template, params)
        )
      }
  }
}
