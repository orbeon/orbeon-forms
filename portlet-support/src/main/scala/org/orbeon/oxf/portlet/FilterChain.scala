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


object FilterChain {
  def apply(filterChain: javax.portlet.filter.FilterChain): FilterChain     = new JavaxFilterChain(filterChain)
  def apply(filterChain: jakarta.portlet.filter.FilterChain): FilterChain = new JakartaFilterChain(filterChain)
}

trait FilterChain {
  def doFilter(request: ActionRequest, response: ActionResponse): Unit
  def doFilter(request: EventRequest, response: EventResponse): Unit
  def doFilter(request: RenderRequest, response: RenderResponse): Unit
  def doFilter(request: ResourceRequest, response: ResourceResponse): Unit
}

class JavaxFilterChain(filterChain: javax.portlet.filter.FilterChain) extends FilterChain {
  override def doFilter(request: ActionRequest, response: ActionResponse): Unit =
    filterChain.doFilter(
      request.getNativePortletRequest.asInstanceOf[javax.portlet.ActionRequest],
      response.getNativePortletResponse.asInstanceOf[javax.portlet.ActionResponse]
    )

  override def doFilter(request: EventRequest, response: EventResponse): Unit =
    filterChain.doFilter(
      request.getNativePortletRequest.asInstanceOf[javax.portlet.EventRequest],
      response.getNativePortletResponse.asInstanceOf[javax.portlet.EventResponse]
    )

  override def doFilter(request: RenderRequest, response: RenderResponse): Unit =
    filterChain.doFilter(
      request.getNativePortletRequest.asInstanceOf[javax.portlet.RenderRequest],
      response.getNativePortletResponse.asInstanceOf[javax.portlet.RenderResponse]
    )

  override def doFilter(request: ResourceRequest, response: ResourceResponse): Unit =
    filterChain.doFilter(
      request.getNativePortletRequest.asInstanceOf[javax.portlet.ResourceRequest],
      response.getNativePortletResponse.asInstanceOf[javax.portlet.ResourceResponse]
    )
}

class JakartaFilterChain(filterChain: jakarta.portlet.filter.FilterChain) extends FilterChain {
  override def doFilter(request: ActionRequest, response: ActionResponse): Unit =
    filterChain.doFilter(
      request.getNativePortletRequest.asInstanceOf[jakarta.portlet.ActionRequest],
      response.getNativePortletResponse.asInstanceOf[jakarta.portlet.ActionResponse]
    )

  override def doFilter(request: EventRequest, response: EventResponse): Unit =
    filterChain.doFilter(
      request.getNativePortletRequest.asInstanceOf[jakarta.portlet.EventRequest],
      response.getNativePortletResponse.asInstanceOf[jakarta.portlet.EventResponse]
    )

  override def doFilter(request: RenderRequest, response: RenderResponse): Unit =
    filterChain.doFilter(
      request.getNativePortletRequest.asInstanceOf[jakarta.portlet.RenderRequest],
      response.getNativePortletResponse.asInstanceOf[jakarta.portlet.RenderResponse]
    )

  override def doFilter(request: ResourceRequest, response: ResourceResponse): Unit =
    filterChain.doFilter(
      request.getNativePortletRequest.asInstanceOf[jakarta.portlet.ResourceRequest],
      response.getNativePortletResponse.asInstanceOf[jakarta.portlet.ResourceResponse]
    )
}
