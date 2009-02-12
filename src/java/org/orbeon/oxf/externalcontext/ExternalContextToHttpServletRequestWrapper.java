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
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.util.FastHttpDateFormat;

import javax.servlet.ServletInputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpSession;
import java.io.*;
import java.security.Principal;
import java.util.*;
import java.text.SimpleDateFormat;

/**
 * Wrap an ExternalContext.Request into an HttpServletRequest.
 *
 * This is intended to "simulate" request information upon servlet forward/include. Information supported is:
 *
 * o request method
 * o request path and query string
 * o request parameters
 * o request body
 * o request attributes
 * o request headers
 *
 * Often, an
 * HttpServletRequestWrapper is used to forward within the same application, and in this case developers have good
 * control over all the aspects of the application.
 *
 * Here, we need to make it look to the recipient that the request is as close as possible as a new incoming HTTP
 * request. The difficulty is to decide what to delegate to the servlet container, and what to get from the incoming
 * request object passed to the constructor.
 */
public class ExternalContextToHttpServletRequestWrapper extends HttpServletRequestWrapper {

    private ExternalContext.Request request;

    private ServletInputStream servletInputStream;

    public ExternalContextToHttpServletRequestWrapper(ExternalContext.Request request) {
        super((HttpServletRequest) request.getNativeRequest());
        this.request = request;
    }

    /* SUPPORTED: request method */

    public String getMethod() {
        return request.getMethod();
    }

    /* SUPPORTED: request path and query string */

    public String getQueryString() {
        return NetUtils.encodeQueryString(request.getParameterMap());
    }

    public String getRequestURI() {
        return request.getRequestURI();
    }

    public StringBuffer getRequestURL() {
        return new StringBuffer(request.getRequestURL());
    }

    /* SUPPORTED: request parameters */

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

    /* SUPPORTED: request body */

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

    public BufferedReader getReader() throws IOException {
        final Reader reader = request.getReader();
        return (reader instanceof BufferedReader) ? ((BufferedReader) reader) : new BufferedReader(request.getReader());
    }

    public void setCharacterEncoding(String encoding) throws UnsupportedEncodingException {
        // TODO: Request does not support setCharacterEncoding()
        //super.setCharacterEncoding(encoding);
    }

    /* SUPPORTED: request attributes */

    public Object getAttribute(String clazz) {
        return request.getAttributesMap().get(clazz);
    }

    public Enumeration getAttributeNames() {
        return Collections.enumeration(request.getAttributesMap().keySet());
    }

    public void removeAttribute(String clazz) {
        request.getAttributesMap().remove(clazz);
    }

    public void setAttribute(String clazz, Object o) {
        request.getAttributesMap().put(clazz, o);
    }

    /* SUPPORTED: request headers */

    // From Apache Tomcat 5.5.7, under Apache 2.0 license.
    protected SimpleDateFormat formats[] = {
        new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US),
        new SimpleDateFormat("EEEEEE, dd-MMM-yy HH:mm:ss zzz", Locale.US),
        new SimpleDateFormat("EEE MMMM d HH:mm:ss yyyy", Locale.US)
    };

    // From Apache Tomcat 5.5.7, under Apache 2.0 license.
    public long getDateHeader(String name) {
        final String value = getHeader(name);
        if (value == null)
            return -1L;

        // Attempt to convert the date header in a variety of formats
        final long result = FastHttpDateFormat.parseDate(value, formats);
        if (result != -1L) {
            return result;
        }
        throw new IllegalArgumentException(value);
    }

    public String getHeader(String name) {
        final String[] values = ((String[]) request.getHeaderValuesMap().get(name));
        return values == null ? null : values[0];
    }

    public Enumeration getHeaderNames() {
        return Collections.enumeration(request.getHeaderValuesMap().keySet());
    }

    public Enumeration getHeaders(String name) {
        final String[] values = ((String[]) request.getHeaderValuesMap().get(name));
        return Collections.enumeration(Arrays.asList(values));
    }

    public int getIntHeader(String name) {
        final String value = getHeader(name);
        if (value == null) {
            return -1;
        } else {
            return Integer.parseInt(value);
        }
    }


    /*
     * DELEGATED: other path information
     */

    public String getPathInfo() {
        return super.getPathInfo();
    }

    public String getServletPath() {
        // NOTE: we assume that this is properly handled by
        return super.getServletPath();
    }

    public String getContextPath() {
        return super.getContextPath();
    }

    public String getRealPath(String path) {
        return super.getRealPath(path);
    }

    public String getPathTranslated() {
        return super.getPathTranslated();
    }

    /* DELEGATED: authentication methods */

    public String getAuthType() {
        return super.getAuthType();
    }

    public String getRemoteUser() {
        return super.getRemoteUser();
    }

    public Principal getUserPrincipal() {
        return super.getUserPrincipal();
    }

    public boolean isUserInRole(String clazz) {
        return super.isUserInRole(clazz);
    }

    /*
     * DELEGATED: session handling
     *
     * We know for a fact that session handling fails with Tomcat with cross-context forwards if we don't use the
     * superclass's session methods.
     */

    public String getRequestedSessionId() {
        return super.getRequestedSessionId();
    }

    public HttpSession getSession() {
        return super.getSession();
    }

    public HttpSession getSession(boolean b) {
        return super.getSession(b);
    }

    public boolean isRequestedSessionIdFromCookie() {
        return super.isRequestedSessionIdFromCookie();
    }

    public boolean isRequestedSessionIdFromURL() {
        return super.isRequestedSessionIdFromURL();
    }

    public boolean isRequestedSessionIdFromUrl() {
        return super.isRequestedSessionIdFromURL();
    }

    public boolean isRequestedSessionIdValid() {
        return super.isRequestedSessionIdValid();
    }

    /*
     * DELEGATED: other client information
     */

    public Locale getLocale() {
        return super.getLocale();
    }

    public Enumeration getLocales() {
        return super.getLocales();
    }

    public Cookie[] getCookies() {
        return super.getCookies();
    }

    /*
     * DELEGATED: remote host
     *
     * NOTE: Could also somehow use the local host's information, probably not worth it
     */

    public String getRemoteAddr() {
        return super.getRemoteAddr();
    }

    public String getRemoteHost() {
        return super.getRemoteHost();
    }

    /*
     * DELEGATED: other protocol-level information
     *
     * NOTE: Could also somehow use the local host's information, probably not worth it
     */

    public String getProtocol() {
        return super.getProtocol();
    }

    public boolean isSecure() {
        return super.isSecure();
    }

    public String getScheme() {
        return super.getScheme();
    }

    /*
     * DELEGATED: local server information
     */

    public String getServerName() {
        return super.getServerName();
    }

    public int getServerPort() {
        return super.getServerPort();
    }

    /* DELEGATED: request dispatcher */

    public javax.servlet.RequestDispatcher getRequestDispatcher(String path) {
        return super.getRequestDispatcher(path);
    }
}
