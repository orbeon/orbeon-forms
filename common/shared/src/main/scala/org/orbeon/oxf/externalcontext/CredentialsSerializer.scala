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
package org.orbeon.oxf.externalcontext

import io.circe.Json
import org.orbeon.oxf.http.Headers

import java.net.URLEncoder
import scala.collection.compat._


object CredentialsSerializer {

  private[externalcontext] object Symbols {
    val Username      = "username"
    val Groups        = "groups"
    val Roles         = "roles"
    val Organizations = "organizations"
    val Organization  = "organization"
    val Name          = "name"
    val Levels        = "levels"
  }

  def toHeaders[T[_]](
    credentials: Credentials)(implicit
    cbf        : Factory[String, T[String]],
    ev         : T[String] => Traversable[String]
  ): List[(String, T[String])] = {

    import org.orbeon.oxf.util.CoreUtils._

    val usernameArray    = (cbf.newBuilder += credentials.userAndGroup.username).result()
    val credentialsArray = (cbf.newBuilder += serializeCredentials(credentials, encodeForHeader = true)).result()
    val groupNameArray   = (cbf.newBuilder ++= credentials.userAndGroup.groupname).result()
    val roleNamesArray   = (cbf.newBuilder ++= credentials.roles collect { case r: SimpleRole => r.roleName }).result()

    (                              Headers.OrbeonUsernameLower    -> usernameArray)    ::
    (                              Headers.OrbeonCredentialsLower -> credentialsArray) ::
    (groupNameArray.nonEmpty list (Headers.OrbeonGroupLower       -> groupNameArray))  :::
    (roleNamesArray.nonEmpty list (Headers.OrbeonRolesLower       -> roleNamesArray))
  }

  def serializeCredentials(
    credentials     : Credentials,
    encodeForHeader : Boolean
  ): String = {

    def maybeEncodeJsStringContent(s: String): String = {
      val urlEncoded    = URLEncoder.encode(s, ExternalContext.StandardHeaderCharacterEncoding)
      val spacesEncoded = s.replace(" ", "%20")

      if (urlEncoded.replace("+", "%20") != spacesEncoded)
        urlEncoded.replace("+", " ")
      else
        s
    }

    val encode: String => String =
      if (encodeForHeader) maybeEncodeJsStringContent else identity

    def serializeRole(userRole: UserRole): Json =
      userRole match {
        case SimpleRole(roleName) =>
          Json.fromFields(
            List(Symbols.Name -> Json.fromString(encode(roleName)))
          )
        case ParametrizedRole(roleName, organizationName) =>
          Json.fromFields(
            List(
              Symbols.Name         -> Json.fromString(encode(roleName)),
              Symbols.Organization -> Json.fromString(encode(organizationName))
            )
          )
      }

    def serializeOrganization(organization: Organization): Json =
      Json.arr(organization.levels.iterator map encode map Json.fromString toVector: _*)

    Json.fromFields(
      List(
        Symbols.Username      -> Json.fromString(encode(credentials.userAndGroup.username)),
        Symbols.Groups        -> Json.arr(credentials.userAndGroup.groupname map encode map Json.fromString toVector: _*),
        Symbols.Roles         -> Json.arr(credentials.roles          map serializeRole              toVector: _*),
        Symbols.Organizations -> Json.arr(credentials.organizations  map serializeOrganization      toVector: _*)
      )
    ).noSpaces
  }
}