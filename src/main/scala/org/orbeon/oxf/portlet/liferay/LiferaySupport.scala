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
import javax.servlet.http.HttpServletRequest

import com.liferay.portal.kernel.language.LanguageUtil
import org.orbeon.oxf.util.ScalaUtils._

import scala.collection.JavaConverters._
import scala.util.Try

import java.{util ⇒ ju}

trait LiferayUser {
  // Return Liferay user, group and role information as headers. There can be multiple role headers.
  def userHeaders: List[(String, String)]
}

object LiferaySupport {

  // For https://github.com/orbeon/orbeon-forms/issues/2843
  // We must abstract over code which changed packages between Liferay 6.2 and 7.0. We achieve this
  // using Java reflection.

  // Abstract `PortalUtil` static methods which we use
  private val (getHttpServletRequestMethod, getUserMethod) = {

    val portalUtilClass =
      Try(Class.forName("com.liferay.portal.kernel.util.PortalUtil")) getOrElse
        Class.forName("com.liferay.portal.util.PortalUtil")

    portalUtilClass.getMethod("getHttpServletRequest", classOf[PortletRequest]) →
    portalUtilClass.getMethod("getUser", classOf[HttpServletRequest])
  }

  def getHttpServletRequest(req: PortletRequest): HttpServletRequest =
    getHttpServletRequestMethod.invoke(req).asInstanceOf[HttpServletRequest]

  def getUser(req: HttpServletRequest): UserFacade =
    getUserMethod.invoke(req).asInstanceOf[UserFacade]

  // Facade types as structural types so that we get Java reflective calls with little boilerplate!
  private type UserFacade = {
    def getUserId       : Long
    def getScreenName   : String
    def getFullName     : String
    def getEmailAddress : String
    def getGroup        : GroupFacade
    def getRoles        : ju.List[RoleFacade]
  }

  private type GroupFacade = {
    def getName         : String
    def getGroupId      : Long
  }

  private type RoleFacade = {
    def getName         : String
  }

  // Headers
  private val HeaderNamesGetters = List[(String, UserFacade ⇒ List[String])](
    "Orbeon-Liferay-User-Id"          → (u ⇒ Option(u.getUserId) map (_.toString) toList),
    "Orbeon-Liferay-User-Screen-Name" → (u ⇒ Option(u.getScreenName).toList),
    "Orbeon-Liferay-User-Full-Name"   → (u ⇒ Option(u.getFullName).toList),
    "Orbeon-Liferay-User-Email"       → (u ⇒ Option(u.getEmailAddress).toList),
    "Orbeon-Liferay-User-Group-Id"    → (u ⇒ Option(u.getGroup) map (_.getGroupId.toString) toList),
    "Orbeon-Liferay-User-Group-Name"  → (u ⇒ Option(u.getGroup) map (_.getName) toList),
    "Orbeon-Liferay-User-Roles"       → (u ⇒ u.getRoles.asScala map (_.getName) toList)
  )

  val AllHeaderNames                   = HeaderNamesGetters map (_._1) toSet
  val AllHeaderNamesLower              = AllHeaderNames map (_.toLowerCase)
  val AllHeaderNamesLowerToCapitalized = AllHeaderNamesLower zip AllHeaderNames toMap

  def languageHeader(req: PortletRequest) =
    LanguageUtil.getLanguageId(req).trimAllToOpt map ("Orbeon-Liferay-Language" →)

  def getLiferayUser(req: PortletRequest): Option[LiferayUser] =
    Option(getUser(getHttpServletRequest(req))) map { user ⇒
      new LiferayUser {
        def userHeaders: List[(String, String)] =
          for {
            (name, getter) ← HeaderNamesGetters
            value          ← getter(user)
          } yield
            name → value
      }
    }
}
