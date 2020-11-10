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

import enumeratum._
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.externalcontext.{Credentials, ExternalContext, ServletPortletRequest, SimpleRole}
import org.orbeon.oxf.http.Headers
import org.orbeon.oxf.properties.Properties
import org.orbeon.oxf.webapp.UserRolesFacade
import org.orbeon.oxf.xforms.state.XFormsStateManager
import org.orbeon.oxf.xforms.XFormsUtils
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

  def getCredentialsAsHeadersUseSession(
    userRoles  : UserRolesFacade,
    session    : ExternalContext.Session,
    getHeader  : String => List[String]
  ): List[(String, Array[String])] = {

    getCredentialsUseSession(userRoles, session, getHeader) match {
      case Some(credentials) =>
        val result = Credentials.toHeaders(credentials)
        Logger.debug(s"setting auth headers to: ${headersAsJSONString(result)}")
        result
      case None =>
        // Don't set any headers in case there is no username
        Logger.warn(s"not setting credentials headers because credentials are not found")
        Nil
    }
  }

  private object Private {

    val Logger = LoggerFactory.getLogger("org.orbeon.auth")

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
    val HeaderStickyPropertyName            = PropertyPrefix + "header.sticky"

    val NameValueMatch = "([^=]+)=([^=]+)".r

    sealed abstract class AuthMethod(override val entryName: String) extends EnumEntry
    object AuthMethod extends Enum[AuthMethod] {
       val values = findValues
       case object Container extends AuthMethod("container")
       case object Header    extends AuthMethod("header")
    }

    // Get the username, group and roles from the request, based on the Form Runner configuration.
    // The first time this is called, the result is stored into the required session. The subsequent times,
    // the value stored in the session is retrieved. This ensures that authentication information remains an
    // invariant for a given session.
    //
    // See https://github.com/orbeon/orbeon-forms/issues/2464
    // See also https://github.com/orbeon/orbeon-forms/issues/2632
    def getCredentialsUseSession(
      userRoles  : UserRolesFacade,
      session    : ExternalContext.Session,
      getHeader  : String => List[String]
    ): Option[Credentials] = {

      val sessionCredentialsOpt = ServletPortletRequest.findCredentialsInSession(session)
      val newCredentialsOpt     = findCredentialsFromContainerOrHeaders(userRoles, getHeader)
      val propertySet           = Properties.instance.getPropertySet
      val sticky                = propertySet.getBoolean(HeaderStickyPropertyName, default = false)
      val updateSessionIfNew    = authMethod == AuthMethod.Header && ! sticky

      if (updateSessionIfNew || sessionCredentialsOpt.isEmpty) {
        if (newCredentialsOpt != sessionCredentialsOpt) {
          // Reset content of the session before we store the new credentials
          XFormsStateManager.sessionDestroyed(session)
          session.getAttributeNames().foreach(session.removeAttribute(_))
          XFormsStateManager.sessionCreated(session)
          ServletPortletRequest.storeCredentialsInSession(session, newCredentialsOpt)
        }
        newCredentialsOpt
      } else {
        sessionCredentialsOpt
      }
    }

    def headersAsJSONString(headers: List[(String, Array[String])]): String = {

      val headerAsJSONStrings =
        headers map {
          case (name, values) =>
            val valuesAsString = values.map(XFormsUtils.escapeJavaScript).mkString("""["""", """", """", """"]""")
            s""""$name": $valuesAsString"""
        }

      headerAsJSONStrings.mkString("{", ", ", "}")
    }

    def authMethod: AuthMethod = {

      val propertySet               = Properties.instance.getPropertySet
      val requestedAuthMethodString = propertySet.getString(MethodPropertyName, "container")
      val requestedAuthMethodOpt    = AuthMethod.withNameOption(requestedAuthMethodString)
      def unsupportedAuthMethod     = s"`$MethodPropertyName` property: unsupported authentication method `$requestedAuthMethodString`"

      requestedAuthMethodOpt.getOrElse(throw new OXFException(unsupportedAuthMethod))
    }

    def findCredentialsFromContainerOrHeaders(
      userRoles : UserRolesFacade,
      getHeader : String => List[String]
    ): Option[Credentials] = {

      val propertySet = Properties.instance.getPropertySet

      val requestedAuthMethod = propertySet.getString(MethodPropertyName, "container")

      AuthMethod.withNameOption(requestedAuthMethod) match {

        case Some(authMethod @ AuthMethod.Container) =>

          Logger.debug(s"using `$authMethod` method")

          val usernameOpt    = Option(userRoles.getRemoteUser)
          val rolesStringOpt = propertySet.getNonBlankString(ContainerRolesPropertyName)

          Logger.debug(s"usernameOpt: `$usernameOpt`, roles property: `$rolesStringOpt`")

          usernameOpt map { username =>

              // Wrap exceptions as Liferay throws if the role is not available instead of returning false
              def isUserInRole(role: String) =
                try userRoles.isUserInRole(role)
                catch { case NonFatal(_) => false}

              val rolesSplitRegex =
                propertySet.getString(ContainerRolesSplitPropertyName, """,|\s+""")

              val rolesList =
                for {
                  rolesString <- rolesStringOpt.toList
                  roleName    <- rolesString split rolesSplitRegex
                  if isUserInRole(roleName)
                } yield
                  SimpleRole(roleName)

              Credentials(
                username      = username,
                roles         = rolesList,
                group         = rolesList.headOption map (_.roleName),
                organizations = Nil
              )
          }

        case Some(authMethod @ AuthMethod.Header) =>

          Logger.debug(s"using `$authMethod` method")

          val headerPropertyName =
            propertySet.getNonBlankString(HeaderRolesPropertyNamePropertyName)

          def headerList(name: String) =
            propertySet.getNonBlankString(name).toList flatMap (p => getHeader(p.toLowerCase))

          val rolesSplit = propertySet.getString(HeaderRolesSplitPropertyName, """(\s*[,\|]\s*)+""")
          def splitRoles(value: String) = value split rolesSplit

          Logger.debug(s"using properties: $HeaderRolesPropertyNamePropertyName=`$headerPropertyName`, $HeaderRolesSplitPropertyName=`$rolesSplit`")

          // If configured, a header can have the form `name=value` where `name` is specified in a property
          def splitWithinRole(value: String) = headerPropertyName match {
            case Some(propertyName) =>
              value match {
                case NameValueMatch(`propertyName`, value) => List(value)
                case _                                     => Nil
              }
            case _ => List(value)
          }

          import org.orbeon.oxf.util.CoreUtils._

          // Credentials coming from the JSON-encoded HTTP header
          def fromCredentialsHeader =
            headerList(HeaderCredentialsPropertyName).headOption flatMap
            (Credentials.parseCredentials(_, decodeForHeader = true)) kestrel
            (_ => Logger.debug(s"found from credential headers"))

          // Credentials coming from individual headers (requires at least the username)
          def fromIndividualHeaders =
            headerList(HeaderUsernamePropertyName).headOption map { username =>

              // Roles: all headers with the given name are used, each header value is split, and result combined
              // See also: https://github.com/orbeon/orbeon-forms/issues/1690
              val roles =
                headerList(HeaderRolesPropertyName) flatMap splitRoles flatMap splitWithinRole map SimpleRole.apply

              Credentials(
                username      = username,
                roles         = roles,
                group         = headerList(HeaderGroupPropertyName).headOption,
                organizations = Nil
              )
            } kestrel
            (_ => Logger.debug(s"found from individual headers"))

          fromCredentialsHeader orElse fromIndividualHeaders

        case None =>
          throw new OXFException(s"'$MethodPropertyName' property: unsupported authentication method `$requestedAuthMethod`")
      }
    }
  }
}
