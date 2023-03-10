package org.orbeon.oxf.fr.permission

import cats.syntax.option._
import org.orbeon.oxf.externalcontext._
import org.orbeon.oxf.fr.permission.Operation.{Create, Read, Update}
import org.orbeon.oxf.util.{IndentedLogger, LoggerFactory}
import org.scalatest.funspec.AnyFunSpec

import scala.util.Try


class PermissionsAuthorizationTest extends AnyFunSpec {

  private implicit val Logger =
    new IndentedLogger(LoggerFactory.createLogger(classOf[PermissionsAuthorizationTest]), true)

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

  describe("The `authorizedOperations()` function") {

    it("Returns all operations if the form has no permissions") {
      for (userRoles <- List(Nil, List(SimpleRole("clerk")))) {
        val user = juser.copy(roles = userRoles)
        val ops  = PermissionsAuthorization.authorizedOperations(
          UndefinedPermissions,
          Some(user),
          PermissionsAuthorization.CheckWithoutDataUserPessimistic
        )
        assert(ops == AnyOperation)
      }
    }

    describe("With the 'clerk can read' permission") {
      it("allows clerk to read") {
        val ops = PermissionsAuthorization.authorizedOperations(
          clerkPermissions,
          Some(juser.copy(roles = List(SimpleRole("clerk" )))),
          PermissionsAuthorization.CheckWithoutDataUserPessimistic
        )
        assert(ops == SpecificOperations(Set(Read)))
      }
      it("prevents a user with another role from reading") {
        val ops = PermissionsAuthorization.authorizedOperations(
          clerkPermissions,
          Some(juser.copy(roles = List(SimpleRole("other" )))),
          PermissionsAuthorization.CheckWithoutDataUserPessimistic
        )
        assert(ops == Operations.None)
      }
    }

    val guestOperations = SpecificOperations(Set(Create))
    val fullOperations  = SpecificOperations(Set(Create, Read, Update))

    it("lets anonymous users only create") {
      val ops = PermissionsAuthorization.authorizedOperations(
        clerkAndManagerPermissions,
        Some(juser),
        PermissionsAuthorization.CheckWithDataUser(
          userAndGroup = None,
          organization = None
        )
      )
      assert(ops == guestOperations)
    }

    it("lets owners access their data") {
      val ops = PermissionsAuthorization.authorizedOperations(
        clerkAndManagerPermissions,
        Some(juser),
        PermissionsAuthorization.CheckWithDataUser(
          userAndGroup = Some(UserAndGroup("juser", None)),
          organization = None
        )
      )
      assert(ops == fullOperations)
    }

    describe("Organization-based permissions") {

      describe("With known user") {

        val dataUser = PermissionsAuthorization.CheckWithDataUser(
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
            val ops = PermissionsAuthorization.authorizedOperations(
              clerkAndManagerPermissions,
              Some(jmanager.copy(roles = List(roles))),
              dataUser
            )
            assert(ops == operations)
          }
        }
      }

      describe("Assuming organization match") {

        it("grants access to a manager, whatever she manages") {
          assert(
            PermissionsAuthorization.authorizedOperations(
              permissions           = clerkAndManagerPermissions,
              currentCredentialsOpt = Some(jmanager.copy(roles = List(ParametrizedRole("manager", "x")))),
              PermissionsAuthorization.CheckAssumingOrganizationMatch
            ) == fullOperations
          )
        }
        it("doesn't grant access to a manager if the permissions don't grant any access to manager") {
          assert(
            PermissionsAuthorization.authorizedOperations(
              permissions           = clerkPermissions,
              currentCredentialsOpt = Some(jmanager.copy(roles = List(ParametrizedRole("manager", "x")))),
              PermissionsAuthorization.CheckAssumingOrganizationMatch
            ) == Operations.None
          )
        }
        it("doesn't grant access to a user with a parametrized role other than manager") {
          assert(
            PermissionsAuthorization.authorizedOperations(
              permissions           = clerkPermissions,
              currentCredentialsOpt = Some(jmanager.copy(roles = List(ParametrizedRole("chief", "x")))),
              PermissionsAuthorization.CheckAssumingOrganizationMatch
            ) == Operations.None
          )
        }
      }
    }
  }

  describe("Authorization for detail page") {

    describe("using operations from data") {
      val varyOperations = List(
        AnyOperation,
        SpecificOperations(Set(Operation.Read)),
        SpecificOperations(Set(Operation.Create, Operation.Read, Operation.Update)),
      )

      val varyPermissions = List(
        UndefinedPermissions,
        DefinedPermissions(Nil),
        DefinedPermissions(
          List(Permission(Nil, SpecificOperations(Set(Operation.Read))))
        )
      )

      val varyCredentials = List(
        None,
        Credentials(UserAndGroup("dewey", None), Nil, Nil).some,
        Credentials(UserAndGroup("dewey", "orbeon-users".some), Nil, Nil).some,
        Credentials(UserAndGroup("dewey", "employee".some), List(SimpleRole("orbeon-user")), Nil).some,
      )

      for {
        operationsFromData <- varyOperations
        permissions        <- varyPermissions
        credentialsOpt     <- varyCredentials
      } locally {
        it(s"must use operations from data for`$operationsFromData`, permissions = `$permissions`, credentials = `$credentialsOpt`") {
          assert(
            Try(
              PermissionsAuthorization.authorizedOperationsForDetailModeOrThrow(
                mode                  = "view",
                permissions           = permissions,
                operationsFromDataOpt = operationsFromData.some,
                credentialsOpt        = credentialsOpt,
                isSubmit              = false
              )
            ).toOption == operationsFromData.some
          )
        }
      }
    }

    describe("`new` mode with permissions but no credentials") {

      val varyAdditionalOperations = List[Set[Operation]](
        Set.empty,
        Set(Operation.Read),
        Set(Operation.Update),
        Set(Operation.Delete),
        Set(Operation.List),
        Set(Operation.Read, Operation.Update),
        Set(Operation.Read, Operation.Delete),
        Set(Operation.Read, Operation.List),
        Set(Operation.Update, Operation.Delete),
        Set(Operation.Update, Operation.List),
        Set(Operation.Delete, Operation.List),
      )

      for {
        additionalOperations <- varyAdditionalOperations
        specificOperations   = SpecificOperations(Set(Operation.Create) ++ additionalOperations)
        definedPermissions   = DefinedPermissions(List(Permission(Nil, specificOperations)))
      } locally {
        it(s"must pass with `new` mode for permissions including `Create`: $additionalOperations") {
          assert(
            Try(
              PermissionsAuthorization.authorizedOperationsForDetailModeOrThrow(
                mode                  = "new",
                permissions           = definedPermissions,
                operationsFromDataOpt = None,
                credentialsOpt        = None,
                isSubmit              = false
              )
            ).toOption.contains(specificOperations)
          )
        }
      }

      for {
        operations         <- varyAdditionalOperations
        specificOperations = SpecificOperations(operations)
        definedPermissions = DefinedPermissions(List(Permission(Nil, specificOperations)))
      } locally {
        it(s"must pass with `new` mode for permissions not including `Create`: $operations") {
          assert(
            Try(
              PermissionsAuthorization.authorizedOperationsForDetailModeOrThrow(
                mode                  = "new",
                permissions           = definedPermissions,
                operationsFromDataOpt = None,
                credentialsOpt        = None,
                isSubmit              = false
              )
            ).isFailure
          )
        }
      }
    }

    describe("`new` mode other tests") {
      val otherExpectedCases = List(
        ("new", UndefinedPermissions, None, None)                                    -> AnyOperation.some,
        ("new", DefinedPermissions(List(Permission(Nil, AnyOperation))), None, None) -> AnyOperation.some,
        // Disallowed use of `operationsFromDataOpt` for `new` mode
        ("new", UndefinedPermissions, AnyOperation.some, None)                       -> None,
      )

      for ((t @ (mode, permissions, operationsFromDataOpt, credentialsOpt), expected) <- otherExpectedCases) {
        it(s"must pass for $t") {
          assert(
            Try(
              PermissionsAuthorization.authorizedOperationsForDetailModeOrThrow(
                mode                  = mode,
                permissions           = permissions,
                operationsFromDataOpt = operationsFromDataOpt,
                credentialsOpt        = credentialsOpt,
                isSubmit              = false
              )
            ).toOption == expected
          )
        }
      }
    }

    describe("The autosave check") {

      // User must be logged in as precondition
      val credentials = Credentials(UserAndGroup("dewey", "employee".some), List(SimpleRole("orbeon-user")), Nil)

      val permissionsWithResults = List(
        UndefinedPermissions                                                                 -> true,
        DefinedPermissions(List(Permission(Nil, SpecificOperations(Set(Operation.Update))))) -> true,
        DefinedPermissions(List(Permission(Nil, SpecificOperations(Set(Operation.Read)))))   -> false,
        DefinedPermissions(
          List(
            Permission(Nil,         SpecificOperations(Set(Operation.Create))),
            Permission(List(Owner), SpecificOperations(Set(Operation.Update))),
          )
        ) -> true,
        // See comment in `PermissionsAuthorization`: "If the user can't create data, don't return permissions the user
        // might have if that user was the owner; we assume that if the user can't create data, the user can never be
        // the owner of any data."
        DefinedPermissions(
          List(
            Permission(List(Owner), SpecificOperations(Set(Operation.Update))),
          )
        ) -> false,

      )

      for {
        (permissions, result) <- permissionsWithResults
      } locally {
        it(s"must ${if (result) "" else "not "}authorize for `$permissions`") {
          assert(
            PermissionsAuthorization.autosaveAuthorizedForNew(
              permissions    = permissions,
              credentialsOpt = credentials.some,
            ) == result
          )
        }
      }
    }
  }
}
