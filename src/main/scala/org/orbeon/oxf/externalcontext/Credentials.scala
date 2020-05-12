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

import java.net.{URLDecoder, URLEncoder}

import org.orbeon.oxf.http.Headers
import org.orbeon.oxf.util.StringUtils._
import spray.json._
import scala.collection.compat._

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

  object Symbols {
    val Username      = "username"
    val Groups        = "groups"
    val Roles         = "roles"
    val Organizations = "organizations"
    val Organization  = "organization"
    val Name          = "name"
    val Levels        = "levels"
  }

  def toHeaders(credentials: Credentials): List[(String, Array[String])] = {

    import org.orbeon.oxf.util.CoreUtils._

    val usernameArray    = Array(credentials.username)
    val credentialsArray = Array(serializeCredentials(credentials, encodeForHeader = true))
    val groupNameArray   = credentials.group.toArray
    val roleNamesArray   = credentials.roles collect { case r: SimpleRole => r.roleName } toArray

    (                              Headers.OrbeonUsernameLower    -> usernameArray)    ::
    (                              Headers.OrbeonCredentialsLower -> credentialsArray) ::
    (groupNameArray.nonEmpty list (Headers.OrbeonGroupLower       -> groupNameArray))  :::
    (roleNamesArray.nonEmpty list (Headers.OrbeonRolesLower       -> roleNamesArray))
  }

  def serializeCredentials(
    credentials     : Credentials,
    encodeForHeader : Boolean
  ): String = {

    def maybeEncodeJsStringContent(s: String) = {
      val urlEncoded    = URLEncoder.encode(s, ExternalContext.StandardHeaderCharacterEncoding)
      val spacesEncoded = s.replaceAllLiterally(" ", "%20")

      if (urlEncoded.replaceAllLiterally("+", "%20") != spacesEncoded)
        urlEncoded.replaceAllLiterally("+", " ")
      else
        s
    }

    val encode: String => String =
      if (encodeForHeader) maybeEncodeJsStringContent else identity

    def serializeRole(userRole: UserRole) =
      userRole match {
        case SimpleRole(roleName) =>
          JsObject(
            Symbols.Name -> JsString(encode(roleName))
          )
        case ParametrizedRole(roleName, organizationName) =>
          JsObject(
            Symbols.Name         -> JsString(encode(roleName)),
            Symbols.Organization -> JsString(encode(organizationName))
          )
      }

    def serializeOrganization(organization: Organization) =
      JsArray(organization.levels.iterator map encode map JsString.apply toVector)

    JsObject(
      Symbols.Username      -> JsString(encode(credentials.username)),
      Symbols.Groups        -> JsArray(credentials.group         map encode map JsString.apply toVector),
      Symbols.Roles         -> JsArray(credentials.roles         map serializeRole             toVector),
      Symbols.Organizations -> JsArray(credentials.organizations map serializeOrganization     toVector)
    ).compactPrint
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

      val parsed = credentials.parseJson

      def deserializeRole(value: JsValue): UserRole = value match {
        case JsObject(roleNameValues) =>

          val roleName = roleNameValues.get(Symbols.Name) match {
            case Some(JsString(value)) => decode(value)
            case _                     => deserializationError("key `name` with string value expected in role")
          }

          val organizationOpt = roleNameValues.get(Symbols.Organization) match {
            case Some(JsString(value)) => Some(decode(value))
            case None                  => None
            case _                     => deserializationError("key `organization` with string value expected in role")
          }

          organizationOpt match {
            case Some(organizationName) => ParametrizedRole(roleName, organizationName)
            case none                   => SimpleRole(roleName)
          }

        case _ => deserializationError("object value expected")
      }

      def deserializeOrganization(value: JsValue): Organization = value match {
        case JsArray(values) =>

          val levels =
            values map {
              case JsString(value) => decode(value)
              case _               => deserializationError("string value expected")
            }

          Organization(levels = levels.to(List))
        case _ => deserializationError("array value expected")
      }

      parsed match {
        case JsObject(topLevel) =>

          val username =
            topLevel.get(Symbols.Username) match {
              case Some(JsString(value)) => decode(value)
              case _                     => deserializationError("key `username` with string value expected")
            }

          // NOTE: We support an array in the JSON format but not yet in `Credentials`
          val groupOpt =
            topLevel.get(Symbols.Groups) match {
              case Some(JsArray(values)) =>
                values.headOption match {
                  case Some(JsString(value)) => Some(decode(value))
                  case None                  => None
                  case _                     => deserializationError("string value expected")
                }
              case None => None
              case _    => deserializationError("key `groups` with array value expected")
            }

          val roles =
            topLevel.get(Symbols.Roles) match {
              case Some(JsArray(values)) => values map deserializeRole
              case None                  => Vector.empty
              case _                     => deserializationError("key `roles` with array value expected")
            }

          val organizations =
            topLevel.get(Symbols.Organizations) match {
              case Some(JsArray(values)) => values map deserializeOrganization
              case None                  => Vector.empty
              case _                     => deserializationError("array value expected")
            }

          Some(
            Credentials(
              username      = username,
              group         = groupOpt,
              roles         = roles.to(List),
              organizations = organizations.to(List)
            )
          )

        case _ => deserializationError("Object expected")
      }
    }
}