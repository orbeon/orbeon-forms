/**
 *  Copyright (C) 2005 Orbeon, Inc.
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

import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.servlet.ServletExternalContext;
import org.orbeon.oxf.util.URLRewriterUtils;

import javax.portlet.PortletContext;

public class PortletContextExternalContext extends PortletWebAppExternalContext implements ExternalContext {
    private Application application;

    public PortletContextExternalContext(PortletContext portletContext) {
        super(portletContext);
    }

    public Object getNativeRequest() {
        throw new UnsupportedOperationException();
    }

    public Object getNativeResponse() {
        throw new UnsupportedOperationException();
    }

    public RequestDispatcher getRequestDispatcher(String path, boolean isContextRelative) {
        throw new UnsupportedOperationException();
    }

    public Request getRequest() {
        throw new UnsupportedOperationException();
    }

    public Response getResponse() {
        throw new UnsupportedOperationException();
    }

    public Session getSession(boolean create) {
        throw new UnsupportedOperationException();
    }

    public ExternalContext.Application getApplication() {
        if (portletContext == null)
          throw new UnsupportedOperationException();
        if (application == null)
          application = new Application(portletContext);

        return application;
    }

    public String getStartLoggerString() {
        return "";
    }

    public String getEndLoggerString() {
        return "";
    }

    private class Application implements ExternalContext.Application {
        private PortletContext portletContext;

        public Application(PortletContext portletContext) {
            this.portletContext = portletContext;
        }

        public void addListener(ApplicationListener applicationListener) {

            ServletExternalContext.ApplicationListeners listeners = (ServletExternalContext.ApplicationListeners) portletContext.getAttribute(ServletExternalContext.APPLICATION_LISTENERS);
            if (listeners == null) {
                listeners = new ServletExternalContext.ApplicationListeners();
                portletContext.setAttribute(ServletExternalContext.APPLICATION_LISTENERS, listeners);
            }
            listeners.addListener(applicationListener);
        }

        public void removeListener(ApplicationListener applicationListener) {
            final ServletExternalContext.ApplicationListeners listeners = (ServletExternalContext.ApplicationListeners) portletContext.getAttribute(ServletExternalContext.APPLICATION_LISTENERS);
            if (listeners != null)
                listeners.removeListener(applicationListener);
        }
    }

    public String rewriteServiceURL(String urlString, int rewriteMode) {
        return URLRewriterUtils.rewriteServiceURL(getRequest(), urlString, rewriteMode);
    }
}
