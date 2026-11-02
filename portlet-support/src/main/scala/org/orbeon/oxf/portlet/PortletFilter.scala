/**
 * Copyright (C) 2026 Orbeon, Inc.
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


trait PortletFilter {
  def init(filterConfig: FilterConfig): Unit = ()
  def destroy(): Unit = ()
  def doFilter(request: RenderRequest, response: RenderResponse, chain: FilterChain): Unit = chain.doFilter(request, response)
  def doFilter(request: ActionRequest, response: ActionResponse, chain: FilterChain): Unit = chain.doFilter(request, response)
  def doFilter(request: ResourceRequest, response: ResourceResponse, chain: FilterChain): Unit = chain.doFilter(request, response)
  def doFilter(request: EventRequest, response: EventResponse, chain: FilterChain): Unit = chain.doFilter(request, response)
}

trait JavaxOrJakartaPortletFilter

class JavaxPortletFilter(filter: PortletFilter)
  extends javax.portlet.filter.PortletFilter
     with javax.portlet.filter.RenderFilter
     with javax.portlet.filter.ActionFilter
     with javax.portlet.filter.ResourceFilter
     with javax.portlet.filter.EventFilter
     with JavaxOrJakartaPortletFilter {

  override def init(filterConfig: javax.portlet.filter.FilterConfig): Unit =
    filter.init(FilterConfig(filterConfig))

  override def destroy(): Unit =
    filter.destroy()

  override def doFilter(
    request : javax.portlet.RenderRequest,
    response: javax.portlet.RenderResponse,
    chain   : javax.portlet.filter.FilterChain
  ): Unit =
    filter.doFilter(RenderRequest(request), RenderResponse(response), FilterChain(chain))

  override def doFilter(
    request : javax.portlet.ActionRequest,
    response: javax.portlet.ActionResponse,
    chain   : javax.portlet.filter.FilterChain
  ): Unit =
    filter.doFilter(ActionRequest(request), ActionResponse(response), FilterChain(chain))

  override def doFilter(
    request : javax.portlet.ResourceRequest,
    response: javax.portlet.ResourceResponse,
    chain   : javax.portlet.filter.FilterChain
  ): Unit =
    filter.doFilter(ResourceRequest(request), ResourceResponse(response), FilterChain(chain))

  override def doFilter(
    request : javax.portlet.EventRequest,
    response: javax.portlet.EventResponse,
    chain   : javax.portlet.filter.FilterChain
  ): Unit =
    filter.doFilter(EventRequest(request), EventResponse(response), FilterChain(chain))
}

class JakartaPortletFilter(filter: PortletFilter)
  extends jakarta.portlet.filter.PortletFilter
     with jakarta.portlet.filter.RenderFilter
     with jakarta.portlet.filter.ActionFilter
     with jakarta.portlet.filter.ResourceFilter
     with jakarta.portlet.filter.EventFilter
     with JavaxOrJakartaPortletFilter {

  override def init(filterConfig: jakarta.portlet.filter.FilterConfig): Unit =
    filter.init(FilterConfig(filterConfig))

  override def destroy(): Unit =
    filter.destroy()

  override def doFilter(
    request : jakarta.portlet.RenderRequest,
    response: jakarta.portlet.RenderResponse,
    chain   : jakarta.portlet.filter.FilterChain
  ): Unit =
    filter.doFilter(RenderRequest(request), RenderResponse(response), FilterChain(chain))

  override def doFilter(
    request : jakarta.portlet.ActionRequest,
    response: jakarta.portlet.ActionResponse,
    chain   : jakarta.portlet.filter.FilterChain
  ): Unit =
    filter.doFilter(ActionRequest(request), ActionResponse(response), FilterChain(chain))

  override def doFilter(
    request : jakarta.portlet.ResourceRequest,
    response: jakarta.portlet.ResourceResponse,
    chain   : jakarta.portlet.filter.FilterChain
  ): Unit =
    filter.doFilter(ResourceRequest(request), ResourceResponse(response), FilterChain(chain))

  override def doFilter(
    request : jakarta.portlet.EventRequest,
    response: jakarta.portlet.EventResponse,
    chain   : jakarta.portlet.filter.FilterChain
  ): Unit =
    filter.doFilter(EventRequest(request), EventResponse(response), FilterChain(chain))
}
