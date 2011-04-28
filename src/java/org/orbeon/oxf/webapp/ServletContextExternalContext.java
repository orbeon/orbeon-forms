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
import org.orbeon.oxf.util.URLRewriterUtils;

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
    private Application application;

    private Map<String, Object> attributesMap;
    private Map<String, String> initAttributesMap;

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

    public Map<String, String> getInitAttributesMap() {
        if (initAttributesMap == null) {
            if (servletContext != null)
                initAttributesMap = InitUtils.getContextInitParametersMap(servletContext);
            else
                initAttributesMap = delegatingExternalContext.getInitAttributesMap();
        }
        return initAttributesMap;
    }

    public Map<String, Object> getAttributesMap() {
        if (attributesMap == null) {
            if (servletContext != null)
                attributesMap = new ServletWebAppExternalContext.ServletContextMap(servletContext);
            else
                attributesMap = delegatingExternalContext.getAttributesMap();
        }
        return attributesMap;
    }

    public Object getNativeContext() {
        if (servletContext != null)
            return servletContext;
        else
            return delegatingExternalContext.getNativeContext();
    }

    public Object getNativeRequest() {
        return null;
    }

    public Object getNativeResponse() {
        return null;
    }

    public String getRealPath(String path) {
        if (servletContext != null)
            return servletContext.getRealPath(path);
        else
            return delegatingExternalContext.getRealPath(path);
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
        return null;
    }

    public RequestDispatcher getRequestDispatcher(String path, boolean isContextRelative) {
        return null;
    }

    public Response getResponse() {
        return null;
    }

    public ExternalContext.Session getSession(boolean create) {
        // Return null if we were not provided with a session. This allows detecting whether the session is available or not.
        if (httpSession == null)
            return null;

        // Create the session wrapper if not already done
        if (session == null) {
            session = new Session(httpSession);
        }
        
        return session;
    }

    public ExternalContext.Application getApplication() {
        if (servletContext == null)
            return null;
        if (application == null)
            application = new Application(servletContext);

        return application;
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
        private Map<String, Object> sessionAttributesMap;

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

        public Map<String, Object> getAttributesMap() {
            if (sessionAttributesMap == null) {
                sessionAttributesMap = new InitUtils.SessionMap(httpSession);
            }
            return sessionAttributesMap;
        }

        public Map<String, Object> getAttributesMap(int scope) {
            if (scope != Session.APPLICATION_SCOPE)
                throw new OXFException("Invalid session scope scope: only the application scope is allowed in Servlets");
            return getAttributesMap();
        }


        public void addListener(SessionListener sessionListener) {
            throw new UnsupportedOperationException();
        }

        public void removeListener(SessionListener sessionListener) {
            throw new UnsupportedOperationException();
        }
    }

    private class Application implements ExternalContext.Application {
        private ServletContext servletContext;

        public Application(ServletContext servletContext) {
          this.servletContext = servletContext;
        }

        public void addListener(ApplicationListener applicationListener) {
          throw new UnsupportedOperationException();
        }

        public void removeListener(ApplicationListener applicationListener) {
          throw new UnsupportedOperationException();
        }
    }

    public String rewriteServiceURL(String urlString, int rewriteMode) {
        return URLRewriterUtils.rewriteServiceURL(getRequest(), urlString, rewriteMode);
    }
}
