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

import org.apache.log4j.Logger;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.Version;
import org.orbeon.oxf.pipeline.InitUtils;
import org.orbeon.oxf.resources.ResourceManagerWrapper;
import org.orbeon.oxf.resources.WebAppResourceManagerImpl;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.xml.TomcatClasspathFix;

import javax.servlet.ServletContext;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * WebAppContext is a singleton that represents context information for OXF
 * unique to an entire Web application.
 *
 * When the instance is created:
 *
 * 1. Initialize a resource manager
 * 2. Initialize Orbeon Forms Properties
 * 3. Initialize logger based on properties
 * 4. Initialize the processor registry
 */
public class WebAppContext {
    
    static {
        try {
            TomcatClasspathFix.applyIfNeedBe();
        } catch (Throwable t) {
            // ignore
        }
    }

    public static final String PROPERTIES_PROPERTY = "oxf.properties";
    public static final String LOGGING_PROPERTY = "oxf.initialize-logging";

    private static WebAppContext instance;
    private static Logger logger = LoggerFactory.createLogger(WebAppContext.class);

    private ServletContext servletContext;
    private Map<String, String> contextInitParameters;

    public static WebAppContext instance() {
        if (instance == null)
            throw new OXFException("Orbeon Forms WebAppContext not initialized. Make sure at least one servlet or context listener is initialized first.");
        return instance;
    }

    /**
     * Initialize the context. This method has to be called at least once before Presentation
     * Server can be used.
     */
    public static synchronized WebAppContext instance(ServletContext servletContext) {
        if (instance == null)
            instance = new WebAppContext(servletContext);
        return instance;
    }

    private WebAppContext(ServletContext servletContext) {
        try {
            // Remember Servlet context
            this.servletContext = servletContext;
            
            // Check whether logging initialization is disabled
            final boolean initializeLogging = !"false".equals(getServletInitParametersMap().get(LOGGING_PROPERTY));

            if (initializeLogging) {
                LoggerFactory.initBasicLogger();
            }
            logger.info("Starting Orbeon Forms Release " + Version.getVersion());

            // 1. Initialize the Resource Manager
            final Map<String, Object> properties = new LinkedHashMap<String, Object>();
            for (final String name: getServletInitParametersMap().keySet()) {
                if (name.startsWith("oxf.resources."))
                    properties.put(name, getServletInitParametersMap().get(name));
            }
            properties.put(WebAppResourceManagerImpl.SERVLET_CONTEXT_KEY, servletContext);
            logger.info("Initializing Resource Manager with: " + properties);

            ResourceManagerWrapper.init(properties);

            // 2. Initialize properties
            final String propertiesFile = getServletInitParametersMap().get(PROPERTIES_PROPERTY);
            if (propertiesFile != null)
                org.orbeon.oxf.properties.Properties.init(propertiesFile);

            // 3. Initialize log4j with a DOMConfiguration
            if (initializeLogging) {
                LoggerFactory.initLogger();
            }

            // 4. Register processor definitions with the default XML Processor Registry
            InitUtils.initializeProcessorDefinitions();

        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    /**
     * Return an unmodifiable Map of the Servlet initialization parameters.
     */
    public Map<String, String> getServletInitParametersMap() {
        if (contextInitParameters == null) {
            synchronized (this) {
                if (contextInitParameters == null) {
                    final Map<String, String> result = new LinkedHashMap<String, String>();
                    for (Enumeration e = servletContext.getInitParameterNames(); e.hasMoreElements();) {
                        final String name = (String) e.nextElement();
                        result.put(name, servletContext.getInitParameter(name));
                    }
                    contextInitParameters = Collections.unmodifiableMap(result);
                }
            }
        }
        return contextInitParameters;
    }
}
