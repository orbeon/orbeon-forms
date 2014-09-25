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
package org.orbeon.oxf.portlet.liferay

import collection.JavaConverters._
import collection.JavaConversions.asJavaEnumeration
import com.liferay.portal.model.User
import com.liferay.portal.util.PortalUtil
import javax.portlet._
import javax.portlet.filter._
import org.orbeon.oxf.fr.FormRunner
import org.orbeon.oxf.portlet.RequestFilter
import org.orbeon.oxf.util.ScalaUtils

/**
 * Custom request filter for Liferay.
 */
class FormRunnerRequestFilter extends RequestFilter {

    val Prefix = "orbeon.liferay.user."

    def amendRequest(portletRequest: PortletRequest): PortletRequest =
        // NOTE: request.getRemoteUser() can be configured in liferay-portlet.xml with user-principal-strategy to
        // either userId (a number) or screenName (a string). It seems more reliable to use the API below to obtain the
        // user.
        PortalUtil.getUser(PortalUtil.getHttpServletRequest(portletRequest)) match {
            case user: User ⇒ amendRequest(portletRequest, user)
            case _          ⇒ portletRequest
        }

    def amendRequest(portletRequest: PortletRequest, user: User): PortletRequest = {

        val headers = {
            // 1. Get Orbeon-Liferay-User-* headers

            // Get and lowercase user headers
            val liferayUserRolesHeaders =
                ScalaUtils.combineValues[String, String, Array](LiferaySupport.userHeaders(user)) map
                    { case (name, value) ⇒ name.toLowerCase → value } toMap

            // 2. Get Orbeon-* headers

            // Get a header value first from the list of user/roles headers, then from the original request
            def getCombine(s: String) = {
                def getCombine1(s: String) = liferayUserRolesHeaders.get(s)
                def getCombine2(s: String) = portletRequest.getProperties(s).asScala.toArray match {
                    case Array() ⇒ None
                    case array ⇒ Some(array)
                }

                getCombine1(s).orElse(getCombine2(s))
            }

            // 3. Result is all new headers
            liferayUserRolesHeaders ++ FormRunner.getUserGroupRolesAsHeaders(portletRequest, getCombine)
        }

        // Wrap incoming request depending on request type and add to existing properties
        trait CustomProperties extends PortletRequestWrapper {
            override def getPropertyNames =
                asJavaEnumeration(headers.keysIterator ++ super.getPropertyNames.asScala)

            override def getProperty(name: String) =
                headers.get(name) map (_.head) getOrElse super.getProperty(name)

            override def getProperties(name: String) =
                headers.get(name) map (n ⇒ asJavaEnumeration(n.iterator)) getOrElse super.getProperties(name)
        }

        portletRequest match {
            case r: RenderRequest   ⇒ new RenderRequestWrapper(r)   with CustomProperties
            case r: ActionRequest   ⇒ new ActionRequestWrapper(r)   with CustomProperties
            case r: ResourceRequest ⇒ new ResourceRequestWrapper(r) with CustomProperties
            case r: EventRequest    ⇒ new EventRequestWrapper(r)    with CustomProperties
            case r: PortletRequest  ⇒ new PortletRequestWrapper(r)  with CustomProperties
        }
    }
}
