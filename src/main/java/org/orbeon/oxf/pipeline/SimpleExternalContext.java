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
package org.orbeon.oxf.pipeline;

import org.apache.log4j.Logger;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.util.StringBuilderWriter;
import org.orbeon.oxf.webapp.TestWebAppContext;
import org.orbeon.oxf.webapp.WebAppContext;

import java.io.*;
import java.security.Principal;
import java.util.*;

/**
 * Simple implementation of the ExternalContext and related interfaces.
 *
 * Used by CommandLineExternalContext.
 */
class SimpleExternalContext implements ExternalContext {

    private static final Logger logger = LoggerFactory.createLogger(SimpleExternalContext.class);
    private WebAppContext webAppContext = new TestWebAppContext(logger, new scala.collection.mutable.LinkedHashMap<String, Object>());

    public WebAppContext getWebAppContext() {
        return webAppContext;
    }

    protected class Request implements ExternalContext.Request {

        protected Map<String, Object> attributesMap = new HashMap<String, Object>();

        public String getContainerType() {
            return "simple";
        }

        public String getContainerNamespace() {
            return "";
        }

        public String getContextPath() {
            return "";
        }

        public String getPathInfo() {
            return "";
        }

        public String getRemoteAddr() {
            return "127.0.0.1";
        }

        public Map<String, Object> getAttributesMap() {
            return attributesMap;
        }

        public Map<String, String[]> getHeaderValuesMap() {
            return Collections.emptyMap();
        }

        public Map<String, Object[]> getParameterMap() {
            return Collections.emptyMap();
        }

        public String getAuthType() {
            return "basic";
        }

        public String getUsername() { return null; }

        public String getUserGroup() { return null; }

        public String[] getUserRoles() { return new String[0]; }

        public boolean isSecure() {
            return false;
        }

        public boolean isUserInRole(String role) {
            return false;
        }

        public ExternalContext.Session getSession(boolean create) {
            return session;
        }

        public void sessionInvalidate() {
        }

        public String getCharacterEncoding() {
            return "utf-8";
        }

        public int getContentLength() {
            return 0;
        }

        public String getContentType() {
            return "";
        }

        public String getServerName() {
            return "";
        }

        public int getServerPort() {
            return 0;
        }

        public String getMethod() {
            return "GET";
        }

        public String getProtocol() {
            return "http";
        }

        public String getRemoteHost() {
            return "";
        }

        public String getScheme() {
            return "";
        }

        public String getPathTranslated() {
            return "";
        }

        public String getQueryString() {
            return "";
        }

        public String getRequestedSessionId() {
            return "";
        }

        public String getRequestPath() {
            return "";
        }

        public String getRequestURI() {
            return "";
        }

        public String getRequestURL() {
            return "";
        }

        public String getServletPath() {
            return "";
        }

        public String getClientContextPath(String urlString) {
            return getContextPath();
        }

        public Reader getReader() throws IOException {
            return null;
        }

        public InputStream getInputStream() throws IOException {
            return null;
        }

        public Locale getLocale() {
            return null;
        }

        public Enumeration getLocales() {
            return null;
        }

        public boolean isRequestedSessionIdValid() {
            return false;
        }

        public Principal getUserPrincipal() {
            return null;
        }

        public Object getNativeRequest() {
            return null;
        }

        public String getPortletMode() {
            return null;
        }

        public String getWindowState() {
            return null;
        }
    }

    protected class Response implements ExternalContext.Response {
        protected ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        protected StringBuilderWriter writer = new StringBuilderWriter();
        protected String contentType;
        protected int status;
        protected Map<String, String> headers = new HashMap<String, String>();

        public OutputStream getOutputStream() throws IOException {
            return outputStream;
        }

        public PrintWriter getWriter() throws IOException {
            return new PrintWriter(writer);
        }

        public boolean isCommitted() {
            return false;
        }

        public void reset() {
            outputStream.reset();
            writer.getBuilder().delete(0, writer.getBuilder().length());
        }

        public void setContentType(String contentType) {
            this.contentType = contentType;
        }

        public void setStatus(int status) {
            this.status = status;
        }


        public void setHeader(String name, String value) {
            headers.put(name, value);
        }

        public void addHeader(String name, String value) {
            headers.put(name, value);
        }

        public void sendRedirect(String location, boolean isServerSide, boolean isExitPortal) throws IOException {
        }

        public void setContentLength(int len) {
        }

        public void sendError(int code) throws IOException {
        }

        public String getCharacterEncoding() {
            return "utf-8";
        }

        public void setPageCaching(long lastModified) {
        }

        public void setResourceCaching(long lastModified, long expires) {
        }

        public boolean checkIfModifiedSince(long lastModified) {
            return true;
        }

        public String rewriteActionURL(String urlString) {
            return "";
        }

        public String rewriteRenderURL(String urlString) {
            return "";
        }

        public String rewriteActionURL(String urlString, String portletMode, String windowState) {
            return "";
        }

        public String rewriteRenderURL(String urlString, String portletMode, String windowState) {
            return "";
        }

        public String rewriteResourceURL(String urlString) {
            return "";
        }

        public String rewriteResourceURL(String urlString, boolean generateAbsoluteURL) {
            return "";
        }

        public String rewriteResourceURL(String urlString, int rewriteMode) {
            return "";
        }

        public String getNamespacePrefix() {
            return "";
        }

        public void setTitle(String title) {
        }

        public Object getNativeResponse() {
            return null;
        }
    }

    protected class Session implements ExternalContext.Session {

        protected Map<String, Object> sessionAttributesMap = new HashMap<String, Object>();

        public long getCreationTime() {
            return 0;
        }

        public String getId() {
            return Integer.toString(sessionAttributesMap.hashCode());
        }

        public long getLastAccessedTime() {
            return 0;
        }

        public int getMaxInactiveInterval() {
            return 0;
        }

        public void invalidate() {
            sessionAttributesMap = new HashMap<String, Object>();
        }

        public boolean isNew() {
            return false;
        }

        public void setMaxInactiveInterval(int interval) {
        }

        public Map<String, Object> getAttributesMap() {
            return sessionAttributesMap;
        }

        public Map<String, Object> getAttributesMap(int scope) {
            if (scope != APPLICATION_SCOPE)
                throw new OXFException("Invalid session scope scope: only the application scope is allowed in Eclipse");
            return getAttributesMap();
        }


        public void addListener(SessionListener sessionListener) {
        }

        public void removeListener(SessionListener sessionListener) {
        }
    }

    protected Request request = new Request();
    protected Response response = new Response();
    protected Session session = new Session();

    public ExternalContext.Request getRequest() {
        return request;
    }

    public ExternalContext.Response getResponse() {
        return response;
    }

    public ExternalContext.Session getSession(boolean create) {
        return session;
    }

    public String getStartLoggerString() {
        return "Running processor";
    }

    public String getEndLoggerString() {
        return "Done running processor";
    }

    public ExternalContext.RequestDispatcher getRequestDispatcher(String path, boolean isContextRelative) {
        return null;
    }
}
