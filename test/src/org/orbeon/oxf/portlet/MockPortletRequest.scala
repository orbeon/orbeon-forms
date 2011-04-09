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

import javax.portlet.{PortletMode, WindowState, PortletRequest}
import java.lang.String

class MockPortletRequest extends PortletRequest {
    def getPublicParameterMap = null
    def getPrivateParameterMap = null
    def getCookies = null
    def getWindowID = ""
    def getServerPort = 0
    def getServerName = ""
    def getScheme = ""
    def getLocales = null
    def getLocale = null
    def getResponseContentTypes = null
    def getResponseContentType = ""
    def isRequestedSessionIdValid = false
    def getRequestedSessionId = ""
    def removeAttribute(p1: String) {}
    def setAttribute(p1: String, p2: AnyRef) {}
    def isSecure = false
    def getParameterMap = null
    def getParameterValues(p1: String) = null
    def getParameterNames = null
    def getParameter(p1: String) = ""
    def getAttributeNames: java.util.Enumeration[String] = null
    def getAttribute(p1: String): Object = null
    def isUserInRole(p1: String) = false
    def getUserPrincipal = null
    def getRemoteUser = ""
    def getContextPath = ""
    def getAuthType = ""
    def getPortalContext = null
    def getPropertyNames: java.util.Enumeration[String] = null
    def getProperties(p1: String): java.util.Enumeration[String] = null
    def getProperty(p1: String) = ""
    def getPortletSession(p1: Boolean) = null
    def getPortletSession = null
    def getPreferences = null
    def getWindowState = null
    def getPortletMode = null
    def isPortletModeAllowed(p1: PortletMode) = false
    def isWindowStateAllowed(p1: WindowState) = false
}