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

import org.orbeon.oxf.externalcontext.{Credentials, Organization, ParametrizedRole, SimpleRole, UserAndGroup}
import org.orbeon.oxf.fr.permission.Operation.{Create, Read, Update}
import org.orbeon.oxf.fr.permission.PermissionsAuthorization._
import org.orbeon.oxf.fr.permission._
import org.scalatest.funspec.AnyFunSpec


class FormRunnerPermissionsTest extends AnyFunSpec {

  val guest =
    Credentials(
      userAndGroup  = UserAndGroup("juser", None),
      roles         = Nil,
      organizations = Nil
    )

  val juser    = guest.copy(userAndGroup = guest.userAndGroup.copy(username = "juser"))
  val jmanager = guest.copy(userAndGroup = guest.userAndGroup.copy(username = "jmanager"))

  val clerkPermissions = DefinedPermissions(List(
    Permission(List(RolesAnyOf(List("clerk"))), SpecificOperations(Set(Read)))
  ))
  val clerkAndManagerPermissions = DefinedPermissions(List(
    Permission(Nil                              , SpecificOperations(Set(Create))),
    Permission(List(Owner)                      , SpecificOperations(Set(Read, Update))),
    Permission(List(RolesAnyOf(List("clerk")))  , SpecificOperations(Set(Read))),
    Permission(List(RolesAnyOf(List("manager"))), SpecificOperations(Set(Read, Update)))
  ))

  describe("The `authorizedOperationsBasedOnRoles()` function") {

    it("Returns all operations if the form has no permissions") {
      for (userRoles <- List(Nil, List(SimpleRole("clerk")))) {
        val user = juser.copy(roles = userRoles)
        val ops  = authorizedOperations(
          UndefinedPermissions,
          Some(user),
          CheckWithoutDataUserPessimistic
        )
        assert(ops === SpecificOperations(Operations.AllSet))
      }
    }

    describe("With the 'clerk can read' permission") {
      it("allows clerk to read") {
        val ops = authorizedOperations(
          clerkPermissions,
          Some(juser.copy(roles = List(SimpleRole("clerk" )))),
          CheckWithoutDataUserPessimistic
        )
        assert(ops === SpecificOperations(Set(Read)))
      }
      it("prevents a user with another role from reading") {
        val ops = authorizedOperations(
          clerkPermissions,
          Some(juser.copy(roles = List(SimpleRole("other" )))),
          CheckWithoutDataUserPessimistic
        )
        assert(ops === Operations.None)
      }
    }
  }

  describe("The `authorizedOperations()` function") {

    val guestOperations = SpecificOperations(Set(Create))
    val fullOperations  = SpecificOperations(Set(Create, Read, Update))

    it("lets anonymous users only create") {
      val ops = authorizedOperations(
        clerkAndManagerPermissions,
        Some(juser),
        CheckWithDataUser(
          userAndGroup = None,
          organization = None
        )
      )
      assert(ops === guestOperations)
    }

    it("lets owners access their data") {
      val ops = authorizedOperations(
        clerkAndManagerPermissions,
        Some(juser),
        CheckWithDataUser(
          userAndGroup = Some(UserAndGroup("juser", None)),
          organization = None
        )
      )
      assert(ops === fullOperations)
    }

    describe("Organization-based permissions") {

      describe("With known user") {

        val dataUser = CheckWithDataUser(
          userAndGroup = Some(UserAndGroup("juser", None)),
          organization = Some(Organization(List("a", "b", "c")))
        )

        val checks = List(
          "lets direct manager access the data"                -> ParametrizedRole("manager", "c") -> fullOperations,
          "lets manager of manager access the data"            -> ParametrizedRole("manager", "b") -> fullOperations,
          "prevents unrelated manager from accessing the data" -> ParametrizedRole("manager", "d") -> guestOperations
        )

        for (((specText, roles), operations) <- checks) {
          it(specText) {
            val ops = authorizedOperations(
              clerkAndManagerPermissions,
              Some(jmanager.copy(roles = List(roles))),
              dataUser
            )
            assert(ops === operations)
          }
        }
      }

      describe("Assuming organization match") {

        it("grants access to a manager, whatever she manages") {
          assert(
            authorizedOperations(
              permissions = clerkAndManagerPermissions,
              currentUser = Some(jmanager.copy(roles = List(ParametrizedRole("manager", "x")))),
              CheckAssumingOrganizationMatch
            ) === fullOperations
          )
        }
        it("doesn't grant access to a manager if the permissions don't grant any access to manager") {
          assert(
            authorizedOperations(
              permissions = clerkPermissions,
              currentUser = Some(jmanager.copy(roles = List(ParametrizedRole("manager", "x")))),
              CheckAssumingOrganizationMatch
            ) === Operations.None
          )
        }
        it("doesn't grant access to a user with a parametrized role other than manager") {
          assert(
            authorizedOperations(
              permissions = clerkPermissions,
              currentUser = Some(jmanager.copy(roles = List(ParametrizedRole("chief", "x")))),
              CheckAssumingOrganizationMatch
            ) === Operations.None
          )
        }
      }
    }
  }
}
