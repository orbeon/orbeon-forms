package org.orbeon.oxf.fr.permission

import cats.syntax.option.*
import org.orbeon.oxf.externalcontext.*
import org.orbeon.oxf.fr.permission.Operation.{Create, Read, Update}
import org.orbeon.oxf.util.{IndentedLogger, LoggerFactory}
import org.scalatest.funspec.AnyFunSpec

import scala.util.Try


class PermissionsAuthorizationTest extends AnyFunSpec {

  private implicit val Logger: IndentedLogger =
    new IndentedLogger(LoggerFactory.createLogger(classOf[PermissionsAuthorizationTest]), true)

  val guest =
    Credentials(
      userAndGroup  = UserAndGroup("juser", None),
      roles         = Nil,
      organizations = Nil
    )

  val juser    = guest.copy(userAndGroup = guest.userAndGroup.copy(username = "juser"))
  val jmanager = guest.copy(userAndGroup = guest.userAndGroup.copy(username = "jmanager"))

  val clerkPermissions = Permissions.Defined(List(
    Permission(List(Condition.RolesAnyOf(List("clerk"))), SpecificOperations(Set(Read)))
  ))

  val clerkAndManagerPermissions = Permissions.Defined(List(
    Permission(Nil                              , SpecificOperations(Set(Create))),
    Permission(List(Condition.Owner)                      , SpecificOperations(Set(Read, Update))),
    Permission(List(Condition.RolesAnyOf(List("clerk")))  , SpecificOperations(Set(Read))),
    Permission(List(Condition.RolesAnyOf(List("manager"))), SpecificOperations(Set(Read, Update)))
  ))

  describe("The `authorizedOperations()` function") {

    it("Returns all operations if the form has no permissions") {
      for (userRoles <- List(Nil, List(SimpleRole("clerk")))) {
        val user = juser.copy(roles = userRoles)
        val ops  = PermissionsAuthorization.authorizedOperations(
          Permissions.Undefined,
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

    describe("using operations from data in `Readonly` mode") {

      val NoOperation                = Operations.None
      val ReadOperation              = SpecificOperations(Set(Operation.Read))
      val CreateOperation            = SpecificOperations(Set(Operation.Create))
      val ReadUpdateOperation        = SpecificOperations(Set(Operation.Read, Operation.Update))
      val CreateReadUpdateOperation  = SpecificOperations(Set(Operation.Create, Operation.Read, Operation.Update))

      val DefinedButEmptyPermissions = Permissions.Defined(Nil)
      val AnyoneCanReadPermissions   = Permissions.Defined(List(Permission(Nil, ReadOperation)))
      val RoleCanReadPermissions     = Permissions.Defined(List(Permission(List(Condition.RolesAnyOf(List("orbeon-user"))), ReadOperation)))
      val RoleCanUpdatePermissions   = Permissions.Defined(List(Permission(List(Condition.RolesAnyOf(List("orbeon-user"))), SpecificOperations(Set(Operation.Update)))))

      val NoCredentials              = None
      val UserOnlyCredentials        = Credentials(UserAndGroup("dewey", None), Nil, Nil).some
      val UserAndGroupCredentials    = Credentials(UserAndGroup("dewey", "employee".some), Nil, Nil).some
      val UserAndRolesCredentials    = Credentials(UserAndGroup("dewey", "employee".some), List(SimpleRole("orbeon-user")), Nil).some

      val VaryOperations = List(
        NoOperation,
        AnyOperation,
        CreateOperation,
        ReadOperation,
        ReadUpdateOperation,
        CreateReadUpdateOperation
      )

      val VaryPermissions = List(
        Permissions.Undefined,
        DefinedButEmptyPermissions,
        AnyoneCanReadPermissions,
        RoleCanReadPermissions,
        RoleCanUpdatePermissions
      )

      val VaryCredentials = List(
        NoCredentials,
        UserOnlyCredentials,
        UserAndGroupCredentials,
        UserAndRolesCredentials
      )

      val expected =
        for {
          operations  <- VaryOperations
          permissions <- VaryPermissions
          credentials <- VaryCredentials
        } yield
          (operations, permissions, credentials) -> {
            if (! Operations.allows(operations, Operation.Read)) // since we are in `Readonly` mode
              None
            else
              operations.some
        }

      for {
        ((operationsFromData, permissions, credentialsOpt), resultOpt) <- expected
      } locally {
        it(s"must use operations from data for `$operationsFromData`, permissions = `$permissions`, credentials = `$credentialsOpt`") {
          assert(
            Try(
              PermissionsAuthorization.authorizedOperationsForDetailModeOrThrow(
                modeTypeAndOps        = ModeTypeAndOps.Other(ModeType.Readonly, operationsFromData),
                permissions           = permissions,
                credentialsOpt        = credentialsOpt,
                isSubmit              = false
              )
            ).toOption == resultOpt
          )
        }
      }
    }

    describe("`Creation` mode with permissions but no credentials") {

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
        definedPermissions   = Permissions.Defined(List(Permission(Nil, specificOperations)))
      } locally {
        it(s"must pass with `new` mode for permissions including `Create`: $additionalOperations") {
          assert(
            Try(
              PermissionsAuthorization.authorizedOperationsForDetailModeOrThrow(
                modeTypeAndOps        = ModeTypeAndOps.Creation,
                permissions           = definedPermissions,
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
        definedPermissions = Permissions.Defined(List(Permission(Nil, specificOperations)))
      } locally {
        it(s"must pass with `new` mode for permissions not including `Create`: $operations") {
          assert(
            Try(
              PermissionsAuthorization.authorizedOperationsForDetailModeOrThrow(
                modeTypeAndOps        = ModeTypeAndOps.Creation,
                permissions           = definedPermissions,
                credentialsOpt        = None,
                isSubmit              = false
              )
            ).isFailure
          )
        }
      }
    }

    describe("The autosave check") {

      // User must be logged in as precondition
      val credentials = Credentials(UserAndGroup("dewey", "employee".some), List(SimpleRole("orbeon-user")), Nil)

      val permissionsWithResults = List(
        Permissions.Undefined                                                                 -> true,
        Permissions.Defined(List(Permission(Nil, SpecificOperations(Set(Operation.Update))))) -> true,
        Permissions.Defined(List(Permission(Nil, SpecificOperations(Set(Operation.Read)))))   -> false,
        Permissions.Defined(
          List(
            Permission(Nil,                   SpecificOperations(Set(Operation.Create))),
            Permission(List(Condition.Owner), SpecificOperations(Set(Operation.Update))),
          )
        ) -> true,
        // See comment in `PermissionsAuthorization`: "If the user can't create data, don't return permissions the user
        // might have if that user was the owner; we assume that if the user can't create data, the user can never be
        // the owner of any data."
        Permissions.Defined(
          List(
            Permission(List(Condition.Owner), SpecificOperations(Set(Operation.Update))),
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

  describe("#7089: The `authorizedOperationsForSummary()` function") {

    val permissions =
      Permissions.Defined(
        List(
          Permission(
            List(
              Condition.RolesAnyOf(
                List("clerk")
              )
            ),
            SpecificOperations(Set(Operation.Create, Operation.List))
          )
        )
      )

    val credentialsWithParametrizedRole =
      Credentials(
        userAndGroup  = UserAndGroup("juser", None),
        roles         = List(ParametrizedRole("clerk", "456")),
        organizations = List(Organization(List("123", "456")))
      )

    it("must return the `list` operation if the user has it through an organization role") {
      assert(
        SpecificOperations(Set(Operation.Create, Operation.List)) ==
          PermissionsAuthorization.authorizedOperationsForSummary(
            permissions           = permissions,
            currentCredentialsOpt = credentialsWithParametrizedRole.some
          )
      )
    }

    val credentialsWithSimpleRole =
      Credentials(
        userAndGroup  = UserAndGroup("juser", None),
        roles         = List(SimpleRole("clerk")),
        organizations = Nil
      )

    it("must return the `list` operation if the user has it through a simple role") {
      assert(
        SpecificOperations(Set(Operation.Create, Operation.List)) ==
          PermissionsAuthorization.authorizedOperationsForSummary(
            permissions           = permissions,
            currentCredentialsOpt = credentialsWithSimpleRole.some
          )
      )
    }

    val credentialsWithoutParametrizedRole =
      Credentials(
        userAndGroup  = UserAndGroup("juser", None),
        roles         = List(ParametrizedRole("other-role", "456")),
        organizations = List(Organization(List("123", "456")))
      )

    it("must not return not the `list` operation if the user doesn't have the role") {
      assert(
        SpecificOperations(Set.empty) ==
          PermissionsAuthorization.authorizedOperationsForSummary(
            permissions           = permissions,
            currentCredentialsOpt = credentialsWithoutParametrizedRole.some
          )
      )
    }
  }
}
