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

import org.apache.log4j.Logger;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.Version;
import org.orbeon.oxf.pipeline.InitUtils;
import org.orbeon.oxf.resources.OXFProperties;
import org.orbeon.oxf.resources.ResourceManagerWrapper;
import org.orbeon.oxf.resources.WebAppResourceManagerImpl;
import org.orbeon.oxf.util.LoggerFactory;

import javax.servlet.ServletContext;
import java.util.*;

/**
 * WebAppContext is a singleton that represents context information for OXF
 * unique to an entire Web application.
 *
 * When the instance is created:
 *
 * 1. Initialize a resource manager
 * 2. Initialize OXF Properties
 * 3. Initialize logger based on properties
 * 4. Initialize the processor registry
 */
public class WebAppContext {

    public static final String PROPERTIES_PROPERTY = "oxf.properties";

    private static WebAppContext instance;
    private static Logger logger = LoggerFactory.createLogger(WebAppContext.class);

    private ServletContext servletContext;
    private Map contextInitParameters;

    public static WebAppContext instance() {
        if (instance == null)
            throw new OXFException("OXF WebAppContext not initialized. Make sure at least one servlet or context listener is initialized first.");
        return instance;
    }

    /**
     * Initialize the context. This method has to be called at least once before
     * OXF can be used.
     */
    public static synchronized WebAppContext instance(ServletContext servletContext) {
        if (instance == null)
            instance = new WebAppContext(servletContext);
        return instance;
    }

    private WebAppContext(ServletContext servletContext) {
        try {
            logger.info("Starting OXF Release " + Version.getVersion());

            // Remember Servlet context
            this.servletContext = servletContext;

            // 1. Initialize the Resource Manager
            Map properties = new HashMap();
            for (Iterator i = getServletInitParametersMap().keySet().iterator(); i.hasNext();) {
                String name = (String) i.next();
                if (name.startsWith("oxf.resources."))
                    properties.put(name, getServletInitParametersMap().get(name));
            }
            properties.put(WebAppResourceManagerImpl.SERVLET_CONTEXT_KEY, servletContext);
            logger.info("Initializing Resource Manager with: " + properties);

            ResourceManagerWrapper.init(properties);

            // 2. Initialize properties
            String propertiesFile = (String) getServletInitParametersMap().get(PROPERTIES_PROPERTY);
            if (propertiesFile != null)
                OXFProperties.init(propertiesFile);

            // 3. Initialize log4j with a DOMConfiguration
            LoggerFactory.initLogger();

            // 4. Register processor definitions with the default XML Processor Registry
            InitUtils.initializeProcessorDefinitions();

        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    /**
     * Return an unmodifiable Map of the Servlet initialization parameters.
     */
    public Map getServletInitParametersMap() {
        if (contextInitParameters == null) {
            synchronized (this) {
                if (contextInitParameters == null) {
                    Map result = new HashMap();
                    for (Enumeration e = servletContext.getInitParameterNames(); e.hasMoreElements();) {
                        String name = (String) e.nextElement();
                        result.put(name, servletContext.getInitParameter(name));
                    }
                    contextInitParameters = Collections.unmodifiableMap(result);
                }
            }
        }
        return contextInitParameters;
    }
}
