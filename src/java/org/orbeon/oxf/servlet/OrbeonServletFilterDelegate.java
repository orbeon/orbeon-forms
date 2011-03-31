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
package org.orbeon.oxf.servlet;

import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.InitUtils;
import org.orbeon.oxf.pipeline.api.*;
import org.orbeon.oxf.processor.ServletFilterGenerator;
import org.orbeon.oxf.util.AttributesToMap;
import org.orbeon.oxf.webapp.ProcessorService;
import org.orbeon.oxf.webapp.WebAppContext;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Enumeration;

public class OrbeonServletFilterDelegate implements Filter {

    private ServletContext servletContext;
    private ProcessorService processorService;

    // Web application context instance
    private WebAppContext webAppContext;

    public void init(FilterConfig config) throws ServletException {

        try {
            // Make sure the Web app context is initialized
            servletContext = config.getServletContext();
            webAppContext = WebAppContext.instance(servletContext);

            // Get main processor definition
            ProcessorDefinition mainProcessorDefinition;
            {
                // Try to obtain a local processor definition
                mainProcessorDefinition
                    = InitUtils.getDefinitionFromMap(new ServletFilterInitMap(config), ProcessorService.MAIN_PROCESSOR_PROPERTY_PREFIX,
                            ProcessorService.MAIN_PROCESSOR_INPUT_PROPERTY_PREFIX);
                // Try to obtain a processor definition from the properties
                if (mainProcessorDefinition == null)
                    mainProcessorDefinition = InitUtils.getDefinitionFromProperties(ProcessorService.MAIN_PROCESSOR_PROPERTY_PREFIX,
                        ProcessorService.MAIN_PROCESSOR_INPUT_PROPERTY_PREFIX);
                // Try to obtain a processor definition from the context
                if (mainProcessorDefinition == null)
                    mainProcessorDefinition = InitUtils.getDefinitionFromServletContext(servletContext, ProcessorService.MAIN_PROCESSOR_PROPERTY_PREFIX,
                        ProcessorService.MAIN_PROCESSOR_INPUT_PROPERTY_PREFIX);
            }
            // Get error processor definition
            ProcessorDefinition errorProcessorDefinition;
            {
                // Try to obtain a local processor definition
                errorProcessorDefinition
                        = InitUtils.getDefinitionFromMap(new ServletFilterInitMap(config), ProcessorService.ERROR_PROCESSOR_PROPERTY_PREFIX,
                                ProcessorService.ERROR_PROCESSOR_INPUT_PROPERTY_PREFIX);
                // Try to obtain a processor definition from the properties
                if (errorProcessorDefinition == null)
                    errorProcessorDefinition = InitUtils.getDefinitionFromProperties(ProcessorService.ERROR_PROCESSOR_PROPERTY_PREFIX,
                            ProcessorService.ERROR_PROCESSOR_INPUT_PROPERTY_PREFIX);
                // Try to obtain a processor definition from the context
                if (errorProcessorDefinition == null)
                    errorProcessorDefinition = InitUtils.getDefinitionFromServletContext(servletContext, ProcessorService.ERROR_PROCESSOR_PROPERTY_PREFIX,
                        ProcessorService.ERROR_PROCESSOR_INPUT_PROPERTY_PREFIX);
            }

            // Create and initialize service
            processorService = new ProcessorService();
            processorService.init(mainProcessorDefinition, errorProcessorDefinition);
        } catch (Exception e) {
            throw new ServletException(OXFException.getRootThrowable(e));
        }
    }

    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {

        // Add filter chain to the pipeline context for use by the ServletFilterGenerator
        final PipelineContext pipelineContext = new PipelineContext();
        pipelineContext.setAttribute(ServletFilterGenerator.FILTER_CHAIN, chain);

        // Process the regular pipeline
        try {
            final ExternalContext externalContext = new ServletExternalContext(servletContext, pipelineContext, webAppContext.getServletInitParametersMap(), (HttpServletRequest) request, (HttpServletResponse) response);
            processorService.service(externalContext, pipelineContext);
        } catch (Exception e) {
            throw new ServletException(OXFException.getRootThrowable(e));
        }
    }

    public void destroy() {
        processorService.destroy();
        processorService = null;
        webAppContext = null;
    }

    /**
     * Present a read-only view of the Servlet initialization parameters as a Map.
     */
    public static class ServletFilterInitMap extends AttributesToMap {
        public ServletFilterInitMap(final FilterConfig config) {
            super(new AttributesToMap.Attributeable() {
                public Object getAttribute(String s) {
                    return config.getInitParameter(s);
                }

                public Enumeration getAttributeNames() {
                    return config.getInitParameterNames();
                }

                public void removeAttribute(String s) {
                    throw new UnsupportedOperationException();
                }

                public void setAttribute(String s, Object o) {
                    throw new UnsupportedOperationException();
                }
            });
        }
    }
}
