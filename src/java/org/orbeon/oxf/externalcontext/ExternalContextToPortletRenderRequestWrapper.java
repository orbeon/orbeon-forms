/**
 * Copyright (C) 2010 Orbeon, Inc.
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
package org.orbeon.oxf.externalcontext;

import org.orbeon.oxf.pipeline.api.ExternalContext;

import javax.portlet.*;
import javax.servlet.http.Cookie;
import java.security.Principal;
import java.util.*;

/**
 * Wrap an ExternalContext.Request into a PortletRequest.
 *
 * TODO: Review this and compare to ExternalContextToHttpServletRequestWrapper.
 */
public class ExternalContextToPortletRenderRequestWrapper implements RenderRequest {

    private ExternalContext.Request request;
    private boolean isForward;

    private PortletRequest nativeRequest;

    public ExternalContextToPortletRenderRequestWrapper(ExternalContext.Request request, boolean isForward) {
        this.request = request;
        this.isForward = isForward;

        if (request.getNativeRequest() instanceof PortletRequest)
            this.nativeRequest = (PortletRequest) request.getNativeRequest();

        // TODO: Throw this until we implement this properly
        throw new UnsupportedOperationException();
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
        if (nativeRequest != null)
            return nativeRequest.getPortalContext();
        else
            return null;
    }

    public PortletMode getPortletMode() {
        if (nativeRequest != null)
            return nativeRequest.getPortletMode();
        else
            return null;
    }

    public PortletSession getPortletSession() {
        if (nativeRequest != null)
            return nativeRequest.getPortletSession();
        else
            return null;
    }

    public PortletSession getPortletSession(boolean b) {
        if (nativeRequest != null)
            return nativeRequest.getPortletSession(b);
        else
            return null;
    }

    public PortletPreferences getPreferences() {
        if (nativeRequest != null)
            return nativeRequest.getPreferences();
        else
            return null;
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
        if (nativeRequest != null)
            return nativeRequest.getResponseContentType();
        else
            return null;
    }

    public Enumeration getResponseContentTypes() {
        if (nativeRequest != null)
            return nativeRequest.getResponseContentTypes();
        else
            return null;
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
        if (nativeRequest != null)
            return nativeRequest.getWindowState();
        else
            return null;
    }

    public boolean isPortletModeAllowed(PortletMode portletMode) {
        if (nativeRequest != null)
            return nativeRequest.isPortletModeAllowed(portletMode);
        else
            return false;
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
        if (nativeRequest != null)
            return nativeRequest.isWindowStateAllowed(windowState);
        else
            return false;
    }

    public void removeAttribute(String clazz) {
        request.getAttributesMap().remove(clazz);
    }

    public void setAttribute(String clazz, Object o) {
        request.getAttributesMap().put(clazz, o);
    }

    // JSR-286 methods
    public String getETag() {
        // TODO
        return null;
    }

    public String getWindowID() {
        // TODO
        return null;
    }

    public Cookie[] getCookies() {
        // TODO
        return new Cookie[0];
    }

    public Map /*<String, String[]>*/ getPrivateParameterMap() {
        // TODO
        return null;
    }

    public Map /*<String, String[]>*/ getPublicParameterMap() {
        // TODO
        return null;
    }
}
