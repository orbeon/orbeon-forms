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

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

/**
 * This listener listens for HTTP context lifecycle changes.
 *
 * WARNING: This class must only depend on the Servlet API and the OXF Class Loader.
 */
public class OXFServletContextListener implements ServletContextListener {

    // ServletContextListener delegate
    private ServletContextListener servletContextListenerDelegate;

     public void contextInitialized(ServletContextEvent event) {
        // Instanciate ServletContextListener delegate if needed
        WebAppExternalContext webAppExternalContext = new ServletWebAppExternalContext(event.getServletContext());
        initializeDelegate(webAppExternalContext);

        // Delegate to ServletContextListener delegate
        Thread currentThread = Thread.currentThread();
        ClassLoader oldThreadContextClassLoader = currentThread.getContextClassLoader();
        try {
            currentThread.setContextClassLoader(OXFClassLoader.getClassLoader(webAppExternalContext));
            servletContextListenerDelegate.contextInitialized(event);
        } finally {
            currentThread.setContextClassLoader(oldThreadContextClassLoader);
        }
    }

    public void contextDestroyed(ServletContextEvent event) {
        // Instanciate ServletContextListener delegate if needed
        WebAppExternalContext webAppExternalContext = new ServletWebAppExternalContext(event.getServletContext());
        initializeDelegate(webAppExternalContext);

        // Delegate to ServletContextListener delegate
        Thread currentThread = Thread.currentThread();
        ClassLoader oldThreadContextClassLoader = currentThread.getContextClassLoader();
        try {
            currentThread.setContextClassLoader(OXFClassLoader.getClassLoader(webAppExternalContext));
            servletContextListenerDelegate.contextDestroyed(event);
        } finally {
            currentThread.setContextClassLoader(oldThreadContextClassLoader);
        }
    }

    private void initializeDelegate(WebAppExternalContext webAppExternalContext) {
        try {
            if (servletContextListenerDelegate == null) {
                Class delegateServletClass = OXFClassLoader.getClassLoader(webAppExternalContext).loadClass(OXFServletContextListener.class.getName() + OXFClassLoader.DELEGATE_CLASS_SUFFIX);
                servletContextListenerDelegate = (ServletContextListener) delegateServletClass.newInstance();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
