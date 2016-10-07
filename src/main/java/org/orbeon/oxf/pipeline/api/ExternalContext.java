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

import org.orbeon.oxf.externalcontext.URLRewriter;
import org.orbeon.oxf.fr.UserRole;
import org.orbeon.oxf.webapp.WebAppContext;

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
public interface ExternalContext {

    int SC_OK = 200;
    int SC_NOT_FOUND = 404;
    int SC_NOT_MODIFIED = 304;
    int SC_INTERNAL_SERVER_ERROR = 500;

    interface Request {
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

        // NOTE: Consider using a class such as `Credentials` (there is one in used in test code).
        String getUsername();
        String getUserGroup();
        UserRole[] getUserRoles();
        String[] getUserOrganization();
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

    interface Rewriter extends URLRewriter {
        String rewriteActionURL(String urlString);
        String rewriteRenderURL(String urlString);
        String rewriteActionURL(String urlString, String portletMode, String windowState);
        String rewriteRenderURL(String urlString, String portletMode, String windowState);
        String rewriteResourceURL(String urlString, int rewriteMode);
        String getNamespacePrefix();
    }

    interface Response extends Rewriter {
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

        void sendRedirect(String location, boolean isServerSide, boolean isExitPortal) throws IOException;

        void setPageCaching(long lastModified);

        /**
         * Set expiration headers for resources.
         *
         * - If lastModified is > 0, Last-Modified is set to that value
         * - If lastModified is <= 0, Last-Modified and Expires are set to the time of the response
         * - If expires is > 0 and lastModified is > 0, Expires is set to that value
         * - If expires is <= 0 , Expires is set using the default policy: 1/10 of the age of the resource
         *
         * @param lastModified  last modification date of resource, or <= 0 if unknown
         * @param expires       requested expiration, or <=0 if unknown or to trigger default policy
         */
        void setResourceCaching(long lastModified, long expires);

        boolean checkIfModifiedSince(long lastModified);

        void setTitle(String title);

        Object getNativeResponse();
    }

    interface Session {
        int APPLICATION_SCOPE = 1;
        int PORTLET_SCOPE = 2;

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

    WebAppContext getWebAppContext();

    // NOTE: The only reason the session is available here is for session created/destroyed listeners, which make
    // available a session even though no request or response is available.
    Session getSession(boolean create);

    interface RequestDispatcher {
        void forward(Request request, Response response) throws IOException;
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

    String getStartLoggerString();
    String getEndLoggerString();
}
