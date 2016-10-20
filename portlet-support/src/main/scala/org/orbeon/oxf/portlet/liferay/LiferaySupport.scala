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

import java.{lang ⇒ jl, util ⇒ ju}
import javax.portlet._
import javax.servlet.http.HttpServletRequest

import com.liferay.portal.kernel.language.LanguageUtil
import org.orbeon.oxf.fr.{Organization, Organizations, ParametrizedRole, SimpleRole}
import org.orbeon.oxf.util.StringUtils._

import scala.collection.JavaConverters._
import scala.util.{Success, Try}

trait LiferayUser {
  // Return Liferay user, group and role information as headers. There can be multiple role headers.
  def userHeaders: List[(String, String)]
}

object LiferaySupport {

  // These are the standard organization roles in Liferay (also in Liferay `RoleConstants.java`)
  sealed trait LiferayOrganizationRoles                           { def name: String                           }
  case  object LiferayOrganizationOwnerRoleName           extends { val name = "Organization Owner"            } with LiferayOrganizationRoles
  case  object LiferayOrganizationAdministratorRoleName   extends { val name = "Organization Administrator"    } with LiferayOrganizationRoles
  case  object LiferayOrganizationContentReviewerRoleName extends { val name = "Organization Content Reviewer" } with LiferayOrganizationRoles

  sealed trait LiferayRoleType                     { def value: Int }
  case  object LiferayRegularRoleType      extends { val value = 1  } with LiferayRoleType
  case  object LiferaySiteRoleType         extends { val value = 2  } with LiferayRoleType
  case  object LiferayOrganizationRoleType extends { val value = 3  } with LiferayRoleType

  // For https://github.com/orbeon/orbeon-forms/issues/2843
  // We must abstract over code which changed packages between Liferay 6.2 and 7.0. We achieve this
  // using Java reflection.
  private val LiferayPackages = List("com.liferay.portal.kernel", "com.liferay.portal")

  private def liferayClass(suffix: String) =
    LiferayPackages.iterator                      map
      (_ + '.' + suffix)                          map
      (className ⇒ Try(Class.forName(className))) collectFirst
      { case Success(clazz) ⇒ clazz }             get

  private def liferayMethod(suffix: String, name: String, types: Class[_]*) =
    liferayClass(suffix).getMethod(name, types: _*)

  // Static methods. We can't use structural types as the methods are static.
  private lazy val getHttpServletRequestMethod = liferayMethod("util.PortalUtil",                       "getHttpServletRequest", classOf[PortletRequest])
  private lazy val getUserMethod               = liferayMethod("util.PortalUtil",                       "getUser",               classOf[HttpServletRequest])
  private lazy val getUserOrganizationsMethod  = liferayMethod("service.OrganizationLocalServiceUtil",  "getUserOrganizations",  classOf[Long])
  private lazy val getUserGroupRolesMethod     = liferayMethod("service.UserGroupRoleLocalServiceUtil", "getUserGroupRoles",     classOf[Long])
  private lazy val hasUserGroupRoleMethod      = liferayMethod("service.UserGroupRoleLocalServiceUtil", "hasUserGroupRole",      classOf[Long], classOf[Long], classOf[String])

  def getHttpServletRequest(req: PortletRequest): HttpServletRequest =
    getHttpServletRequestMethod.invoke(null, req).asInstanceOf[HttpServletRequest]

  def getUser(req: HttpServletRequest): UserFacade =
    getUserMethod.invoke(null, req).asInstanceOf[UserFacade]

  // A user can belong to multiple organizations. Each such organization can be part of
  // a hierarchy. Users can be part of more than one organization which is part of the
  // same hierarchy (also checked experimentally):
  //
  // [
 	//   ["Liferay, Inc.", "Liferay Engineering"],
 	//   ["Liferay, Inc.", "Liferay San Francisco"],
 	//   ["Orbeon World", "Orbeon California", "Orbeon Foster City"]
  // ]
  //
  def getUserOrganizations(userId: Long): List[OrganizationFacade] =
    getUserOrganizationsMethod.invoke(null, new jl.Long(userId)).asInstanceOf[ju.List[OrganizationFacade]].asScala.to[List]

  def getUserGroupRoles(userId: Long): List[UserGroupRoleFacade] =
    getUserGroupRolesMethod.invoke(null, new jl.Long(userId)).asInstanceOf[ju.List[UserGroupRoleFacade]].asScala.to[List]

  def hasUserGroupRoleMethod(userId: Long, organizationGroupId: Long, roleName: String): Boolean =
    hasUserGroupRoleMethod.invoke(null, new jl.Long(userId), new jl.Long(organizationGroupId), roleName).asInstanceOf[jl.Boolean]

  private[liferay] type OrganizationFacade = {
    def getName        : String
    def getAncestors   : ju.List[AnyRef] // the compiler is unhappy about `OrganizationFacade` in the result type
    def getGroup       : GroupFacade
  }

  // Facade types as structural types so that we get Java reflective calls with little boilerplate!
  // NOTE: Package private for tests.
  private[liferay] type UserFacade = {
    def getUserId       : Long
    def getScreenName   : String
    def getFullName     : String
    def getEmailAddress : String
    def getGroup        : GroupFacade
    def getRoles        : ju.List[RoleFacade]
  }

  private[liferay] type GroupFacade = {
    def getGroupId         : Long
    def getName            : String
    def getDescriptiveName : String
  }

  private[liferay] type RoleFacade = {
    def getName         : String
    def getType         : Int
    def toString        : String
  }

  private[liferay] type UserGroupRoleFacade = {
    def getGroup        : GroupFacade
    def getRole         : RoleFacade
    def getUser         : UserFacade
  }

  private def ancestorOrSelfLiferayOrgsForUser(u: UserFacade): List[List[OrganizationFacade]] =
    getUserOrganizations(u.getUserId) map { org ⇒

      var orgNamesRootToLeaf: List[OrganizationFacade] = List(org)

      for (name ← org.getAncestors.asScala map (_.asInstanceOf[OrganizationFacade]))
        orgNamesRootToLeaf = name :: orgNamesRootToLeaf

      orgNamesRootToLeaf
    }

  def getCredentialsAsSerializedJson(u: UserFacade): String = {

    val organizations =
      ancestorOrSelfLiferayOrgsForUser(u) map { orgs ⇒
        Organization(orgs map (_.getName))
      }

    val simpleRoles =
      for {
        role ← u.getRoles.asScala
        roleName = role.getName
      } yield
         SimpleRole(roleName)

    val parametrizedRoles =
      for {
        rootOrgs ← ancestorOrSelfLiferayOrgsForUser(u)
        org      ← rootOrgs
        group    = org.getGroup
        role     ← List(LiferayOrganizationOwnerRoleName, LiferayOrganizationAdministratorRoleName)
        roleName = role.name
        hasRole  = hasUserGroupRoleMethod(u.getUserId, group.getGroupId, roleName)
      } yield
        ParametrizedRole(roleName, org.getName)

    val orbeonCredentialsJson =
      Organizations.serializeCredentials(
        username        = u.getScreenName,
        userRoles       = simpleRoles ++: parametrizedRoles,
        groups          = Option(u.getGroup) map (_.getDescriptiveName) toList,
        organizations   = organizations,
        encodeForHeader = true
      )

    orbeonCredentialsJson.compactPrint
  }

  private val HeaderNamesGetters = List[(String, UserFacade ⇒ List[String])](
    "Orbeon-Liferay-User-Id"          → (u ⇒ Option(u.getUserId)                          map (_.toString)            toList),
    "Orbeon-Liferay-User-Screen-Name" → (u ⇒ Option(u.getScreenName)                                                  toList),
    "Orbeon-Liferay-User-Full-Name"   → (u ⇒ Option(u.getFullName)                                                    toList),
    "Orbeon-Liferay-User-Email"       → (u ⇒ Option(u.getEmailAddress)                                                toList),
    "Orbeon-Liferay-User-Group-Id"    → (u ⇒ Option(u.getGroup)                           map (_.getGroupId.toString) toList),
    "Orbeon-Liferay-User-Group-Name"  → (u ⇒ Option(u.getGroup)                           map (_.getDescriptiveName)  toList),
    "Orbeon-Liferay-User-Roles"       → (u ⇒ u.getRoles.asScala                           map (_.getName)             toList),
    "Orbeon-Liferay-User-Credentials" → (u ⇒ List(getCredentialsAsSerializedJson(u))                                        )
  )

  private val AllHeaderNamesList       = HeaderNamesGetters map (_._1)
  private val AllHeaderNamesLowerList  = AllHeaderNamesList map (_.toLowerCase)

  val AllHeaderNamesLower              = AllHeaderNamesLowerList.toSet
  val AllHeaderNamesLowerToCapitalized = AllHeaderNamesLowerList zip AllHeaderNamesList toMap

  def languageHeader(req: PortletRequest) =
    LanguageUtil.getLanguageId(req).trimAllToOpt map ("Orbeon-Liferay-Language" →)

  def userHeaders(user: UserFacade, tests: Boolean = false): List[(String, String)] =
    for {
      (name, getter) ← HeaderNamesGetters
      if ! (tests && name == "Orbeon-Liferay-User-Credentials") // we can't yet make this work during tests
      value          ← getter(user)
    } yield
      name → value

  def getLiferayUser(req: PortletRequest): Option[LiferayUser] =
    Option(getUser(getHttpServletRequest(req))) map { user ⇒
      new LiferayUser {
        def userHeaders = LiferaySupport.userHeaders(user)
      }
    }
}
