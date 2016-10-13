/**
 * Copyright (C) 2016 Orbeon, Inc.
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
package org.orbeon.oxf.fr

import org.orbeon.oxf.fr.permission.PermissionsAuthorization._
import org.orbeon.oxf.fr.permission._
import org.orbeon.oxf.fr.persistence.relational.crud.Organization
import org.scalatest.FunSpec

class FormRunnerPermissionsTest extends FunSpec {

  val guest = CurrentUser(
      username     = Some("juser"),
      groupname    = None,
      organization = None,
      roles        = Nil
    )
  val juser    = guest.copy(username = Some("juser"))
  val jmanager = guest.copy(username = Some("jmanager"))

  describe("The `authorizedOperationsBasedOnRoles()` function") {

    it("Returns all operations if the form has no permissions") {
      for (userRoles ← List(Nil, List(SimpleRole("clerk")))) {
        val user = juser.copy(roles = userRoles)
        val ops  = authorizedOperations(
          UndefinedPermissions,
          user,
          CheckWithoutDataUser(optimistic = false)
        )
        assert(ops === List("*"))

      }
    }

    describe("With the 'clerk can read' permission") {
      val clerkCanRead = DefinedPermissions(List(
        Permission(List(RolesAnyOf(List("clerk"))), List("read")))
      )
      it("Allows clerk to read") {
        val ops = authorizedOperations(
          clerkCanRead,
          juser.copy(roles = List(SimpleRole("clerk" ))),
          CheckWithoutDataUser(optimistic = false)
        )
        assert(ops === List("read"))
      }
      it("Prevents a user with another role from reading") {
        val ops = authorizedOperations(
          clerkCanRead,
          juser.copy(roles = List(SimpleRole("other" ))),
          CheckWithoutDataUser(optimistic = false)
        )
        assert(ops === Nil)
      }
    }
  }

  describe("The `allAuthorizedOperations()` function (based on data)") {

    // Pretty typical permissions defined in the form
    val formPermissions = DefinedPermissions(List(
      Permission(Nil                              , List("create")),
      Permission(List(Owner)                      , List("read", "update")),
      Permission(List(RolesAnyOf(List("clerk")))  , List("read")),
      Permission(List(RolesAnyOf(List("manager"))), List("read", "update"))
    ))

    val guestOperations = List("create")
    val fullOperations  = List("create", "read", "update")

    it("Lets anonymous users only create") {
      val ops = authorizedOperations(
        formPermissions,
        juser,
        CheckWithDataUser(
          username     = None,
          groupname    = None,
          organization = None
        )
      )
      assert(ops === guestOperations)
    }

    it("Lets owners access their data") {
      val ops = authorizedOperations(
        formPermissions,
        juser,
        CheckWithDataUser(
          username     = Some("juser"),
          groupname    = None,
          organization = None
        )
      )
      assert(ops === fullOperations)
    }

    describe("Organization-based permissions") {

      val dataUser = CheckWithDataUser(
        username     = Some("juser"),
        groupname    = None,
        organization = Some(Organization(List("a", "b", "c")))
      )

      val checks = List(
        "Lets direct manager access the data"                → ParametrizedRole("manager", "c") → fullOperations,
        "Lets manager of manager access the data"            → ParametrizedRole("manager", "b") → fullOperations,
        "Prevents unrelated manager from accessing the data" → ParametrizedRole("manager", "d") → guestOperations
      )

      for (((specText, roles), operations) ← checks) {
        it(specText) {
          val ops = authorizedOperations(
            formPermissions,
            jmanager.copy(roles = List(roles)),
            dataUser
          )
          assert(ops === operations)
        }
      }
    }
  }
}
