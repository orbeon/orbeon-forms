/**
 * Copyright (C) 2013 Orbeon, Inc.
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
package org.orbeon.oxf.fb

import org.orbeon.oxf.fr.FormRunner._
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.NodeConversions._
import org.orbeon.scaxon.SimplePath._
import org.scalatest.funspec.AnyFunSpec

class FormBuilderPermissionsTest extends AnyFunSpec {

  describe("The `formBuilderPermissions()` function") {

    val frRoles: NodeInfo =
      <roles>
        <role name="*"                  app="app-always" form="form-always"/>
        <role name="all-forms-role"     app="*"          form="*"/>
        <role name="all-foo-forms-role" app="foo"        form="*"/>
        <role name="foo-baz-role"       app="foo"        form="baz"/>
        <role name="bar-baz-role"       app="bar"        form="baz"/>
        <role name="bar-baz2-role"      app="bar"        form="baz2"/>
      </roles>

    val frRolesOpt = Some(frRoles)

    val always = Map("app-always" -> Set("form-always"))

    it ("must always return app/form which matches all roles") {
      assert(formBuilderPermissions(frRolesOpt, Set("some", "other"))                         === always)
      assert(formBuilderPermissions(frRolesOpt, Set("all-foo-forms-role"))                    === always + ("foo" -> Set("*")))
      assert(formBuilderPermissions(frRolesOpt, Set("bar-baz-role"))                          === always + ("bar" -> Set("baz")))
      assert(formBuilderPermissions(frRolesOpt, Set("all-foo-forms-role", "bar-baz-role"))    === always + ("foo" -> Set("*")) + ("bar" -> Set("baz")))
    }

    it ("must return `*` with role which matches all app/form names") {
      val all = Map("*" -> Set("*"))

      assert(formBuilderPermissions(frRolesOpt, Set("all-forms-role"))                        === all)
      assert(formBuilderPermissions(frRolesOpt, Set("all-forms-role", "some", "other"))       === all)
      assert(formBuilderPermissions(frRolesOpt, Set("all-forms-role", "all-foo-forms-role"))  === all)
      assert(formBuilderPermissions(frRolesOpt, Set("all-forms-role", "bar-baz-role"))        === all)
    }

    it ("must combine roles with wildcard and specific app") {
      assert(formBuilderPermissions(frRolesOpt, Set("all-foo-forms-role", "foo-baz-role"))    === always + ("foo" -> Set("*")))
    }

    it ("must combine app/form names") {
      assert(formBuilderPermissions(frRolesOpt, Set("foo-baz-role", "bar-baz-role"))          === always + ("foo" -> Set("baz")) + ("bar" -> Set("baz")))
    }

    it ("must combine form names with same app name") {
      assert(formBuilderPermissions(frRolesOpt, Set("bar-baz-role", "bar-baz2-role"))         === always + ("bar" -> Set("baz", "baz2")))
    }

    it ("must return an empty result when empty roles are passed") {
      val emptyRoles = Some(<roles/>: NodeInfo)
      assert(formBuilderPermissions(emptyRoles, Set("some")) === Map())
    }
  }

  describe("Issue #1963/#2737 'FB user sees all forms in summary page if role is not found'") {

     val frRoles: NodeInfo =
      <roles>
        <role name="orbeon-sales" app="sales" form="*"/>
        <role name="dummy"        app="*"     form="*"/>
      </roles>

    val frRolesOpt = Some(frRoles)

    describe("The `formBuilderPermissions()` function") {
      it ("must return an empty result when no role matches the configured roles") {
        assert(formBuilderPermissions(frRolesOpt, Set("foo-role", "bar-role"))              === Map.empty)
      }
    }

    describe("The `formBuilderPermissionsForCurrentUserAsXML()` function") {
      it ("must report that roles are configured in this case") {
        assert((formBuilderPermissionsForCurrentUserAsXML(frRolesOpt, Set.empty) attValue "has-roles") === "true")
      }
    }
  }
}
