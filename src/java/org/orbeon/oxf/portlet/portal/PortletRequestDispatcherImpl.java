package org.orbeon.oxf.portlet.portal;

import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.xml.dom4j.LocationData;

import javax.portlet.PortletException;
import javax.portlet.PortletRequestDispatcher;
import javax.portlet.RenderRequest;
import javax.portlet.RenderResponse;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Cookie;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Locale;

/**
 * Copyright (C) 2007 Orbeon, Inc.
 * <p/>
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 * <p/>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 * <p/>
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
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