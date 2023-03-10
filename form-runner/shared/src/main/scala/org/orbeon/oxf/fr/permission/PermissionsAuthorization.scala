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
import org.orbeon.oxf.util.CoreCrossPlatformSupport


object PermissionsAuthorization {

  def findCurrentCredentialsFromSession: Option[Credentials] =
    CoreCrossPlatformSupport.externalContext.getRequest.credentials

  sealed trait PermissionsCheck
  case class CheckWithDataUser(
    userAndGroup : Option[UserAndGroup],
    organization : Option[Organization]
  )                                           extends PermissionsCheck
  case object CheckWithoutDataUserPessimistic extends PermissionsCheck
  case object CheckAssumingOrganizationMatch  extends PermissionsCheck

  def authorizedOperations(
    permissions          : Permissions,
    currentCredentialsOpt: Option[Credentials],
    check                : PermissionsCheck
  ): Operations =
    permissions match {
      case DefinedPermissions(permissionsList) =>
        Operations.combine(permissionsList.map(authorizedOperationsForPermission(_, currentCredentialsOpt, check)))
      case UndefinedPermissions =>
        SpecificOperations(Operations.AllSet)
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
              case (Some(currentUsername), Some(dataGroupnameOpt)) if currentUsername == dataGroupnameOpt => true
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
