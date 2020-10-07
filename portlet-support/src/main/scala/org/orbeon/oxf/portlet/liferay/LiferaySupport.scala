/**
 * Copyright (C) 2013 Orbeon, Inc.
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
package org.orbeon.oxf.portlet.liferay

import javax.portlet._
import com.liferay.portal.kernel.language.LanguageUtil
import org.orbeon.oxf.externalcontext.{Credentials, CredentialsSupport, Organization, ParametrizedRole, SimpleRole}
import org.orbeon.oxf.util.StringUtils._

import scala.jdk.CollectionConverters._

trait LiferayUser {
  // Return Liferay user, group and role information as headers. There can be multiple role headers.
  def userHeaders: List[(String, String)]
}

object LiferaySupport {

  import LiferayAPI._

  def getCredentialsAsSerializedJson(u: UserFacade, c: CompanyFacade): String = {

    def ancestorOrSelfLiferayOrgsForUser(u: UserFacade): List[List[OrganizationFacade]] =
      getUserOrganizations(u.getUserId) map { org =>

        var orgNamesRootToLeaf: List[OrganizationFacade] = List(org)

        for (name <- org.getAncestors.asScala map (_.asInstanceOf[OrganizationFacade]))
          orgNamesRootToLeaf = name :: orgNamesRootToLeaf

        orgNamesRootToLeaf
      }

    val ancestorOrSelfLiferayOrgs = ancestorOrSelfLiferayOrgsForUser(u)

    val simpleRoles =
      for {
        role <- u.getRoles.asScala
        roleName = role.getName
      } yield
         SimpleRole(roleName)

    val parametrizedRoles =
      for {
        rootOrgs <- ancestorOrSelfLiferayOrgs
        org      <- rootOrgs
        group    = org.getGroup
        role     <- List(LiferayOrganizationOwnerRoleName, LiferayOrganizationAdministratorRoleName)
        roleName = role.name
        if hasUserGroupRoleMethod(u.getUserId, group.getGroupId, roleName)
      } yield
        ParametrizedRole(roleName, org.getName)

    val username = c.getAuthType match {
      case LiferayEmailAddressAuthType.name => u.getEmailAddress
      case LiferayUserIdAuthType.name       => u.getUserId.toString
      case LiferayScreenNameAuthType.name   => u.getScreenName
    }

    CredentialsSupport.serializeCredentials(
      Credentials(
        username      = username,
        group         = Option(u.getGroup) map (_.getDescriptiveName),
        roles         = simpleRoles ++: parametrizedRoles,
        organizations = ancestorOrSelfLiferayOrgs map(org => Organization(org map (_.getName)))
      ),
      encodeForHeader = true
    )
  }

  private val HeaderNamesGetters = List[(String, (UserFacade, CompanyFacade) => List[String])](
    "Orbeon-Liferay-User-Id"          -> ((u, _) => Option(u.getUserId)                          map (_.toString)            toList),
    "Orbeon-Liferay-User-Screen-Name" -> ((u, _) => Option(u.getScreenName)                                                  toList),
    "Orbeon-Liferay-User-Full-Name"   -> ((u, _) => Option(u.getFullName)                                                    toList),
    "Orbeon-Liferay-User-First-Name"  -> ((u, _) => Option(u.getFirstName)                                                   toList),
    "Orbeon-Liferay-User-Middle-Name" -> ((u, _) => Option(u.getMiddleName)                                                  toList),
    "Orbeon-Liferay-User-Last-Name"   -> ((u, _) => Option(u.getLastName)                                                    toList),
    "Orbeon-Liferay-User-Email"       -> ((u, _) => Option(u.getEmailAddress)                                                toList),
    "Orbeon-Liferay-User-Group-Id"    -> ((u, _) => Option(u.getGroup)                           map (_.getGroupId.toString) toList),
    "Orbeon-Liferay-User-Group-Name"  -> ((u, _) => Option(u.getGroup)                           map (_.getDescriptiveName)  toList),
    "Orbeon-Liferay-User-Roles"       -> ((u, _) => u.getRoles.asScala                           map (_.getName)             toList),
    "Orbeon-Liferay-User-Credentials" -> ((u, c) => List(getCredentialsAsSerializedJson(u, c))                                     )
  )

  private val AllHeaderNamesList       = HeaderNamesGetters map (_._1)
  private val AllHeaderNamesLowerList  = AllHeaderNamesList map (_.toLowerCase)

  val AllHeaderNamesLower              = AllHeaderNamesLowerList.toSet
  val AllHeaderNamesLowerToCapitalized = AllHeaderNamesLowerList zip AllHeaderNamesList toMap

  // TODO: Use LanguageUtil.getBCP47LanguageId.
  def languageHeader(req: PortletRequest) =
    LanguageUtil.getLanguageId(req).trimAllToOpt map ("Orbeon-Liferay-Language" ->)

  def userHeaders(user: UserFacade, company: CompanyFacade, tests: Boolean): List[(String, String)] =
    for {
      (name, getter) <- HeaderNamesGetters
      if ! (tests && name == "Orbeon-Liferay-User-Credentials") // we can't yet make this work during tests
      value          <- getter(user, company)
    } yield
      name -> value

  def getLiferayUser(req: PortletRequest): Option[LiferayUser] = {

    val httpReq = getHttpServletRequest(req)

    Option(getUser(httpReq)) map { user =>
      new LiferayUser {
        def userHeaders = LiferaySupport.userHeaders(user, getCompany(httpReq), tests = false)
      }
    }
  }
}
