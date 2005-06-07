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

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletInputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpSession;
import java.io.*;
import java.security.Principal;
import java.util.*;

/**
 * Wrap an ExternalContext.Request into an HttpServletRequest.
 *
 * Methods with counterparts in ExternalContext.Request use the wrapped ExternalContext.Request
 * object and can be overrided using RequestWrapper. Other methods directly forward to the native
 * request.
 */
public class ExternalContextToHttpServletRequestWrapper extends HttpServletRequestWrapper {

    private ExternalContext.Request request;
    private HttpServletRequest nativeRequest;

    private ServletInputStream servletInputStream;

    public ExternalContextToHttpServletRequestWrapper(ExternalContext.Request request) {
        super((HttpServletRequest) request.getNativeRequest());
        this.request = request;
        if (request.getNativeRequest() instanceof HttpServletRequest)
            this.nativeRequest = (HttpServletRequest) request.getNativeRequest();
    }

    public String getAuthType() {
        return request.getAuthType();
    }

    public String getContextPath() {
        return request.getContextPath();
    }

    public Cookie[] getCookies() {
        if (nativeRequest != null)
            return nativeRequest.getCookies();
        else
            return null;
    }

    public long getDateHeader(String clazz) {
        return 0;// TODO
    }

    public String getHeader(String clazz) {
        return null;//TODO
    }

    public Enumeration getHeaderNames() {
        return Collections.enumeration(request.getHeaderValuesMap().keySet());
    }

    public Enumeration getHeaders(String clazz) {
        final String[] values = ((String[]) request.getHeaderValuesMap().get(clazz));
        return Collections.enumeration(Arrays.asList(values));
    }

    public int getIntHeader(String clazz) {
        return 0;//TODO
    }

    public String getMethod() {
        return request.getMethod();
    }

    public String getPathInfo() {
        return request.getPathInfo();
    }

    public String getPathTranslated() {
        return request.getPathTranslated();
    }

    public String getQueryString() {
        return null;//TODO
    }

    public String getRemoteUser() {
        return request.getRemoteUser();
    }

    public String getRequestedSessionId() {
        return request.getRequestedSessionId();
    }

    public String getRequestURI() {
        return request.getRequestURI();
    }

    public StringBuffer getRequestURL() {
        return new StringBuffer(request.getRequestURL());
    }

    public String getServletPath() {
        return request.getServletPath();
    }

    public HttpSession getSession() {
        if (nativeRequest != null)
            return nativeRequest.getSession();
        else
            return null;
    }

    public HttpSession getSession(boolean b) {
        if (nativeRequest != null)
            return nativeRequest.getSession(b);
        else
            return null;
    }

    public Principal getUserPrincipal() {
        return request.getUserPrincipal();
    }

    public boolean isRequestedSessionIdFromCookie() {
        if (nativeRequest != null)
            return nativeRequest.isRequestedSessionIdFromCookie();
        else
            return false;
    }

    public boolean isRequestedSessionIdFromURL() {
        if (nativeRequest != null)
            return nativeRequest.isRequestedSessionIdFromURL();
        else
            return false;
    }

    public boolean isRequestedSessionIdFromUrl() {
        return isRequestedSessionIdFromURL();
    }

    public boolean isRequestedSessionIdValid() {
        return request.isRequestedSessionIdValid();
    }

    public boolean isUserInRole(String clazz) {
        return request.isUserInRole(clazz);
    }

    public Object getAttribute(String clazz) {
        return request.getAttributesMap().get(clazz);
    }

    public Enumeration getAttributeNames() {
        return Collections.enumeration(request.getAttributesMap().keySet());
    }

    public String getCharacterEncoding() {
        return request.getCharacterEncoding();
    }

    public int getContentLength() {
        return request.getContentLength();
    }

    public String getContentType() {
        return request.getContentType();
    }

    public ServletInputStream getInputStream() throws IOException {
        if (servletInputStream == null) {
            final InputStream is = request.getInputStream();
            servletInputStream = new ServletInputStream() {
                public int read() throws IOException {
                    return is.read();
                }
            };
        }
        return servletInputStream;
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

    public String getProtocol() {
        return request.getProtocol();
    }

    public BufferedReader getReader() throws IOException {
        final Reader reader = request.getReader();
        return (reader instanceof BufferedReader) ? ((BufferedReader) reader) : new BufferedReader(request.getReader());
    }

    public String getRealPath(String clazz) {
        if (nativeRequest != null)
            return nativeRequest.getRealPath(clazz);
        else
            return null;
    }

    public String getRemoteAddr() {
        return request.getRemoteAddr();
    }

    public String getRemoteHost() {
        return request.getRemoteHost();
    }

    public javax.servlet.RequestDispatcher getRequestDispatcher(String clazz) {
        if (nativeRequest != null)
            return nativeRequest.getRequestDispatcher(clazz);
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

    public boolean isSecure() {
        return request.isSecure();
    }

    public void removeAttribute(String clazz) {
        request.getAttributesMap().remove(clazz);
    }

    public void setAttribute(String clazz, Object o) {
        request.getAttributesMap().put(clazz, o);
    }

    public void setCharacterEncoding(String clazz) throws UnsupportedEncodingException {
        if (nativeRequest != null)
            nativeRequest.setCharacterEncoding(clazz);
    }
}
