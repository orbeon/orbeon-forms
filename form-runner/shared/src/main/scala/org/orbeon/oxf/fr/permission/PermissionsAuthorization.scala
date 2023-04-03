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
package org.orbeon.oxf.fr.permission

import org.orbeon.oxf.externalcontext._
import org.orbeon.oxf.http.{HttpStatusCodeException, StatusCode}
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.Logging.debug
import org.orbeon.oxf.util.{CoreCrossPlatformSupport, IndentedLogger}


object PermissionsAuthorization {

  // NOTE: `tiff` and `test-pdf` are reduced to `pdf` at the XForms level, but not at the XSLT level. We don't
  // yet expose this to XSLT, but we might in the future, so check on those modes as well.
  // 2021-12-22: `schema` could be a readonly mode, but we consider this special as it is protected as a service.
  val CreationModes  = Set("new", "import", "validate")
  val EditingModes   = Set("edit")
  val ReadonlyModes  = Set("view", "pdf", "email", "controls", "tiff", "test-pdf", "export", "excel-export") // `excel-export` is legacy
  val AllDetailModes = CreationModes ++ EditingModes ++ ReadonlyModes + "schema" + "test"

  def findCurrentCredentialsFromSession: Option[Credentials] =
    CoreCrossPlatformSupport.externalContext.getRequest.credentials

  sealed trait PermissionsCheck
  case class CheckWithDataUser(
    userAndGroup : Option[UserAndGroup],
    organization : Option[Organization]
  )                                           extends PermissionsCheck
  case object CheckWithoutDataUserPessimistic extends PermissionsCheck
  case object CheckAssumingOrganizationMatch  extends PermissionsCheck

  def hasPermissionCond(permissions: Permissions, condition: Condition, anyOfOperations: Set[Operation]): Boolean =
    permissions match {
      case UndefinedPermissions => true
      case DefinedPermissions(permissionsList) =>
        permissionsList.exists { permission =>
          permission.conditions.contains(condition) &&
            Operations.allowsAny(permission.operations, anyOfOperations)
        }
    }

  def authorizedOperations(
    permissions          : Permissions,
    currentCredentialsOpt: Option[Credentials],
    check                : PermissionsCheck
  ): Operations =
    permissions match {
      case DefinedPermissions(permissionsList) =>
        Operations.combine(permissionsList.map(authorizedOperationsForPermission(_, currentCredentialsOpt, check)))
      case UndefinedPermissions =>
        AnyOperation
    }

  def authorizedBasedOnRole(
    formPermissions: Permissions,
    credentialsOpt : Option[Credentials],
    anyOfOperations: Set[Operation],
    optimistic     : Boolean
  ): Boolean = {

    val authorizedOperationsNotAssumingOwner =
      authorizedOperations(formPermissions, credentialsOpt, CheckWithoutDataUserPessimistic)

    val authorizedOps = {
      val checkAssumingOwner =
        optimistic &&
        credentialsOpt.isDefined &&
        Operations.allows(authorizedOperationsNotAssumingOwner, Operation.Create)
      if (checkAssumingOwner) {
        val userAndGroupOpt    = credentialsOpt.map(_.userAndGroup)
        val assumingOwnerCheck = CheckWithDataUser(userAndGroupOpt, None)
        authorizedOperations(formPermissions, credentialsOpt, assumingOwnerCheck)
      } else {
        authorizedOperationsNotAssumingOwner
      }
    }

    Operations.allowsAny(authorizedOps, anyOfOperations)
  }

  def authorizedOperationsForDetailModeOrThrow(
    mode                 : String,// TODO: use an ADT
    permissions          : Permissions,
    operationsFromDataOpt: Option[Operations],
    credentialsOpt       : Option[Credentials],
    isSubmit             : Boolean)(implicit
    logger               : IndentedLogger
  ): Operations = {

    // With `new`, the caller obviously doesn't read the data from the persistence layer first, therefore
    // `operationsFromDataOpt` must always be `None.
    require(mode != "new" || operationsFromDataOpt.isEmpty, "`operationsFromDataOpt` must be empty for mode `new`")

    val operationsOpt =
      operationsFromDataOpt match {
        case Some(operationsFromData) =>
          authorizedForModeOperationsFromData(
            mode,
            operationsFromData,
          )
        case None if CreationModes(mode) =>
          authorizedForModeNoData(
            mode           = mode, // could be normalized to `new`
            permissions    = permissions,
            credentialsOpt = credentialsOpt
          )
        case None =>
          // Case where we don't have tokens obtained from the persistence layer with the `Orbeon-Operations` header
          // AND we are not in `new` mode. This can happen in the following cases:
          //
          // - `isSubmit == false`:
          //   - the persistence layer didn't return a `Orbeon-Operations` with tokens, which implies it is a custom
          //     persistence layer
          // - `isSubmit == true`:
          //   - external `POST` of data to the `edit`/`view`/`pdf`/etc. page
          //   - internal `POST` of data to the `edit`/`view`/`pdf`/etc. page as result of a mode change
          authorizedForModeAndData(
            mode           = mode,
            permissions    = permissions,
            credentialsOpt = credentialsOpt,
            isSubmit       = isSubmit
          )
      }

    operationsOpt match {
      case None =>
        debug(s"UNAUTHORIZED USER")
        throw HttpStatusCodeException(StatusCode.Forbidden)
      case Some(ops) =>
        debug(s"AUTHORIZED OPERATIONS ON FORM (DETAIL MODES): ${Operations.serialize(ops, normalized = true)}")
        ops
    }
  }

  def authorizedForModeOperationsFromData(
    mode              : String,
    operationsFromData: Operations
  ): Option[Operations] =
    isUserAuthorizedBasedOnOperationsAndMode(operationsFromData, mode, isSubmit = false) option operationsFromData

  def authorizedForModeNoData(
    mode          : String, // 2023-03-09: creation and update modes only
    permissions   : Permissions,
    credentialsOpt: Option[Credentials]
  ): Option[Operations] = {

    val operations =
      authorizedOperationsForNoData(
        permissions,
        credentialsOpt
      )

    // `isSubmit` is not used when mode is `new`
    isUserAuthorizedBasedOnOperationsAndMode(operations, mode, isSubmit = false) option operations
  }

  def authorizedOperationsForNoData(
    permissions   : Permissions,
    credentialsOpt: Option[Credentials]
  ): Operations =
    permissions match {
      case UndefinedPermissions =>
        AnyOperation
      case defined @ DefinedPermissions(_) =>

        val operationsWithoutAssumingOwnership =
          authorizedOperations(defined, credentialsOpt, CheckWithoutDataUserPessimistic)

        def operationsAssumingOwnership: Operations = {

          def ownerGroupOperations(condition: Option[Condition]): List[Operations] =
            condition match {
              case Some(condition) => defined.permissionsList.filter(_.conditions.contains(condition)).map(_.operations)
              case None            => Nil
            }

          val ownerOperations       = ownerGroupOperations(credentialsOpt.isDefined option                                   Owner)
          val groupMemberOperations = ownerGroupOperations(credentialsOpt.flatMap(_.userAndGroup.groupname).isDefined option Group)

          Operations.combine(operationsWithoutAssumingOwnership :: ownerOperations ::: groupMemberOperations)
        }

        // If the user can't create data, don't return permissions the user might have if that user was the owner; we
        // assume that if the user can't create data, the user can never be the owner of any data.
        if (Operations.allows(operationsWithoutAssumingOwnership, Operation.Create))
          operationsAssumingOwnership
        else
          operationsWithoutAssumingOwnership
    }

  private def authorizedForModeAndData(
    mode           : String,
    permissions    : Permissions,
    credentialsOpt : Option[Credentials],
    isSubmit       : Boolean)(implicit
    logger         : IndentedLogger
  ): Option[Operations] = {

    val operations =
      authorizedOperations(
        permissions,
        credentialsOpt,
        CheckWithoutDataUserPessimistic
      )

    debug(s"operations obtained: `$operations`")

    isUserAuthorizedBasedOnOperationsAndMode(operations, mode, isSubmit) option operations
  }

  private def isUserAuthorizedBasedOnOperationsAndMode(operations: Operations, mode: String, isSubmit: Boolean): Boolean = {

    // Special cases:
    //
    // - `schema`: doesn't require any authorized permission as it is simply protected as a service
    // - `test`: doesn't require any authorized permission, see https://github.com/orbeon/orbeon-forms/issues/2050
    //
    // When `POST`ing data to the page, this is generally considered a "mode change" and doesn't require the
    // same permissions, especially since the POST is protected by other means. For example, a user with
    // `create` permission only is allowed to navigate to the `view` page and `edit` page back. This does not
    // imply that the user need `read` or `update` permissions. Because the current use case is that the user
    // has at least the `create` permission, we currently require that permission, although this restriction
    // could be removed in the future if need be.

    def unauthorizedCreation =
      CreationModes(mode) && ! Operations.allows(operations, Operation.Create)

    def unauthorizedEditing =
      EditingModes(mode) && ! (
        Operations.allows(operations, Operation.Update) ||
        isSubmit && Operations.allows(operations, Operation.Create)
      )

    def unauthorizedViewing =
      ReadonlyModes(mode) && ! (
        Operations.allows(operations, Operation.Read) ||
        isSubmit && Operations.allows(operations, Operation.Create)
      )

    def unauthorizedMode =
      ! AllDetailModes(mode)

    ! (
      unauthorizedCreation ||
      unauthorizedEditing  ||
      unauthorizedViewing  ||
      unauthorizedMode
    )
  }

  private val UpdateMatchingTokens = Set("*", "update")

  def autosaveAuthorizedForNew(
    permissions    : Permissions,
    credentialsOpt : Option[Credentials],
  ): Boolean =
    authorizedForModeNoData(
      mode           = "edit", // this is for the `new` mode but disallow autosave if users can't *edit* data
      permissions    = permissions,
      credentialsOpt = credentialsOpt
    ).map(Operations.serialize(_, normalized = true))
      .getOrElse(Nil)
      .exists(UpdateMatchingTokens)

  private def authorizedOperationsForPermission(
    permission           : Permission,
    currentCredentialsOpt: Option[Credentials],
    check                : PermissionsCheck
  ): Operations = {

    def allConditionsPass(check: PermissionsCheck): Boolean =
      permission.conditions.forall(conditionPasses(_, currentCredentialsOpt, check))

    // For `Create`, we can't have data, so check if the conditions would match on the data created by
    // the current user, if that user was allowed to create the data
    lazy val checkWithCurrentUser = CheckWithDataUser(
      userAndGroup = currentCredentialsOpt.map(_.userAndGroup),
      organization = currentCredentialsOpt.flatMap(_.defaultOrganization)
    )

    permission.operations match {
      case SpecificOperations(operations) =>
        SpecificOperations(operations.filter { operation =>
          val checkBasedOnOperation = check match {
            case CheckWithoutDataUserPessimistic
                 if operation == Operation.Create => checkWithCurrentUser
            case _                                => check
          }
          allConditionsPass(checkBasedOnOperation)
        })
      case AnyOperation =>
        if (allConditionsPass(check))
          AnyOperation
        else
          check match {
            case CheckWithoutDataUserPessimistic =>
              if (allConditionsPass(checkWithCurrentUser))
                SpecificOperations(Set(Operation.Create))
              else
                Operations.None
            case _ =>
              Operations.None
          }
    }
  }

  private def conditionPasses(
    condition         : Condition,
    currentCredentials: Option[Credentials],
    check             : PermissionsCheck
  ): Boolean =
    condition match {
      case Owner =>
        check match {
          case CheckWithDataUser(dataUserAndGroupOpt, _) =>
            (currentCredentials map (_.userAndGroup.username), dataUserAndGroupOpt.map(_.username)) match {
              case (Some(currentUsername), Some(dataUsername)) if currentUsername == dataUsername => true
              case _ => false
            }
          case CheckWithoutDataUserPessimistic => false
          case CheckAssumingOrganizationMatch  => false
        }
      case Group =>
        check match {
          case CheckWithDataUser(dataUserAndGroupOpt, _) =>
            (currentCredentials flatMap (_.userAndGroup.groupname), dataUserAndGroupOpt.flatMap(_.groupname)) match {
              case (Some(currentGroupname), Some(dataGroupname)) if currentGroupname == dataGroupname => true
              case _ => false
            }
          case CheckWithoutDataUserPessimistic => false
          case CheckAssumingOrganizationMatch  => false
        }
      case RolesAnyOf(permissionRoles) =>
        permissionRoles.exists(permissionRoleName =>
          currentCredentials.toList.flatMap(_.roles) exists {
            case SimpleRole(userRoleName) =>
              userRoleName == permissionRoleName
            case ParametrizedRole(userRoleName, userOrganizationName) =>
              userRoleName == permissionRoleName && (
                check match {
                  case CheckWithDataUser(_, dataOrganizationOpt) =>
                    dataOrganizationOpt.exists(_.levels.contains(userOrganizationName))
                  case CheckWithoutDataUserPessimistic => false
                  case CheckAssumingOrganizationMatch  => true
                }
              )
          }
        )
    }
}
