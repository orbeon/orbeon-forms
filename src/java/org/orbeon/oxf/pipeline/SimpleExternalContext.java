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
package org.orbeon.oxf.pipeline;

import org.apache.log4j.Logger;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.util.LoggerFactory;

import java.io.*;
import java.security.Principal;
import java.util.*;

/**
 * Simple implementation of the ExternalContext and related interfaces. When embedding
 * PresentationServer (e.g. in Eclipse), this class can be used directly be used directly or can
 * be subclassed.
 */
public class SimpleExternalContext implements ExternalContext {

    private static final Logger logger = LoggerFactory.createLogger(SimpleExternalContext.class);

    protected class Request implements ExternalContext.Request {

        protected Map attributesMap = new HashMap();

        public String getContainerType() {
            return "simple";
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

        public Map getAttributesMap() {
            return attributesMap;
        }

        public Map getHeaderMap() {
            return Collections.EMPTY_MAP;
        }

        public Map getHeaderValuesMap() {
            return Collections.EMPTY_MAP;
        }

        public Map getParameterMap() {
            return Collections.EMPTY_MAP;
        }

        public String getAuthType() {
            return "basic";
        }

        public String getRemoteUser() {
            return null;
        }

        public boolean isSecure() {
            return false;
        }

        public boolean isUserInRole(String role) {
            return false;
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
            return SimpleExternalContext.this.getNativeRequest();
        }
    }

    protected class Response implements ExternalContext.Response {
        protected ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        protected StringWriter writer = new StringWriter();
        protected String contentType;
        protected int status;
        protected Map headers = new HashMap();

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
            writer.getBuffer().delete(0, writer.getBuffer().length());
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

        public void sendRedirect(String pathInfo, Map parameters, boolean isServerSide, boolean isExitPortal) throws IOException {
        }

        public void setContentLength(int len) {
        }

        public void sendError(int code) throws IOException {
        }

        public String getCharacterEncoding() {
            return "utf-8";
        }

        public void setCaching(long lastModified, boolean revalidate, boolean allowOverride) {
        }

        public boolean checkIfModifiedSince(long lastModified, boolean allowOverride) {
            return true;
        }

        public String rewriteActionURL(String urlString) {
            return "";
        }

        public String rewriteRenderURL(String urlString) {
            return "";
        }

        public String rewriteResourceURL(String urlString) {
            return "";
        }

        public String rewriteResourceURL(String urlString, boolean generateAbsoluteURL) {
            return "";
        }

        public String getNamespacePrefix() {
            return "";
        }

        public void setTitle(String title) {
        }

        public Object getNativeResponse() {
            return SimpleExternalContext.this.getNativeResponse();
        }
    }

    protected class Session implements ExternalContext.Session {

        protected Map sessionAttributesMap = new HashMap();

        public long getCreationTime() {
            return 0;
        }

        public String getId() {
            return new Integer(sessionAttributesMap.hashCode()).toString();
        }

        public long getLastAccessedTime() {
            return 0;
        }

        public int getMaxInactiveInterval() {
            return 0;
        }

        public void invalidate() {
            sessionAttributesMap = new HashMap();
        }

        public boolean isNew() {
            return false;
        }

        public void setMaxInactiveInterval(int interval) {
        }

        public Map getAttributesMap() {
            return sessionAttributesMap;
        }

        public Map getAttributesMap(int scope) {
            if (scope != APPLICATION_SCOPE)
                throw new OXFException("Invalid session scope scope: only the application scope is allowed in Eclipse");
            return getAttributesMap();
        }
    }

    protected Request request = new Request();
    protected Response response = new Response();
    protected Session session = new Session();

    public Object getNativeContext() {
        return null;
    }

    public Object getNativeRequest() {
        return null;
    }

    public Object getNativeResponse() {
        return null;
    }

    public Object getNativeSession(boolean create) {
        return null;
    }

    public ExternalContext.Request getRequest() {
        return request;
    }

    public ExternalContext.Response getResponse() {
        return response;
    }

    public ExternalContext.Session getSession(boolean create) {
        return session;
    }

    public Map getSessionMap() {
        return Collections.EMPTY_MAP;
    }

    public Map getAttributesMap() {
        return Collections.EMPTY_MAP;
    }

    public Map getInitAttributesMap() {
        return Collections.EMPTY_MAP;
    }

    public String getRealPath(String path) {
        return null;
    }

    public String getStartLoggerString() {
        return "Running processor";
    }

    public String getEndLoggerString() {
        return "Done running processor";
    }

    public void log(String message, Throwable throwable) {
        logger.error(message, throwable);
    }

    public void log(String msg) {
        logger.info(msg);
    }

    public ExternalContext.RequestDispatcher getNamedDispatcher(String name) {
        return null;
    }

    public ExternalContext.RequestDispatcher getRequestDispatcher(String path) {
        return null;
    }
}
