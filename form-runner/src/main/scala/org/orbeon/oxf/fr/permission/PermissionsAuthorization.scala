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

import org.orbeon.oxf.fr.{Organization, ParametrizedRole, SimpleRole, UserRole}
import org.orbeon.oxf.util.NetUtils

object PermissionsAuthorization {

  case class CurrentUser(
    username     : Option[String],
    groupname    : Option[String],
    organization : Option[Organization],
    roles        : List[UserRole]
  )

  def currentUserFromSession: CurrentUser = {
    val request = NetUtils.getExternalContext.getRequest
    CurrentUser(
      username     = Option(request.getUsername),
      groupname    = Option(request.getUserGroup),
      organization = request.getUserOrganization,
      roles        = request.getUserRoles.to[List]
    )
  }

  sealed trait PermissionsCheck
  case class CheckWithDataUser(
    username     : Option[String],
    groupname    : Option[String],
    organization : Option[Organization]
  )                                          extends PermissionsCheck
  case class CheckWithoutDataUser(
    optimistic   : Boolean
  )                                          extends PermissionsCheck
  case object CheckAssumingOrganizationMatch extends PermissionsCheck

  def authorizedOperations(
    permissions : Permissions,
    currentUser : CurrentUser,
    check       : PermissionsCheck
  ): Operations =
    permissions match {
      case DefinedPermissions(permissionsList) ⇒
        val operationsList = permissionsList.map(authorizedOperations(_, currentUser, check))
        Operations.combine(operationsList)
      case UndefinedPermissions ⇒
        SpecificOperations(Operations.All)
    }

  private def authorizedOperations(
    permission  : Permission,
    currentUser : CurrentUser,
    check       : PermissionsCheck
  ): Operations =
    if (permission.conditions.forall(conditionPasses(_, currentUser, check)))
      permission.operations
    else
      Operations.None

  private def conditionPasses(
    condition   : Condition,
    currentUser : CurrentUser,
    check       : PermissionsCheck
  ): Boolean =
    condition match {
      case Owner ⇒
        check match {
          case CheckWithDataUser(dataUsernameOpt, _, _) ⇒
            (currentUser.username, dataUsernameOpt) match {
              case (Some(currentUsername), Some(dataUsername)) if currentUsername == dataUsername ⇒ true
              case _ ⇒ false
            }
          case CheckWithoutDataUser(optimistic) ⇒ optimistic
          case CheckAssumingOrganizationMatch   ⇒ false
        }
      case Group ⇒
        check match {
          case CheckWithDataUser(_, dataGroupnameOpt, _) ⇒
            (currentUser.groupname, dataGroupnameOpt) match {
              case (Some(currentUsername), Some(dataGroupnameOpt)) if currentUsername == dataGroupnameOpt ⇒ true
              case _ ⇒ false
            }
          case CheckWithoutDataUser(optimistic) ⇒ optimistic
          case CheckAssumingOrganizationMatch   ⇒ false
        }
      case RolesAnyOf(permissionRoles) ⇒
        permissionRoles.exists(permissionRoleName ⇒
          currentUser.roles.exists {
            case SimpleRole(userRoleName) ⇒
              userRoleName == permissionRoleName
            case ParametrizedRole(userRoleName, userOrganizationName) ⇒
              userRoleName == permissionRoleName && (
                check match {
                  case CheckWithDataUser(_, _, dataOrganizationOpt) ⇒
                    dataOrganizationOpt.exists(_.levels.contains(userOrganizationName))
                  case CheckWithoutDataUser(optimistic) ⇒
                    optimistic
                  case CheckAssumingOrganizationMatch   ⇒ true
                }
              )
          }
        )
    }

}
