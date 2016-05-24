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

import org.dom4j.DocumentFactory
import org.orbeon.oxf.fb.FormBuilder
import org.orbeon.oxf.http.Headers
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.util.{NetUtils, ScalaUtils}
import org.orbeon.oxf.xforms.action.XFormsAPI._
import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import org.orbeon.saxon.dom4j.DocumentWrapper
import org.orbeon.saxon.om.{NodeInfo, SequenceIterator}
import org.orbeon.scaxon.XML._

import scala.collection.JavaConverters._

trait FormRunnerPermissions {

  /**
   * Given a permission element, e.g. <permission operations="read update delete">, returns the tokenized value of
   * the operations attribute.
   */
  private def permissionOperations(permissionElement: NodeInfo): List[String] =
    permissionElement attTokens "operations" toList

  /**
   * Given the metadata for a form, returns the sequence of operations that the current user is authorized to perform,
   * just based on the user's roles. Users might be able to perform additional operations on specific data, which
   * can be tested with allAuthorizedOperations().
   * The sequence can contain just the "*" string to denote that the user is allowed to perform any operation.
   */
  //@XPathFunction
  def authorizedOperationsBasedOnRoles(permissionsElOrNull: NodeInfo): List[String] = {

    Option(permissionsElOrNull) match {
      case None ⇒
        // No permissions defined for this form, authorize any operation
        List("*")
      case Some(permissionsEl) ⇒
        permissionsEl
            .child("permission").toList
            // Only consider permissions with no constraints, or where all constraints are for a role
            .filter(_.child(*).forall(_.localname == "user-role"))
            // User must match all the <user-role>
            .filter(_.child("user-role").forall(
              // User must have at least one of the roles in any-of="…"
              _.attValue("any-of")
                .pipe(ScalaUtils.split[List](_))
                // Unescape internal spaces as the roles used in Liferay are user-facing labels that can contain space
                .map(_.replace("%20", " "))
                .intersect(orbeonRoles.toSeq)
                .nonEmpty
            ))
            // Extract the operations on each <permission>
            .flatMap(permissionOperations)
            // Remove duplicate operations
            .distinct
    }
  }

  //@XPathFunction
  def xpathAllAuthorizedOperations(
    permissionsElement : NodeInfo,
    dataUsername       : String,
    dataGroupname      : String
  ): Seq[String] = {
    def toOption(s: String) = if (s == null || s == "") None else Some(s)
    allAuthorizedOperations(permissionsElement, toOption(dataUsername), toOption(dataGroupname))
  }

  // Used by persistence layers (relational, eXist) and by xpathAllAuthorizedOperations and
  // allAuthorizedOperationsAssumingOwnerGroupMember
  def allAuthorizedOperations(
    permissionsElement : NodeInfo,
    dataUsername       : Option[String],
    dataGroupname      : Option[String]
  ): List[String] = {

    // For both username and groupname, we don't want nulls, or if specified empty string
    require(dataUsername  ne null)
    require(dataGroupname ne null)
    require(!dataUsername.contains(""))
    require(!dataGroupname.contains(""))

    def ownerGroupMemberOperations(
      headerWithUsernameOrGroupname : String,
      maybeDataUsernameOrGroupname  : Option[String],
      condition                     : String
    ): List[String] = {
      val request = NetUtils.getExternalContext.getRequest

      val maybeCurrentUsernameOrGroupname =
        request.getHeaderValuesMap.asScala.get(headerWithUsernameOrGroupname).to[List].flatten.headOption

      (maybeCurrentUsernameOrGroupname, maybeDataUsernameOrGroupname) match {
        case (Some(currentUsernameOrGroupname), Some(dataUsernameOrGroupname))
          if currentUsernameOrGroupname == dataUsernameOrGroupname ⇒
            val allPermissions                   = permissionsElement.child("permission").toList
            val permissionsForOwnerOrGroupMember = allPermissions.filter(p ⇒ p \ * forall (_.localname == condition))
            permissionsForOwnerOrGroupMember.flatMap(permissionOperations)
        case _ ⇒ Nil
      }
    }

    val rolesOperations = authorizedOperationsBasedOnRoles(permissionsElement)

    rolesOperations match {
      case Seq("*") ⇒ List("*")
      case _ ⇒
        val ownerOperations       = ownerGroupMemberOperations(Headers.OrbeonUsernameLower, dataUsername,  "owner")
        val groupMemberOperations = ownerGroupMemberOperations(Headers.OrbeonGroupLower   , dataGroupname, "group-member")
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

    allAuthorizedOperations(permissionsElement, username, group)
  }

  /** Given a list of forms metadata:
   *  - determines the operations the current user can perform,
   *  - annotates the `<form>` with an `operations="…"` attribute,
   *  - filters out forms the current user can perform no operation on.
   */
  def filterFormsAndAnnotateWithOperations(formsEls: List[NodeInfo]): List[NodeInfo] = {

    // We only need one wrapper; create it when we encounter the first <form>
    var wrapperOpt: Option[DocumentWrapper] = None

    val fbPermissions = FormBuilder.formBuilderPermissions(FormBuilder.formBuilderPermissionsConfiguration, orbeonRoles)

    formsEls.flatMap { formEl ⇒

      val wrapper = wrapperOpt.getOrElse(
        // Create wrapper we don't have one already
        new DocumentWrapper(DocumentFactory.createDocument, null, formEl.getConfiguration)
        // Save wrapper for following iterations
        |!> (w ⇒ wrapperOpt = Some(w))
      )

      val appName  = formEl.elemValue("application-name")
      val formName = formEl.elemValue("form-name")
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
        isAdmin ||                                 // admins can see everything, otherwise:
        ! (
          formName == "library" ||                 // filter libraries
          operations.isEmpty    ||                 // filter forms on which user can't possibly do anything
          formEl.elemValue("available") == "false" // filter forms marked as not available
        )

      // If kept, rewrite <form> to add operations="…" attribute
      keepForm list {
        val newFormEl      = wrapper.wrap(element("form"))
        val operationsAttr = attributeInfo("operations", operations mkString " ")
        val newFormContent = operationsAttr +: formEl.child(*)

        insert(into = Seq(newFormEl), origin = newFormContent)

        newFormEl
      }
    }
  }

  def orbeonRoles: Set[String] =
    NetUtils.getExternalContext.getRequest.getUserRoles.to[Set]

  //@XPathFunction
  def orbeonRolesSequence: SequenceIterator =
    orbeonRoles.iterator map stringToStringValue
}
