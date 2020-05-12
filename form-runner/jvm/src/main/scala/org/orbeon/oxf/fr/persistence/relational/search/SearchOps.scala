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
package org.orbeon.oxf.fr.persistence.relational.search

import org.orbeon.oxf.externalcontext.{Credentials, Organization, ParametrizedRole}
import org.orbeon.oxf.fr.permission.Operation.{Delete, Read, Update}
import org.orbeon.oxf.fr.permission.PermissionsAuthorization.CheckWithDataUser
import org.orbeon.oxf.fr.permission._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.SimplePath._
import scala.collection.compat._

object SearchOps {

  private val SearchOperations = List(Read, Update, Delete)

  def xpathAuthorizedIfOrganizationMatch(formPermissionsElOrNull: NodeInfo): List[String] =
    authorizedIfOrganizationMatch(
      permissions = PermissionsXML.parse(formPermissionsElOrNull),
      currentUser = PermissionsAuthorization.currentUserFromSession
    )

  def authorizedIfOrganizationMatch(
    permissions : Permissions,
    currentUser : Option[Credentials]
  ): List[String] = {
    val check  = PermissionsAuthorization.CheckAssumingOrganizationMatch
    val userParametrizedRoles = currentUser.to(List).flatMap(_.roles).collect{ case role @ ParametrizedRole(_, _) => role }
    val usefulUserParametrizedRoles = userParametrizedRoles.filter(role => {
      val userWithJustThisRole = currentUser.map(_.copy(roles = List(role)))
      val authorizedOperations = PermissionsAuthorization.authorizedOperations(permissions, userWithJustThisRole, check)
      Operations.allowsAny(authorizedOperations, SearchOperations)
    } )
    usefulUserParametrizedRoles.map(_.organizationName)
  }

  def authorizedOperations(
    formPermissionsElOrNull : NodeInfo,
    metadataOrNullEl        : NodeInfo
  ): List[String] = {

    val checkWithData = {

      def childValue(name: String): Option[String] =
        Option(metadataOrNullEl)
          .flatMap(_.firstChildOpt(name))
          .map(_.stringValue)
          .flatMap(_.trimAllToOpt)

      val username     = childValue("username")
      val groupname    = childValue("groupname")
      val organization = {
        val levels = Option(metadataOrNullEl)
          .flatMap(_.firstChildOpt("organization"))
          .map(_.child("level").to(List).map(_.stringValue))
        levels.map(Organization.apply)
      }

      CheckWithDataUser(username, groupname, organization)
    }

    val operations =
      PermissionsAuthorization.authorizedOperations(
        permissions = PermissionsXML.parse(formPermissionsElOrNull),
        currentUser = PermissionsAuthorization.currentUserFromSession,
        check       = checkWithData
      )

    Operations.serialize(operations)
  }
}
