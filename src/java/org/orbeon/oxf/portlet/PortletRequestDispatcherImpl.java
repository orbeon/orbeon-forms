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

import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;

import javax.portlet.PortletException;
import javax.portlet.PortletRequestDispatcher;
import javax.portlet.RenderRequest;
import javax.portlet.RenderResponse;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.*;
import java.security.Principal;
import java.util.*;

public class PortletRequestDispatcherImpl implements PortletRequestDispatcher {

    public static final String SERVLET_PREFIX = "javax.servlet.include.";
    public static final String REQUEST_URI_ATTRIBUTE = SERVLET_PREFIX + "request_uri";
    public static final String CONTEXT_PATH_ATTRIBUTE = SERVLET_PREFIX + "context_path";
    public static final String SERVLET_PATH_ATTRIBUTE = SERVLET_PREFIX + "servlet_path";
    public static final String PATH_INFO_ATTRIBUTE = SERVLET_PREFIX + "path_info";
    public static final String QUERY_STRING_ATTRIBUTE = SERVLET_PREFIX + "query_string";

    public static final String PORTLET_PREFIX = "javax.portlet.";
    public static final String CONFIG_ATTRIBUTE = PORTLET_PREFIX + "config";
    public static final String REQUEST_ATTRIBUTE = PORTLET_PREFIX + "request";
    public static final String RESPONSE_ATTRIBUTE = PORTLET_PREFIX + "response";

    private PortletConfigImpl portletConfig;
    private RequestDispatcher requestDispatcher;
    private String pathInfo;
    private String queryString;
    private String path;

    public PortletRequestDispatcherImpl(PortletConfigImpl portletConfig, RequestDispatcher requestDispatcher) {
        this(portletConfig, requestDispatcher, null);
    }

    public PortletRequestDispatcherImpl(PortletConfigImpl portletConfig, RequestDispatcher requestDispatcher, String path) {
        this.portletConfig = portletConfig;
        this.requestDispatcher = requestDispatcher;
        this.path = path;

        if (path != null) {
            int index = path.indexOf("?");
            this.pathInfo = path;

            if (index != -1) {
                this.pathInfo = path.substring(0, index);
                this.queryString = path.substring(index + 1);
            }
        }
    }

    public void include(RenderRequest request, RenderResponse response)
            throws PortletException, IOException {
        if (!(request instanceof RenderRequestImpl) || !(response instanceof RenderResponseImpl))
            throw new IllegalArgumentException("RenderRequest and RenderResponse objects must be the ones passed to Portlet.render()");
        try {
            requestDispatcher.include(new PortletRequestDispatcherRequestWrapper(portletConfig, request, response, pathInfo, queryString), new PortletRequestDispatcherResponseWrapper(request, response));
        } catch (ServletException e) {
            if (path != null) {
                throw new PortletException(new ValidationException(e, new LocationData(path, -1, -1)));
            } else {
                throw new PortletException(e);
            }
        }
    }
}

class PortletRequestDispatcherRequestWrapper implements HttpServletRequest {
    private RenderRequest _request;

    private boolean hasServletAttributes;
    private Map servletIncludeAttributes = new HashMap();
    private Map parameterMap;

    public PortletRequestDispatcherRequestWrapper(PortletConfigImpl portletConfig, RenderRequest request, RenderResponse response, String pathInfo, String queryString) {
        this._request = request;

        // Add portlet attributes
        servletIncludeAttributes.put(PortletRequestDispatcherImpl.CONFIG_ATTRIBUTE, portletConfig);
        servletIncludeAttributes.put(PortletRequestDispatcherImpl.REQUEST_ATTRIBUTE, request);
        servletIncludeAttributes.put(PortletRequestDispatcherImpl.RESPONSE_ATTRIBUTE, response);

        parameterMap = new HashMap(request.getParameterMap());

        // Add javax.servlet.include attrigbutes if needed
        if (pathInfo != null) {
            String contextPath = _request.getContextPath();

            // Merge query string parameters
            Map queryStringMap = NetUtils.decodeQueryString(queryString, false);
            for (Iterator i = queryStringMap.keySet().iterator(); i.hasNext();) {
                String name = (String) i.next();
                String[] values = (String[]) queryStringMap.get(name);

                NetUtils.addValuesToStringArrayMap(queryStringMap, name, values);
            }

            servletIncludeAttributes.put(PortletRequestDispatcherImpl.REQUEST_URI_ATTRIBUTE, contextPath + pathInfo + ((queryString != null) ? "?" + queryString : ""));
            servletIncludeAttributes.put(PortletRequestDispatcherImpl.CONTEXT_PATH_ATTRIBUTE, contextPath);
            servletIncludeAttributes.put(PortletRequestDispatcherImpl.SERVLET_PATH_ATTRIBUTE, "");
            servletIncludeAttributes.put(PortletRequestDispatcherImpl.SERVLET_PATH_ATTRIBUTE, pathInfo);
            servletIncludeAttributes.put(PortletRequestDispatcherImpl.QUERY_STRING_ATTRIBUTE, queryString);

            hasServletAttributes = true;
        }
    }

    public String getAuthType() {
        return _request.getAuthType();
    }

    public String getContextPath() {
        return _request.getContextPath();
    }

    public Cookie[] getCookies() {
        // TODO / NIY
        // Spec says this should be based on portlet request properties
        return null;
    }

    public long getDateHeader(String s) {
        String stringValue = getHeader(s);
        try {
            return NetUtils.getDateHeader(stringValue);
        } catch (Exception e) {
            throw new IllegalArgumentException();
        }
    }

    public String getHeader(String s) {
        return _request.getProperty(s);
    }

    public Enumeration getHeaderNames() {
        return _request.getPropertyNames();
    }

    public Enumeration getHeaders(String s) {
        return _request.getProperties(s);
    }

    public int getIntHeader(String s) {
        return Integer.parseInt(getHeader(s));
    }

    public String getMethod() {
        // NIY
        return null;
    }

    public String getPathInfo() {
        return (String) servletIncludeAttributes.get(PortletRequestDispatcherImpl.PATH_INFO_ATTRIBUTE);
    }

    public String getPathTranslated() {
        return getPathInfo();
    }

    public String getQueryString() {
        return (String) servletIncludeAttributes.get(PortletRequestDispatcherImpl.QUERY_STRING_ATTRIBUTE);
    }

    public String getRemoteUser() {
        return _request.getRemoteUser();
    }

    public String getRequestedSessionId() {
        return _request.getRequestedSessionId();
    }

    public String getRequestURI() {
        return (String) servletIncludeAttributes.get(PortletRequestDispatcherImpl.REQUEST_URI_ATTRIBUTE);
    }

    public StringBuffer getRequestURL() {
        // Per the spec
        return null;
    }

    public String getServletPath() {
        return (String) servletIncludeAttributes.get(PortletRequestDispatcherImpl.SERVLET_PATH_ATTRIBUTE);
    }

    public HttpSession getSession() {
        // NIY
        return null;
    }

    public HttpSession getSession(boolean flag) {
        return null;
    }

    public Principal getUserPrincipal() {
        return _request.getUserPrincipal();
    }

    public boolean isRequestedSessionIdFromCookie() {
        // NIY
        return false;
    }

    public boolean isRequestedSessionIdFromURL() {
        // NIY
        return false;
    }

    public boolean isRequestedSessionIdFromUrl() {
        return isRequestedSessionIdFromURL();
    }

    public boolean isRequestedSessionIdValid() {
        return _request.isRequestedSessionIdValid();
    }

    public boolean isUserInRole(String s) {
        return _request.isUserInRole(s);
    }

    public Object getAttribute(String s) {

        if (hasServletAttributes) {
            Object result = servletIncludeAttributes.get(s);
            return (result != null) ? result : _request.getAttribute(s);
        } else
            return _request.getAttribute(s);
    }

    public Enumeration getAttributeNames() {
        if (hasServletAttributes) {
            // Not very efficient to do this for every call
            List result = new ArrayList(servletIncludeAttributes.keySet());
            result.addAll(Collections.list(_request.getAttributeNames()));
            return Collections.enumeration(result);
        } else
            return _request.getAttributeNames();
    }

    public String getCharacterEncoding() {
        // Per the spec
        return null;
    }

    public int getContentLength() {
        // Per the spec
        return 0;
    }

    public String getContentType() {
        // Per the spec
        return null;
    }

    public ServletInputStream getInputStream()
            throws IOException {
        // Per the spec
        return null;
    }

    public Locale getLocale() {
        return _request.getLocale();
    }

    public Enumeration getLocales() {
        // Per the spec
        return Collections.enumeration(Collections.singleton(_request.getLocale()));
    }

    public String getParameter(String s) {
        String[] result = (String[]) parameterMap.get(s);
        return (result == null) ? null : result[0];
    }

    public Map getParameterMap() {
        return parameterMap;
    }

    public Enumeration getParameterNames() {
        return Collections.enumeration(parameterMap.keySet());
    }

    public String[] getParameterValues(String s) {
        return (String[]) parameterMap.get(s);
    }

    public String getProtocol() {
        // Per the spec
        return null;
    }

    public BufferedReader getReader()
            throws IOException {
        // Per the spec
        return null;
    }

    public String getRealPath(String s) {
        // Per the spec
        return null;
    }

    public String getRemoteAddr() {
        // Per the spec
        return null;
    }

    public String getRemoteHost() {
        // Per the spec
        return null;
    }

    public RequestDispatcher getRequestDispatcher(String s) {
        // NIY
        return null;
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

    public boolean isSecure() {
        return _request.isSecure();
    }

    public void removeAttribute(String s) {
        _request.removeAttribute(s);
    }

    public void setAttribute(String s, Object obj) {
        _request.setAttribute(s, obj);
    }

    public void setCharacterEncoding(String s)
            throws UnsupportedEncodingException {
        // Per the spec
    }
}

class PortletRequestDispatcherResponseWrapper implements HttpServletResponse {

    private RenderRequest _request;
    private RenderResponse _response;

    private ServletOutputStream servletOutputStream;

    public PortletRequestDispatcherResponseWrapper(RenderRequest request, RenderResponse response) {
        this._request = request;
        this._response = response;
    }

    public void addCookie(Cookie cookie) {
        // Per the spec
    }

    public void addDateHeader(String s, long l) {
        // Per the spec
    }

    public void addHeader(String s, String s1) {
        // Per the spec
    }

    public void addIntHeader(String s, int i) {
        // Per the spec
    }

    public boolean containsHeader(String s) {
        // Per the spec
        return false;
    }

    public String encodeRedirectURL(String s) {
        // Per the spec
        return null;
    }

    public String encodeRedirectUrl(String s) {
        // Per the spec
        return null;
    }

    public String encodeURL(String s) {
        return _response.encodeURL(s);
    }

    public String encodeUrl(String s) {
        return _response.encodeURL(s);
    }

    public void sendError(int i)
            throws IOException {
        // Per the spec
    }

    public void sendError(int i, String s)
            throws IOException {
        // Per the spec
    }

    public void sendRedirect(String s)
            throws IOException {
        // Per the spec
    }

    public void setDateHeader(String s, long l) {
        // Per the spec
    }

    public void setHeader(String s, String s1) {
        // Per the spec
    }

    public void setIntHeader(String s, int i) {
        // Per the spec
    }

    public void setStatus(int i) {
        // Per the spec
    }

    public void setStatus(int i, String s) {
        // Per the spec
    }

    public void flushBuffer()
            throws IOException {
        _response.flushBuffer();
    }

    public int getBufferSize() {
        return _response.getBufferSize();
    }

    public String getCharacterEncoding() {
        return _response.getCharacterEncoding();
    }

    public Locale getLocale() {
        // Per the spec
        return _request.getLocale();
    }

    public ServletOutputStream getOutputStream()
            throws IOException {

        if (servletOutputStream == null) {
            final OutputStream portletOutputStream = _response.getPortletOutputStream();
            servletOutputStream = new ServletOutputStream() {
                public void write(int b) throws IOException {
                    portletOutputStream.write(b);
                }
            };
        }
        return servletOutputStream;
    }

    public PrintWriter getWriter()
            throws IOException {
        return _response.getWriter();
    }

    public boolean isCommitted() {
        return _response.isCommitted();
    }

    public void reset() {
        _response.reset();
    }

    public void resetBuffer() {
        _response.resetBuffer();
    }

    public void setBufferSize(int i) {
        _response.setBufferSize(i);
    }

    public void setContentLength(int i) {
        // Per the spec
    }

    public void setContentType(String s) {
        // Per the spec
    }

    public void setLocale(Locale locale) {
        // Per the spec
    }
}
