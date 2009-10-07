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
package org.orbeon.oxf.pipeline.api;

import java.io.*;
import java.security.Principal;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Map;

/**
 * ExternalContext abstracts context, request and response information so that compile-time dependencies on the
 * Servlet API or Portlet API can be removed.
 *
 * It is also possible to use ExternalContext to embed Orbeon Forms and to provide a web-like request/response
 * interface.
 */
public interface ExternalContext extends WebAppExternalContext {

    public static final int SC_OK = 200;
    public static final int SC_NOT_FOUND = 404;
    public static final int SC_NOT_MODIFIED = 304;
    public static final int SC_INTERNAL_SERVER_ERROR = 500;

    public interface Request {
        public String getContainerType();
        public String getContainerNamespace();

        public String getPathInfo();
        public String getRequestPath();
        public String getContextPath();
        public String getServletPath();
        public String getClientContextPath(String urlString);

        public Map<String, Object> getAttributesMap();
        public Map<String, String> getHeaderMap();
        public Map<String, String[]> getHeaderValuesMap();
        public Map<String, Object[]> getParameterMap();

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
        public String getRequestURL();

        public String getPortletMode();
        public String getWindowState();

        public Object getNativeRequest();
    }

    public interface Response {

        public static final int REWRITE_MODE_ABSOLUTE = 1;
        public static final int REWRITE_MODE_ABSOLUTE_PATH = 2;
        public static final int REWRITE_MODE_ABSOLUTE_PATH_OR_RELATIVE = 3;
        public static final int REWRITE_MODE_ABSOLUTE_PATH_NO_CONTEXT = 4;

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

        public void sendRedirect(String pathInfo, Map<String, String[]> parameters, boolean isServerSide, boolean isExitPortal, boolean isNoRewrite) throws IOException;

        public void setCaching(long lastModified, boolean revalidate, boolean allowOverride);

        /**
         * Set expiration headers for resources.
         *
         * o If lastModified is > 0, Last-Modified is set to that value
         * o If lastModified is <= 0, Last-Modified and Expires are set to the time of the response
         * o If expires is > 0 and lastModified is > 0, Expires is set to that value
         * o If expires is <= 0 , Expires is set using the default policy: 1/10 of the age of the resource
         *
         * @param lastModified  last modification date of resource, or <= 0 if unknown
         * @param expires       requested expiration, or <=0 if unknown or to trigger default policy
         */
        public void setResourceCaching(long lastModified, long expires);

        public boolean checkIfModifiedSince(long lastModified, boolean allowOverride);

        public String rewriteActionURL(String urlString);
        public String rewriteRenderURL(String urlString);
        public String rewriteActionURL(String urlString, String portletMode, String windowState);
        public String rewriteRenderURL(String urlString, String portletMode, String windowState);
        public String rewriteResourceURL(String urlString, boolean absolute);
        public String rewriteResourceURL(String urlString, int rewriteMode);
        public String getNamespacePrefix();
        public void setTitle(String title);

        public Object getNativeResponse();
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

        public Map<String, Object> getAttributesMap();
        public Map<String, Object> getAttributesMap(int scope);

        public void addListener(SessionListener sessionListener);
        public void removeListener(SessionListener sessionListener);

        public interface SessionListener {
            public void sessionDestroyed();
        }
    }

    public interface Application {
        public void addListener(ApplicationListener applicationListener);
        public void removeListener(ApplicationListener applicationListener);

        public interface ApplicationListener {
            public void servletDestroyed();
        }
    }

    public interface RequestDispatcher {
        public abstract void forward(Request request, Response response) throws IOException;
        public void include(Request request, Response response) throws IOException;
        public boolean isDefaultContext();
    }

    /**
     * Return a request dispatcher usable to perform forwards and includes.
     *
     * NOTE: When isContextRelative is false, assume that the first path element points to the context. E.g. /foo/bar
     * resolves to a context mounted on /foo, and /bar is the resource pointed to in that context.
     *
     * @param path                  path of the resource (must start with "/")
     * @param isContextRelative     if true, path is relative to the current context root, otherwise to the document root
     * @return                      RequestDispatcher or null if cannot be found
     */
    public RequestDispatcher getRequestDispatcher(String path, boolean isContextRelative);
    public RequestDispatcher getNamedDispatcher(String name);

    public Request getRequest();
    public Response getResponse();
    public Session getSession(boolean create);
    public Application getApplication();

    /**
     * Rewrite a service URL. The URL is rewritten against a base URL which is:
     *
     * o specified externally or
     * o the incoming request if not specified externally
     *
     * @param urlString     URL to rewrite
     * @param forceAbsolute force absolute URL
     * @return              rewritten URL
     */
    public String rewriteServiceURL(String urlString, boolean forceAbsolute);

    public String getStartLoggerString();
    public String getEndLoggerString();

    public Object getNativeRequest();
    public Object getNativeResponse();
    public Object getNativeSession(boolean flag);
}
