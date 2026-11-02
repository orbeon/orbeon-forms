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
import scala.annotation.nowarn


object PortletRequest {
  def apply(portletRequest: javax.portlet.PortletRequest): PortletRequest =
    portletRequest match {
      case renderRequest: javax.portlet.RenderRequest         => new JavaxRenderRequest(renderRequest)
      case actionRequest: javax.portlet.ActionRequest         => new JavaxActionRequest(actionRequest)
      case resourceRequest: javax.portlet.ResourceRequest     => new JavaxResourceRequest(resourceRequest)
      case clientDataRequest: javax.portlet.ClientDataRequest => new JavaxClientDataRequest(clientDataRequest)
      case eventRequest: javax.portlet.EventRequest           => new JavaxEventRequest(eventRequest)
      case _                                                  => new JavaxPortletRequest(portletRequest)
    }

  def apply(portletRequest: jakarta.portlet.PortletRequest): PortletRequest =
    portletRequest match {
      case renderRequest: jakarta.portlet.RenderRequest         => new JakartaRenderRequest(renderRequest)
      case actionRequest: jakarta.portlet.ActionRequest         => new JakartaActionRequest(actionRequest)
      case resourceRequest: jakarta.portlet.ResourceRequest     => new JakartaResourceRequest(resourceRequest)
      case clientDataRequest: jakarta.portlet.ClientDataRequest => new JakartaClientDataRequest(clientDataRequest)
      case eventRequest: jakarta.portlet.EventRequest           => new JakartaEventRequest(eventRequest)
      case _                                                    => new JakartaPortletRequest(portletRequest)
    }
}

trait PortletRequest {
  // javax/jakarta.portlet.PortletRequest
  def getNativePortletRequest: AnyRef

  def getAttribute(name: String): AnyRef
  def getAttributeNames: ju.Enumeration[String]
  def getAuthType: String
  def getContextPath: String
  def getCookies: Array[Cookie]
  def getLocale: ju.Locale
  def getLocales: ju.Enumeration[ju.Locale]
  def getParameter(name: String): String
  def getParameterMap: ju.Map[String, Array[String]]
  def getParameterNames: ju.Enumeration[String]
  def getParameterValues(name: String): Array[String]
  def getPortletContext: PortletContext
  def getPortletMode: PortletMode
  def getPortletSession: PortletSession
  def getPortletSession(create: Boolean): PortletSession
  def getPreferences: PortletPreferences
  def getPrivateParameterMap: ju.Map[String, Array[String]]
  def getProperties(name: String): ju.Enumeration[String]
  def getProperty(name: String): String
  def getPropertyNames: ju.Enumeration[String]
  def getPublicParameterMap: ju.Map[String, Array[String]]
  // Keep parentheses for UserRolesFacade
  def getRemoteUser(): String
  def getRenderParameters: RenderParameters
  def getRequestedSessionId: String
  def getResponseContentType: String
  def getResponseContentTypes: ju.Enumeration[String]
  def getScheme: String
  def getServerName: String
  def getServerPort: Int
  def getUserPrincipal: Principal
  def getWindowID: String
  def getWindowState: WindowState
  def isPortletModeAllowed(mode: PortletMode): Boolean
  def isRequestedSessionIdValid: Boolean
  def isSecure: Boolean
  def isUserInRole(role: String): Boolean
  def isWindowStateAllowed(state: WindowState): Boolean
  def removeAttribute(name: String): Unit
  def setAttribute(name: String, o: AnyRef): Unit

  // javax/jakarta.portlet.filter.PortletRequestWrapper
  def wrappedWith(wrapper: PortletRequestWrapper): AnyRef
}

object RenderRequest {
  def apply(renderRequest: javax.portlet.RenderRequest): RenderRequest     = new JavaxRenderRequest(renderRequest)
  def apply(renderRequest: jakarta.portlet.RenderRequest): RenderRequest = new JakartaRenderRequest(renderRequest)
}

trait RenderRequest extends PortletRequest {
  def getETag: String
  def wrappedWith(wrapper: RenderRequestWrapper): AnyRef
}

object EventRequest {
  def apply(eventRequest: javax.portlet.EventRequest): EventRequest     = new JavaxEventRequest(eventRequest)
  def apply(eventRequest: jakarta.portlet.EventRequest): EventRequest = new JakartaEventRequest(eventRequest)
}

trait EventRequest extends PortletRequest {
  def getMethod: String
  def wrappedWith(wrapper: EventRequestWrapper): AnyRef
}

object ClientDataRequest {
  def apply(clientDataRequest: javax.portlet.ClientDataRequest): ClientDataRequest =
    clientDataRequest match {
      case actionRequest: javax.portlet.ActionRequest     => new JavaxActionRequest(actionRequest)
      case resourceRequest: javax.portlet.ResourceRequest => new JavaxResourceRequest(resourceRequest)
      case _                                              => new JavaxClientDataRequest(clientDataRequest)
    }

  def apply(clientDataRequest: jakarta.portlet.ClientDataRequest): ClientDataRequest =
    clientDataRequest match {
      case actionRequest: jakarta.portlet.ActionRequest     => new JakartaActionRequest(actionRequest)
      case resourceRequest: jakarta.portlet.ResourceRequest => new JakartaResourceRequest(resourceRequest)
      case _                                                => new JakartaClientDataRequest(clientDataRequest)
    }
}

trait ClientDataRequest extends PortletRequest {
  def getCharacterEncoding: String
  def getContentLength: Int
  def getContentLengthLong: Long
  def getContentType: String
  def getMethod: String
  def getPortletInputStream: InputStream
  def getReader: BufferedReader
  def setCharacterEncoding(enc: String): Unit
}

object ActionRequest {
  val ActionName: String = "javax.portlet.action"
  val ACTION_NAME: String = ActionName

  def apply(actionRequest: javax.portlet.ActionRequest): ActionRequest     = new JavaxActionRequest(actionRequest)
  def apply(actionRequest: jakarta.portlet.ActionRequest): ActionRequest = new JakartaActionRequest(actionRequest)
}

trait ActionRequest extends ClientDataRequest {
  def wrappedWith(wrapper: ActionRequestWrapper): AnyRef
}

object ResourceRequest {
  def apply(resourceRequest: javax.portlet.ResourceRequest): ResourceRequest     = new JavaxResourceRequest(resourceRequest)
  def apply(resourceRequest: jakarta.portlet.ResourceRequest): ResourceRequest = new JakartaResourceRequest(resourceRequest)
}

trait ResourceRequest extends ClientDataRequest {
  def getCacheability: String
  def getETag: String
  def getPrivateRenderParameterMap: ju.Map[String, Array[String]]
  def getResourceID: String
  def wrappedWith(wrapper: ResourceRequestWrapper): AnyRef
}

@nowarn("cat=deprecation")
class JavaxPortletRequest(portletRequest: javax.portlet.PortletRequest) extends PortletRequest {
  override def getNativePortletRequest: javax.portlet.PortletRequest = portletRequest

  override def getAttribute(name: String): AnyRef = portletRequest.getAttribute(name)
  override def getAttributeNames: ju.Enumeration[String] = portletRequest.getAttributeNames
  override def getAuthType: String = portletRequest.getAuthType
  override def getContextPath: String = portletRequest.getContextPath
  override def getCookies: Array[Cookie] =
    Option(portletRequest.getCookies)
      .map(_.map(c => Cookie(c): Cookie))
      .getOrElse(Array.empty[Cookie])
  override def getLocale: ju.Locale = portletRequest.getLocale
  override def getLocales: ju.Enumeration[ju.Locale] = portletRequest.getLocales
  override def getParameter(name: String): String = portletRequest.getParameter(name)
  override def getParameterMap: ju.Map[String, Array[String]] = portletRequest.getParameterMap
  override def getParameterNames: ju.Enumeration[String] = portletRequest.getParameterNames
  override def getParameterValues(name: String): Array[String] = portletRequest.getParameterValues(name)
  override def getPortletContext: PortletContext = PortletContext(portletRequest.getPortletContext)
  override def getPortletMode: PortletMode = PortletMode(portletRequest.getPortletMode)
  override def getPortletSession: PortletSession = Option(portletRequest.getPortletSession).map(PortletSession.apply).orNull
  override def getPortletSession(create: Boolean): PortletSession = Option(portletRequest.getPortletSession(create)).map(PortletSession.apply).orNull
  override def getPreferences: PortletPreferences = PortletPreferences(portletRequest.getPreferences)
  override def getPrivateParameterMap: ju.Map[String, Array[String]] = portletRequest.getPrivateParameterMap
  override def getProperties(name: String): ju.Enumeration[String] = portletRequest.getProperties(name)
  override def getProperty(name: String): String = portletRequest.getProperty(name)
  override def getPropertyNames: ju.Enumeration[String] = portletRequest.getPropertyNames
  override def getPublicParameterMap: ju.Map[String, Array[String]] = portletRequest.getPublicParameterMap
  override def getRemoteUser(): String = portletRequest.getRemoteUser
  override def getRenderParameters: RenderParameters = Option(portletRequest.getRenderParameters).map(RenderParameters.apply).orNull
  override def getRequestedSessionId: String = portletRequest.getRequestedSessionId
  override def getResponseContentType: String = portletRequest.getResponseContentType
  override def getResponseContentTypes: ju.Enumeration[String] = portletRequest.getResponseContentTypes
  override def getScheme: String = portletRequest.getScheme
  override def getServerName: String = portletRequest.getServerName
  override def getServerPort: Int = portletRequest.getServerPort
  override def getUserPrincipal: Principal = portletRequest.getUserPrincipal
  override def getWindowID: String = portletRequest.getWindowID
  override def getWindowState: WindowState = WindowState(portletRequest.getWindowState)
  override def isPortletModeAllowed(mode: PortletMode): Boolean =
    portletRequest.isPortletModeAllowed(Option(mode).map(m => new javax.portlet.PortletMode(m.toString)).orNull)
  override def isRequestedSessionIdValid: Boolean = portletRequest.isRequestedSessionIdValid
  override def isSecure: Boolean = portletRequest.isSecure
  override def isUserInRole(role: String): Boolean = portletRequest.isUserInRole(role)
  override def isWindowStateAllowed(state: WindowState): Boolean =
    portletRequest.isWindowStateAllowed(Option(state).map(s => new javax.portlet.WindowState(s.toString)).orNull)
  override def removeAttribute(name: String): Unit = portletRequest.removeAttribute(name)
  override def setAttribute(name: String, o: AnyRef): Unit = portletRequest.setAttribute(name, o)

  override def wrappedWith(wrapper: PortletRequestWrapper): javax.portlet.filter.PortletRequestWrapper =
    new javax.portlet.filter.PortletRequestWrapper(portletRequest) {
      override def setRequest(request: javax.portlet.PortletRequest): Unit = {
        super.setRequest(request)
        wrapper.request = PortletRequest(request)
      }
      override def getAttribute(name: String): AnyRef = wrapper.getAttribute(name)
      override def getAttributeNames: ju.Enumeration[String] = wrapper.getAttributeNames
      override def getAuthType: String = wrapper.getAuthType
      override def getContextPath: String = wrapper.getContextPath
      override def getCookies: Array[javax.servlet.http.Cookie] =
        Option(wrapper.getCookies)
          .map(_.map(_.getNativeCookie.asInstanceOf[javax.servlet.http.Cookie]))
          .getOrElse(Array.empty[javax.servlet.http.Cookie])
      override def getLocale: ju.Locale = wrapper.getLocale
      override def getLocales: ju.Enumeration[ju.Locale] = wrapper.getLocales
      override def getParameter(name: String): String = wrapper.getParameter(name)
      override def getParameterMap: ju.Map[String, Array[String]] = wrapper.getParameterMap
      override def getParameterNames: ju.Enumeration[String] = wrapper.getParameterNames
      override def getParameterValues(name: String): Array[String] = wrapper.getParameterValues(name)
      override def getPortletContext: javax.portlet.PortletContext =
        Option(wrapper.getPortletContext).map(_.getNativePortletContext.asInstanceOf[javax.portlet.PortletContext]).orNull
      override def getPortletMode: javax.portlet.PortletMode =
        Option(wrapper.getPortletMode).map(m => new javax.portlet.PortletMode(m.toString)).orNull
      override def getPortletSession: javax.portlet.PortletSession =
        Option(wrapper.getPortletSession).map(_.getNativePortletSession.asInstanceOf[javax.portlet.PortletSession]).orNull
      override def getPortletSession(create: Boolean): javax.portlet.PortletSession =
        Option(wrapper.getPortletSession(create)).map(_.getNativePortletSession.asInstanceOf[javax.portlet.PortletSession]).orNull
      override def getPreferences: javax.portlet.PortletPreferences =
        Option(wrapper.getPreferences).map(_.getNativePortletPreferences.asInstanceOf[javax.portlet.PortletPreferences]).orNull
      override def getPrivateParameterMap: ju.Map[String, Array[String]] = wrapper.getPrivateParameterMap
      override def getProperties(name: String): ju.Enumeration[String] = wrapper.getProperties(name)
      override def getProperty(name: String): String = wrapper.getProperty(name)
      override def getPropertyNames: ju.Enumeration[String] = wrapper.getPropertyNames
      override def getPublicParameterMap: ju.Map[String, Array[String]] = wrapper.getPublicParameterMap
      override def getRemoteUser: String = wrapper.getRemoteUser()
      override def getRenderParameters: javax.portlet.RenderParameters =
        Option(wrapper.getRenderParameters).map(_.getNativeRenderParameters.asInstanceOf[javax.portlet.RenderParameters]).orNull
      override def getRequestedSessionId: String = wrapper.getRequestedSessionId
      override def getResponseContentType: String = wrapper.getResponseContentType
      override def getResponseContentTypes: ju.Enumeration[String] = wrapper.getResponseContentTypes
      override def getScheme: String = wrapper.getScheme
      override def getServerName: String = wrapper.getServerName
      override def getServerPort: Int = wrapper.getServerPort
      override def getUserPrincipal: Principal = wrapper.getUserPrincipal
      override def getWindowID: String = wrapper.getWindowID
      override def getWindowState: javax.portlet.WindowState =
        Option(wrapper.getWindowState).map(s => new javax.portlet.WindowState(s.toString)).orNull
      override def isPortletModeAllowed(mode: javax.portlet.PortletMode): Boolean =
        wrapper.isPortletModeAllowed(PortletMode(mode))
      override def isRequestedSessionIdValid: Boolean = wrapper.isRequestedSessionIdValid
      override def isSecure: Boolean = wrapper.isSecure
      override def isUserInRole(role: String): Boolean = wrapper.isUserInRole(role)
      override def isWindowStateAllowed(state: javax.portlet.WindowState): Boolean =
        wrapper.isWindowStateAllowed(WindowState(state))
      override def removeAttribute(name: String): Unit = wrapper.removeAttribute(name)
      override def setAttribute(name: String, o: AnyRef): Unit = wrapper.setAttribute(name, o)
    }
}

@nowarn("cat=deprecation")
class JakartaPortletRequest(portletRequest: jakarta.portlet.PortletRequest) extends PortletRequest {
  override def getNativePortletRequest: jakarta.portlet.PortletRequest = portletRequest

  override def getAttribute(name: String): AnyRef = portletRequest.getAttribute(name)
  override def getAttributeNames: ju.Enumeration[String] = portletRequest.getAttributeNames
  override def getAuthType: String = portletRequest.getAuthType
  override def getContextPath: String = portletRequest.getContextPath
  override def getCookies: Array[Cookie] =
    Option(portletRequest.getCookies)
      .map(_.map(c => Cookie(c): Cookie))
      .getOrElse(Array.empty[Cookie])
  override def getLocale: ju.Locale = portletRequest.getLocale
  override def getLocales: ju.Enumeration[ju.Locale] = portletRequest.getLocales
  override def getParameter(name: String): String = portletRequest.getParameter(name)
  override def getParameterMap: ju.Map[String, Array[String]] = portletRequest.getParameterMap
  override def getParameterNames: ju.Enumeration[String] = portletRequest.getParameterNames
  override def getParameterValues(name: String): Array[String] = portletRequest.getParameterValues(name)
  override def getPortletContext: PortletContext = PortletContext(portletRequest.getPortletContext)
  override def getPortletMode: PortletMode = PortletMode(portletRequest.getPortletMode)
  override def getPortletSession: PortletSession = Option(portletRequest.getPortletSession).map(PortletSession.apply).orNull
  override def getPortletSession(create: Boolean): PortletSession = Option(portletRequest.getPortletSession(create)).map(PortletSession.apply).orNull
  override def getPreferences: PortletPreferences = PortletPreferences(portletRequest.getPreferences)
  override def getPrivateParameterMap: ju.Map[String, Array[String]] = portletRequest.getPrivateParameterMap
  override def getProperties(name: String): ju.Enumeration[String] = portletRequest.getProperties(name)
  override def getProperty(name: String): String = portletRequest.getProperty(name)
  override def getPropertyNames: ju.Enumeration[String] = portletRequest.getPropertyNames
  override def getPublicParameterMap: ju.Map[String, Array[String]] = portletRequest.getPublicParameterMap
  override def getRemoteUser(): String = portletRequest.getRemoteUser
  override def getRenderParameters: RenderParameters = Option(portletRequest.getRenderParameters).map(RenderParameters.apply).orNull
  override def getRequestedSessionId: String = portletRequest.getRequestedSessionId
  override def getResponseContentType: String = portletRequest.getResponseContentType
  override def getResponseContentTypes: ju.Enumeration[String] = portletRequest.getResponseContentTypes
  override def getScheme: String = portletRequest.getScheme
  override def getServerName: String = portletRequest.getServerName
  override def getServerPort: Int = portletRequest.getServerPort
  override def getUserPrincipal: Principal = portletRequest.getUserPrincipal
  override def getWindowID: String = portletRequest.getWindowID
  override def getWindowState: WindowState = WindowState(portletRequest.getWindowState)
  override def isPortletModeAllowed(mode: PortletMode): Boolean =
    portletRequest.isPortletModeAllowed(Option(mode).map(m => new jakarta.portlet.PortletMode(m.toString)).orNull)
  override def isRequestedSessionIdValid: Boolean = portletRequest.isRequestedSessionIdValid
  override def isSecure: Boolean = portletRequest.isSecure
  override def isUserInRole(role: String): Boolean = portletRequest.isUserInRole(role)
  override def isWindowStateAllowed(state: WindowState): Boolean =
    portletRequest.isWindowStateAllowed(Option(state).map(s => new jakarta.portlet.WindowState(s.toString)).orNull)
  override def removeAttribute(name: String): Unit = portletRequest.removeAttribute(name)
  override def setAttribute(name: String, o: AnyRef): Unit = portletRequest.setAttribute(name, o)

  override def wrappedWith(wrapper: PortletRequestWrapper): jakarta.portlet.filter.PortletRequestWrapper =
    new jakarta.portlet.filter.PortletRequestWrapper(portletRequest) {
      override def setRequest(request: jakarta.portlet.PortletRequest): Unit = {
        super.setRequest(request)
        wrapper.request = PortletRequest(request)
      }
      override def getAttribute(name: String): AnyRef = wrapper.getAttribute(name)
      override def getAttributeNames: ju.Enumeration[String] = wrapper.getAttributeNames
      override def getAuthType: String = wrapper.getAuthType
      override def getContextPath: String = wrapper.getContextPath
      override def getCookies: Array[jakarta.servlet.http.Cookie] =
        Option(wrapper.getCookies)
          .map(_.map(_.getNativeCookie.asInstanceOf[jakarta.servlet.http.Cookie]))
          .getOrElse(Array.empty[jakarta.servlet.http.Cookie])
      override def getLocale: ju.Locale = wrapper.getLocale
      override def getLocales: ju.Enumeration[ju.Locale] = wrapper.getLocales
      override def getParameter(name: String): String = wrapper.getParameter(name)
      override def getParameterMap: ju.Map[String, Array[String]] = wrapper.getParameterMap
      override def getParameterNames: ju.Enumeration[String] = wrapper.getParameterNames
      override def getParameterValues(name: String): Array[String] = wrapper.getParameterValues(name)
      override def getPortletContext: jakarta.portlet.PortletContext =
        Option(wrapper.getPortletContext).map(_.getNativePortletContext.asInstanceOf[jakarta.portlet.PortletContext]).orNull
      override def getPortletMode: jakarta.portlet.PortletMode =
        Option(wrapper.getPortletMode).map(m => new jakarta.portlet.PortletMode(m.toString)).orNull
      override def getPortletSession: jakarta.portlet.PortletSession =
        Option(wrapper.getPortletSession).map(_.getNativePortletSession.asInstanceOf[jakarta.portlet.PortletSession]).orNull
      override def getPortletSession(create: Boolean): jakarta.portlet.PortletSession =
        Option(wrapper.getPortletSession(create)).map(_.getNativePortletSession.asInstanceOf[jakarta.portlet.PortletSession]).orNull
      override def getPreferences: jakarta.portlet.PortletPreferences =
        Option(wrapper.getPreferences).map(_.getNativePortletPreferences.asInstanceOf[jakarta.portlet.PortletPreferences]).orNull
      override def getPrivateParameterMap: ju.Map[String, Array[String]] = wrapper.getPrivateParameterMap
      override def getProperties(name: String): ju.Enumeration[String] = wrapper.getProperties(name)
      override def getProperty(name: String): String = wrapper.getProperty(name)
      override def getPropertyNames: ju.Enumeration[String] = wrapper.getPropertyNames
      override def getPublicParameterMap: ju.Map[String, Array[String]] = wrapper.getPublicParameterMap
      override def getRemoteUser: String = wrapper.getRemoteUser()
      override def getRenderParameters: jakarta.portlet.RenderParameters =
        Option(wrapper.getRenderParameters).map(_.getNativeRenderParameters.asInstanceOf[jakarta.portlet.RenderParameters]).orNull
      override def getRequestedSessionId: String = wrapper.getRequestedSessionId
      override def getResponseContentType: String = wrapper.getResponseContentType
      override def getResponseContentTypes: ju.Enumeration[String] = wrapper.getResponseContentTypes
      override def getScheme: String = wrapper.getScheme
      override def getServerName: String = wrapper.getServerName
      override def getServerPort: Int = wrapper.getServerPort
      override def getUserPrincipal: Principal = wrapper.getUserPrincipal
      override def getWindowID: String = wrapper.getWindowID
      override def getWindowState: jakarta.portlet.WindowState =
        Option(wrapper.getWindowState).map(s => new jakarta.portlet.WindowState(s.toString)).orNull
      override def isPortletModeAllowed(mode: jakarta.portlet.PortletMode): Boolean =
        wrapper.isPortletModeAllowed(PortletMode(mode))
      override def isRequestedSessionIdValid: Boolean = wrapper.isRequestedSessionIdValid
      override def isSecure: Boolean = wrapper.isSecure
      override def isUserInRole(role: String): Boolean = wrapper.isUserInRole(role)
      override def isWindowStateAllowed(state: jakarta.portlet.WindowState): Boolean =
        wrapper.isWindowStateAllowed(WindowState(state))
      override def removeAttribute(name: String): Unit = wrapper.removeAttribute(name)
      override def setAttribute(name: String, o: AnyRef): Unit = wrapper.setAttribute(name, o)
    }
}

@nowarn("cat=deprecation")
class JavaxRenderRequest(renderRequest: javax.portlet.RenderRequest)
  extends JavaxPortletRequest(renderRequest)
     with RenderRequest {
  override def getNativePortletRequest: javax.portlet.RenderRequest = renderRequest
  override def getETag: String = renderRequest.getETag

  override def wrappedWith(wrapper: RenderRequestWrapper): javax.portlet.filter.RenderRequestWrapper =
    new javax.portlet.filter.RenderRequestWrapper(renderRequest) {
      override def setRequest(request: javax.portlet.PortletRequest): Unit = {
        super.setRequest(request)
        request match {
          case rr: javax.portlet.RenderRequest => wrapper.renderRequest = RenderRequest(rr)
          case _                               => wrapper.request       = PortletRequest(request)
        }
      }
      override def getETag: String = wrapper.getETag
      override def getAttribute(name: String): AnyRef = wrapper.getAttribute(name)
      override def getAttributeNames: ju.Enumeration[String] = wrapper.getAttributeNames
      override def getAuthType: String = wrapper.getAuthType
      override def getContextPath: String = wrapper.getContextPath
      override def getCookies: Array[javax.servlet.http.Cookie] =
        Option(wrapper.getCookies)
          .map(_.map(_.getNativeCookie.asInstanceOf[javax.servlet.http.Cookie]))
          .getOrElse(Array.empty[javax.servlet.http.Cookie])
      override def getLocale: ju.Locale = wrapper.getLocale
      override def getLocales: ju.Enumeration[ju.Locale] = wrapper.getLocales
      override def getParameter(name: String): String = wrapper.getParameter(name)
      override def getParameterMap: ju.Map[String, Array[String]] = wrapper.getParameterMap
      override def getParameterNames: ju.Enumeration[String] = wrapper.getParameterNames
      override def getParameterValues(name: String): Array[String] = wrapper.getParameterValues(name)
      override def getPortletContext: javax.portlet.PortletContext =
        Option(wrapper.getPortletContext).map(_.getNativePortletContext.asInstanceOf[javax.portlet.PortletContext]).orNull
      override def getPortletMode: javax.portlet.PortletMode =
        Option(wrapper.getPortletMode).map(m => new javax.portlet.PortletMode(m.toString)).orNull
      override def getPortletSession: javax.portlet.PortletSession =
        Option(wrapper.getPortletSession).map(_.getNativePortletSession.asInstanceOf[javax.portlet.PortletSession]).orNull
      override def getPortletSession(create: Boolean): javax.portlet.PortletSession =
        Option(wrapper.getPortletSession(create)).map(_.getNativePortletSession.asInstanceOf[javax.portlet.PortletSession]).orNull
      override def getPreferences: javax.portlet.PortletPreferences =
        Option(wrapper.getPreferences).map(_.getNativePortletPreferences.asInstanceOf[javax.portlet.PortletPreferences]).orNull
      override def getPrivateParameterMap: ju.Map[String, Array[String]] = wrapper.getPrivateParameterMap
      override def getProperties(name: String): ju.Enumeration[String] = wrapper.getProperties(name)
      override def getProperty(name: String): String = wrapper.getProperty(name)
      override def getPropertyNames: ju.Enumeration[String] = wrapper.getPropertyNames
      override def getPublicParameterMap: ju.Map[String, Array[String]] = wrapper.getPublicParameterMap
      override def getRemoteUser: String = wrapper.getRemoteUser()
      override def getRenderParameters: javax.portlet.RenderParameters =
        Option(wrapper.getRenderParameters).map(_.getNativeRenderParameters.asInstanceOf[javax.portlet.RenderParameters]).orNull
      override def getRequestedSessionId: String = wrapper.getRequestedSessionId
      override def getResponseContentType: String = wrapper.getResponseContentType
      override def getResponseContentTypes: ju.Enumeration[String] = wrapper.getResponseContentTypes
      override def getScheme: String = wrapper.getScheme
      override def getServerName: String = wrapper.getServerName
      override def getServerPort: Int = wrapper.getServerPort
      override def getUserPrincipal: Principal = wrapper.getUserPrincipal
      override def getWindowID: String = wrapper.getWindowID
      override def getWindowState: javax.portlet.WindowState =
        Option(wrapper.getWindowState).map(s => new javax.portlet.WindowState(s.toString)).orNull
      override def isPortletModeAllowed(mode: javax.portlet.PortletMode): Boolean =
        wrapper.isPortletModeAllowed(PortletMode(mode))
      override def isRequestedSessionIdValid: Boolean = wrapper.isRequestedSessionIdValid
      override def isSecure: Boolean = wrapper.isSecure
      override def isUserInRole(role: String): Boolean = wrapper.isUserInRole(role)
      override def isWindowStateAllowed(state: javax.portlet.WindowState): Boolean =
        wrapper.isWindowStateAllowed(WindowState(state))
      override def removeAttribute(name: String): Unit = wrapper.removeAttribute(name)
      override def setAttribute(name: String, o: AnyRef): Unit = wrapper.setAttribute(name, o)
    }
}

@nowarn("cat=deprecation")
class JakartaRenderRequest(renderRequest: jakarta.portlet.RenderRequest)
  extends JakartaPortletRequest(renderRequest)
     with RenderRequest {
  override def getNativePortletRequest: jakarta.portlet.RenderRequest = renderRequest
  override def getETag: String = renderRequest.getETag

  override def wrappedWith(wrapper: RenderRequestWrapper): jakarta.portlet.filter.RenderRequestWrapper =
    new jakarta.portlet.filter.RenderRequestWrapper(renderRequest) {
      override def setRequest(request: jakarta.portlet.PortletRequest): Unit = {
        super.setRequest(request)
        request match {
          case rr: jakarta.portlet.RenderRequest => wrapper.renderRequest = RenderRequest(rr)
          case _                                 => wrapper.request       = PortletRequest(request)
        }
      }
      override def getETag: String = wrapper.getETag
      override def getAttribute(name: String): AnyRef = wrapper.getAttribute(name)
      override def getAttributeNames: ju.Enumeration[String] = wrapper.getAttributeNames
      override def getAuthType: String = wrapper.getAuthType
      override def getContextPath: String = wrapper.getContextPath
      override def getCookies: Array[jakarta.servlet.http.Cookie] =
        Option(wrapper.getCookies)
          .map(_.map(_.getNativeCookie.asInstanceOf[jakarta.servlet.http.Cookie]))
          .getOrElse(Array.empty[jakarta.servlet.http.Cookie])
      override def getLocale: ju.Locale = wrapper.getLocale
      override def getLocales: ju.Enumeration[ju.Locale] = wrapper.getLocales
      override def getParameter(name: String): String = wrapper.getParameter(name)
      override def getParameterMap: ju.Map[String, Array[String]] = wrapper.getParameterMap
      override def getParameterNames: ju.Enumeration[String] = wrapper.getParameterNames
      override def getParameterValues(name: String): Array[String] = wrapper.getParameterValues(name)
      override def getPortletContext: jakarta.portlet.PortletContext =
        Option(wrapper.getPortletContext).map(_.getNativePortletContext.asInstanceOf[jakarta.portlet.PortletContext]).orNull
      override def getPortletMode: jakarta.portlet.PortletMode =
        Option(wrapper.getPortletMode).map(m => new jakarta.portlet.PortletMode(m.toString)).orNull
      override def getPortletSession: jakarta.portlet.PortletSession =
        Option(wrapper.getPortletSession).map(_.getNativePortletSession.asInstanceOf[jakarta.portlet.PortletSession]).orNull
      override def getPortletSession(create: Boolean): jakarta.portlet.PortletSession =
        Option(wrapper.getPortletSession(create)).map(_.getNativePortletSession.asInstanceOf[jakarta.portlet.PortletSession]).orNull
      override def getPreferences: jakarta.portlet.PortletPreferences =
        Option(wrapper.getPreferences).map(_.getNativePortletPreferences.asInstanceOf[jakarta.portlet.PortletPreferences]).orNull
      override def getPrivateParameterMap: ju.Map[String, Array[String]] = wrapper.getPrivateParameterMap
      override def getProperties(name: String): ju.Enumeration[String] = wrapper.getProperties(name)
      override def getProperty(name: String): String = wrapper.getProperty(name)
      override def getPropertyNames: ju.Enumeration[String] = wrapper.getPropertyNames
      override def getPublicParameterMap: ju.Map[String, Array[String]] = wrapper.getPublicParameterMap
      override def getRemoteUser: String = wrapper.getRemoteUser()
      override def getRenderParameters: jakarta.portlet.RenderParameters =
        Option(wrapper.getRenderParameters).map(_.getNativeRenderParameters.asInstanceOf[jakarta.portlet.RenderParameters]).orNull
      override def getRequestedSessionId: String = wrapper.getRequestedSessionId
      override def getResponseContentType: String = wrapper.getResponseContentType
      override def getResponseContentTypes: ju.Enumeration[String] = wrapper.getResponseContentTypes
      override def getScheme: String = wrapper.getScheme
      override def getServerName: String = wrapper.getServerName
      override def getServerPort: Int = wrapper.getServerPort
      override def getUserPrincipal: Principal = wrapper.getUserPrincipal
      override def getWindowID: String = wrapper.getWindowID
      override def getWindowState: jakarta.portlet.WindowState =
        Option(wrapper.getWindowState).map(s => new jakarta.portlet.WindowState(s.toString)).orNull
      override def isPortletModeAllowed(mode: jakarta.portlet.PortletMode): Boolean =
        wrapper.isPortletModeAllowed(PortletMode(mode))
      override def isRequestedSessionIdValid: Boolean = wrapper.isRequestedSessionIdValid
      override def isSecure: Boolean = wrapper.isSecure
      override def isUserInRole(role: String): Boolean = wrapper.isUserInRole(role)
      override def isWindowStateAllowed(state: jakarta.portlet.WindowState): Boolean =
        wrapper.isWindowStateAllowed(WindowState(state))
      override def removeAttribute(name: String): Unit = wrapper.removeAttribute(name)
      override def setAttribute(name: String, o: AnyRef): Unit = wrapper.setAttribute(name, o)
    }
}

@nowarn("cat=deprecation")
class JavaxEventRequest(eventRequest: javax.portlet.EventRequest)
  extends JavaxPortletRequest(eventRequest)
     with EventRequest {
  override def getNativePortletRequest: javax.portlet.EventRequest = eventRequest
  override def getMethod: String = eventRequest.getMethod

  override def wrappedWith(wrapper: EventRequestWrapper): javax.portlet.filter.EventRequestWrapper =
    new javax.portlet.filter.EventRequestWrapper(eventRequest) {
      override def setRequest(request: javax.portlet.PortletRequest): Unit = {
        super.setRequest(request)
        request match {
          case er: javax.portlet.EventRequest => wrapper.eventRequest = EventRequest(er)
          case _                              => wrapper.request      = PortletRequest(request)
        }
      }
      override def getMethod: String = wrapper.getMethod
      override def getAttribute(name: String): AnyRef = wrapper.getAttribute(name)
      override def getAttributeNames: ju.Enumeration[String] = wrapper.getAttributeNames
      override def getAuthType: String = wrapper.getAuthType
      override def getContextPath: String = wrapper.getContextPath
      override def getCookies: Array[javax.servlet.http.Cookie] =
        Option(wrapper.getCookies)
          .map(_.map(_.getNativeCookie.asInstanceOf[javax.servlet.http.Cookie]))
          .getOrElse(Array.empty[javax.servlet.http.Cookie])
      override def getLocale: ju.Locale = wrapper.getLocale
      override def getLocales: ju.Enumeration[ju.Locale] = wrapper.getLocales
      override def getParameter(name: String): String = wrapper.getParameter(name)
      override def getParameterMap: ju.Map[String, Array[String]] = wrapper.getParameterMap
      override def getParameterNames: ju.Enumeration[String] = wrapper.getParameterNames
      override def getParameterValues(name: String): Array[String] = wrapper.getParameterValues(name)
      override def getPortletContext: javax.portlet.PortletContext =
        Option(wrapper.getPortletContext).map(_.getNativePortletContext.asInstanceOf[javax.portlet.PortletContext]).orNull
      override def getPortletMode: javax.portlet.PortletMode =
        Option(wrapper.getPortletMode).map(m => new javax.portlet.PortletMode(m.toString)).orNull
      override def getPortletSession: javax.portlet.PortletSession =
        Option(wrapper.getPortletSession).map(_.getNativePortletSession.asInstanceOf[javax.portlet.PortletSession]).orNull
      override def getPortletSession(create: Boolean): javax.portlet.PortletSession =
        Option(wrapper.getPortletSession(create)).map(_.getNativePortletSession.asInstanceOf[javax.portlet.PortletSession]).orNull
      override def getPreferences: javax.portlet.PortletPreferences =
        Option(wrapper.getPreferences).map(_.getNativePortletPreferences.asInstanceOf[javax.portlet.PortletPreferences]).orNull
      override def getPrivateParameterMap: ju.Map[String, Array[String]] = wrapper.getPrivateParameterMap
      override def getProperties(name: String): ju.Enumeration[String] = wrapper.getProperties(name)
      override def getProperty(name: String): String = wrapper.getProperty(name)
      override def getPropertyNames: ju.Enumeration[String] = wrapper.getPropertyNames
      override def getPublicParameterMap: ju.Map[String, Array[String]] = wrapper.getPublicParameterMap
      override def getRemoteUser: String = wrapper.getRemoteUser()
      override def getRenderParameters: javax.portlet.RenderParameters =
        Option(wrapper.getRenderParameters).map(_.getNativeRenderParameters.asInstanceOf[javax.portlet.RenderParameters]).orNull
      override def getRequestedSessionId: String = wrapper.getRequestedSessionId
      override def getResponseContentType: String = wrapper.getResponseContentType
      override def getResponseContentTypes: ju.Enumeration[String] = wrapper.getResponseContentTypes
      override def getScheme: String = wrapper.getScheme
      override def getServerName: String = wrapper.getServerName
      override def getServerPort: Int = wrapper.getServerPort
      override def getUserPrincipal: Principal = wrapper.getUserPrincipal
      override def getWindowID: String = wrapper.getWindowID
      override def getWindowState: javax.portlet.WindowState =
        Option(wrapper.getWindowState).map(s => new javax.portlet.WindowState(s.toString)).orNull
      override def isPortletModeAllowed(mode: javax.portlet.PortletMode): Boolean =
        wrapper.isPortletModeAllowed(PortletMode(mode))
      override def isRequestedSessionIdValid: Boolean = wrapper.isRequestedSessionIdValid
      override def isSecure: Boolean = wrapper.isSecure
      override def isUserInRole(role: String): Boolean = wrapper.isUserInRole(role)
      override def isWindowStateAllowed(state: javax.portlet.WindowState): Boolean =
        wrapper.isWindowStateAllowed(WindowState(state))
      override def removeAttribute(name: String): Unit = wrapper.removeAttribute(name)
      override def setAttribute(name: String, o: AnyRef): Unit = wrapper.setAttribute(name, o)
    }
}

@nowarn("cat=deprecation")
class JakartaEventRequest(eventRequest: jakarta.portlet.EventRequest)
  extends JakartaPortletRequest(eventRequest)
     with EventRequest {
  override def getNativePortletRequest: jakarta.portlet.EventRequest = eventRequest
  override def getMethod: String = eventRequest.getMethod

  override def wrappedWith(wrapper: EventRequestWrapper): jakarta.portlet.filter.EventRequestWrapper =
    new jakarta.portlet.filter.EventRequestWrapper(eventRequest) {
      override def setRequest(request: jakarta.portlet.PortletRequest): Unit = {
        super.setRequest(request)
        request match {
          case er: jakarta.portlet.EventRequest => wrapper.eventRequest = EventRequest(er)
          case _                                => wrapper.request      = PortletRequest(request)
        }
      }
      override def getMethod: String = wrapper.getMethod
      override def getAttribute(name: String): AnyRef = wrapper.getAttribute(name)
      override def getAttributeNames: ju.Enumeration[String] = wrapper.getAttributeNames
      override def getAuthType: String = wrapper.getAuthType
      override def getContextPath: String = wrapper.getContextPath
      override def getCookies: Array[jakarta.servlet.http.Cookie] =
        Option(wrapper.getCookies)
          .map(_.map(_.getNativeCookie.asInstanceOf[jakarta.servlet.http.Cookie]))
          .getOrElse(Array.empty[jakarta.servlet.http.Cookie])
      override def getLocale: ju.Locale = wrapper.getLocale
      override def getLocales: ju.Enumeration[ju.Locale] = wrapper.getLocales
      override def getParameter(name: String): String = wrapper.getParameter(name)
      override def getParameterMap: ju.Map[String, Array[String]] = wrapper.getParameterMap
      override def getParameterNames: ju.Enumeration[String] = wrapper.getParameterNames
      override def getParameterValues(name: String): Array[String] = wrapper.getParameterValues(name)
      override def getPortletContext: jakarta.portlet.PortletContext =
        Option(wrapper.getPortletContext).map(_.getNativePortletContext.asInstanceOf[jakarta.portlet.PortletContext]).orNull
      override def getPortletMode: jakarta.portlet.PortletMode =
        Option(wrapper.getPortletMode).map(m => new jakarta.portlet.PortletMode(m.toString)).orNull
      override def getPortletSession: jakarta.portlet.PortletSession =
        Option(wrapper.getPortletSession).map(_.getNativePortletSession.asInstanceOf[jakarta.portlet.PortletSession]).orNull
      override def getPortletSession(create: Boolean): jakarta.portlet.PortletSession =
        Option(wrapper.getPortletSession(create)).map(_.getNativePortletSession.asInstanceOf[jakarta.portlet.PortletSession]).orNull
      override def getPreferences: jakarta.portlet.PortletPreferences =
        Option(wrapper.getPreferences).map(_.getNativePortletPreferences.asInstanceOf[jakarta.portlet.PortletPreferences]).orNull
      override def getPrivateParameterMap: ju.Map[String, Array[String]] = wrapper.getPrivateParameterMap
      override def getProperties(name: String): ju.Enumeration[String] = wrapper.getProperties(name)
      override def getProperty(name: String): String = wrapper.getProperty(name)
      override def getPropertyNames: ju.Enumeration[String] = wrapper.getPropertyNames
      override def getPublicParameterMap: ju.Map[String, Array[String]] = wrapper.getPublicParameterMap
      override def getRemoteUser: String = wrapper.getRemoteUser()
      override def getRenderParameters: jakarta.portlet.RenderParameters =
        Option(wrapper.getRenderParameters).map(_.getNativeRenderParameters.asInstanceOf[jakarta.portlet.RenderParameters]).orNull
      override def getRequestedSessionId: String = wrapper.getRequestedSessionId
      override def getResponseContentType: String = wrapper.getResponseContentType
      override def getResponseContentTypes: ju.Enumeration[String] = wrapper.getResponseContentTypes
      override def getScheme: String = wrapper.getScheme
      override def getServerName: String = wrapper.getServerName
      override def getServerPort: Int = wrapper.getServerPort
      override def getUserPrincipal: Principal = wrapper.getUserPrincipal
      override def getWindowID: String = wrapper.getWindowID
      override def getWindowState: jakarta.portlet.WindowState =
        Option(wrapper.getWindowState).map(s => new jakarta.portlet.WindowState(s.toString)).orNull
      override def isPortletModeAllowed(mode: jakarta.portlet.PortletMode): Boolean =
        wrapper.isPortletModeAllowed(PortletMode(mode))
      override def isRequestedSessionIdValid: Boolean = wrapper.isRequestedSessionIdValid
      override def isSecure: Boolean = wrapper.isSecure
      override def isUserInRole(role: String): Boolean = wrapper.isUserInRole(role)
      override def isWindowStateAllowed(state: jakarta.portlet.WindowState): Boolean =
        wrapper.isWindowStateAllowed(WindowState(state))
      override def removeAttribute(name: String): Unit = wrapper.removeAttribute(name)
      override def setAttribute(name: String, o: AnyRef): Unit = wrapper.setAttribute(name, o)
    }
}

class JavaxClientDataRequest(clientDataRequest: javax.portlet.ClientDataRequest)
  extends JavaxPortletRequest(clientDataRequest)
     with ClientDataRequest {
  override def getNativePortletRequest: javax.portlet.ClientDataRequest = clientDataRequest

  override def getCharacterEncoding: String = clientDataRequest.getCharacterEncoding
  override def getContentLength: Int = clientDataRequest.getContentLength
  override def getContentLengthLong: Long = clientDataRequest.getContentLengthLong
  override def getContentType: String = clientDataRequest.getContentType
  override def getMethod: String = clientDataRequest.getMethod
  override def getPortletInputStream: InputStream = clientDataRequest.getPortletInputStream
  override def getReader: BufferedReader = clientDataRequest.getReader
  override def setCharacterEncoding(enc: String): Unit = clientDataRequest.setCharacterEncoding(enc)
}

class JakartaClientDataRequest(clientDataRequest: jakarta.portlet.ClientDataRequest)
  extends JakartaPortletRequest(clientDataRequest)
     with ClientDataRequest {
  override def getNativePortletRequest: jakarta.portlet.ClientDataRequest = clientDataRequest

  override def getCharacterEncoding: String = clientDataRequest.getCharacterEncoding
  override def getContentLength: Int = clientDataRequest.getContentLength
  override def getContentLengthLong: Long = clientDataRequest.getContentLengthLong
  override def getContentType: String = clientDataRequest.getContentType
  override def getMethod: String = clientDataRequest.getMethod
  override def getPortletInputStream: InputStream = clientDataRequest.getPortletInputStream
  override def getReader: BufferedReader = clientDataRequest.getReader
  override def setCharacterEncoding(enc: String): Unit = clientDataRequest.setCharacterEncoding(enc)
}

@nowarn("cat=deprecation")
class JavaxActionRequest(actionRequest: javax.portlet.ActionRequest)
  extends JavaxClientDataRequest(actionRequest)
     with ActionRequest {
  override def getNativePortletRequest: javax.portlet.ActionRequest = actionRequest

  override def wrappedWith(wrapper: ActionRequestWrapper): javax.portlet.filter.ActionRequestWrapper =
    new javax.portlet.filter.ActionRequestWrapper(actionRequest) {
      override def setRequest(request: javax.portlet.PortletRequest): Unit = {
        super.setRequest(request)
        request match {
          case ar: javax.portlet.ActionRequest => wrapper.actionRequest = ActionRequest(ar)
          case _                               => wrapper.request       = PortletRequest(request)
        }
      }
      override def getCharacterEncoding: String = wrapper.getCharacterEncoding
      override def getContentLength: Int = wrapper.getContentLength
      override def getContentLengthLong: Long = wrapper.getContentLengthLong
      override def getContentType: String = wrapper.getContentType
      override def getMethod: String = wrapper.getMethod
      override def getPortletInputStream: InputStream = wrapper.getPortletInputStream
      override def getReader: BufferedReader = wrapper.getReader
      override def setCharacterEncoding(enc: String): Unit = wrapper.setCharacterEncoding(enc)
      override def getAttribute(name: String): AnyRef = wrapper.getAttribute(name)
      override def getAttributeNames: ju.Enumeration[String] = wrapper.getAttributeNames
      override def getAuthType: String = wrapper.getAuthType
      override def getContextPath: String = wrapper.getContextPath
      override def getCookies: Array[javax.servlet.http.Cookie] =
        Option(wrapper.getCookies)
          .map(_.map(_.getNativeCookie.asInstanceOf[javax.servlet.http.Cookie]))
          .getOrElse(Array.empty[javax.servlet.http.Cookie])
      override def getLocale: ju.Locale = wrapper.getLocale
      override def getLocales: ju.Enumeration[ju.Locale] = wrapper.getLocales
      override def getParameter(name: String): String = wrapper.getParameter(name)
      override def getParameterMap: ju.Map[String, Array[String]] = wrapper.getParameterMap
      override def getParameterNames: ju.Enumeration[String] = wrapper.getParameterNames
      override def getParameterValues(name: String): Array[String] = wrapper.getParameterValues(name)
      override def getPortletContext: javax.portlet.PortletContext =
        Option(wrapper.getPortletContext).map(_.getNativePortletContext.asInstanceOf[javax.portlet.PortletContext]).orNull
      override def getPortletMode: javax.portlet.PortletMode =
        Option(wrapper.getPortletMode).map(m => new javax.portlet.PortletMode(m.toString)).orNull
      override def getPortletSession: javax.portlet.PortletSession =
        Option(wrapper.getPortletSession).map(_.getNativePortletSession.asInstanceOf[javax.portlet.PortletSession]).orNull
      override def getPortletSession(create: Boolean): javax.portlet.PortletSession =
        Option(wrapper.getPortletSession(create)).map(_.getNativePortletSession.asInstanceOf[javax.portlet.PortletSession]).orNull
      override def getPreferences: javax.portlet.PortletPreferences =
        Option(wrapper.getPreferences).map(_.getNativePortletPreferences.asInstanceOf[javax.portlet.PortletPreferences]).orNull
      override def getPrivateParameterMap: ju.Map[String, Array[String]] = wrapper.getPrivateParameterMap
      override def getProperties(name: String): ju.Enumeration[String] = wrapper.getProperties(name)
      override def getProperty(name: String): String = wrapper.getProperty(name)
      override def getPropertyNames: ju.Enumeration[String] = wrapper.getPropertyNames
      override def getPublicParameterMap: ju.Map[String, Array[String]] = wrapper.getPublicParameterMap
      override def getRemoteUser: String = wrapper.getRemoteUser()
      override def getRenderParameters: javax.portlet.RenderParameters =
        Option(wrapper.getRenderParameters).map(_.getNativeRenderParameters.asInstanceOf[javax.portlet.RenderParameters]).orNull
      override def getRequestedSessionId: String = wrapper.getRequestedSessionId
      override def getResponseContentType: String = wrapper.getResponseContentType
      override def getResponseContentTypes: ju.Enumeration[String] = wrapper.getResponseContentTypes
      override def getScheme: String = wrapper.getScheme
      override def getServerName: String = wrapper.getServerName
      override def getServerPort: Int = wrapper.getServerPort
      override def getUserPrincipal: Principal = wrapper.getUserPrincipal
      override def getWindowID: String = wrapper.getWindowID
      override def getWindowState: javax.portlet.WindowState =
        Option(wrapper.getWindowState).map(s => new javax.portlet.WindowState(s.toString)).orNull
      override def isPortletModeAllowed(mode: javax.portlet.PortletMode): Boolean =
        wrapper.isPortletModeAllowed(PortletMode(mode))
      override def isRequestedSessionIdValid: Boolean = wrapper.isRequestedSessionIdValid
      override def isSecure: Boolean = wrapper.isSecure
      override def isUserInRole(role: String): Boolean = wrapper.isUserInRole(role)
      override def isWindowStateAllowed(state: javax.portlet.WindowState): Boolean =
        wrapper.isWindowStateAllowed(WindowState(state))
      override def removeAttribute(name: String): Unit = wrapper.removeAttribute(name)
      override def setAttribute(name: String, o: AnyRef): Unit = wrapper.setAttribute(name, o)
    }
}

@nowarn("cat=deprecation")
class JakartaActionRequest(actionRequest: jakarta.portlet.ActionRequest)
  extends JakartaClientDataRequest(actionRequest)
     with ActionRequest {
  override def getNativePortletRequest: jakarta.portlet.ActionRequest = actionRequest

  override def wrappedWith(wrapper: ActionRequestWrapper): jakarta.portlet.filter.ActionRequestWrapper =
    new jakarta.portlet.filter.ActionRequestWrapper(actionRequest) {
      override def setRequest(request: jakarta.portlet.PortletRequest): Unit = {
        super.setRequest(request)
        request match {
          case ar: jakarta.portlet.ActionRequest => wrapper.actionRequest = ActionRequest(ar)
          case _                                 => wrapper.request       = PortletRequest(request)
        }
      }
      override def getCharacterEncoding: String = wrapper.getCharacterEncoding
      override def getContentLength: Int = wrapper.getContentLength
      override def getContentLengthLong: Long = wrapper.getContentLengthLong
      override def getContentType: String = wrapper.getContentType
      override def getMethod: String = wrapper.getMethod
      override def getPortletInputStream: InputStream = wrapper.getPortletInputStream
      override def getReader: BufferedReader = wrapper.getReader
      override def setCharacterEncoding(enc: String): Unit = wrapper.setCharacterEncoding(enc)
      override def getAttribute(name: String): AnyRef = wrapper.getAttribute(name)
      override def getAttributeNames: ju.Enumeration[String] = wrapper.getAttributeNames
      override def getAuthType: String = wrapper.getAuthType
      override def getContextPath: String = wrapper.getContextPath
      override def getCookies: Array[jakarta.servlet.http.Cookie] =
        Option(wrapper.getCookies)
          .map(_.map(_.getNativeCookie.asInstanceOf[jakarta.servlet.http.Cookie]))
          .getOrElse(Array.empty[jakarta.servlet.http.Cookie])
      override def getLocale: ju.Locale = wrapper.getLocale
      override def getLocales: ju.Enumeration[ju.Locale] = wrapper.getLocales
      override def getParameter(name: String): String = wrapper.getParameter(name)
      override def getParameterMap: ju.Map[String, Array[String]] = wrapper.getParameterMap
      override def getParameterNames: ju.Enumeration[String] = wrapper.getParameterNames
      override def getParameterValues(name: String): Array[String] = wrapper.getParameterValues(name)
      override def getPortletContext: jakarta.portlet.PortletContext =
        Option(wrapper.getPortletContext).map(_.getNativePortletContext.asInstanceOf[jakarta.portlet.PortletContext]).orNull
      override def getPortletMode: jakarta.portlet.PortletMode =
        Option(wrapper.getPortletMode).map(m => new jakarta.portlet.PortletMode(m.toString)).orNull
      override def getPortletSession: jakarta.portlet.PortletSession =
        Option(wrapper.getPortletSession).map(_.getNativePortletSession.asInstanceOf[jakarta.portlet.PortletSession]).orNull
      override def getPortletSession(create: Boolean): jakarta.portlet.PortletSession =
        Option(wrapper.getPortletSession(create)).map(_.getNativePortletSession.asInstanceOf[jakarta.portlet.PortletSession]).orNull
      override def getPreferences: jakarta.portlet.PortletPreferences =
        Option(wrapper.getPreferences).map(_.getNativePortletPreferences.asInstanceOf[jakarta.portlet.PortletPreferences]).orNull
      override def getPrivateParameterMap: ju.Map[String, Array[String]] = wrapper.getPrivateParameterMap
      override def getProperties(name: String): ju.Enumeration[String] = wrapper.getProperties(name)
      override def getProperty(name: String): String = wrapper.getProperty(name)
      override def getPropertyNames: ju.Enumeration[String] = wrapper.getPropertyNames
      override def getPublicParameterMap: ju.Map[String, Array[String]] = wrapper.getPublicParameterMap
      override def getRemoteUser: String = wrapper.getRemoteUser()
      override def getRenderParameters: jakarta.portlet.RenderParameters =
        Option(wrapper.getRenderParameters).map(_.getNativeRenderParameters.asInstanceOf[jakarta.portlet.RenderParameters]).orNull
      override def getRequestedSessionId: String = wrapper.getRequestedSessionId
      override def getResponseContentType: String = wrapper.getResponseContentType
      override def getResponseContentTypes: ju.Enumeration[String] = wrapper.getResponseContentTypes
      override def getScheme: String = wrapper.getScheme
      override def getServerName: String = wrapper.getServerName
      override def getServerPort: Int = wrapper.getServerPort
      override def getUserPrincipal: Principal = wrapper.getUserPrincipal
      override def getWindowID: String = wrapper.getWindowID
      override def getWindowState: jakarta.portlet.WindowState =
        Option(wrapper.getWindowState).map(s => new jakarta.portlet.WindowState(s.toString)).orNull
      override def isPortletModeAllowed(mode: jakarta.portlet.PortletMode): Boolean =
        wrapper.isPortletModeAllowed(PortletMode(mode))
      override def isRequestedSessionIdValid: Boolean = wrapper.isRequestedSessionIdValid
      override def isSecure: Boolean = wrapper.isSecure
      override def isUserInRole(role: String): Boolean = wrapper.isUserInRole(role)
      override def isWindowStateAllowed(state: jakarta.portlet.WindowState): Boolean =
        wrapper.isWindowStateAllowed(WindowState(state))
      override def removeAttribute(name: String): Unit = wrapper.removeAttribute(name)
      override def setAttribute(name: String, o: AnyRef): Unit = wrapper.setAttribute(name, o)
    }
}

@nowarn("cat=deprecation")
class JavaxResourceRequest(resourceRequest: javax.portlet.ResourceRequest)
  extends JavaxClientDataRequest(resourceRequest)
     with ResourceRequest {
  override def getNativePortletRequest: javax.portlet.ResourceRequest = resourceRequest

  override def getCacheability: String = resourceRequest.getCacheability
  override def getETag: String = resourceRequest.getETag
  override def getPrivateRenderParameterMap: ju.Map[String, Array[String]] = resourceRequest.getPrivateRenderParameterMap
  override def getResourceID: String = resourceRequest.getResourceID

  override def wrappedWith(wrapper: ResourceRequestWrapper): javax.portlet.filter.ResourceRequestWrapper =
    new javax.portlet.filter.ResourceRequestWrapper(resourceRequest) {
      override def setRequest(request: javax.portlet.PortletRequest): Unit = {
        super.setRequest(request)
        request match {
          case rr: javax.portlet.ResourceRequest => wrapper.resourceRequest = ResourceRequest(rr)
          case _                                 => wrapper.request         = PortletRequest(request)
        }
      }
      override def getCacheability: String = wrapper.getCacheability
      override def getETag: String = wrapper.getETag
      override def getPrivateRenderParameterMap: ju.Map[String, Array[String]] = wrapper.getPrivateRenderParameterMap
      override def getResourceID: String = wrapper.getResourceID
      override def getCharacterEncoding: String = wrapper.getCharacterEncoding
      override def getContentLength: Int = wrapper.getContentLength
      override def getContentLengthLong: Long = wrapper.getContentLengthLong
      override def getContentType: String = wrapper.getContentType
      override def getMethod: String = wrapper.getMethod
      override def getPortletInputStream: InputStream = wrapper.getPortletInputStream
      override def getReader: BufferedReader = wrapper.getReader
      override def setCharacterEncoding(enc: String): Unit = wrapper.setCharacterEncoding(enc)
      override def getAttribute(name: String): AnyRef = wrapper.getAttribute(name)
      override def getAttributeNames: ju.Enumeration[String] = wrapper.getAttributeNames
      override def getAuthType: String = wrapper.getAuthType
      override def getContextPath: String = wrapper.getContextPath
      override def getCookies: Array[javax.servlet.http.Cookie] =
        Option(wrapper.getCookies)
          .map(_.map(_.getNativeCookie.asInstanceOf[javax.servlet.http.Cookie]))
          .getOrElse(Array.empty[javax.servlet.http.Cookie])
      override def getLocale: ju.Locale = wrapper.getLocale
      override def getLocales: ju.Enumeration[ju.Locale] = wrapper.getLocales
      override def getParameter(name: String): String = wrapper.getParameter(name)
      override def getParameterMap: ju.Map[String, Array[String]] = wrapper.getParameterMap
      override def getParameterNames: ju.Enumeration[String] = wrapper.getParameterNames
      override def getParameterValues(name: String): Array[String] = wrapper.getParameterValues(name)
      override def getPortletContext: javax.portlet.PortletContext =
        Option(wrapper.getPortletContext).map(_.getNativePortletContext.asInstanceOf[javax.portlet.PortletContext]).orNull
      override def getPortletMode: javax.portlet.PortletMode =
        Option(wrapper.getPortletMode).map(m => new javax.portlet.PortletMode(m.toString)).orNull
      override def getPortletSession: javax.portlet.PortletSession =
        Option(wrapper.getPortletSession).map(_.getNativePortletSession.asInstanceOf[javax.portlet.PortletSession]).orNull
      override def getPortletSession(create: Boolean): javax.portlet.PortletSession =
        Option(wrapper.getPortletSession(create)).map(_.getNativePortletSession.asInstanceOf[javax.portlet.PortletSession]).orNull
      override def getPreferences: javax.portlet.PortletPreferences =
        Option(wrapper.getPreferences).map(_.getNativePortletPreferences.asInstanceOf[javax.portlet.PortletPreferences]).orNull
      override def getPrivateParameterMap: ju.Map[String, Array[String]] = wrapper.getPrivateParameterMap
      override def getProperties(name: String): ju.Enumeration[String] = wrapper.getProperties(name)
      override def getProperty(name: String): String = wrapper.getProperty(name)
      override def getPropertyNames: ju.Enumeration[String] = wrapper.getPropertyNames
      override def getPublicParameterMap: ju.Map[String, Array[String]] = wrapper.getPublicParameterMap
      override def getRemoteUser: String = wrapper.getRemoteUser()
      override def getRenderParameters: javax.portlet.RenderParameters =
        Option(wrapper.getRenderParameters).map(_.getNativeRenderParameters.asInstanceOf[javax.portlet.RenderParameters]).orNull
      override def getRequestedSessionId: String = wrapper.getRequestedSessionId
      override def getResponseContentType: String = wrapper.getResponseContentType
      override def getResponseContentTypes: ju.Enumeration[String] = wrapper.getResponseContentTypes
      override def getScheme: String = wrapper.getScheme
      override def getServerName: String = wrapper.getServerName
      override def getServerPort: Int = wrapper.getServerPort
      override def getUserPrincipal: Principal = wrapper.getUserPrincipal
      override def getWindowID: String = wrapper.getWindowID
      override def getWindowState: javax.portlet.WindowState =
        Option(wrapper.getWindowState).map(s => new javax.portlet.WindowState(s.toString)).orNull
      override def isPortletModeAllowed(mode: javax.portlet.PortletMode): Boolean =
        wrapper.isPortletModeAllowed(PortletMode(mode))
      override def isRequestedSessionIdValid: Boolean = wrapper.isRequestedSessionIdValid
      override def isSecure: Boolean = wrapper.isSecure
      override def isUserInRole(role: String): Boolean = wrapper.isUserInRole(role)
      override def isWindowStateAllowed(state: javax.portlet.WindowState): Boolean =
        wrapper.isWindowStateAllowed(WindowState(state))
      override def removeAttribute(name: String): Unit = wrapper.removeAttribute(name)
      override def setAttribute(name: String, o: AnyRef): Unit = wrapper.setAttribute(name, o)
    }
}

@nowarn("cat=deprecation")
class JakartaResourceRequest(resourceRequest: jakarta.portlet.ResourceRequest)
  extends JakartaClientDataRequest(resourceRequest)
     with ResourceRequest {
  override def getNativePortletRequest: jakarta.portlet.ResourceRequest = resourceRequest

  override def getCacheability: String = resourceRequest.getCacheability
  override def getETag: String = resourceRequest.getETag
  override def getPrivateRenderParameterMap: ju.Map[String, Array[String]] = resourceRequest.getPrivateRenderParameterMap
  override def getResourceID: String = resourceRequest.getResourceID

  override def wrappedWith(wrapper: ResourceRequestWrapper): jakarta.portlet.filter.ResourceRequestWrapper =
    new jakarta.portlet.filter.ResourceRequestWrapper(resourceRequest) {
      override def setRequest(request: jakarta.portlet.PortletRequest): Unit = {
        super.setRequest(request)
        request match {
          case rr: jakarta.portlet.ResourceRequest => wrapper.resourceRequest = ResourceRequest(rr)
          case _                                   => wrapper.request         = PortletRequest(request)
        }
      }
      override def getCacheability: String = wrapper.getCacheability
      override def getETag: String = wrapper.getETag
      override def getPrivateRenderParameterMap: ju.Map[String, Array[String]] = wrapper.getPrivateRenderParameterMap
      override def getResourceID: String = wrapper.getResourceID
      override def getCharacterEncoding: String = wrapper.getCharacterEncoding
      override def getContentLength: Int = wrapper.getContentLength
      override def getContentLengthLong: Long = wrapper.getContentLengthLong
      override def getContentType: String = wrapper.getContentType
      override def getMethod: String = wrapper.getMethod
      override def getPortletInputStream: InputStream = wrapper.getPortletInputStream
      override def getReader: BufferedReader = wrapper.getReader
      override def setCharacterEncoding(enc: String): Unit = wrapper.setCharacterEncoding(enc)
      override def getAttribute(name: String): AnyRef = wrapper.getAttribute(name)
      override def getAttributeNames: ju.Enumeration[String] = wrapper.getAttributeNames
      override def getAuthType: String = wrapper.getAuthType
      override def getContextPath: String = wrapper.getContextPath
      override def getCookies: Array[jakarta.servlet.http.Cookie] =
        Option(wrapper.getCookies)
          .map(_.map(_.getNativeCookie.asInstanceOf[jakarta.servlet.http.Cookie]))
          .getOrElse(Array.empty[jakarta.servlet.http.Cookie])
      override def getLocale: ju.Locale = wrapper.getLocale
      override def getLocales: ju.Enumeration[ju.Locale] = wrapper.getLocales
      override def getParameter(name: String): String = wrapper.getParameter(name)
      override def getParameterMap: ju.Map[String, Array[String]] = wrapper.getParameterMap
      override def getParameterNames: ju.Enumeration[String] = wrapper.getParameterNames
      override def getParameterValues(name: String): Array[String] = wrapper.getParameterValues(name)
      override def getPortletContext: jakarta.portlet.PortletContext =
        Option(wrapper.getPortletContext).map(_.getNativePortletContext.asInstanceOf[jakarta.portlet.PortletContext]).orNull
      override def getPortletMode: jakarta.portlet.PortletMode =
        Option(wrapper.getPortletMode).map(m => new jakarta.portlet.PortletMode(m.toString)).orNull
      override def getPortletSession: jakarta.portlet.PortletSession =
        Option(wrapper.getPortletSession).map(_.getNativePortletSession.asInstanceOf[jakarta.portlet.PortletSession]).orNull
      override def getPortletSession(create: Boolean): jakarta.portlet.PortletSession =
        Option(wrapper.getPortletSession(create)).map(_.getNativePortletSession.asInstanceOf[jakarta.portlet.PortletSession]).orNull
      override def getPreferences: jakarta.portlet.PortletPreferences =
        Option(wrapper.getPreferences).map(_.getNativePortletPreferences.asInstanceOf[jakarta.portlet.PortletPreferences]).orNull
      override def getPrivateParameterMap: ju.Map[String, Array[String]] = wrapper.getPrivateParameterMap
      override def getProperties(name: String): ju.Enumeration[String] = wrapper.getProperties(name)
      override def getProperty(name: String): String = wrapper.getProperty(name)
      override def getPropertyNames: ju.Enumeration[String] = wrapper.getPropertyNames
      override def getPublicParameterMap: ju.Map[String, Array[String]] = wrapper.getPublicParameterMap
      override def getRemoteUser: String = wrapper.getRemoteUser()
      override def getRenderParameters: jakarta.portlet.RenderParameters =
        Option(wrapper.getRenderParameters).map(_.getNativeRenderParameters.asInstanceOf[jakarta.portlet.RenderParameters]).orNull
      override def getRequestedSessionId: String = wrapper.getRequestedSessionId
      override def getResponseContentType: String = wrapper.getResponseContentType
      override def getResponseContentTypes: ju.Enumeration[String] = wrapper.getResponseContentTypes
      override def getScheme: String = wrapper.getScheme
      override def getServerName: String = wrapper.getServerName
      override def getServerPort: Int = wrapper.getServerPort
      override def getUserPrincipal: Principal = wrapper.getUserPrincipal
      override def getWindowID: String = wrapper.getWindowID
      override def getWindowState: jakarta.portlet.WindowState =
        Option(wrapper.getWindowState).map(s => new jakarta.portlet.WindowState(s.toString)).orNull
      override def isPortletModeAllowed(mode: jakarta.portlet.PortletMode): Boolean =
        wrapper.isPortletModeAllowed(PortletMode(mode))
      override def isRequestedSessionIdValid: Boolean = wrapper.isRequestedSessionIdValid
      override def isSecure: Boolean = wrapper.isSecure
      override def isUserInRole(role: String): Boolean = wrapper.isUserInRole(role)
      override def isWindowStateAllowed(state: jakarta.portlet.WindowState): Boolean =
        wrapper.isWindowStateAllowed(WindowState(state))
      override def removeAttribute(name: String): Unit = wrapper.removeAttribute(name)
      override def setAttribute(name: String, o: AnyRef): Unit = wrapper.setAttribute(name, o)
    }
}
