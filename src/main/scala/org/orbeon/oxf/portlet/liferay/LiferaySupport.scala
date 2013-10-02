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


import collection.JavaConverters._
import com.liferay.portal.kernel.language.LanguageUtil
import com.liferay.portal.model.User
import com.liferay.portal.util.PortalUtil
import javax.portlet._
import javax.servlet.http.HttpServletRequest
import org.orbeon.oxf.util.ScalaUtils._

object LiferaySupport {

    private val HeaderNamesGetters = List[(String, User ⇒ String)](
        "Orbeon-Liferay-User-Id"          → (_.getUserId.toString),
        "Orbeon-Liferay-User-Screen-Name" → (_.getScreenName),
        "Orbeon-Liferay-User-Full-Name"   → (_.getFullName),
        "Orbeon-Liferay-User-Email"       → (_.getEmailAddress),
        "Orbeon-Liferay-Roles"            → (_.getRoles.asScala map (_.getName) mkString ", ")
    )

    def userHeaders(request: HttpServletRequest) =
        for {
            user       ← Option(PortalUtil.getUser(request)).toList
            (name, fn) ← HeaderNamesGetters
            value      ← Option(fn(user))
        } yield
            name → value

    def languageHeader(request: PortletRequest) =
        nonEmptyOrNone(LanguageUtil.getLanguageId(request)) map ("Orbeon-Liferay-Language" →)
}
