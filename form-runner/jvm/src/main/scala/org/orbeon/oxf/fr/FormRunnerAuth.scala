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

import cats.data.NonEmptyList
import enumeratum.*
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.externalcontext.*
import org.orbeon.oxf.fr.auth.RolesProviderFactory
import org.orbeon.oxf.http.Headers
import org.orbeon.oxf.properties.{Properties, PropertySet}
import org.orbeon.oxf.util.CoreUtils.*
import org.orbeon.oxf.util.MarkupUtils.*
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.webapp.{OrbeonSessionListener, UserRolesFacade}
import org.orbeon.oxf.xforms.state.XFormsStateManager
import org.slf4j.LoggerFactory

import scala.util.control.NonFatal


object FormRunnerAuth {

  // Only used to determine external headers to remove
  val AllAuthHeaderNames = Set(
    Headers.OrbeonUsername,
    Headers.OrbeonGroup,
    Headers.OrbeonRoles,
    Headers.OrbeonCredentials
  )

  import Private.*

  // We're using a distinct getAttribute parameter instead of adding getAttribute to the UserRolesFacade structural
  // type as doing so leads to weird runtime errors (NoClassDefFoundError related to javax vs jakarta packages). This
  // is not the first time it happened and a hint that we should probably avoid structural types altogether.

  def getCredentialsAsHeadersUseSession(
    userRoles   : UserRolesFacade,
    getAttribute: String => AnyRef,
    session     : ExternalContext.Session,
    getHeader   : String => List[String]
  ): List[(String, NonEmptyList[String])] =
    getCredentialsUseSession(userRoles, getAttribute, session, getHeader) match {
      case Some(credentials) =>
        val result = CredentialsSerializer.toHeaders[List](credentials)
        Logger.debug(s"setting auth headers to: ${headersAsJSONString(result)}")
        result.map { case (name, values) => (name, NonEmptyList.fromListUnsafe(values)) } // we "know" the `List`s returned by `toHeaders()` are not empty
      case None =>
        // Don't set any headers in case there is no username
        Logger.debug(s"not setting credentials headers because credentials are not found")
        Nil
    }

  def fromHeaderValues(
    credentialsOpt: => Option[String],
    usernameOpt   : => Option[String],
    rolesList     : => List[String],
    groupOpt      : => Option[String]
  ): Option[Credentials] =
    fromCredentialsHeader(credentialsOpt) orElse
      fromIndividualHeaders(
        usernameOpt = usernameOpt,
        rolesList   = rolesList,
        groupOpt    = groupOpt
      )

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
    // invariant for a given session. However, in the case of non-sticky headers, the incoming headers are
    // checked at each request for changes and can cause the session content to be cleared.
    //
    // See:
    //
    // - https://github.com/orbeon/orbeon-forms/issues/2464
    // - https://github.com/orbeon/orbeon-forms/issues/2632
    // - https://github.com/orbeon/orbeon-forms/issues/4436
    //
    def getCredentialsUseSession(
      userRoles   : UserRolesFacade,
      getAttribute: String => AnyRef,
      session     : ExternalContext.Session,
      getHeader   : String => List[String]
    ): Option[Credentials] = {

      val sessionCredentialsOpt = ServletPortletRequest.findCredentialsInSession(session)

      lazy val stickyHeadersConfigured =
        Properties.instance.getPropertySet.getBoolean(HeaderStickyPropertyName, default = false)

      lazy val newCredentialsOpt = findCredentialsFromContainerOrHeaders(userRoles, getAttribute, getHeader)

      def storeAndReturnNewCredentials(): Option[Credentials] = {
        ServletPortletRequest.storeCredentialsInSession(session, newCredentialsOpt)
        newCredentialsOpt
      }

      def clearAndInitializeSessionContent(): Unit = {
        XFormsStateManager.sessionDestroyed(session)
        OrbeonSessionListener.sessionListenersDestroy(session)

        session.getAttributeNames().foreach(session.removeAttribute(_))

        OrbeonSessionListener.sessionListenersCreate(session)
        XFormsStateManager.sessionCreated(session)
      }

      if (sessionCredentialsOpt.isEmpty && newCredentialsOpt.nonEmpty) {
        // Covers the case of going from anonymous to having credentials
        storeAndReturnNewCredentials()
      } else if (authMethod == AuthMethod.Header && ! stickyHeadersConfigured && newCredentialsOpt != sessionCredentialsOpt) {
        // Covers the case of an existing login where the user changes (different "non-sticky" headers) or the
        // case of a user logging out (via headers, as in the case of container auth a logout is implemented
        // by destroying the session).
        clearAndInitializeSessionContent()
        storeAndReturnNewCredentials()
      } else {
        sessionCredentialsOpt
      }
    }

    def headersAsJSONString(headers: Iterable[(String, Iterable[String])]): String = {

      val headerAsJSONStrings =
        headers map {
          case (name, values) =>
            val valuesAsString = values.map(_.escapeJavaScript).mkString("""["""", """", """", """"]""")
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
      userRoles   : UserRolesFacade,
      getAttribute: String => AnyRef,
      getHeader   : String => List[String]
    ): Option[Credentials] = {

      val propertySet = Properties.instance.getPropertySet

      val requestedAuthMethod = propertySet.getString(MethodPropertyName, "container")

      AuthMethod.withNameOption(requestedAuthMethod) match {

        case Some(authMethod @ AuthMethod.Container) =>

          Logger.debug(s"using `$authMethod` method")

           containerCredentialsOpt(userRoles, propertySet).map { credentials =>

             // Merge roles from container credentials and roles providers
             val rolesFromProvider = RolesProviderFactory.getRoles(getAttribute)
             credentials.copy(roles = (credentials.roles ++ rolesFromProvider).distinct)
           }

        case Some(authMethod @ AuthMethod.Header) =>

          Logger.debug(s"using `$authMethod` method")

          def headerList(propertyName: String): List[String] =
            propertySet.getNonBlankString(propertyName).toList.flatMap(getHeader)

          fromHeaderValues(
            credentialsOpt = headerList(HeaderCredentialsPropertyName).headOption,
            usernameOpt    = headerList(HeaderUsernamePropertyName).headOption,
            rolesList      = headerList(HeaderRolesPropertyName),
            groupOpt       = headerList(HeaderGroupPropertyName).headOption
          )

        case None =>
          throw new OXFException(s"'$MethodPropertyName' property: unsupported authentication method `$requestedAuthMethod`")
      }
    }

    private def containerCredentialsOpt(userRoles : UserRolesFacade, propertySet: PropertySet): Option[Credentials] = {
      val usernameOpt    = Option(userRoles.getRemoteUser()).flatMap(_.trimAllToOpt)
      val rolesStringOpt = propertySet.getNonBlankString(ContainerRolesPropertyName)

      Logger.debug(s"usernameOpt: `$usernameOpt`, roles property: `$rolesStringOpt`")

      usernameOpt map { username =>

        // Wrap exceptions as Liferay throws if the role is not available instead of returning false
        def isUserInRole(role: String) =
          try userRoles.isUserInRole(role)
          catch { case NonFatal(_) => false }

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
          userAndGroup  = UserAndGroup.fromStringsOrThrow(username, rolesList.headOption.map(_.roleName).getOrElse("")),
          roles         = rolesList,
          organizations = Nil
        )
      }
    }

    // Credentials coming from the JSON-encoded HTTP header
    def fromCredentialsHeader(
      headerValueOpt: => Option[String]
    ): Option[Credentials] =
      headerValueOpt flatMap (CredentialsParser.parseCredentials(_, decodeForHeader = true)) kestrel
        (_ => Logger.debug(s"found from credential headers"))

    // Credentials coming from individual headers (requires at least the username)
    def fromIndividualHeaders(
      usernameOpt: => Option[String],
      rolesList  : => List[String],
      groupOpt   : => Option[String]
    ): Option[Credentials] =
      usernameOpt map { username =>
        // Roles: all headers with the given name are used, each header value is split, and result combined
        // See also: https://github.com/orbeon/orbeon-forms/issues/1690

        val propertySet = Properties.instance.getPropertySet

        val headerPropertyName =
          propertySet.getNonBlankString(HeaderRolesPropertyNamePropertyName)

        // If configured, a header can have the form `name=value` where `name` is specified in a property
        def splitWithinRole(value: String) = headerPropertyName match {
          case Some(propertyName) =>
            value match {
              case NameValueMatch(`propertyName`, value) => List(value)
              case _                                     => Nil
            }
          case _ => List(value)
        }

        def splitRoles(value: String): Array[String] =
          propertySet.getPattern(HeaderRolesSplitPropertyName, """(\s*[,\|]\s*)+""").split(value)

        Credentials(
          userAndGroup  = UserAndGroup.fromStringsOrThrow(username, groupOpt.getOrElse("")),
          roles         = rolesList flatMap splitRoles flatMap splitWithinRole map SimpleRole.apply,
          organizations = Nil
        )
      } kestrel
        (_ => Logger.debug(s"found from individual headers"))
  }
}
