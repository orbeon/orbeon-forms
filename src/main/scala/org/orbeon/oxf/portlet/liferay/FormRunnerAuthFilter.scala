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
package org.orbeon.oxf.portlet.liferay

import javax.portlet._
import javax.portlet.filter._

import com.liferay.portal.model.User
import com.liferay.portal.util.PortalUtil
import org.orbeon.oxf.fr.FormRunnerAuth
import org.orbeon.oxf.util.ScalaUtils

import scala.collection.JavaConversions.asJavaEnumeration
import scala.collection.JavaConverters._

class FormRunnerAuthFilter
    extends PortletFilter
       with RenderFilter
       with ActionFilter
       with ResourceFilter
       with EventFilter {

    override def doFilter(req: RenderRequest, res: RenderResponse, chain: FilterChain) =
        chain.doFilter(FormRunnerAuthFilter.amendRequest(req), res)

    override def doFilter(req: ActionRequest, res: ActionResponse, chain: FilterChain) =
        chain.doFilter(FormRunnerAuthFilter.amendRequest(req), res)

    override def doFilter(req: ResourceRequest, res: ResourceResponse, chain: FilterChain) =
        chain.doFilter(FormRunnerAuthFilter.amendRequest(req), res)

    override def doFilter(req: EventRequest, res: EventResponse, chain: FilterChain) =
        chain.doFilter(FormRunnerAuthFilter.amendRequest(req), res)

    def init(filterConfig: filter.FilterConfig) = ()
    def destroy() = ()
}

object FormRunnerAuthFilter {

    def amendRequest[T <: PortletRequest](req: T): T =
        // NOTE: request.getRemoteUser() can be configured in liferay-portlet.xml with user-principal-strategy to
        // either userId (a number) or screenName (a string). It seems more reliable to use the API below to obtain the
        // user.
        PortalUtil.getUser(PortalUtil.getHttpServletRequest(req)) match {
            case user: User ⇒ amendRequest(req, user)
            case _          ⇒ req
        }

    // Public for unit tests
    def amendRequest[T <: PortletRequest](req: T, user: User): T = {

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
                def getCombine2(s: String) = req.getProperties(s).asScala.toArray match {
                    case Array() ⇒ None
                    case array ⇒ Some(array)
                }

                getCombine1(s).orElse(getCombine2(s))
            }

            // 3. Result is all new headers
            liferayUserRolesHeaders ++ FormRunnerAuth.getUserGroupRolesAsHeaders(req, getCombine)
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

        (
            req match {
                case r: RenderRequest   ⇒ new RenderRequestWrapper(r)   with CustomProperties
                case r: ActionRequest   ⇒ new ActionRequestWrapper(r)   with CustomProperties
                case r: ResourceRequest ⇒ new ResourceRequestWrapper(r) with CustomProperties
                case r: EventRequest    ⇒ new EventRequestWrapper(r)    with CustomProperties
                case r: PortletRequest  ⇒ new PortletRequestWrapper(r)  with CustomProperties
            }
        ).asInstanceOf[T] // We can prove that the types work out for us ;)
    }
}
