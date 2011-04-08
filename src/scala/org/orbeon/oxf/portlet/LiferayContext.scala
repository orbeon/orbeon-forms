/**
 * Copyright (C) 2011 Orbeon, Inc.
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
package org.orbeon.oxf.portlet

import com.liferay.portal.util.PortalUtil
import org.orbeon.oxf.pipeline.api.ExternalContext
import javax.portlet.PortletRequest

/**
 * Custom context for Liferay.
 */
class LiferayContext() extends CustomContext {

    def amendRequest(portletRequest: PortletRequest, request: ExternalContext.Request) {
        // NOTE: request.getRemoteUser() can be configured in liferay-portlet.xml with user-principal-strategy to
        // either userId (a number) or screenName (a string). It seems more reliable to use the API below to obtain the
        // user.
        val user = PortalUtil.getUser(PortalUtil.getHttpServletRequest(portletRequest))
        if (user ne null) {
            Map(
                ("email" -> user.getEmailAddress),
                ("full-name" -> user.getFullName)
            ) foreach {
                case (name, value: String) =>
                    // Store both as request attribute and header for convenience
                    // Use the "dot" convention for request, and the "dash" convention for headers
                    // Make sure headers names are in lowercase to facilitate comparisons
                    val prefix = "orbeon.liferay.user."
                    request.getAttributesMap.put(prefix + name toLowerCase, value)
                    request.getHeaderValuesMap.put(prefix + value split "[.-]" mkString "-" toLowerCase, Array(value))
                case _ =>
            }
        }
    }
}

