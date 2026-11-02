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

import java.util as ju


trait JavaxOrJakartaPortlet

trait PortletContextProvider {
  def getInitParameter(name: String): String
  def getInitParameterNames: ju.Enumeration[String]
  def getOrbeonPortletContext: PortletContext
  def getTitle(request: RenderRequest): String
}

trait GenericPortlet {
  def init(config: PortletConfig): Unit = ()
  def init(): Unit = ()
  def destroy(): Unit = ()
  def doView(request: RenderRequest, response: RenderResponse): Unit = ()
  def doEdit(request: RenderRequest, response: RenderResponse): Unit = ()
  def doHelp(request: RenderRequest, response: RenderResponse): Unit = ()
  def processAction(request: ActionRequest, response: ActionResponse): Unit = ()
  def serveResource(request: ResourceRequest, response: ResourceResponse): Unit = ()
}

abstract class JavaxGenericPortlet extends javax.portlet.GenericPortlet with JavaxOrJakartaPortlet with PortletContextProvider {
  def genericPortlet: GenericPortlet

  override def getOrbeonPortletContext: PortletContext =
    PortletContext(getPortletContext)

  override def getTitle(request: RenderRequest): String =
    super.getTitle(request.getNativePortletRequest.asInstanceOf[javax.portlet.RenderRequest])

  override def init(config: javax.portlet.PortletConfig): Unit = {
    super.init(config)
    genericPortlet.init(PortletConfig(config))
  }

  override def init(): Unit =
    genericPortlet.init()

  override def destroy(): Unit = {
    genericPortlet.destroy()
    super.destroy()
  }

  override def doView(request: javax.portlet.RenderRequest, response: javax.portlet.RenderResponse): Unit =
    genericPortlet.doView(RenderRequest(request), RenderResponse(response))

  override def doEdit(request: javax.portlet.RenderRequest, response: javax.portlet.RenderResponse): Unit =
    genericPortlet.doEdit(RenderRequest(request), RenderResponse(response))

  override def doHelp(request: javax.portlet.RenderRequest, response: javax.portlet.RenderResponse): Unit =
    genericPortlet.doHelp(RenderRequest(request), RenderResponse(response))

  override def processAction(request: javax.portlet.ActionRequest, response: javax.portlet.ActionResponse): Unit =
    genericPortlet.processAction(ActionRequest(request), ActionResponse(response))

  override def serveResource(request: javax.portlet.ResourceRequest, response: javax.portlet.ResourceResponse): Unit =
    genericPortlet.serveResource(ResourceRequest(request), ResourceResponse(response))
}

abstract class JakartaGenericPortlet extends jakarta.portlet.GenericPortlet with JavaxOrJakartaPortlet with PortletContextProvider {
  def genericPortlet: GenericPortlet

  override def getOrbeonPortletContext: PortletContext =
    PortletContext(super.getPortletContext)

  override def getTitle(request: RenderRequest): String =
    super.getTitle(request.getNativePortletRequest.asInstanceOf[jakarta.portlet.RenderRequest])

  override def init(config: jakarta.portlet.PortletConfig): Unit = {
    super.init(config)
    genericPortlet.init(PortletConfig(config))
  }

  override def init(): Unit =
    genericPortlet.init()

  override def destroy(): Unit = {
    genericPortlet.destroy()
    super.destroy()
  }

  override def doView(request: jakarta.portlet.RenderRequest, response: jakarta.portlet.RenderResponse): Unit =
    genericPortlet.doView(RenderRequest(request), RenderResponse(response))

  override def doEdit(request: jakarta.portlet.RenderRequest, response: jakarta.portlet.RenderResponse): Unit =
    genericPortlet.doEdit(RenderRequest(request), RenderResponse(response))

  override def doHelp(request: jakarta.portlet.RenderRequest, response: jakarta.portlet.RenderResponse): Unit =
    genericPortlet.doHelp(RenderRequest(request), RenderResponse(response))

  override def processAction(request: jakarta.portlet.ActionRequest, response: jakarta.portlet.ActionResponse): Unit =
    genericPortlet.processAction(ActionRequest(request), ActionResponse(response))

  override def serveResource(request: jakarta.portlet.ResourceRequest, response: jakarta.portlet.ResourceResponse): Unit =
    genericPortlet.serveResource(ResourceRequest(request), ResourceResponse(response))
}
