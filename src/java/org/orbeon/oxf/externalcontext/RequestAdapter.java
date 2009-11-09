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

public class RequestAdapter implements ExternalContext.Request {
    public String getContainerType() {
        return null;
    }

    public String getContainerNamespace() {
        return null;
    }

    public String getPathInfo() {
        return null;
    }

    public String getRequestPath() {
        return null;
    }

    public String getContextPath() {
        return null;
    }

    public String getServletPath() {
        return null;
    }

    public String getClientContextPath(String urlString) {
        return null;
    }

    public Map<String, Object> getAttributesMap() {
        return null;
    }

    public Map<String, String[]> getHeaderValuesMap() {
        return null;
    }

    public Map<String, Object[]> getParameterMap() {
        return null;
    }

    public Map<String, String> getHeaderMap() {
        return null;
    }

    public String getCharacterEncoding() {
        return null;
    }

    public int getContentLength() {
        return 0;
    }

    public String getContentType() {
        return null;
    }

    public InputStream getInputStream() throws IOException {
        return null;
    }

    public Reader getReader() throws IOException {
        return null;
    }

    public String getProtocol() {
        return null;
    }

    public String getRemoteHost() {
        return null;
    }

    public String getRemoteAddr() {
        return null;
    }

    public String getScheme() {
        return null;
    }

    public String getMethod() {
        return null;
    }

    public String getServerName() {
        return null;
    }

    public int getServerPort() {
        return 0;
    }

    public void sessionInvalidate() {
    }

    public boolean isRequestedSessionIdValid() {
        return false;
    }

    public String getRequestedSessionId() {
        return null;
    }

    public String getAuthType() {
        return null;
    }

    public boolean isSecure() {
        return false;
    }

    public String getRemoteUser() {
        return null;
    }

    public boolean isUserInRole(String role) {
        return false;
    }

    public Principal getUserPrincipal() {
        return null;
    }

    public Locale getLocale() {
        return null;
    }

    public Enumeration getLocales() {
        return null;
    }

    public String getPathTranslated() {
        return null;
    }

    public String getQueryString() {
        return null;
    }

    public String getRequestURI() {
        return null;
    }

    public String getRequestURL() {
        return null;
    }

    public String getPortletMode() {
        return null;
    }

    public String getWindowState() {
        return null;
    }

    public Object getNativeRequest() {
        return null;
    }
}
