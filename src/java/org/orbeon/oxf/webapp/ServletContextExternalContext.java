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
package org.orbeon.oxf.webapp;

import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.InitUtils;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.servlet.ServletWebAppExternalContext;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;
import java.util.Map;

/**
 * This context provides access to the Servlet context, and optionaly the session.
 *
 * An instance can be either constructed from a ServletContext, or from an existing
 * ExternalContext. In the second case, this class just delegates a subset of the functionality
 * to an existing ExternalContext.
 */
public class ServletContextExternalContext implements ExternalContext {

    private ServletContext servletContext;
    private HttpSession httpSession;

    private ExternalContext delegatingExternalContext;

    private Session session;

    private Map attributesMap;
    private Map initAttributesMap;

    public ServletContextExternalContext(ServletContext servletContext) {
        this.servletContext = servletContext;
    }

    public ServletContextExternalContext(ServletContext servletContext, HttpSession httpSession) {
        this(servletContext);
        this.httpSession = httpSession;
    }

    public ServletContextExternalContext(ExternalContext externalContext) {
        this.delegatingExternalContext = externalContext;
    }

    public Map getInitAttributesMap() {
        if (initAttributesMap == null) {
            if (servletContext != null)
                initAttributesMap = InitUtils.getContextInitParametersMap(servletContext);
            else
                initAttributesMap = delegatingExternalContext.getInitAttributesMap();
        }
        return initAttributesMap;
    }

    public Map getAttributesMap() {
        if (attributesMap == null) {
            if (servletContext != null)
                attributesMap = new ServletWebAppExternalContext.ServletContextMap(servletContext);
            else
                attributesMap = delegatingExternalContext.getAttributesMap();
        }
        return attributesMap;
    }

    public RequestDispatcher getNamedDispatcher(String name) {
        throw new UnsupportedOperationException();
    }

    public Object getNativeContext() {
        if (servletContext != null)
            return servletContext;
        else
            return delegatingExternalContext.getNativeContext();
    }

    public Object getNativeRequest() {
        throw new UnsupportedOperationException();
    }

    public Object getNativeResponse() {
        throw new UnsupportedOperationException();
    }

    public Object getNativeSession(boolean flag) {
        if (session == null)
            throw new UnsupportedOperationException();
        return session;
    }

    public String getRealPath(String path) {
        throw new UnsupportedOperationException();
    }

    public String getStartLoggerString() {
        if (delegatingExternalContext != null)
            return delegatingExternalContext.getStartLoggerString();
        else
            return "";
    }

    public String getEndLoggerString() {
        if (delegatingExternalContext != null)
            return delegatingExternalContext.getEndLoggerString();
        else
            return "";
    }

    public Request getRequest() {
        throw new UnsupportedOperationException();
    }

    public RequestDispatcher getRequestDispatcher(String path) {
        throw new UnsupportedOperationException();
    }

    public Response getResponse() {
        throw new UnsupportedOperationException();
    }

    public ExternalContext.Session getSession(boolean create) {
        if (httpSession == null)
            throw new UnsupportedOperationException();
        if (session == null) {
            session = new Session(httpSession);
        }
        return session;
    }

    public void log(String message, Throwable throwable) {
        if (servletContext != null)
            servletContext.log(message, throwable);
        else
            delegatingExternalContext.log(message, throwable);
    }

    public void log(String msg) {
        if (servletContext != null)
            servletContext.log(msg);
        else
            delegatingExternalContext.log(msg);
    }

    private class Session implements ExternalContext.Session {

        private HttpSession httpSession;
        private Map sessionAttributesMap;

        public Session(HttpSession httpSession) {
            this.httpSession = httpSession;
        }

        public long getCreationTime() {
            return httpSession.getCreationTime();
        }

        public String getId() {
            return httpSession.getId();
        }

        public long getLastAccessedTime() {
            return httpSession.getLastAccessedTime();
        }

        public int getMaxInactiveInterval() {
            return httpSession.getMaxInactiveInterval();
        }

        public void invalidate() {
            httpSession.invalidate();
        }

        public boolean isNew() {
            return httpSession.isNew();
        }

        public void setMaxInactiveInterval(int interval) {
            httpSession.setMaxInactiveInterval(interval);
        }

        public Map getAttributesMap() {
            if (sessionAttributesMap == null) {
                sessionAttributesMap = new InitUtils.SessionMap(httpSession);
            }
            return sessionAttributesMap;
        }

        public Map getAttributesMap(int scope) {
            if (scope != Session.APPLICATION_SCOPE)
                throw new OXFException("Invalid session scope scope: only the application scope is allowed in Servlets");
            return getAttributesMap();
        }
    }
}
