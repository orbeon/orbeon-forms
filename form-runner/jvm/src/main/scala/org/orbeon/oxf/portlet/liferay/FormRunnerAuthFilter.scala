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
import org.orbeon.oxf.fr.FormRunnerAuth
import org.orbeon.oxf.portlet.{PortletSessionImpl, RequestPrependHeaders, RequestRemoveHeaders}
import org.orbeon.oxf.util.CollectionUtils

import scala.collection.JavaConverters._
import scala.collection.compat._

class AddOrbeonAuthHeadersFilter
  extends PortletFilter
     with RenderFilter
     with ActionFilter
     with ResourceFilter
     with EventFilter {

  import FormRunnerAuthFilter._

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

class AddLiferayUserHeadersFilter
  extends PortletFilter
     with RenderFilter
     with ActionFilter
     with ResourceFilter
     with EventFilter {

  import FormRunnerAuthFilter._

  def doFilter(req: RenderRequest, res: RenderResponse, chain: FilterChain): Unit =
    chain.doFilter(amendRequestWithUser(req)(wrapWithLiferayUserHeaders), res)

  def doFilter(req: ActionRequest, res: ActionResponse, chain: FilterChain): Unit =
    chain.doFilter(amendRequestWithUser(req)(wrapWithLiferayUserHeaders), res)

  def doFilter(req: ResourceRequest, res: ResourceResponse, chain: FilterChain): Unit =
    chain.doFilter(amendRequestWithUser(req)(wrapWithLiferayUserHeaders), res)

  def doFilter(req: EventRequest, res: EventResponse, chain: FilterChain): Unit =
    chain.doFilter(amendRequestWithUser(req)(wrapWithLiferayUserHeaders), res)

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

    wrap(req, FormRunnerAuth.AllHeaderNamesLower, authHeaders)
  }

  // NOTE: request.getRemoteUser() can be configured in liferay-portlet.xml with user-principal-strategy to either
  // userId (a number) or screenName (a string). It seems more reliable to use the API below to obtain the user.
  def amendRequestWithUser[T <: PortletRequest](req: T)(amend: (T, LiferayUser) => T): T =
    LiferaySupport.getLiferayUser(req) match {
      case Some(user) => amend(req, user)
      case None       => req
    }

  def wrapWithLiferayUserHeaders[T <: PortletRequest](req: T, user: LiferayUser): T = {

    val liferayUserHeaders =
      CollectionUtils.combineValues[String, String, Array](user.userHeaders) map
        { case (name, value) => name.toLowerCase -> value } toMap

    wrap(req, LiferaySupport.AllHeaderNamesLower, liferayUserHeaders)
  }

  private def wrap[T <: PortletRequest](req: T, remove: Set[String], prepend: Map[String, Array[String]]): T = {

    trait CustomProperties extends RequestRemoveHeaders with RequestPrependHeaders  {
      val headersToRemove  = remove
      val headersToPrepend = prepend
    }

    req match {
      case r: RenderRequest   => new RenderRequestWrapper(r)   with CustomProperties
      case r: ActionRequest   => new ActionRequestWrapper(r)   with CustomProperties
      case r: ResourceRequest => new ResourceRequestWrapper(r) with CustomProperties
      case r: EventRequest    => new EventRequestWrapper(r)    with CustomProperties
      case r: PortletRequest  => new PortletRequestWrapper(r)  with CustomProperties
    }

  }.asInstanceOf[T] // We can prove that the types work out for us ;)
}
