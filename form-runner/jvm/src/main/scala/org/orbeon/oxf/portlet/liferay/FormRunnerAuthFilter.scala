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

import org.orbeon.oxf.fr.FormRunnerAuth
import org.orbeon.oxf.portlet.PortletSessionImpl

import javax.portlet._
import javax.portlet.filter._
import scala.collection.compat._
import scala.jdk.CollectionConverters._


class AddOrbeonAuthHeadersFilter
  extends PortletFilter
     with RenderFilter
     with ActionFilter
     with ResourceFilter
     with EventFilter {

  import FormRunnerAuthFilter.wrapWithOrbeonAuthHeaders

  def doFilter(req: RenderRequest, res: RenderResponse, chain: FilterChain): Unit =
    chain.doFilter(wrapWithOrbeonAuthHeaders(req), res)

  def doFilter(req: ActionRequest, res: ActionResponse, chain: FilterChain): Unit =
    chain.doFilter(wrapWithOrbeonAuthHeaders(req), res)

  def doFilter(req: ResourceRequest, res: ResourceResponse, chain: FilterChain): Unit =
    chain.doFilter(wrapWithOrbeonAuthHeaders(req), res)

  def doFilter(req: EventRequest, res: EventResponse, chain: FilterChain): Unit =
    chain.doFilter(wrapWithOrbeonAuthHeaders(req), res)

  def init(filterConfig: filter.FilterConfig) = ()
  def destroy() = ()
}

object FormRunnerAuthFilter {

  def wrapWithOrbeonAuthHeaders[T <: PortletRequest](req: T): T = {

    val authHeaders = FormRunnerAuth.getCredentialsAsHeadersUseSession(
      userRoles  = req,
      session    = new PortletSessionImpl(req.getPortletSession),
      getHeader  = req.getProperties(_).asScala.to(List)
    ).toMap

    AddLiferayUserHeadersFilter.wrap(req, FormRunnerAuth.AllHeaderNamesLower, authHeaders)
  }
}
