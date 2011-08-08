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

    static final int SC_OK = 200;
    static final int SC_NOT_FOUND = 404;
    static final int SC_NOT_MODIFIED = 304;
    static final int SC_INTERNAL_SERVER_ERROR = 500;

    public interface Request {
        String getContainerType();
        String getContainerNamespace();

        String getPathInfo();
        String getRequestPath();
        String getContextPath();
        String getServletPath();
        String getClientContextPath(String urlString);

        Map<String, Object> getAttributesMap();
        Map<String, String[]> getHeaderValuesMap();
        Map<String, Object[]> getParameterMap();

        String getCharacterEncoding();
        int getContentLength();
        String getContentType();
        InputStream getInputStream() throws IOException;
        Reader getReader() throws IOException;

        String getProtocol();
        String getRemoteHost();
        String getRemoteAddr();
        String getScheme();
        String getMethod();
        String getServerName();
        int getServerPort();

        Session getSession(boolean create);
        void sessionInvalidate();
        boolean isRequestedSessionIdValid();
        String getRequestedSessionId();

        String getAuthType();
        boolean isSecure();
        String getRemoteUser();
        boolean isUserInRole(String role);
        Principal getUserPrincipal();

        Locale getLocale();
        Enumeration getLocales();

        String getPathTranslated();
        String getQueryString();
        String getRequestURI();
        String getRequestURL();

        String getPortletMode();
        String getWindowState();

        Object getNativeRequest();
    }

    public interface Response {

        // Works as a bitset
        // 1: whether to produce an absolute URL (starting with "http" or "https")
        // 2: whether to leave the URL as is if it is does not start with "/"
        // 4: whether to prevent insertion of a context at the start of the path
        static final int REWRITE_MODE_ABSOLUTE = 1;
        static final int REWRITE_MODE_ABSOLUTE_PATH = 0;
        static final int REWRITE_MODE_ABSOLUTE_PATH_OR_RELATIVE = 2;
        static final int REWRITE_MODE_ABSOLUTE_PATH_NO_CONTEXT = 4;
        static final int REWRITE_MODE_ABSOLUTE_NO_CONTEXT = 5;

        PrintWriter getWriter() throws IOException;
        OutputStream getOutputStream() throws IOException;
        boolean isCommitted();
        void reset();
        void setContentType(String contentType);
        void setStatus(int status);
        void setContentLength(int len);
        void setHeader(String name, String value);
        void addHeader(String name, String value);
        void sendError(int code) throws IOException;
        String getCharacterEncoding();

        void sendRedirect(String pathInfo, Map<String, String[]> parameters, boolean isServerSide, boolean isExitPortal) throws IOException;

        void setCaching(long lastModified, boolean revalidate, boolean allowOverride);

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
        void setResourceCaching(long lastModified, long expires);

        boolean checkIfModifiedSince(long lastModified, boolean allowOverride);

        String rewriteActionURL(String urlString);
        String rewriteRenderURL(String urlString);
        String rewriteActionURL(String urlString, String portletMode, String windowState);
        String rewriteRenderURL(String urlString, String portletMode, String windowState);
        String rewriteResourceURL(String urlString, boolean absolute);
        String rewriteResourceURL(String urlString, int rewriteMode);
        String getNamespacePrefix();
        void setTitle(String title);

        Object getNativeResponse();
    }

    public interface Session {
        static final int APPLICATION_SCOPE = 1;
        static final int PORTLET_SCOPE = 2;

        long getCreationTime();
        String getId();
        long getLastAccessedTime();
        int getMaxInactiveInterval();
        void invalidate();
        boolean isNew();
        void setMaxInactiveInterval(int interval);

        Map<String, Object> getAttributesMap();
        Map<String, Object> getAttributesMap(int scope);

        void addListener(SessionListener sessionListener);
        void removeListener(SessionListener sessionListener);

        interface SessionListener {
            void sessionDestroyed();
        }
    }

    public interface Application {
        void addListener(ApplicationListener applicationListener);
        void removeListener(ApplicationListener applicationListener);

        interface ApplicationListener {
            void servletDestroyed();
        }
    }

    public interface RequestDispatcher {
        abstract void forward(Request request, Response response) throws IOException;
        void include(Request request, Response response) throws IOException;
        boolean isDefaultContext();
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
    RequestDispatcher getRequestDispatcher(String path, boolean isContextRelative);

    Request getRequest();
    Response getResponse();
    Session getSession(boolean create);
    Application getApplication();

    /**
     * Rewrite a service URL. The URL is rewritten against a base URL which is:
     *
     * o specified externally or
     * o the incoming request if not specified externally
     *
     * @param urlString     URL to rewrite
     * @param rewriteMode   rewrite mode
     * @return              rewritten URL
     */
    String rewriteServiceURL(String urlString, int rewriteMode);

    String getStartLoggerString();
    String getEndLoggerString();

    Object getNativeRequest();
    Object getNativeResponse();
}
