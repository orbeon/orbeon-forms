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

import org.orbeon.oxf.common.OXFException
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
import scala.collection.generic.SeqFactory
import scala.util.control.NonFatal

trait FormRunnerPermissions {

    import org.orbeon.oxf.fr.FormRunner._
    import org.orbeon.oxf.fr.FormRunnerPermissions._

    val PropertyPrefix = "oxf.fr.authentication."

    val MethodPropertyName                  = PropertyPrefix + "method"
    val ContainerRolesPropertyName          = PropertyPrefix + "container.roles"
    val HeaderUsernamePropertyName          = PropertyPrefix + "header.username"
    val HeaderRolesPropertyName             = PropertyPrefix + "header.roles"
    val HeaderGroupPropertyName             = PropertyPrefix + "header.group"
    val HeaderRolesPropertyNamePropertyName = PropertyPrefix + "header.roles.property-name"

    val NameValueMatch = "([^=]+)=([^=]+)".r

    type UserRoles = {
        def getRemoteUser(): String
        def isUserInRole(role: String): Boolean
    }

    /**
     * Get the username and roles from the request, based on the Form Runner configuration.
     */
    def getUserGroupRoles(userRoles: UserRoles, getHeader: String ⇒ Option[Array[String]]): (Option[String], Option[String], Option[Array[String]]) = {

        val propertySet = properties
        propertySet.getString(MethodPropertyName, "container") match {
            case "container" ⇒

                val username    = Option(userRoles.getRemoteUser)
                val rolesString = propertySet.getString(ContainerRolesPropertyName)

                if (rolesString eq null) {
                    (username, None, None)
                } else {

                    // Wrap exceptions as Liferay throws if the role is not available instead of returning false
                    def isUserInRole(role: String) =
                        try userRoles.isUserInRole(role)
                        catch { case NonFatal(_) ⇒ false}

                    val rolesArray =
                        for {
                            role ← rolesString.split(""",|\s+""")
                            if isUserInRole(role)
                        } yield
                            role

                    val roles = rolesArray match {
                        case Array() ⇒ None
                        case array   ⇒ Some(array)
                    }

                    (username, rolesArray.headOption, roles)
                }

            case "header" ⇒

                val headerPropertyName = propertySet.getString(HeaderRolesPropertyNamePropertyName, "").trim match {
                    case "" ⇒ None
                    case value ⇒ Some(value)
                }

                def headerOption(name: String) = Option(propertySet.getString(name)) flatMap (p ⇒ getHeader(p.toLowerCase))

                // Headers can be separated by comma or pipe
                def split1(value: String) = value split """(\s*[,\|]\s*)+"""
                // Then, if configured, a header can have the form name=value, where name is specified in a property
                def split2(value: String) = headerPropertyName match {
                    case Some(propertyName) ⇒
                        value match {
                            case NameValueMatch(`propertyName`, value) ⇒ Seq(value)
                            case _ ⇒ Seq.empty
                        }
                    case _ ⇒ Seq(value)
                }

                // Username and group: take the first header
                val username = headerOption(HeaderUsernamePropertyName) map (_.head)
                val group    = headerOption(HeaderGroupPropertyName) map (_.head)

                // Roles: all headers with the given name are used, each header value is split, and result combined
                val roles    = headerOption(HeaderRolesPropertyName) map (_ flatMap split1 flatMap split2)

                (username, group, roles)

            case other ⇒ throw new OXFException("Unsupported authentication method, check the '" + MethodPropertyName + "' property:" + other)
        }
    }

    def getUserGroupRolesAsHeaders(userRoles: UserRoles, getHeader: String ⇒ Option[Array[String]]): List[(String, Array[String])] = {
        val (username, group, roles) = getUserGroupRoles(userRoles, getHeader)

        (username.toList map (OrbeonUsernameHeaderName → Array(_))) :::
        (group.toList    map (OrbeonGroupHeaderName    → Array(_))) :::
        (roles.toList    map (OrbeonRolesHeaderName    →))
    }

    /**
     * Given a permission element, e.g. <permission operations="read update delete">, returns the tokenized value of
     * the operations attribute.
     */
    private def permissionOperations(permissionElement: NodeInfo): Seq[String] =
        permissionElement attTokens "operations" toList

    /**
     * Given the metadata for a form, returns the sequence of operations that the current user is authorized to perform,
     * just based on the user's roles. Users might be able to perform additional operations on specific data, which
     * can be tested with allAuthorizedOperations().
     * The sequence can contain just the "*" string to denote that the user is allowed to perform any operation.
     */
    def authorizedOperationsBasedOnRoles(permissionsElement: NodeInfo): Seq[String] = {
        if (permissionsElement eq null)
            Seq("*")                                                         // No permissions defined for this form, authorize any operation
        else
            (permissionsElement \ "permission")
                    .filter(p ⇒
                    (p \ * isEmpty) ||                                       // Only consider permissions with no constraints (unnecessary line for clarity)
                    (p \ * forall (_.localname == "user-role")))             // … or with only `user-role` constraints
                    .filter(p ⇒
                    p \ "user-role" forall (r ⇒                              // If we have user-role constraints, they must all pass
                        ScalaUtils.split((r \@ "any-of").stringValue)        // Constraint is satisfied if user has at least one of the roles
                        .map(_.replace("%20", " "))                          // Unescape internal spaces as the roles used in Liferay are user-facing labels that can contain space (see also permissions.xbl)
                            .intersect(orbeonRoles.toSeq).nonEmpty))
                .flatMap(permissionOperations)                               // For the permissions that passed, return the list operations
                .distinct                                                    // Remove duplicate operations
    }

    def xpathAllAuthorizedOperations(permissionsElement: NodeInfo, dataUsername: String, dataGroupname: String): Seq[String] = {
        def toOption(s: String) = if (s == null || s == "") None else Some(s)
        allAuthorizedOperations(permissionsElement, toOption(dataUsername), toOption(dataGroupname))
    }

    def allAuthorizedOperations(permissionsElement: NodeInfo, dataUsername: Option[String], dataGroupname: Option[String]): Seq[String] = {

        // For both username and groupname, we don't want nulls, or if specified empty string
        assert(dataUsername  != null)
        assert(dataGroupname != null)
        assert(dataUsername .map(_ != "").getOrElse(true))
        assert(dataGroupname.map(_ != "").getOrElse(true))

        def operations(headerWithUsernameOrGroupname: String, maybeDataUsernameOrGroupname: Option[String], condition: String): Seq[String] = {
            val request = NetUtils.getExternalContext.getRequest
            val maybeCurrentUsernameOrGroupname = request.getHeaderValuesMap.asScala.get(headerWithUsernameOrGroupname).toSeq.flatten.headOption
            (maybeCurrentUsernameOrGroupname, maybeDataUsernameOrGroupname) match {
                case (Some(currentUsernameOrGroupname), Some(dataUsernameOrGroupname))
                    if currentUsernameOrGroupname == dataUsernameOrGroupname ⇒
                        val allPermissions = permissionsElement \ "permission"
                        val permissionsForOwnerOrGroupMember = allPermissions.filter(p ⇒ p \ * forall (_.localname == condition))
                        permissionsForOwnerOrGroupMember.flatMap(permissionOperations)
                case _ ⇒ Nil
            }
        }

        val rolesOperations = authorizedOperationsBasedOnRoles(permissionsElement)
        rolesOperations match {
            case Seq("*") ⇒ Seq("*")
            case _ ⇒
                val ownerOperations       = operations(OrbeonUsernameHeaderName, dataUsername,  "owner")
                val groupMemberOperations = operations(OrbeonGroupHeaderName   , dataGroupname, "group-member")
                (rolesOperations ++ ownerOperations ++ groupMemberOperations).distinct
        }
    }

    /**
     * This is an "optimistic" version of allAuthorizedOperations, asking what operation you can do on data assuming
     * you are the owner and a group member. It is used in the Form Runner home page to determine if it is even
     * worth linking to the summary page for a given form.
     */
    def allAuthorizedOperationsAssumingOwnerGroupMember(permissionsElement: NodeInfo): Seq[String] = {
        val headers  = NetUtils.getExternalContext.getRequest.getHeaderValuesMap.asScala
        val username = headers.get(OrbeonUsernameHeaderName).toSeq.flatten.headOption
        val group    = headers.get(OrbeonGroupHeaderName   ).toSeq.flatten.headOption

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

        val fbPermissions = FormBuilder.formBuilderPermissions(FormBuilder.fbRoles, orbeonRoles)

        formsEls.flatMap { formEl ⇒

            val wrapper = wrapperOpt.getOrElse(
                // Create wrapper we don't have one already
                new DocumentWrapper(Dom4jUtils.createDocument, null, formEl.getConfiguration)
                // Save wrapper for following iterations
                |!> (w ⇒ wrapperOpt = Some(w))
            )

            val appName  = formEl.elemValue("application-name")
            val formName = formEl.elemValue("form-name")
            val isAdmin  = {
                def canAccessEverything = fbPermissions.isDefinedAt("*")
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
            val keepForm = isAdmin ||                             // Admins can see everything, otherwise:
                ! (   formName == "library"                       // Filter libraries
                   || operations.isEmpty                          // Filter forms on which user can't possibly do anything
                   || formEl.elemValue("available") == "false")   // Filter forms marked as not available

            // If kept, rewrite <form> to add operations="…" attribute
            keepForm.list {
                val newFormEl = wrapper.wrap(element("form"))
                val operationsAttr = attributeInfo("operations", operations mkString " ")
                val newFormContent = operationsAttr +: formEl.child(*)
                insert(into = Seq(newFormEl), origin = newFormContent)
                newFormEl
            }
        }
    }

    def orbeonRoles: Set[String] = {
        val request = NetUtils.getExternalContext.getRequest
        // Split header values on commas, in case the incoming header was not processed, see:
        // https://github.com/orbeon/orbeon-forms/issues/1690
        request.getHeaderValuesMap.asScala.getOrElse(OrbeonRolesHeaderName, Array.empty[String]) flatMap
                (ScalaUtils.split[Array](_, ",")) toSet
    }

    def orbeonRolesSequence: SequenceIterator =
        orbeonRoles.iterator map stringToStringValue
}

object FormRunnerPermissions {
    val OrbeonUsernameHeaderName = Headers.OrbeonUsernameLower
    val OrbeonGroupHeaderName    = Headers.OrbeonGroupLower
    val OrbeonRolesHeaderName    = Headers.OrbeonRolesLower
}