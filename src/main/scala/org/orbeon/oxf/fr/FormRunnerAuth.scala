/**
 * Copyright (C) 2015 Orbeon, Inc.
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
import org.orbeon.oxf.http.Headers
import org.orbeon.oxf.properties.{Properties, PropertySet}
import org.orbeon.oxf.util.ScalaUtils._
import org.slf4j.LoggerFactory

import scala.util.control.NonFatal

object FormRunnerAuth {

  val LoggerName = "org.orbeon.auth"
  val Logger     = LoggerFactory.getLogger(LoggerName)

  val OrbeonUsernameHeaderName = Headers.OrbeonUsernameLower
  val OrbeonGroupHeaderName    = Headers.OrbeonGroupLower
  val OrbeonRolesHeaderName    = Headers.OrbeonRolesLower
  
  val AllHeaderNamesLower = Set(OrbeonUsernameHeaderName, OrbeonGroupHeaderName, OrbeonRolesHeaderName)

  private object Private {
    val PropertyPrefix = "oxf.fr.authentication."

    val MethodPropertyName                  = PropertyPrefix + "method"
    val ContainerRolesPropertyName          = PropertyPrefix + "container.roles"
    val ContainerRolesSplitPropertyName     = PropertyPrefix + "container.roles.split"
    val HeaderUsernamePropertyName          = PropertyPrefix + "header.username"
    val HeaderRolesPropertyName             = PropertyPrefix + "header.roles"
    val HeaderRolesSplitPropertyName        = PropertyPrefix + "header.roles.split"
    val HeaderGroupPropertyName             = PropertyPrefix + "header.group"
    val HeaderRolesPropertyNamePropertyName = PropertyPrefix + "header.roles.property-name"

    val NameValueMatch = "([^=]+)=([^=]+)".r

    type UserRoles = {
      def getRemoteUser(): String
      def isUserInRole(role: String): Boolean
    }
  }

  import Private._

  def properties: PropertySet = Properties.instance.getPropertySet

  // Get the username and roles from the request, based on the Form Runner configuration.
  def getUserGroupRoles(
    userRoles : UserRoles,
    getHeader : String ⇒ Option[Array[String]]
  ): (Option[String], Option[String], Option[Array[String]]) = {

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

          val rolesSplit =
            propertySet.getString(ContainerRolesSplitPropertyName, """,|\s+""")

          val rolesArray =
            for {
              role ← rolesString split rolesSplit
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

        val headerPropertyName =
          nonEmptyOrNone(propertySet.getString(HeaderRolesPropertyNamePropertyName))

        def headerOption(name: String) =
          Option(propertySet.getString(name)) flatMap (p ⇒ getHeader(p.toLowerCase))

        val rolesSplit = propertySet.getString(HeaderRolesSplitPropertyName, """(\s*[,\|]\s*)+""")
        def splitRoles(value: String) = value split rolesSplit

        // If configured, a header can have the form `name=value` where `name` is specified in a property
        def splitWithinRole(value: String) = headerPropertyName match {
          case Some(propertyName) ⇒
            value match {
              case NameValueMatch(`propertyName`, value) ⇒ List(value)
              case _                                     ⇒ Nil
            }
          case _ ⇒ List(value)
        }

        // Username and group: take the first header
        val username = headerOption(HeaderUsernamePropertyName) map (_.head)
        val group    = headerOption(HeaderGroupPropertyName)    map (_.head)

        // Roles: all headers with the given name are used, each header value is split, and result combined
        // See also: https://github.com/orbeon/orbeon-forms/issues/1690
        val roles    = headerOption(HeaderRolesPropertyName) map (_ flatMap splitRoles flatMap splitWithinRole)

        (username, group, roles)

      case other ⇒
        throw new OXFException(s"'$MethodPropertyName' property: unsupported authentication method `$other`")
    }
  }

  private def headersAsJSONString(headers: List[(String, Array[String])]) = {

    val headerAsJSONStrings =
      headers map {
        case (name, values) ⇒
          val valuesAsString = values.mkString("""["""", """", """", """"]""")
          s""""$name": $valuesAsString"""
      }

    headerAsJSONStrings.mkString("{", ", ", "}")
  }

  def getUserGroupRolesAsHeaders(
    userRoles : UserRoles,
    getHeader : String ⇒ Option[Array[String]]
  ): List[(String, Array[String])] = {

    val (usernameOpt, groupOpt, rolesOpt) = getUserGroupRoles(userRoles, getHeader)

    def headersAsList =
      (usernameOpt.toList map (OrbeonUsernameHeaderName → Array(_))) :::
      (groupOpt.toList    map (OrbeonGroupHeaderName    → Array(_))) :::
      (rolesOpt.toList    map (OrbeonRolesHeaderName    →))

    usernameOpt match {
      case Some(username) ⇒
        val result = headersAsList
        Logger.debug(s"setting auth headers to: ${headersAsJSONString(result)}")
        result
      case None ⇒
        // Don't set any headers in case there is no username
        if (groupOpt.isDefined || (rolesOpt exists (_.nonEmpty)))
          Logger.warn(s"not setting auth headers because username is missing: $headersAsList")
        Nil
    }
  }
}
