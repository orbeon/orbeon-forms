/**
 *  Copyright (C) 2005 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.externalcontext;

import org.orbeon.oxf.pipeline.api.ExternalContext;

import javax.portlet.*;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Map;
import java.util.Collections;
import java.security.Principal;

/**
* Wrap an ExternalContext.Request into a PortletRequest.
 */
public class ExternalContextToPortletRenderRequestWrapper implements RenderRequest {
    private ExternalContext.Request request;

    public ExternalContextToPortletRenderRequestWrapper(ExternalContext.Request request) {
        this.request = request;
    }

    public Object getAttribute(String clazz) {
        return request.getAttributesMap().get(clazz);
    }

    public Enumeration getAttributeNames() {
        return Collections.enumeration(request.getAttributesMap().keySet());
    }

    public String getAuthType() {
        return request.getAuthType();
    }

    public String getContextPath() {
        return request.getContextPath();
    }

    public Locale getLocale() {
        return request.getLocale();
    }

    public Enumeration getLocales() {
        return request.getLocales();
    }

    public String getParameter(String clazz) {
        final String[] values = getParameterValues(clazz);
        return (values == null) ? null : values[0];
    }

    public Map getParameterMap() {
        return request.getParameterMap();
    }

    public Enumeration getParameterNames() {
        return Collections.enumeration(request.getParameterMap().keySet());
    }

    public String[] getParameterValues(String clazz) {
        return (String[]) request.getParameterMap().get(clazz);
    }

    public PortalContext getPortalContext() {
        return null;//TODO
    }

    public PortletMode getPortletMode() {
        return null;//TODO
    }

    public PortletSession getPortletSession() {
        return null;//TODO
    }

    public PortletSession getPortletSession(boolean b) {
        return null;//TODO
    }

    public PortletPreferences getPreferences() {
        return null;//TODO
    }

    public Enumeration getProperties(String clazz) {
        return null;//TODO
    }

    public String getProperty(String clazz) {
        return null;//TODO
    }

    public Enumeration getPropertyNames() {
        return null;//TODO
    }

    public String getRemoteUser() {
        return request.getRemoteUser();
    }

    public String getRequestedSessionId() {
        return request.getRequestedSessionId();
    }

    public String getResponseContentType() {
        return null;//TODO
    }

    public Enumeration getResponseContentTypes() {
        return null;//TODO
    }

    public String getScheme() {
        return request.getScheme();
    }

    public String getServerName() {
        return request.getServerName();
    }

    public int getServerPort() {
        return request.getServerPort();
    }

    public Principal getUserPrincipal() {
        return request.getUserPrincipal();
    }

    public WindowState getWindowState() {
        return null;//TODO
    }

    public boolean isPortletModeAllowed(PortletMode portletMode) {
        return false;//TODO
    }

    public boolean isRequestedSessionIdValid() {
        return request.isRequestedSessionIdValid();
    }

    public boolean isSecure() {
        return request.isSecure();
    }

    public boolean isUserInRole(String clazz) {
        return request.isUserInRole(clazz);
    }

    public boolean isWindowStateAllowed(WindowState windowState) {
        return false;//TODO
    }

    public void removeAttribute(String clazz) {
        request.getAttributesMap().remove(clazz);
    }

    public void setAttribute(String clazz, Object o) {
        request.getAttributesMap().put(clazz, o);
    }
}
