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

import org.parboiled.errors.ParsingException
import org.parboiled.scala._

case class Credentials(
  username     : String,
  group        : Option[String],
  roles        : List[UserRole],
  organization : Option[Organization]
)

case class Organization(
  levels       : List[String] // list of levels, with the root-level first, leaf-level last
)

sealed trait UserRole   { def roleName: String }
case class   SimpleRole      (roleName: String)                           extends UserRole
case class   ParametrizedRole(roleName: String, organizationName: String) extends UserRole

object UserRole {

  def parse(userRole: String): UserRole =
    UserRoleParser.parse(userRole)

  def serialize(userRole: UserRole): String =
    userRole match {
      case SimpleRole(roleName) ⇒
        roleName
      case ParametrizedRole(roleName, organizationName) ⇒

        val escapedOrganizationName = organizationName
          .replaceAllLiterally("\\", "\\\\")
          .replaceAllLiterally("\"", "\\\"")
        s"""$roleName(organization="$escapedOrganizationName")"""
    }

  private object UserRoleParser extends Parser {

    def parse(role: String): UserRole = {
      val parsingResult = ReportingParseRunner(Role).run(role)
      parsingResult.result match {
        case Some(userRole) ⇒ userRole
        case None           ⇒ throw new ParsingException(s"Invalid role `$role`")
      }
    }

    def Role: Rule1[UserRole] = rule {
      Name ~ optional("(" ~ Param ~ ")") ~~>
        ((name, param) ⇒ param match {
          case None               ⇒ SimpleRole(name)
          case Some(organization) ⇒ ParametrizedRole(name, organization.mkString)
        })
    }

    def Param       : Rule1[List[Char]] = rule { "organization=" ~ ValueString }
    def ValueString : Rule1[List[Char]] = rule { "\"" ~ Characters ~ "\"" }

    def Name        : Rule1[String] = rule { zeroOrMore(NameChar) ~> identity }
    def NameChar    : Rule0         = rule { "a" - "z" | "A" - "Z" | "-" | "_" }

    def Characters  : Rule1[List[Char]] = rule { zeroOrMore(Character) }

    def Character   : Rule1[Char] = rule { EscapedChar | NormalChar }
    def EscapedChar : Rule1[Char] = rule { "\\" ~ anyOf("\"\\")  ~> (_.head) }
    def NormalChar  : Rule1[Char] = rule { ! anyOf("\"\\") ~ ANY ~> (_.head) }

  }
}