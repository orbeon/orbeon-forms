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

import org.orbeon.oxf.servlet.Cookie

import java.io.{BufferedReader, InputStream}
import java.security.Principal
import java.util as ju


class PortletRequestWrapper(var request: PortletRequest) extends PortletRequest {
  override def getNativePortletRequest: AnyRef = request.wrappedWith(this)

  override def getAttribute(name: String): AnyRef = request.getAttribute(name)
  override def getAttributeNames: ju.Enumeration[String] = request.getAttributeNames
  override def getAuthType: String = request.getAuthType
  override def getContextPath: String = request.getContextPath
  override def getCookies: Array[Cookie] = request.getCookies
  override def getLocale: ju.Locale = request.getLocale
  override def getLocales: ju.Enumeration[ju.Locale] = request.getLocales
  override def getParameter(name: String): String = request.getParameter(name)
  override def getParameterMap: ju.Map[String, Array[String]] = request.getParameterMap
  override def getParameterNames: ju.Enumeration[String] = request.getParameterNames
  override def getParameterValues(name: String): Array[String] = request.getParameterValues(name)
  override def getPortletContext: PortletContext = request.getPortletContext
  override def getPortletMode: PortletMode = request.getPortletMode
  override def getPortletSession: PortletSession = request.getPortletSession
  override def getPortletSession(create: Boolean): PortletSession = request.getPortletSession(create)
  override def getPreferences: PortletPreferences = request.getPreferences
  override def getPrivateParameterMap: ju.Map[String, Array[String]] = request.getPrivateParameterMap
  override def getProperties(name: String): ju.Enumeration[String] = request.getProperties(name)
  override def getProperty(name: String): String = request.getProperty(name)
  override def getPropertyNames: ju.Enumeration[String] = request.getPropertyNames
  override def getPublicParameterMap: ju.Map[String, Array[String]] = request.getPublicParameterMap
  override def getRemoteUser(): String = request.getRemoteUser()
  override def getRenderParameters: RenderParameters = request.getRenderParameters
  override def getRequestedSessionId: String = request.getRequestedSessionId
  override def getResponseContentType: String = request.getResponseContentType
  override def getResponseContentTypes: ju.Enumeration[String] = request.getResponseContentTypes
  override def getScheme: String = request.getScheme
  override def getServerName: String = request.getServerName
  override def getServerPort: Int = request.getServerPort
  override def getUserPrincipal: Principal = request.getUserPrincipal
  override def getWindowID: String = request.getWindowID
  override def getWindowState: WindowState = request.getWindowState
  override def isPortletModeAllowed(mode: PortletMode): Boolean = request.isPortletModeAllowed(mode)
  override def isRequestedSessionIdValid: Boolean = request.isRequestedSessionIdValid
  override def isSecure: Boolean = request.isSecure
  override def isUserInRole(role: String): Boolean = request.isUserInRole(role)
  override def isWindowStateAllowed(state: WindowState): Boolean = request.isWindowStateAllowed(state)
  override def removeAttribute(name: String): Unit = request.removeAttribute(name)
  override def setAttribute(name: String, o: AnyRef): Unit = request.setAttribute(name, o)

  override def wrappedWith(wrapper: PortletRequestWrapper): AnyRef = request.wrappedWith(wrapper)
}

class RenderRequestWrapper(var renderRequest: RenderRequest)
  extends PortletRequestWrapper(renderRequest)
     with RenderRequest {
  override def getNativePortletRequest: AnyRef = renderRequest.wrappedWith(this)
  override def getETag: String = renderRequest.getETag
  override def wrappedWith(wrapper: RenderRequestWrapper): AnyRef = renderRequest.wrappedWith(wrapper)
}

class EventRequestWrapper(var eventRequest: EventRequest)
  extends PortletRequestWrapper(eventRequest)
     with EventRequest {
  override def getNativePortletRequest: AnyRef = eventRequest.wrappedWith(this)
  override def getMethod: String = eventRequest.getMethod
  override def wrappedWith(wrapper: EventRequestWrapper): AnyRef = eventRequest.wrappedWith(wrapper)
}

class ClientDataRequestWrapper(var clientDataRequest: ClientDataRequest)
  extends PortletRequestWrapper(clientDataRequest)
     with ClientDataRequest {
  override def getCharacterEncoding: String = clientDataRequest.getCharacterEncoding
  override def getContentLength: Int = clientDataRequest.getContentLength
  override def getContentLengthLong: Long = clientDataRequest.getContentLengthLong
  override def getContentType: String = clientDataRequest.getContentType
  override def getMethod: String = clientDataRequest.getMethod
  override def getPortletInputStream: InputStream = clientDataRequest.getPortletInputStream
  override def getReader: BufferedReader = clientDataRequest.getReader
  override def setCharacterEncoding(enc: String): Unit = clientDataRequest.setCharacterEncoding(enc)
}

class ActionRequestWrapper(var actionRequest: ActionRequest)
  extends ClientDataRequestWrapper(actionRequest)
     with ActionRequest {
  override def getNativePortletRequest: AnyRef = actionRequest.wrappedWith(this)
  override def wrappedWith(wrapper: ActionRequestWrapper): AnyRef = actionRequest.wrappedWith(wrapper)
}

class ResourceRequestWrapper(var resourceRequest: ResourceRequest)
  extends ClientDataRequestWrapper(resourceRequest)
     with ResourceRequest {
  override def getNativePortletRequest: AnyRef = resourceRequest.wrappedWith(this)
  override def getCacheability: String = resourceRequest.getCacheability
  override def getETag: String = resourceRequest.getETag
  override def getPrivateRenderParameterMap: ju.Map[String, Array[String]] = resourceRequest.getPrivateRenderParameterMap
  override def getResourceID: String = resourceRequest.getResourceID
  override def wrappedWith(wrapper: ResourceRequestWrapper): AnyRef = resourceRequest.wrappedWith(wrapper)
}
