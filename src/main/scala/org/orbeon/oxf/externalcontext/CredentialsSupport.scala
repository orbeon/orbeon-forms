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

import io.circe.{Json, parser}
import org.orbeon.oxf.http.Headers
import org.orbeon.oxf.util.StringUtils._

import java.net.{URLDecoder, URLEncoder}
import scala.collection.compat._


object CredentialsSupport {

  private object Symbols {
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

  def parseCredentials(
    credentials     : String,
    decodeForHeader : Boolean
  ): Option[Credentials] =
    if (credentials.isAllBlank) {
      None
    } else {

      val decode: String => String =
        if (decodeForHeader) URLDecoder.decode(_, ExternalContext.StandardHeaderCharacterEncoding) else identity

      val parsed = parser.parse(credentials)

      def deserializeRole(value: Json): UserRole = value.asObject match {
        case Some(roleNameValues) =>

          val roleName = roleNameValues(Symbols.Name).flatMap(_.asString) match {
            case Some(value) => decode(value)
            case _           => throw new IllegalArgumentException("key `name` with string value expected in role")
          }

          val organizationOpt =
            roleNameValues(Symbols.Organization).flatMap(_.asString) match {
              case Some(value)                                           => Some(decode(value))
              case None if roleNameValues(Symbols.Organization).nonEmpty => throw new IllegalArgumentException("key `organization` with string value expected in role")
              case None                                                  => None
            }

          organizationOpt match {
            case Some(organizationName) => ParametrizedRole(roleName, organizationName)
            case _                      => SimpleRole(roleName)
          }

        case _ => throw new IllegalArgumentException("object value expected")
      }

      def deserializeOrganization(value: Json): Organization = value.asArray match {
        case Some(values) =>

          val levels =
            values.flatMap(_.asString).map(decode) //TODO throw new IllegalArgumentException("string value expected")

          Organization(levels = levels.toList)
        case _ => throw new IllegalArgumentException("array value expected")
      }

      parsed.toTry.get.asObject map { topLevelObj =>

        val username =
          topLevelObj(Symbols.Username).flatMap(_.asString) match {
            case Some(value) => decode(value)
            case _           => throw new IllegalArgumentException("key `username` with string value expected")
          }

        // NOTE: We support an array in the JSON format but not yet in `Credentials`
        val groupOpt =
          topLevelObj(Symbols.Groups).flatMap(_.asArray) match {
            case Some(values) =>
              values.headOption.flatMap(_.asString) match {
                case Some(value)             => Some(decode(value))
                case None if values.nonEmpty => throw new IllegalArgumentException("string value expected")
                case None                    => None
              }
            case None if topLevelObj(Symbols.Groups).nonEmpty =>
              throw new IllegalArgumentException("key `groups` with array value expected")
            case None =>
              None
          }

        val roles =
          topLevelObj(Symbols.Roles).flatMap(_.asArray) match {
            case Some(values)                                => values map deserializeRole
            case None if topLevelObj(Symbols.Roles).nonEmpty => throw new IllegalArgumentException("key `roles` with array value expected")
            case None                                        => Vector.empty
          }

        val organizations =
          topLevelObj(Symbols.Organizations).flatMap(_.asArray) match {
            case Some(values)                                        => values map deserializeOrganization
            case None if topLevelObj(Symbols.Organizations).nonEmpty => throw new IllegalArgumentException("array value expected")
            case None                                                => Vector.empty
          }

        Credentials(
          userAndGroup  = UserAndGroup.fromStringsOrThrow(username, groupOpt.getOrElse("")),
          roles         = roles.toList,
          organizations = organizations.toList
        )
      }
    }
}