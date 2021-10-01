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
package org.orbeon.oxf.fr

import org.orbeon.oxf.externalcontext.{Credentials, Organization, UserAndGroup}
import org.orbeon.oxf.fr.permission.PermissionsAuthorization.CheckWithDataUser
import org.orbeon.oxf.fr.permission._
import org.orbeon.oxf.http.Headers
import org.orbeon.oxf.util.CoreCrossPlatformSupport
import org.orbeon.oxf.xforms.action.XFormsAPI._
import org.orbeon.saxon.om.{NodeInfo, SequenceIterator}
import org.orbeon.scaxon.Implicits._
import org.orbeon.scaxon.SimplePath._

import scala.jdk.CollectionConverters._


trait FormRunnerPermissionsOps {

  /**
   * Given a permission element, e.g. <permission operations="read update delete">, returns the tokenized value of
   * the operations attribute.
   */
  def permissionOperations(permissionElement: NodeInfo): List[String] =
    permissionElement attTokens "operations" toList

  //@XPathFunction
  def authorizedOperationsBasedOnRolesXPath(permissionsElOrNull: NodeInfo): List[String] =
    authorizedOperationsBasedOnRoles(permissionsElOrNull)

  /**
   * Given the metadata for a form, returns the sequence of operations that the current user is authorized to perform,
   * just based on the user's roles. Users might be able to perform additional operations on specific data, which
   * can be tested with allAuthorizedOperations().
   * The sequence can contain just the "*" string to denote that the user is allowed to perform any operation.
   */
  def authorizedOperationsBasedOnRoles(
    permissionsElOrNull : NodeInfo,
    currentUser         : Option[Credentials] = CoreCrossPlatformSupport.externalContext.getRequest.credentials
  ): List[String] = {
    val permissions = PermissionsXML.parse(permissionsElOrNull)
    val operations  = PermissionsAuthorization.authorizedOperations(
      permissions,
      currentUser,
      PermissionsAuthorization.CheckWithoutDataUser(optimistic = false)
    )
    Operations.serialize(operations)
  }

  // Used by persistence layers (relational, eXist) and allAuthorizedOperationsAssumingOwnerGroupMember
  def allAuthorizedOperations(
    permissionsElOrNull : NodeInfo,
    dataUsername        : Option[String],
    dataGroupname       : Option[String],
    dataOrganization    : Option[Organization],
    currentUser         : Option[Credentials] = CoreCrossPlatformSupport.externalContext.getRequest.credentials
  ): List[String] = {

    // For both username and groupname, we don't want nulls, or if specified empty string
    require(dataUsername  ne null)
    require(dataGroupname ne null)
    require(! dataUsername .contains(""))
    require(! dataGroupname.contains(""))

    def ownerGroupMemberOperations(
      maybeCurrentUsernameOrGroupname : Option[String],
      maybeDataUsernameOrGroupname    : Option[String],
      condition                       : String
    ): List[String] = {
      (maybeCurrentUsernameOrGroupname, maybeDataUsernameOrGroupname) match {
        case (Some(currentUsernameOrGroupname), Some(dataUsernameOrGroupname))
          if currentUsernameOrGroupname == dataUsernameOrGroupname =>
            val allPermissions                   = permissionsElOrNull.child("permission").toList
            val permissionsForOwnerOrGroupMember = allPermissions.filter(p => p / * forall (_.localname == condition))
            permissionsForOwnerOrGroupMember.flatMap(permissionOperations)
        case _ => Nil
      }
    }

    allOperationsIfNoPermissionsDefined(permissionsElOrNull) { _ =>
      val rolesOperations       = authorizedOperationsBasedOnRoles(permissionsElOrNull, currentUser)
      val ownerOperations       = ownerGroupMemberOperations(currentUser map     (_.userAndGroup.username), dataUsername,  "owner")
      val groupMemberOperations = ownerGroupMemberOperations(currentUser flatMap (_.userAndGroup.groupname),    dataGroupname, "group-member")
      (rolesOperations ++ ownerOperations ++ groupMemberOperations).distinct
    }
  }

  /**
   * This is an "optimistic" version of allAuthorizedOperations, asking what operation you can do on data assuming
   * you are the owner and a group member. It is used in the Form Runner home page to determine if it is even
   * worth linking to the summary page for a given form.
   */
  //@XPathFunction
  def allAuthorizedOperationsAssumingOwnerGroupMember(permissionsElement: NodeInfo): Seq[String] = {
    val headers  = CoreCrossPlatformSupport.externalContext.getRequest.getHeaderValuesMap.asScala
    val username = headers.get(Headers.OrbeonUsernameLower).toSeq.flatten.headOption
    val group    = headers.get(Headers.OrbeonGroupLower   ).toSeq.flatten.headOption

    allAuthorizedOperations(permissionsElement, username, group, None)
  }

  def orbeonRolesFromCurrentRequest: Set[String] =
    CoreCrossPlatformSupport.externalContext.getRequest.credentials.toList.flatMap(_.roles).map(_.roleName).toSet

  //@XPathFunction
  def xpathOrbeonRolesFromCurrentRequest: SequenceIterator =
    orbeonRolesFromCurrentRequest.iterator

  //@XPathFunction
  def redirectToHomePageIfLoggedIn(): Unit = {
    val request  = CoreCrossPlatformSupport.externalContext.getRequest
    val response = CoreCrossPlatformSupport.externalContext.getResponse
    if (request.credentials.isDefined) {
      response.sendRedirect(
        response.rewriteRenderURL("/fr/"),
        isServerSide = false,
        isExitPortal = false
      )
    }
  }

  private def allOperationsIfNoPermissionsDefined
    (permissionsElOrNull : NodeInfo)
    (computePermissions  : List[Permission] => List[String]
  ): List[String] =
    PermissionsXML.parse(permissionsElOrNull) match {
      // No permissions defined for this form, authorize any operation
      case UndefinedPermissions => List("*")
      case DefinedPermissions(permissions) => computePermissions(permissions)
    }
}

object FormRunnerPermissionsOps extends FormRunnerPermissionsOps