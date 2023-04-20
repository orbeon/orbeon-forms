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

import org.orbeon.oxf.externalcontext.{Credentials, ParametrizedRole}
import org.orbeon.oxf.fr.permission.Operation.{Delete, Read, Update}
import org.orbeon.oxf.fr.permission._

import scala.collection.compat._


object SearchOps {

  val SearchOperations: Set[Operation] = Set(Read, Update, Delete)

  def authorizedIfOrganizationMatch(
    permissions   : Permissions,
    credentialsOpt: Option[Credentials]
  ): List[String] = {
    val check  = PermissionsAuthorization.CheckAssumingOrganizationMatch
    val userParametrizedRoles = credentialsOpt.to(List).flatMap(_.roles).collect{ case role @ ParametrizedRole(_, _) => role }
    val usefulUserParametrizedRoles = userParametrizedRoles.filter(role => {
      val credentialsWithJustThisRoleOpt = credentialsOpt.map(_.copy(roles = List(role)))
      val authorizedOperations           = PermissionsAuthorization.authorizedOperations(permissions, credentialsWithJustThisRoleOpt, check)
      Operations.allowsAny(authorizedOperations, SearchOperations)
    } )
    usefulUserParametrizedRoles.map(_.organizationName)
  }
}
