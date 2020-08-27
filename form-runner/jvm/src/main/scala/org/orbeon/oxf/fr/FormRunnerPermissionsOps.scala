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

import org.orbeon.dom
import org.orbeon.dom.saxon.DocumentWrapper
import org.orbeon.oxf.externalcontext.{Credentials, Organization}
import org.orbeon.oxf.fr.permission.PermissionsAuthorization.CheckWithDataUser
import org.orbeon.oxf.fr.permission._
import org.orbeon.oxf.http.Headers
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.NetUtils
import org.orbeon.oxf.xforms.NodeInfoFactory
import org.orbeon.oxf.xforms.action.XFormsAPI._
import org.orbeon.saxon.om.{NodeInfo, SequenceIterator}
import org.orbeon.scaxon.Implicits._
import org.orbeon.scaxon.SimplePath._

import scala.collection.JavaConverters._

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
    currentUser         : Option[Credentials] = NetUtils.getExternalContext.getRequest.credentials
  ): List[String] = {
    val permissions = PermissionsXML.parse(permissionsElOrNull)
    val operations  = PermissionsAuthorization.authorizedOperations(
      permissions,
      currentUser,
      PermissionsAuthorization.CheckWithoutDataUser(optimistic = false)
    )
    Operations.serialize(operations)
  }

  //@XPathFunction
  def xpathAllAuthorizedOperations(
    permissionsElement : NodeInfo,
    dataUsername       : String,
    dataGroupname      : String
    // TODO: take additional organization parameter
  ): Seq[String] = {
    val permissions = PermissionsXML.parse(permissionsElement)
    val user        = NetUtils.getExternalContext.getRequest.credentials
    val check       = CheckWithDataUser(Option(dataUsername), Option(dataGroupname), None)
    Operations.serialize(PermissionsAuthorization.authorizedOperations(permissions, user, check))
  }

  // Used by persistence layers (relational, eXist) and by xpathAllAuthorizedOperations and
  // allAuthorizedOperationsAssumingOwnerGroupMember
  def allAuthorizedOperations(
    permissionsElOrNull : NodeInfo,
    dataUsername        : Option[String],
    dataGroupname       : Option[String],
    dataOrganization    : Option[Organization],
    currentUser         : Option[Credentials] = NetUtils.getExternalContext.getRequest.credentials
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

    allOperationsIfNoPermissionsDefined(permissionsElOrNull) { permissions =>
      val rolesOperations       = authorizedOperationsBasedOnRoles(permissionsElOrNull, currentUser)
      val ownerOperations       = ownerGroupMemberOperations(currentUser map     (_.username), dataUsername,  "owner")
      val groupMemberOperations = ownerGroupMemberOperations(currentUser flatMap (_.group),    dataGroupname, "group-member")
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
    val headers  = NetUtils.getExternalContext.getRequest.getHeaderValuesMap.asScala
    val username = headers.get(Headers.OrbeonUsernameLower).toSeq.flatten.headOption
    val group    = headers.get(Headers.OrbeonGroupLower   ).toSeq.flatten.headOption

    allAuthorizedOperations(permissionsElement, username, group, None)
  }

  /** Given a list of forms metadata:
   *  - determines the operations the current user can perform,
   *  - annotates the `<form>` with an `operations="…"` attribute,
   *  - filters out forms the current user can perform no operation on.
   */
  def filterFormsAndAnnotateWithOperations(formsEls: List[NodeInfo], allForms: Boolean): List[NodeInfo] = {

    // We only need one wrapper; create it when we encounter the first <form>
    var wrapperOpt: Option[DocumentWrapper] = None

    val fbPermissions =
      FormRunner.formBuilderPermissions(
        FormRunner.formBuilderPermissionsConfiguration,
        orbeonRolesFromCurrentRequest
      )

    formsEls.flatMap { formEl =>

      val wrapper = wrapperOpt.getOrElse(
        // Create wrapper we don't have one already
        new DocumentWrapper(dom.Document(), null, formEl.getConfiguration)
        // Save wrapper for following iterations
        |!> (w => wrapperOpt = Some(w))
      )

      val appName  = formEl.elemValue(Names.AppName)
      val formName = formEl.elemValue(Names.FormName)
      val isAdmin  = {
        def canAccessEverything = fbPermissions.contains("*")
        def canAccessAppForm = {
          val formsUserCanAccess = fbPermissions.getOrElse(appName, Set.empty)
          formsUserCanAccess.contains("*") || formsUserCanAccess.contains(formName)
        }
        canAccessEverything || canAccessAppForm
      }

      // For each form, compute the operations the user can potentially perform
      val operations = {
        val adminOperation = isAdmin.list("admin")
        val permissionsElement = formEl.child("permissions").headOption.orNull
        val otherOperations = allAuthorizedOperationsAssumingOwnerGroupMember(permissionsElement)
        adminOperation ++ otherOperations
      }

      // Is this form metadata returned by the API?
      val keepForm =
        allForms ||                                // all forms are explicitly requested
        isAdmin  ||                                // admins can see everything
        ! (
          formName == Names.LibraryFormName ||     // filter libraries
          operations.isEmpty                ||     // filter forms on which user can't possibly do anything
          formEl.elemValue("available") == "false" // filter forms marked as not available
        )

      // If kept, rewrite <form> to add operations="…" attribute
      keepForm list {
        val newFormEl      = wrapper.wrap(dom.Element("form"))
        val operationsAttr = NodeInfoFactory.attributeInfo("operations", operations mkString " ")
        val newFormContent = operationsAttr +: formEl.child(*)

        insert(into = Seq(newFormEl), origin = newFormContent)

        newFormEl
      }
    }
  }

  def orbeonRolesFromCurrentRequest: Set[String] =
    NetUtils.getExternalContext.getRequest.credentials.toList.flatMap(_.roles).map(_.roleName).toSet

  //@XPathFunction
  def xpathOrbeonRolesFromCurrentRequest: SequenceIterator =
    orbeonRolesFromCurrentRequest.iterator

  //@XPathFunction
  def redirectToHomePageIfLoggedIn(): Unit = {
    val request  = NetUtils.getExternalContext.getRequest
    val response = NetUtils.getExternalContext.getResponse
    val username = request.getUsername
    if (username != null)
      response.sendRedirect(
        response.rewriteRenderURL("/fr/"),
        isServerSide = false,
        isExitPortal = false
      )
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