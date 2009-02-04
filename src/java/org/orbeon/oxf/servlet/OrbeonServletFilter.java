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
package org.orbeon.oxf.servlet;

import org.orbeon.oxf.webapp.OXFClassLoader;
import org.orbeon.oxf.pipeline.api.WebAppExternalContext;

import javax.servlet.*;
import java.io.IOException;

/**
 * OXFServletFilter is the Servlet Filter entry point of OXF.
 *
 * WARNING: This class must only depend on the Servlet API and the OXF Class Loader.
 */
public class OrbeonServletFilter implements Filter {

    // Servlet Filter delegate
    private Filter servletFilterDelegate;

    // WebAppExternalContext
    private WebAppExternalContext webAppExternalContext;

    public void init(FilterConfig config) throws ServletException {
        try {
            // Instanciate WebAppExternalContext
            webAppExternalContext = new ServletWebAppExternalContext(config.getServletContext());

            // Instanciate Servlet Filter delegate
            Class delegateServletClass = OXFClassLoader.getClassLoader(webAppExternalContext).loadClass(OrbeonServletFilter.class.getName() + OXFClassLoader.DELEGATE_CLASS_SUFFIX);
            servletFilterDelegate = (Filter) delegateServletClass.newInstance();

            // Initialize Servlet Filter delegate
            Thread currentThread = Thread.currentThread();
            ClassLoader oldThreadContextClassLoader = currentThread.getContextClassLoader();
            try {
                currentThread.setContextClassLoader(OXFClassLoader.getClassLoader(webAppExternalContext));
                servletFilterDelegate.init(config);
            } finally {
                currentThread.setContextClassLoader(oldThreadContextClassLoader);
            }
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }

    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        // Delegate to Servlet Filter delegate
        Thread currentThread = Thread.currentThread();
        ClassLoader oldThreadContextClassLoader = currentThread.getContextClassLoader();
        try {
            currentThread.setContextClassLoader(OXFClassLoader.getClassLoader(webAppExternalContext));
            servletFilterDelegate.doFilter(request, response, chain);
        } finally {
            currentThread.setContextClassLoader(oldThreadContextClassLoader);
        }
    }

    public void destroy() {
        // Delegate to Servlet Filter delegate
        Thread currentThread = Thread.currentThread();
        ClassLoader oldThreadContextClassLoader = currentThread.getContextClassLoader();
        try {
            currentThread.setContextClassLoader(OXFClassLoader.getClassLoader(webAppExternalContext));
            servletFilterDelegate.destroy();
        } finally {
            currentThread.setContextClassLoader(oldThreadContextClassLoader);
        }
    }
}
