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
import org.orbeon.oxf.util.StringUtils._

import java.net.URLDecoder


object CredentialsParser {

  import CredentialsSerializer.Symbols

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