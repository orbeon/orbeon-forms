/**
 *  Copyright (C) 2004 Orbeon, Inc.
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
package org.orbeon.oxf.portlet;

import org.orbeon.oxf.pipeline.api.ExternalContext;

import javax.portlet.*;
import java.security.Principal;
import java.util.*;

public class PortletRequestImpl implements PortletRequest {

    private static final String[] contentTypes = { "text/html" };

    private ExternalContext externalContext;
    private PortletConfigImpl portletConfig;
    private int portletId;
    private PortletPreferences portletPreferences;
    private PortletSession portletSession;

    protected ExternalContext.Request request;
    protected PortletContainer.PortletState portletStatus;
    protected Map readOnlyRequestParameters;

    private Map attributesMap;

    public PortletRequestImpl(ExternalContext externalContext, PortletConfigImpl portletConfig, int portletId, ExternalContext.Request request, PortletContainer.PortletState portletStatus) {
        this.externalContext = externalContext;
        this.portletConfig = portletConfig;
        this.portletId = portletId;
        this.request = request;
        this.portletStatus = portletStatus;
    }

    public Object getAttribute(String name) {
        return (attributesMap == null) ? null : attributesMap.get(name);
    }

    public Enumeration getAttributeNames() {
        if (attributesMap == null)
            attributesMap = new HashMap();
        return Collections.enumeration(attributesMap.keySet());
    }

    public void removeAttribute(String name) {
        if (attributesMap != null)
            attributesMap.remove(name);
    }

    public void setAttribute(String name, Object o) {
        if (attributesMap == null)
            attributesMap = new HashMap();
        attributesMap.put(name, o);
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

    public String getParameter(String name) {
        String[] values = getParameterValues(name);
        return (values == null) ? null : values[0];
    }

    public Map getParameterMap() {
        if (readOnlyRequestParameters == null)
            readOnlyRequestParameters = Collections.unmodifiableMap(portletStatus.getRenderParameters());
        return readOnlyRequestParameters;
    }

    public Enumeration getParameterNames() {
        Map parameters = getParameterMap();
        if (parameters == null)
            return Collections.enumeration(Collections.EMPTY_LIST);
        else
            return Collections.enumeration(parameters.keySet());
    }

    public String[] getParameterValues(String name) {
        Map parameters = getParameterMap();
        return (parameters == null) ? null : (String[]) parameters.get(name);
    }

    public PortalContext getPortalContext() {
        return PortalContextImpl.instance();
    }

    public PortletMode getPortletMode() {
        PortletMode portletMode = portletStatus.getPortletMode();
        return (portletMode == null) ? PortletMode.VIEW : portletMode;
    }

    public PortletSession getPortletSession() {
        return getPortletSession(true);
    }

    public PortletSession getPortletSession(boolean create) {
        if (portletSession == null) {
            ExternalContext.Session session = externalContext.getSession(create);
            if (session != null)
                portletSession = new PortletSessionImpl(portletConfig.getPortletContext(), portletId, session);
        }
        return portletSession;
    }

    public PortletPreferences getPreferences() {
        if (portletPreferences == null) {
            portletPreferences = new PortletPreferencesImpl(portletConfig);
        }
        return portletPreferences;
    }

    public Enumeration getProperties(String name) {
        // Per the spec, properties may contain "portal/portlet-container
        // specific properties through this method and, if available, the
        // headers of the HTTP client request".
        return Collections.enumeration(Arrays.asList((String[]) request.getHeaderValuesMap().get(name)));
    }

    public String getProperty(String name) {
        // Per the spec, properties may contain "portal/portlet-container
        // specific properties through this method and, if available, the
        // headers of the HTTP client request".
        return (String) request.getHeaderMap().get(name);
    }

    public Enumeration getPropertyNames() {
        // Per the spec, properties may contain "portal/portlet-container
        // specific properties through this method and, if available, the
        // headers of the HTTP client request".
        return Collections.enumeration(request.getHeaderMap().keySet());
    }

    public String getRemoteUser() {
        return request.getRemoteUser();
    }

    public String getRequestedSessionId() {
        return request.getRequestedSessionId();
    }

    public String getResponseContentType() {
        // NIY / FIXME
        return contentTypes[0];
    }

    public Enumeration getResponseContentTypes() {
        // NIY / FIXME
        return Collections.enumeration(Arrays.asList(contentTypes));
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
        WindowState windowState = portletStatus.getWindowState();
        return (windowState == null) ? WindowState.NORMAL : windowState;
    }

    public boolean isPortletModeAllowed(PortletMode mode) {
        // NIY / FIXME
        return mode.equals(PortletMode.VIEW) || mode.equals(PortletMode.EDIT) || mode.equals(PortletMode.HELP);
    }

    public boolean isRequestedSessionIdValid() {
        return request.isRequestedSessionIdValid();
    }

    public boolean isSecure() {
        return request.isSecure();
    }

    public boolean isUserInRole(String role) {
        return request.isUserInRole(role);
    }

    public boolean isWindowStateAllowed(WindowState state) {
        // NIY / FIXME
        return state.equals(WindowState.NORMAL) || state.equals(WindowState.MINIMIZED) || state.equals(WindowState.MAXIMIZED);
    }

    public static void checkContentType(String contentType) {
        for (int i = 0; i < contentTypes.length; i++) {
            if (contentTypes[i].equals(contentType))
                return;
        }
        throw new IllegalArgumentException("Content-Type not allowed: " + contentType);
    }
}
