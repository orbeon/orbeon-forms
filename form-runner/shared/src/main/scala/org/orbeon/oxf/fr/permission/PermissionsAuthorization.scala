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
import org.orbeon.oxf.fr.FormRunnerAccessToken
import org.orbeon.oxf.http.{HttpStatusCodeException, StatusCode}
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.Logging.debug
import org.orbeon.oxf.util.{CoreCrossPlatformSupport, IndentedLogger}

import scala.util.Try


// TODO: move to more general location
sealed trait ModeType
object ModeType {

  case object Creation extends ModeType
  case object Edition  extends ModeType
  case object Readonly extends ModeType

  // NOTE: `tiff` and `test-pdf` are reduced to `pdf` at the XForms level, but not at the XSLT level. We don't
  // yet expose this to XSLT, but we might in the future, so check on those modes as well.
  // 2021-12-22: `schema` could be a readonly mode, but we consider this special as it is protected as a service.
  val CreationModes  = Set("new", "import", "validate", "test")
  val EditionModes   = Set("edit")
  val ReadonlyModes  = Set("view", "pdf", "email", "controls", "tiff", "test-pdf", "export", "excel-export", "schema") // `excel-export` is legacy

  def unapply(modeString: String): Option[ModeType] =
    if (CreationModes(modeString))
      Some(Creation)
    else if (EditionModes(modeString))
      Some(Edition)
    else if (ReadonlyModes(modeString))
      Some(Readonly)
    else
      None
}

sealed trait ModeTypeAndOps { val modeType: ModeType }
object ModeTypeAndOps {
  case object Creation                                   extends ModeTypeAndOps { val modeType = ModeType.Creation }
  case class  Other(modeType: ModeType, ops: Operations) extends ModeTypeAndOps
}

object PermissionsAuthorization {

  def findCurrentCredentialsFromSession: Option[Credentials] =
    CoreCrossPlatformSupport.externalContext.getRequest.credentials

  // TODO: move definitions to Permissions.scala
  sealed trait PermissionsCheck
  case class CheckWithDataUser(
    userAndGroup : Option[UserAndGroup],
    organization : Option[Organization]
  )                                           extends PermissionsCheck
  case object CheckWithoutDataUserPessimistic extends PermissionsCheck
  case object CheckAssumingOrganizationMatch  extends PermissionsCheck

  def hasPermissionCond(permissions: Permissions, condition: Condition, anyOfOperations: Set[Operation]): Boolean =
    permissions match {
      case Permissions.Undefined => true
      case Permissions.Defined(permissionsList) =>
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
      case Permissions.Defined(permissionsList) =>
        Operations.combine(permissionsList.map(authorizedOperationsForPermission(_, currentCredentialsOpt, check)))
      case Permissions.Undefined =>
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
    modeTypeAndOps       : ModeTypeAndOps,
    permissions          : Permissions,
    credentialsOpt       : Option[Credentials],
    isSubmit             : Boolean
  )(implicit
    logger               : IndentedLogger
  ): Operations = {

    val resultingAuthorizedOperations =
      modeTypeAndOps match {
        case ModeTypeAndOps.Creation =>
          authorizedOperationsForNoData(
            permissions,
            credentialsOpt
          )
        case ModeTypeAndOps.Other(_, ops) =>
          ops
      }

    isUserAuthorizedBasedOnOperationsAndMode(
      operations = resultingAuthorizedOperations,
      modeType   = modeTypeAndOps.modeType,
      isSubmit   = modeTypeAndOps.modeType != ModeType.Creation && isSubmit
    ) option resultingAuthorizedOperations match {
      case None =>
        debug(s"UNAUTHORIZED USER")
        throw HttpStatusCodeException(StatusCode.Forbidden)
      case Some(ops) =>
        debug(s"AUTHORIZED OPERATIONS ON FORM (DETAIL MODES): ${Operations.serialize(ops, normalized = true)}")
        ops
    }
  }

  def possiblyAllowedTokenOperations(permissions: Permissions, authorizedOperationsOpt: Option[Set[Operation]]): Operations =
    (permissions, authorizedOperationsOpt) match {
      case (Permissions.Undefined, _) =>
        Operations.None
      case (_, None) =>
        Operations.None
      case (Permissions.Defined(permissionsList), Some(authorizedOperations)) =>

        val tokenOpsFromPermissions =
          Operations.combine(
            permissionsList collect {
              case permission if permission.conditions.contains(Condition.AnyoneWithToken) =>
                SpecificOperations(permission.operations.operations)
            }
          )

        SpecificOperations(
          authorizedOperations.filter(authorizedOp => Operations.allows(tokenOpsFromPermissions, authorizedOp))
        )
    }

  def authorizedOperationsForNoData(
    permissions   : Permissions,
    credentialsOpt: Option[Credentials]
  ): Operations =
    permissions match {
      case Permissions.Undefined =>
        AnyOperation
      case defined @ Permissions.Defined(_) =>

        val operationsWithoutAssumingOwnership =
          authorizedOperations(defined, credentialsOpt, CheckWithoutDataUserPessimistic)

        def operationsAssumingOwnership: Operations = {

          def ownerGroupOperations(condition: Option[Condition]): List[Operations] =
            condition match {
              case Some(condition) => defined.permissionsList.filter(_.conditions.contains(condition)).map(_.operations)
              case None            => Nil
            }

          val ownerOperations       = ownerGroupOperations(credentialsOpt.isDefined option                                   Condition.Owner)
          val groupMemberOperations = ownerGroupOperations(credentialsOpt.flatMap(_.userAndGroup.groupname).isDefined option Condition.Group)

          Operations.combine(operationsWithoutAssumingOwnership :: ownerOperations ::: groupMemberOperations)
        }

        // If the user can't create data, don't return permissions the user might have if that user was the owner; we
        // assume that if the user can't create data, the user can never be the owner of any data.
        if (Operations.allows(operationsWithoutAssumingOwnership, Operation.Create))
          operationsAssumingOwnership
        else
          operationsWithoutAssumingOwnership
    }

  private def isUserAuthorizedBasedOnOperationsAndMode(
    operations: Operations,
    modeType  : ModeType,
    isSubmit  : Boolean
  ): Boolean = {

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
      modeType == ModeType.Creation && ! Operations.allows(operations, Operation.Create)

    def unauthorizedEditing =
      modeType == ModeType.Edition && ! (
        Operations.allows(operations, Operation.Update) ||
        isSubmit && Operations.allows(operations, Operation.Create)
      )

    def unauthorizedViewing =
      modeType == ModeType.Readonly && ! (
        Operations.allows(operations, Operation.Read) ||
        isSubmit && Operations.allows(operations, Operation.Create)
      )

    ! (
      unauthorizedCreation ||
      unauthorizedEditing  ||
      unauthorizedViewing
    )
  }

  def autosaveAuthorizedForNew(
    permissions    : Permissions,
    credentialsOpt : Option[Credentials],
  ): Boolean =
    Operations.allows(
      authorizedOperationsForNoData(
        permissions,
        credentialsOpt
      ),
      Operation.Update // this is for creation modes but we disallow autosave if users can't update data
    )

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
    }
  }

  private def conditionPasses(
    condition         : Condition,
    currentCredentials: Option[Credentials],
    check             : PermissionsCheck
  ): Boolean =
    condition match {
      case Condition.AnyoneWithToken      =>
        false // TODO: could support an optional token and extra parameters? how to check on this condition depending on scenario?
      case Condition.AnyAuthenticatedUser =>
        currentCredentials.isDefined
      case Condition.Owner =>
        check match {
          case CheckWithDataUser(dataUserAndGroupOpt, _) =>
            (currentCredentials map (_.userAndGroup.username), dataUserAndGroupOpt.map(_.username)) match {
              case (Some(currentUsername), Some(dataUsername)) if currentUsername == dataUsername => true
              case _ => false
            }
          case CheckWithoutDataUserPessimistic => false
          case CheckAssumingOrganizationMatch  => false
        }
      case Condition.Group =>
        check match {
          case CheckWithDataUser(dataUserAndGroupOpt, _) =>
            (currentCredentials flatMap (_.userAndGroup.groupname), dataUserAndGroupOpt.flatMap(_.groupname)) match {
              case (Some(currentGroupname), Some(dataGroupname)) if currentGroupname == dataGroupname => true
              case _ => false
            }
          case CheckWithoutDataUserPessimistic => false
          case CheckAssumingOrganizationMatch  => false
        }
      case Condition.RolesAnyOf(permissionRoles) =>
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
