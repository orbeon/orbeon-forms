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
import javax.portlet.PortletRequest
import com.liferay.portal.model.User
import javax.portlet.filter.PortletRequestWrapper
import scala.collection.JavaConversions._

/**
 * Custom context for Liferay.
 */
class LiferayContext() extends CustomContext {

    def amendRequest(portletRequest: PortletRequest): PortletRequest = {
        // NOTE: request.getRemoteUser() can be configured in liferay-portlet.xml with user-principal-strategy to
        // either userId (a number) or screenName (a string). It seems more reliable to use the API below to obtain the
        // user.
        PortalUtil.getUser(PortalUtil.getHttpServletRequest(portletRequest)) match {
            case user: User => amendRequest(portletRequest, user)
            case _ => portletRequest
        }
    }

    def amendRequest(portletRequest: PortletRequest, user: User): PortletRequest = {

        val headers: Map[String, Array[String]] = for {
            (name, value) <- Map(
                ("email" -> user.getEmailAddress),
                ("full-name" -> user.getFullName)
            )
            if value ne null
        } yield {
            val prefix = "orbeon.liferay.user."
            // Store as request attribute with the "dot" convention
            portletRequest.setAttribute(prefix + name toLowerCase, value)
            // Return header tuple with header name in lowercase and with the "dash" convention
            (prefix + name split "[.-]" mkString "-" toLowerCase, Array(value))
        }

        // Wrap incoming request and add to existing properties
        new PortletRequestWrapper(portletRequest) {
            override def getPropertyNames =
                headers.keysIterator ++ portletRequest.getPropertyNames
            override def getProperty(name: String) =
                headers.get(name) map (_.head) getOrElse portletRequest.getProperty(name)
            override def getProperties(name: String) =
                headers.get(name) map (_.toIterator: java.util.Enumeration[String]) getOrElse portletRequest.getProperties(name)
        }
    }
}

