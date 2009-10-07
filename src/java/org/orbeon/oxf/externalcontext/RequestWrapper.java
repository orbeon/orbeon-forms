/**
 * Copyright (C) 2009 Orbeon, Inc.
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

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.security.Principal;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Map;

/**
 *
 */
public class RequestWrapper implements ExternalContext.Request {
    private ExternalContext.Request _request;

    public RequestWrapper(ExternalContext.Request request) {
        if (request == null)
            throw new IllegalArgumentException();
        this._request = request;
    }

    public Map<String, Object> getAttributesMap() {
        return _request.getAttributesMap();
    }

    public String getAuthType() {
        return _request.getAuthType();
    }

    public String getCharacterEncoding() {
        return _request.getCharacterEncoding();
    }

    public String getContainerType() {
        return _request.getContainerType();
    }

    public String getContainerNamespace() {
        return _request.getContainerNamespace();
    }

    public int getContentLength() {
        return _request.getContentLength();
    }

    public String getContentType() {
        return _request.getContentType();
    }

    public String getContextPath() {
        return _request.getContextPath();
    }

    public Map<String, String> getHeaderMap() {
        return _request.getHeaderMap();
    }

    public Map<String, String[]> getHeaderValuesMap() {
        return _request.getHeaderValuesMap();
    }

    public InputStream getInputStream() throws IOException {
        return _request.getInputStream();
    }

    public Locale getLocale() {
        return _request.getLocale();
    }

    public Enumeration getLocales() {
        return _request.getLocales();
    }

    public String getMethod() {
        return _request.getMethod();
    }

    public Object getNativeRequest() {
        return _request.getNativeRequest();
    }

    public Map<String, Object[]> getParameterMap() {
        return _request.getParameterMap();
    }

    public String getPathInfo() {
        return _request.getPathInfo();
    }

    public String getPathTranslated() {
        return _request.getPathTranslated();
    }

    public String getProtocol() {
        return _request.getProtocol();
    }

    public String getQueryString() {
        return _request.getQueryString();
    }

    public Reader getReader() throws IOException {
        return _request.getReader();
    }

    public String getRemoteAddr() {
        return _request.getRemoteAddr();
    }

    public String getRemoteHost() {
        return _request.getRemoteHost();
    }

    public String getRemoteUser() {
        return _request.getRemoteUser();
    }

    public String getRequestedSessionId() {
        return _request.getRequestedSessionId();
    }

    public String getRequestPath() {
        return _request.getRequestPath();
    }

    public String getRequestURI() {
        return _request.getRequestURI();
    }

    public String getRequestURL() {
        return _request.getRequestURL();
    }

    public String getScheme() {
        return _request.getScheme();
    }

    public String getServerName() {
        return _request.getServerName();
    }

    public int getServerPort() {
        return _request.getServerPort();
    }

    public String getServletPath() {
        return _request.getServletPath();
    }

    public String getClientContextPath(String urlString) {
        return _request.getClientContextPath(urlString);
    }

    public Principal getUserPrincipal() {
        return _request.getUserPrincipal();
    }

    public boolean isRequestedSessionIdValid() {
        return _request.isRequestedSessionIdValid();
    }

    public boolean isSecure() {
        return _request.isSecure();
    }

    public boolean isUserInRole(String role) {
        return _request.isUserInRole(role);
    }

    public void sessionInvalidate() {
        _request.sessionInvalidate();
    }

    public ExternalContext.Request _getRequest() {
        return _request;
    }

    public String getPortletMode() {
        return _request.getPortletMode();
    }

    public String getWindowState() {
        return _request.getWindowState();
    }
}
