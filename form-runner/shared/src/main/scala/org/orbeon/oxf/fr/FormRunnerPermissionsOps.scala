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

import org.orbeon.oxf.externalcontext.{Credentials, Organization}
import org.orbeon.oxf.fr.FormRunnerCommon.frc
import org.orbeon.oxf.fr.permission._
import org.orbeon.oxf.http.Headers
import org.orbeon.oxf.util.CoreCrossPlatformSupport
import org.orbeon.oxf.util.StringUtils.StringOps
import org.orbeon.saxon.om.{NodeInfo, SequenceIterator}
import org.orbeon.scaxon.Implicits._
import org.orbeon.scaxon.SimplePath._

import scala.jdk.CollectionConverters._


trait FormRunnerPermissionsOps {

  /**
   * Given a permission element, e.g. <permission operations="read update delete">, returns the tokenized value of
   * the operations attribute.
   *
   * See backward compatibility handling: https://github.com/orbeon/orbeon-forms/issues/5397
   */
  def permissionOperationsHandleList(permissionElement: NodeInfo): List[String] = {

    val tokens            = permissionElement.attTokens("operations")
    val hasMinusListToken = tokens(Operation.MinusListToken) // https://github.com/orbeon/orbeon-forms/issues/5397

    val updatedTokens =
      if (! hasMinusListToken && tokens(Operation.Read.entryName))
        tokens + Operation.List.entryName
      else if (hasMinusListToken)
        tokens.filter(_ != Operation.MinusListToken)
      else
        tokens

    updatedTokens.to
  }

  //@XPathFunction
  def authorizedOperationsBasedOnRolesXPath(permissionsElOrNull: NodeInfo): List[String] =
    Operations.serialize(authorizedOperationsBasedOnRoles(permissionsElOrNull))

  //@XPathFunction
  def isUserAuthorizedBasedOnOperationsAndModeXPath(operations: String, mode: String, isSubmit: Boolean): Boolean =
    isUserAuthorizedBasedOnOperationsAndMode(
      Operations.parse(operations.splitTo[List]()),
      mode,
      isSubmit
    )

  /**
   * Given the metadata for a form, returns the sequence of operations that the current user is authorized to perform,
   * just based on the user's roles. Users might be able to perform additional operations on specific data, which
   * can be tested with allAuthorizedOperations().
   * The sequence can contain just the "*" string to denote that the user is allowed to perform any operation.
   */
  def authorizedOperationsBasedOnRoles(
    permissionsElOrNull : NodeInfo,
    currentUser         : Option[Credentials] = CoreCrossPlatformSupport.externalContext.getRequest.credentials
  ): Operations = {
    val permissions = PermissionsXML.parse(permissionsElOrNull)

    PermissionsAuthorization.authorizedOperations(
      permissions,
      currentUser,
      PermissionsAuthorization.CheckWithoutDataUserPessimistic
    )
  }

  def isUserAuthorizedBasedOnOperationsAndMode(operations: Operations, mode: String, isSubmit: Boolean): Boolean = {

    // Special cases:
    //
    // - `schema`: doesn't require any authorized permission as it is simply protected as a service
    // - `test`: doesn't require any authorized permission, see https://github.com/orbeon/orbeon-forms/issues/2050
    //
    // When POSTing data to the page, this is generally considered a "mode change" and doesn't require the
    // same permissions, especially since the POST is protected by other means. For example, a user with
    // `create` permission only is allowed to navigate to the `view` page and `edit` page back. This does not
    // imply that the user need `read` or `update` permissions. Because the current use case is that the user
    // has at least the `create` permission, we currently require that permission, although this restriction
    // could be removed in the future if need be.

    def unauthorizedCreation =
      frc.CreationModes(mode) && ! Operations.allows(operations, Operation.Create)

    def unauthorizedEditing =
      frc.EditingModes(mode) && ! (
        Operations.allows(operations, Operation.Update) ||
        isSubmit && Operations.allows(operations, Operation.Create)
      )

    def unauthorizedViewing =
      frc.ReadonlyModes(mode) && ! (
        Operations.allows(operations, Operation.Read) ||
        isSubmit && Operations.allows(operations, Operation.Create)
      )

    def unauthorizedMode =
      ! frc.AllDetailModes(mode)

    ! (
      unauthorizedCreation ||
      unauthorizedEditing  ||
      unauthorizedViewing  ||
      unauthorizedMode
    )
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
            permissionsForOwnerOrGroupMember.flatMap(permissionOperationsHandleList)
        case _ => Nil
      }
    }

    allOperationsIfNoPermissionsDefined(permissionsElOrNull) { _ =>
      val rolesOperations       = Operations.serialize(authorizedOperationsBasedOnRoles(permissionsElOrNull, currentUser))
      val ownerOperations       = ownerGroupMemberOperations(currentUser map     (_.userAndGroup.username),  dataUsername,  "owner")
      val groupMemberOperations = ownerGroupMemberOperations(currentUser flatMap (_.userAndGroup.groupname), dataGroupname, "group-member")
      (rolesOperations ++ ownerOperations ++ groupMemberOperations).distinct
    }
  }

  /**
   * This is an "optimistic" version of allAuthorizedOperations, asking what operation you can do on data assuming
   * you are the owner and a group member. It is used in the Form Runner home page, through the form metadata API,
   * to determine if it is even worth linking to the summary page for a given form.
   *
   * FIXME: We have similar, but better typed logic in `authorizedBasedOnRole()` (`SearchLogic.scala`), which we
   *        could move to `PermissionsAuthorization`, and use from here and `SearchLogic.scala`
   */
  //@XPathFunction
  def allAuthorizedOperationsAssumingOwnerGroupMember(permissionsElement: NodeInfo): Seq[String] = {

    val headers       = CoreCrossPlatformSupport.externalContext.getRequest.getHeaderValuesMap.asScala
    val authUsername  = headers.get(Headers.OrbeonUsernameLower).toSeq.flatten.headOption
    val authGroupname = headers.get(Headers.OrbeonGroupLower   ).toSeq.flatten.headOption

    val operationsWithoutAssumingOwnership = allAuthorizedOperations(permissionsElement, None, None, None)
    def operationsAssumingOwnership        = allAuthorizedOperations(permissionsElement, authUsername, authGroupname, None)
    val authUserCanCreate                  = Operations.allows(Operations.parse(operationsWithoutAssumingOwnership), Operation.Create)

    // If the user can't create data, don't return permissions the user might have if that user was the owner; we
    // assume that if the user can't create data, the user can never be the owner of any data
    if (authUserCanCreate) operationsAssumingOwnership
    else                   operationsWithoutAssumingOwnership
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