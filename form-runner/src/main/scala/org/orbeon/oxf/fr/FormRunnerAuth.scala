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
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.webapp.{ServletPortletRequest, SessionFacade, UserRolesFacade}
import org.slf4j.LoggerFactory

import scala.util.control.NonFatal

object FormRunnerAuth {

  val AllHeaderNamesLower = Set(
    Headers.OrbeonUsernameLower,
    Headers.OrbeonGroupLower,
    Headers.OrbeonRolesLower,
    Headers.OrbeonCredentialsLower
  )

  import Private._

  // Get the username, group and roles from the request, based on the Form Runner configuration.
  // The first time this is called, the result is stored into the required session. The subsequent times,
  // the value stored in the session is retrieved. This ensures that authentication information remains an
  // invariant for a given session.
  //
  // See https://github.com/orbeon/orbeon-forms/issues/2464
  // See also https://github.com/orbeon/orbeon-forms/issues/2632
  def getCredentialsUseSession(
    userRoles  : UserRolesFacade,
    session    : SessionFacade,
    getHeader  : String ⇒ List[String]
  ): Option[Credentials] =
    ServletPortletRequest.findCredentialsInSession(session) orElse {

      val newCredentialsOpt = findCredentialsFromHeaders(userRoles, getHeader)

      // Only store the information into the session if we get user credentials. This handles the case of the initial
      // login. See: https://github.com/orbeon/orbeon-forms/issues/2732
      newCredentialsOpt foreach
        (ServletPortletRequest.storeCredentialsInSession(session, _))

      newCredentialsOpt
    }

  def getCredentialsAsHeadersUseSession(
    userRoles  : UserRolesFacade,
    session    : SessionFacade,
    getHeader  : String ⇒ List[String]
  ): List[(String, Array[String])] = {

    getCredentialsUseSession(userRoles, session, getHeader) match {
      case Some(credentials) ⇒
        val result = Credentials.toHeaders(credentials)
        Logger.debug(s"setting auth headers to: ${headersAsJSONString(result)}")
        result
      case None ⇒
        // Don't set any headers in case there is no username
        Logger.warn(s"not setting auth headers because username is missing")
        Nil
    }
  }

  private object Private {

    val LoggerName = "org.orbeon.auth"
    val Logger     = LoggerFactory.getLogger(LoggerName)

    val PropertyPrefix                      = "oxf.fr.authentication."

    val MethodPropertyName                  = PropertyPrefix + "method"
    val ContainerRolesPropertyName          = PropertyPrefix + "container.roles"
    val ContainerRolesSplitPropertyName     = PropertyPrefix + "container.roles.split"
    val HeaderUsernamePropertyName          = PropertyPrefix + "header.username"
    val HeaderRolesPropertyName             = PropertyPrefix + "header.roles"
    val HeaderRolesSplitPropertyName        = PropertyPrefix + "header.roles.split"
    val HeaderGroupPropertyName             = PropertyPrefix + "header.group"
    val HeaderRolesPropertyNamePropertyName = PropertyPrefix + "header.roles.property-name"
    val HeaderCredentialsPropertyName       = PropertyPrefix + "header.credentials"

    val NameValueMatch = "([^=]+)=([^=]+)".r

    def properties: PropertySet = Properties.instance.getPropertySet

    def headersAsJSONString(headers: List[(String, Array[String])]) = {

      val headerAsJSONStrings =
        headers map {
          case (name, values) ⇒
            val valuesAsString = values.mkString("""["""", """", """", """"]""")
            s""""$name": $valuesAsString"""
        }

      headerAsJSONStrings.mkString("{", ", ", "}")
    }

    def findCredentialsFromHeaders(
      userRoles : UserRolesFacade,
      getHeader : String ⇒ List[String]
    ): Option[Credentials] = {

      val propertySet = properties
      propertySet.getString(MethodPropertyName, "container") match {

        case "container" ⇒

          val usernameOpt    = Option(userRoles.getRemoteUser)
          val rolesStringOpt = Option(propertySet.getString(ContainerRolesPropertyName))

          rolesStringOpt match {
            case None ⇒
              None
            case Some(rolesString) ⇒

              // Wrap exceptions as Liferay throws if the role is not available instead of returning false
              def isUserInRole(role: String) =
                try userRoles.isUserInRole(role)
                catch { case NonFatal(_) ⇒ false}

              val rolesSplit =
                propertySet.getString(ContainerRolesSplitPropertyName, """,|\s+""")

              val rolesArray =
                for {
                  roleName ← rolesString split rolesSplit
                  if isUserInRole(roleName)
                } yield
                  SimpleRole(roleName)

              val roles = rolesArray match {
                case Array() ⇒ None
                case array   ⇒ Some(array)
              }

              usernameOpt map { username ⇒
                Credentials(
                  username     = username,
                  roles        = rolesArray.to[List],
                  group        = rolesArray.headOption map (_.roleName),
                  organization = None
                )
              }
          }

        case "header" ⇒

          val headerPropertyName =
            propertySet.getString(HeaderRolesPropertyNamePropertyName).trimAllToOpt

          def headerList(name: String) =
            Option(propertySet.getString(name)).toList flatMap (p ⇒ getHeader(p.toLowerCase))

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

          // Credentials coming from the JSON-encoded HTTP header
          def fromCredentialsHeader =
            headerList(HeaderCredentialsPropertyName).headOption flatMap
              (Organizations.parseCredentials(_, decodeForHeader = true))

          // Credentials coming from individual headers (requires at least the username)
          def fromIndividualHeaders =
            headerList(HeaderUsernamePropertyName).headOption map { username ⇒

              // Roles: all headers with the given name are used, each header value is split, and result combined
              // See also: https://github.com/orbeon/orbeon-forms/issues/1690
              val roles =
                headerList(HeaderRolesPropertyName) flatMap splitRoles flatMap splitWithinRole map SimpleRole.apply

              Credentials(
                username     = username,
                roles        = roles,
                group        = headerList(HeaderGroupPropertyName).headOption,
                organization = None
              )
            }

          fromCredentialsHeader orElse fromIndividualHeaders

        case other ⇒
          throw new OXFException(s"'$MethodPropertyName' property: unsupported authentication method `$other`")
      }
    }
  }
}
