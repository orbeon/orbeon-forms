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

import org.orbeon.oxf.pipeline.api.WebAppExternalContext;
import org.orbeon.oxf.servlet.ServletWebAppExternalContext;

import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;

/**
 * This listener listens for HTTP session lifecycle changes.
 *
 * WARNING: This class must only depend on the Servlet API and the OXF Class Loader.
 */
public class OXFSessionListener implements HttpSessionListener {

    // ServletContextListener delegate
    private HttpSessionListener httpSessionListenerDelegate;

    public void sessionCreated(HttpSessionEvent event) {
        // Instanciate ServletContextListener delegate if needed
        WebAppExternalContext webAppExternalContext = new ServletWebAppExternalContext(event.getSession().getServletContext());
        initializeDelegate(webAppExternalContext);

        // Delegate to ServletContextListener delegate
        Thread currentThread = Thread.currentThread();
        ClassLoader oldThreadContextClassLoader = currentThread.getContextClassLoader();
        try {
            currentThread.setContextClassLoader(OXFClassLoader.getClassLoader(webAppExternalContext));
            httpSessionListenerDelegate.sessionCreated(event);
        } finally {
            currentThread.setContextClassLoader(oldThreadContextClassLoader);
        }
    }

    public void sessionDestroyed(HttpSessionEvent event) {
        // Instanciate ServletContextListener delegate if needed
        WebAppExternalContext webAppExternalContext = new ServletWebAppExternalContext(event.getSession().getServletContext());
        initializeDelegate(webAppExternalContext);

        // Delegate to ServletContextListener delegate
        Thread currentThread = Thread.currentThread();
        ClassLoader oldThreadContextClassLoader = currentThread.getContextClassLoader();
        try {
            currentThread.setContextClassLoader(OXFClassLoader.getClassLoader(webAppExternalContext));
            httpSessionListenerDelegate.sessionDestroyed(event);
        } finally {
            currentThread.setContextClassLoader(oldThreadContextClassLoader);
        }
    }

    private void initializeDelegate(WebAppExternalContext webAppExternalContext) {
        try {
            if (httpSessionListenerDelegate == null) {
                Class delegateServletClass = OXFClassLoader.getClassLoader(webAppExternalContext).loadClass(OXFSessionListener.class.getName() + OXFClassLoader.DELEGATE_CLASS_SUFFIX);
                httpSessionListenerDelegate = (HttpSessionListener) delegateServletClass.newInstance();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
