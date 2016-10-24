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
package org.orbeon.oxf.fr

import org.orbeon.oxf.http.Headers


sealed trait UserRole   { def roleName: String }
case class   SimpleRole      (roleName: String)                           extends UserRole
case class   ParametrizedRole(roleName: String, organizationName: String) extends UserRole

case class Organization(
  levels       : List[String] // levels from root to leaf
)

case class Credentials(
  username      : String,
  group         : Option[String],
  roles         : List[UserRole],
  organizations : List[Organization]
) {
  def defaultOrganization: Option[Organization] = organizations.headOption
}

object Credentials {

  def toHeaders(credentials: Credentials): List[(String, Array[String])] = {

    import org.orbeon.oxf.util.CoreUtils._

    val usernameArray  = Array(credentials.username)
    val groupNameArray = credentials.group.to[Array]
    val roleNamesArray = credentials.roles collect { case r: SimpleRole ⇒ r.roleName } toArray

    (                              Headers.OrbeonUsernameLower → usernameArray)   ::
    (groupNameArray.nonEmpty list (Headers.OrbeonGroupLower    → groupNameArray)) :::
    (roleNamesArray.nonEmpty list (Headers.OrbeonRolesLower    → roleNamesArray))
    // TODO: Set OrbeonCredentialsLower so that it can be forwarded to an external persistence layer.
  }

}