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
import javax.portlet._
import org.orbeon.oxf.util.ScalaUtils._

object LiferaySupport {

    private val HeaderNamesGetters = List[(String, User ⇒ List[String])](
        "Orbeon-Liferay-User-Id"          → (u ⇒ Option(u.getUserId) map (_.toString) toList),
        "Orbeon-Liferay-User-Screen-Name" → (u ⇒ Option(u.getScreenName).toList),
        "Orbeon-Liferay-User-Full-Name"   → (u ⇒ Option(u.getFullName).toList),
        "Orbeon-Liferay-User-Email"       → (u ⇒ Option(u.getEmailAddress).toList),
        "Orbeon-Liferay-User-Group-Id"    → (u ⇒ Option(u.getGroup) map (_.getGroupId.toString) toList),
        "Orbeon-Liferay-User-Group-Name"  → (u ⇒ Option(u.getGroup) map (_.getName) toList),
        "Orbeon-Liferay-User-Roles"       → (u ⇒ u.getRoles.asScala map (_.getName) toList)
    )

    val AllHeaderNamesLower = HeaderNamesGetters map (_._1.toLowerCase) toSet

    // Return Liferay user, group and role information as headers. There can be multiple role headers.
    def userHeaders(user: User): List[(String, String)] =
        for {
            (name, getter) ← HeaderNamesGetters
            value          ← getter(user)
        } yield
            name → value

    def languageHeader(request: PortletRequest) =
        nonEmptyOrNone(LanguageUtil.getLanguageId(request)) map ("Orbeon-Liferay-Language" →)
}
