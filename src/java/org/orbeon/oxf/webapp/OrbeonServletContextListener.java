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
package org.orbeon.oxf.webapp;

import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.WebAppExternalContext;
import org.orbeon.oxf.servlet.ServletExternalContext;
import org.orbeon.oxf.servlet.ServletWebAppExternalContext;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import java.util.Iterator;

/**
 * This listener listens for HTTP context lifecycle changes.
 *
 * WARNING: This class must only depend on the Servlet API and the OXF Class Loader.
 */
public class OrbeonServletContextListener implements ServletContextListener {

    // ServletContextListener delegate
    private ServletContextListener servletContextListenerDelegate;

     public void contextInitialized(ServletContextEvent event) {
        // Instantiate ServletContextListener delegate if needed
        WebAppExternalContext webAppExternalContext = new ServletWebAppExternalContext(event.getServletContext());
        initializeDelegate(webAppExternalContext);

        // Delegate to ServletContextListener delegate
        Thread currentThread = Thread.currentThread();
        ClassLoader oldThreadContextClassLoader = currentThread.getContextClassLoader();
        try {
            currentThread.setContextClassLoader(OrbeonClassLoader.getClassLoader(webAppExternalContext));
            servletContextListenerDelegate.contextInitialized(event);
        } finally {
            currentThread.setContextClassLoader(oldThreadContextClassLoader);
        }
    }

    public void contextDestroyed(ServletContextEvent event) {
        // Instantiate ServletContextListener delegate if needed
        WebAppExternalContext webAppExternalContext = new ServletWebAppExternalContext(event.getServletContext());
        initializeDelegate(webAppExternalContext);

        // Delegate to ServletContextListener delegate
        Thread currentThread = Thread.currentThread();
        ClassLoader oldThreadContextClassLoader = currentThread.getContextClassLoader();
        try {
            currentThread.setContextClassLoader(OrbeonClassLoader.getClassLoader(webAppExternalContext));
            // Run listeners if any
            ServletContext servletContext = event.getServletContext();
            if (servletContext != null && servletContext.getAttribute(ServletExternalContext.APPLICATION_LISTENERS) != null) {
              // Iterate through listeners
              final ServletExternalContext.ApplicationListeners listeners = (ServletExternalContext.ApplicationListeners) servletContext.getAttribute(ServletExternalContext.APPLICATION_LISTENERS);
              if (listeners != null) {
                for (Iterator i = listeners.iterator(); i.hasNext();) {
                  final ExternalContext.Application.ApplicationListener currentListener = (ExternalContext.Application.ApplicationListener) i.next();
                  currentListener.servletDestroyed();
                }
              }
            }
            servletContextListenerDelegate.contextDestroyed(event);
        } finally {
            currentThread.setContextClassLoader(oldThreadContextClassLoader);
        }
    }

    private void initializeDelegate(WebAppExternalContext webAppExternalContext) {
        try {
            if (servletContextListenerDelegate == null) {
                Class delegateServletClass = OrbeonClassLoader.getClassLoader(webAppExternalContext).loadClass(OrbeonServletContextListener.class.getName() + OrbeonClassLoader.DELEGATE_CLASS_SUFFIX);
                servletContextListenerDelegate = (ServletContextListener) delegateServletClass.newInstance();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
