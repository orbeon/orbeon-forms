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

import org.orbeon.oxf.pipeline.api.WebAppExternalContext;
import org.orbeon.oxf.webapp.OXFClassLoader;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * OXFServlet and OPSServletDelegate are the Servlet entry point of OPS. OPSServlet simply delegates to
 * OPSServletDelegate and provides an option of using the OPS Class Loader.
 *
 * Several OPSServlet and OPSPortlet instances can be used in the same Web or Portlet application.
 * They all share the same Servlet context initialization parameters, but each Servlet can be
 * configured with its own main processor and inputs.
 *
 * All OPSServlet and OPSPortlet instances in a given Web application share the same resource
 * manager.
 *
 * WARNING: OPSServlet must only depend on the Servlet API and the OPS Class Loader.
 */
public class OrbeonServlet extends HttpServlet {

    // Servlet delegate
    private HttpServlet delegateServlet;

    // WebAppExternalContext
    private WebAppExternalContext webAppExternalContext;

    public void init() throws ServletException {
        try {
            // Instanciate WebAppExternalContext
            webAppExternalContext = new ServletWebAppExternalContext(getServletContext());

            // Instanciate Servlet delegate
            Class delegateServletClass = OXFClassLoader.getClassLoader(webAppExternalContext).loadClass(OrbeonServlet.class.getName() + OXFClassLoader.DELEGATE_CLASS_SUFFIX);
            delegateServlet = (HttpServlet) delegateServletClass.newInstance();

            // Initialize Servlet delegate
            Thread currentThread = Thread.currentThread();
            ClassLoader oldThreadContextClassLoader = currentThread.getContextClassLoader();
            try {
                currentThread.setContextClassLoader(OXFClassLoader.getClassLoader(webAppExternalContext));
                delegateServlet.init(getServletConfig());
            } finally {
                currentThread.setContextClassLoader(oldThreadContextClassLoader);
            }
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }

    public void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // Delegate to Servlet delegate
        Thread currentThread = Thread.currentThread();
        ClassLoader oldThreadContextClassLoader = currentThread.getContextClassLoader();
        try {
            currentThread.setContextClassLoader(OXFClassLoader.getClassLoader(webAppExternalContext));
            delegateServlet.service(request, response);
        } finally {
            currentThread.setContextClassLoader(oldThreadContextClassLoader);
        }
    }

    public void destroy() {
        // Delegate to Servlet delegate
        Thread currentThread = Thread.currentThread();
        ClassLoader oldThreadContextClassLoader = currentThread.getContextClassLoader();
        try {
            currentThread.setContextClassLoader(OXFClassLoader.getClassLoader(webAppExternalContext));
            delegateServlet.destroy();
        } finally {
            currentThread.setContextClassLoader(oldThreadContextClassLoader);
        }
    }
}
