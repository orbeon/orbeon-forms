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
package org.orbeon.oxf.pipeline.api;

import java.io.*;
import java.security.Principal;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Map;

/**
 * ExternalContext abstracts context, request and response information so that compile-time
 * dependencies on the Servlet API or Portlet API can be removed.
 *
 * It is also possible to use ExternalContext to embed OXF and to provide a web-like
 * request/response interface.
 */
public interface ExternalContext {

    public static final int SC_OK = 200;
    public static final int SC_NOT_FOUND = 404;
    public static final int SC_NOT_MODIFIED = 304;
    public static final int SC_INTERNAL_SERVER_ERROR = 500;

    public interface Request {
        public String getContainerType();

        public String getPathInfo();
        public String getRequestPath();
        public String getContextPath();
        public String getServletPath();

        public Map getAttributesMap();
        public Map getHeaderMap();
        public Map getHeaderValuesMap();
        public Map getParameterMap();

        public String getCharacterEncoding();
        public int getContentLength();
        public String getContentType();
        public InputStream getInputStream() throws IOException;
        public Reader getReader() throws IOException;

        public String getProtocol();
        public String getRemoteHost();
        public String getRemoteAddr();
        public String getScheme();
        public String getMethod();
        public String getServerName();
        public int getServerPort();

        public void sessionInvalidate();
        public boolean isRequestedSessionIdValid();
        public String getRequestedSessionId();

        public String getAuthType();
        public boolean isSecure();
        public String getRemoteUser();
        public boolean isUserInRole(String role);
        public Principal getUserPrincipal();

        public Locale getLocale();
        public Enumeration getLocales();

        public String getPathTranslated();
        public String getQueryString();
        public String getRequestURI();
    }

    public interface Response {
        public PrintWriter getWriter() throws IOException;
        public OutputStream getOutputStream() throws IOException;
        public boolean isCommitted();
        public void reset();
        public void setContentType(String contentType);
        public void setStatus(int status);
        public void setContentLength(int len);
        public void setHeader(String name, String value);
        public void addHeader(String name, String value);
        public void sendError(int len) throws IOException;
        public String getCharacterEncoding();
        public void sendRedirect(String pathInfo, Map parameters, boolean isServerSide, boolean isExitPortal) throws IOException;

        public void setCaching(long lastModified, boolean revalidate, boolean allowOverride);
        public boolean checkIfModifiedSince(long lastModified, boolean allowOverride);

        public String rewriteActionURL(String urlString);
        public String rewriteRenderURL(String urlString);
        public String rewriteResourceURL(String urlString, boolean absolute);
        public String getNamespacePrefix();
        public void setTitle(String title);
    }

    public interface Session {
        public static final int APPLICATION_SCOPE = 1;
        public static final int PORTLET_SCOPE = 2;

        public long getCreationTime();
        public String getId();
        public long getLastAccessedTime();
        public int getMaxInactiveInterval();
        public void invalidate();
        public boolean isNew();
        public void setMaxInactiveInterval(int interval);

        public Map getAttributesMap();
        public Map getAttributesMap(int scope);
    }

    public interface RequestDispatcher {
        public abstract void forward(Request request, Response response) throws IOException;
        public void include(Request request, Response response) throws IOException;
    }

    public Object getNativeContext();
    public Object getNativeRequest();
    public Object getNativeResponse();
    public Object getNativeSession(boolean flag);

    public RequestDispatcher getRequestDispatcher(String path);
    public RequestDispatcher getNamedDispatcher(String name);

    public Request getRequest();
    public Response getResponse();
    public Session getSession(boolean create);

    public Map getAttributesMap();
    public Map getInitAttributesMap();
    public String getRealPath(String path);

    public String getStartLoggerString();
    public String getEndLoggerString();

    public void log(String message, Throwable throwable);
    public void log(String msg);
}
