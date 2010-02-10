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
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.InitUtils;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.pipeline.api.ProcessorDefinition;
import org.orbeon.oxf.processor.Processor;
import org.orbeon.oxf.resources.ClassLoaderResourceManagerImpl;
import org.orbeon.oxf.resources.ResourceManager;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.util.StringBuilderWriter;
import org.orbeon.oxf.util.task.TaskScheduler;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;

import javax.naming.Context;
import javax.naming.InitialContext;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.util.HashMap;

public class ProcessorService {

    // Main processor
    public static final String MAIN_PROCESSOR_PROPERTY_PREFIX = "oxf.main-processor.";
    public static final String MAIN_PROCESSOR_INPUT_PROPERTY_PREFIX = "oxf.main-processor.input.";

    // Error processor
    public static final String ERROR_PROCESSOR_PROPERTY_PREFIX = "oxf.error-processor.";
    public static final String ERROR_PROCESSOR_INPUT_PROPERTY_PREFIX = "oxf.error-processor.input.";

    // Other properties
    public static final String HTTP_FORCE_LAST_MODIFIED_PROPERTY = "oxf.http.force-last-modified";
    public static final String HTTP_FORCE_MUST_REVALIDATE_PROPERTY = "oxf.http.force-must-revalidate";

    public static final String HTTP_ACCEPT_METHODS_PROPERTY = "oxf.http.accept-methods";

    public static final String OXF_EXCEPTION = "oxf-exception";

    public static Logger logger = LoggerFactory.createLogger(ProcessorService.class);

    private boolean initialized;

    private Context jndiContext;

    private Processor mainProcessor;
    private Processor errorProcessor;

    public ProcessorService() {
    }

    public synchronized void init(ProcessorDefinition mainProcessorDefinition, ProcessorDefinition errorProcessorDefinition) {
        if (initialized) {
            logger.debug("ProcessorService is already initialized. Skipping new initialization.");
            return;
        }

        try {
            // Create initial context
            jndiContext = new InitialContext();

            // Create and connect main processor
            mainProcessor = InitUtils.createProcessor(mainProcessorDefinition);

            // Create and connect error processor if specified
            if (errorProcessorDefinition != null)
                errorProcessor = InitUtils.createProcessor(errorProcessorDefinition);

            initialized = true;
        } catch (Throwable e) {
            throw new OXFException(e);
        }
    }

    public void service(boolean addClient, ExternalContext externalContext, PipelineContext pipelineContext) {

        // NOTE: Should this just be available from the ExternalContext?
        pipelineContext.setAttribute(PipelineContext.JNDI_CONTEXT, jndiContext);

        try {
            // Run the processor
            InitUtils.runProcessor(mainProcessor, externalContext, pipelineContext, logger);
        } catch (Throwable e) {
            // Something bad happened
            // Store the exception; needed if we are in a portlet
            ExternalContext.Request request = externalContext.getRequest();
            request.getAttributesMap().put(OXF_EXCEPTION, e);
            // Try to start the error pipeline if the response has not been committed yet
            try {
                ExternalContext.Response response = externalContext.getResponse();
                if (response != null) {
                    if (!response.isCommitted()) {
                        serviceError(externalContext, e);
                    } else {
                        serviceStaticError(externalContext, e);
                    }
                }
            } catch (IOException ioe) {
                throw new OXFException(ioe);
            }
        }
    }

    private void serviceError(ExternalContext externalContext, Throwable throwable) throws IOException {
        if (errorProcessor != null) {
            // Create pipeline context
            PipelineContext pipelineContext = new PipelineContext();
            pipelineContext.setAttribute(PipelineContext.THROWABLE, throwable);
//            pipelineContext.setAttribute(PipelineContext.LOCATION_DATA, locationData);
            // NOTE: Should this just be available from the ExternalContext?
            pipelineContext.setAttribute(PipelineContext.JNDI_CONTEXT, jndiContext);
            try {
                // Make sure we generate something clean
                externalContext.getResponse().reset();

                // Run the processor
                InitUtils.runProcessor(errorProcessor, externalContext, pipelineContext, logger);
            } catch (Throwable e) {
                // Something bad happened
                logger.error(e);
                serviceStaticError(externalContext, throwable);
            }
        } else {
            serviceStaticError(externalContext, throwable);
        }
    }

    private void serviceStaticError(ExternalContext externalContext, Throwable throwable) throws IOException {

        // Get root exception information
        final Throwable rootThrowable = OXFException.getRootThrowable(throwable);
        final LocationData rootLocationData = ValidationException.getRootLocationData(throwable);

        final StringBuffer sb = new StringBuffer();
        final ExternalContext.Response response = externalContext.getResponse();
        if (!response.isCommitted()) {
            // Send new headers and HTML prologue
            response.reset();
            response.setContentType("text/html");
            response.setStatus(ExternalContext.SC_INTERNAL_SERVER_ERROR);
        } else {
            // Try to close table that may still be open
            sb.append("</p></table></table></table></table></table>");
        }

        // Head
        sb.append("<html><head><title>Orbeon Forms Error</title>");
        sb.append("<style>");
        Reader styleReader = null;
        try {
            ResourceManager resourceManager = new ClassLoaderResourceManagerImpl(new HashMap(), this.getClass());
            styleReader = new InputStreamReader(resourceManager.getContentAsStream("error.css"));
            char[] buffer = new char[1024];
            while (true) {
                int length = styleReader.read(buffer);
                if (length == -1) break;
                sb.append(buffer, 0, length);
            }
        } catch (Throwable e) {
            logger.error("Unable to load stylesheet error.css while serving static error page. Resuming.", e);
        } finally {
            if (styleReader != null) styleReader.close();
        }
        sb.append("</style>");
        sb.append("</head>");

        // Title
        sb.append("<body>");
        sb.append("<h1>Orbeon Forms Error</h1>");
        sb.append("<table class=\"gridtable\">");


        // Message and exception
        sb.append("<tr><th>Type</th><td>")
                .append(rootThrowable.getClass())
                .append("</td></tr>");
        sb.append("<tr><th>Message</th><td>")
                .append(XMLUtils.escapeHTML(rootThrowable.getMessage())).
                append("</td></tr>");
        if (rootLocationData != null) {
            sb.append("<tr><th>Location</th><td>")
                    .append(rootLocationData.getSystemID())
                    .append("</td></tr>");
            sb.append("<tr><th>Line</th><td>")
                    .append(rootLocationData.getLine())
                    .append("</td></tr>");
            sb.append("<tr><th>Column</th><td>")
                    .append(rootLocationData.getCol())
                    .append("</td></tr>");
        }

        final StringBuilderWriter StringBuilderWriter = new StringBuilderWriter();
        final PrintWriter printWriter = new PrintWriter(StringBuilderWriter);
        rootThrowable.printStackTrace(printWriter);
        sb.append("<tr><th valign=\"top\">Stack Trace</th><td><pre>")
                .append(XMLUtils.escapeHTML(StringBuilderWriter.toString()))
                .append("</pre></td></tr></table></body></html>");

        // Get a Writer
        PrintWriter writer = null;
        try {
            writer = response.getWriter();
        } catch (IllegalStateException e) {
            // Try an OutputStream if getting the writer failed
            // TODO: This uses the platform's default encoding, which is not good
            writer = new PrintWriter(response.getOutputStream());
        }
        writer.print(sb.toString());
    }

    public synchronized void destroy() {
        try {
            TaskScheduler.getInstance().cancelAll(false);
            TaskScheduler.shutdown();
            initialized = false;
        } catch (NoClassDefFoundError error) {
            // Ignore error: this can happen if using JDK 1.3 (scheduling classed not available)
            // and also happens from time to time on Tomcat 4.1.18 with JDK 1.4.2 (not sure why).
        }
    }
}
