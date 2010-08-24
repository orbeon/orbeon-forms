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
package org.orbeon.oxf.portlet;

import org.orbeon.oxf.pipeline.api.WebAppExternalContext;
import org.orbeon.oxf.webapp.OrbeonClassLoader;

import javax.portlet.*;
import java.io.IOException;

/**
 * OPSPortlet and OPSPortletDelegate are the Portlet (JSR-168) entry point of OPS. OPSPortlet simply delegates to
 * OPSPortletDelegate and provides an option of using the OPS Class Loader.
 *
 * Several OPSServlet and OPSPortlet instances can be used in the same Web or Portlet application.
 * They all share the same Servlet context initialization parameters, but each Portlet can be
 * configured with its own main processor and inputs.
 *
 * All OPSServlet and OPSPortlet instances in a given Web application share the same resource
 * manager.
 *
 * WARNING: OPSPortlet must only depend on the Servlet API and the OPS Class Loader.
 */
public class OrbeonPortlet2 extends GenericPortlet {

    // Portlet delegate
    private Portlet delegatePortlet;

    // WebAppExternalContext
    private WebAppExternalContext webAppExternalContext;

    public void init() throws PortletException {
        try {
            // Instantiate WebAppExternalContext
            webAppExternalContext = new PortletWebAppExternalContext(getPortletContext());

            // Instantiate Portlet delegate
            final Class delegatePortletClass = OrbeonClassLoader.getClassLoader(webAppExternalContext).loadClass(OrbeonPortlet2.class.getName() + OrbeonClassLoader.DELEGATE_CLASS_SUFFIX);
            delegatePortlet = (GenericPortlet) delegatePortletClass.newInstance();

            // Initialize Portlet delegate
            final Thread currentThread = Thread.currentThread();
            final ClassLoader oldThreadContextClassLoader = currentThread.getContextClassLoader();
            try {
                currentThread.setContextClassLoader(OrbeonClassLoader.getClassLoader(webAppExternalContext));
                delegatePortlet.init(getPortletConfig());
            } finally {
                currentThread.setContextClassLoader(oldThreadContextClassLoader);
            }
        } catch (Exception e) {
            throw new PortletException(e);
        }
    }

    public void processAction(ActionRequest actionRequest, ActionResponse response) throws PortletException, IOException {
        // Delegate to Portlet delegate
        final Thread currentThread = Thread.currentThread();
        final ClassLoader oldThreadContextClassLoader = currentThread.getContextClassLoader();
        try {
            currentThread.setContextClassLoader(OrbeonClassLoader.getClassLoader(webAppExternalContext));
            delegatePortlet.processAction(actionRequest, response);
        } finally {
            currentThread.setContextClassLoader(oldThreadContextClassLoader);
        }
    }

    public void render(RenderRequest request, RenderResponse response) throws PortletException, IOException {
        // Delegate to Portlet delegate
        final Thread currentThread = Thread.currentThread();
        final ClassLoader oldThreadContextClassLoader = currentThread.getContextClassLoader();
        try {
            currentThread.setContextClassLoader(OrbeonClassLoader.getClassLoader(webAppExternalContext));
            delegatePortlet.render(request, response);
        } finally {
            currentThread.setContextClassLoader(oldThreadContextClassLoader);
        }
    }

    public void destroy() {
        // Delegate to Portlet delegate
        final Thread currentThread = Thread.currentThread();
        final ClassLoader oldThreadContextClassLoader = currentThread.getContextClassLoader();
        try {
            currentThread.setContextClassLoader(OrbeonClassLoader.getClassLoader(webAppExternalContext));
            delegatePortlet.destroy();
        } finally {
            currentThread.setContextClassLoader(oldThreadContextClassLoader);
        }
    }
}
