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

import org.apache.log4j.Logger;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.InitUtils;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.pipeline.api.ProcessorDefinition;
import org.orbeon.oxf.processor.Processor;
import org.orbeon.oxf.processor.ProcessorFactoryRegistry;
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.resources.ClassLoaderResourceManagerImpl;
import org.orbeon.oxf.resources.OXFProperties;
import org.orbeon.oxf.resources.ResourceManager;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.util.PipelineUtils;
import org.orbeon.oxf.util.task.TaskScheduler;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.dom4j.QName;

import javax.naming.Context;
import javax.naming.InitialContext;
import java.io.*;
import java.util.Iterator;

public class ProcessorService {

    // Main processor
    public static final String MAIN_PROCESSOR_PROPERTY_PREFIX = "oxf.main-processor.";
    public static final String MAIN_PROCESSOR_INPUT_PROPERTY_PREFIX = "oxf.main-processor.input.";

    public static final String DEFAULT_PROCESSOR_URI = "oxf/processor/page-flow";
    public static final String DEFAULT_PROCESSOR_INPUT_NAME = "controller";
    public static final String DEFAULT_PROCESSOR_INPUT_URL = "oxf:/page-flow.xml";

    // Error processor
    public static final String ERROR_PROCESSOR_URI_PROPERTY = "oxf.servlet.error.processor";// Suggested new name: oxf.error-processor.uri
    public static final String ERROR_PROCESSOR_INPUT_PROPERTY_PREFIX = "oxf.servlet.error.input.";// Suggested new name: oxf.error-processor.input.

    // Other properties
    public static final String HTTP_FORCE_LAST_MODIFIED_PROPERTY = "oxf.http.force-last-modified";
    public static final String HTTP_FORCE_MUST_REVALIDATE_PROPERTY = "oxf.http.force-must-revalidate";

    public static final String OXF_EXCEPTION = "oxf-exception";

    private static Logger logger = LoggerFactory.createLogger(ProcessorService.class);

    private boolean initialized;

    private Context jndiContext;

    private Processor mainProcessor;
    private Processor errorProcessor;

    public ProcessorService() {
    }

    public synchronized void init(Processor mainProcessor) {
        this.init((ProcessorDefinition)null);
        this.mainProcessor = mainProcessor;
    }

    public synchronized void init(ProcessorDefinition mainProcessorDefinition) {
        if (initialized) {
            logger.debug("ProcessorService is already initialized. Skipping new initialization.");
            return;
        }

        try {
            // Create initial context
            jndiContext = new InitialContext();

            // Create default definition if needed
            if (mainProcessorDefinition == null) {
                mainProcessorDefinition = new ProcessorDefinition();
                mainProcessorDefinition.setUri(DEFAULT_PROCESSOR_URI);
                mainProcessorDefinition.addInput(DEFAULT_PROCESSOR_INPUT_NAME, DEFAULT_PROCESSOR_INPUT_URL);
            }

            // Create and connect main processor
            mainProcessor = InitUtils.createProcessor(mainProcessorDefinition);

            // Create and connect error processor
            // TODO: make this configurable like the main processor
            try {
                QName processorQName = OXFProperties.instance().getPropertySet().getQName(ERROR_PROCESSOR_URI_PROPERTY);
                if (processorQName != null) {
                    errorProcessor = ProcessorFactoryRegistry.lookup(processorQName).createInstance(new PipelineContext());

                    // Add inputs
                    for (Iterator i = OXFProperties.instance().keySet().iterator(); i.hasNext();) {
                        String key = (String) i.next();
                        if (key.startsWith(ERROR_PROCESSOR_INPUT_PROPERTY_PREFIX)) {
                            // It's an input

                            String inputName = key.substring(ERROR_PROCESSOR_INPUT_PROPERTY_PREFIX.length());
                            String inputSrc = OXFProperties.instance().getPropertySet().getStringOrURIAsString(key);

                            Processor urlGenerator = PipelineUtils.createURLGenerator(inputSrc);
                            PipelineUtils.connect(urlGenerator, ProcessorImpl.OUTPUT_DATA, errorProcessor, inputName);
                        }
                    }
                }
            } catch (Exception e) {
                logger.warn("Can't instanciate the error pipeline", e);
            }

            initialized = true;
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    public void service(boolean addClient, ExternalContext externalContext, PipelineContext pipelineContext) {

        // Handle license checking
        ExternalContext.Request request = externalContext.getRequest();
//        if (addClient && request != null)
//            LicenseCheck.instance().addClient(request.getRemoteAddr());

        // NOTE: Should this just be available from the ExternalContext?
        pipelineContext.setAttribute(PipelineContext.JNDI_CONTEXT, jndiContext);

        try {
            // Run the processor
            InitUtils.runProcessor(mainProcessor, externalContext, pipelineContext);
        } catch (Exception e) {
            // Something bad happened
            LocationData locationData = ValidationException.getRootLocationData(e);
            Throwable throwable = OXFException.getRootThrowable(e);
            // Store the exception; needed if we are in a portlet
            request.getAttributesMap().put(OXF_EXCEPTION, e);
            // Try to start the error pipeline if the response has not been committed yet
            try {
                ExternalContext.Response response = externalContext.getResponse();
                if (response != null) {
                    if (!response.isCommitted()) {
                        serviceError(externalContext, throwable, locationData);
                    } else {
                        serviceStaticError(externalContext, throwable, locationData);
                    }
                }
            } catch (IOException ioe) {
                throw new OXFException(ioe);
            }
        }
    }

    private void serviceError(ExternalContext externalContext, Throwable rootException, LocationData locationData) throws IOException {
        if (errorProcessor != null) {
            // Create pipeline context
            PipelineContext pipelineContext = new PipelineContext();
            pipelineContext.setAttribute(PipelineContext.THROWABLE, rootException);
            pipelineContext.setAttribute(PipelineContext.LOCATION_DATA, locationData);
            // NOTE: Should this just be available from the ExternalContext?
            pipelineContext.setAttribute(PipelineContext.JNDI_CONTEXT, jndiContext);
            try {
                // Make sure we generate something clean
                externalContext.getResponse().reset();

                // Run the processor
                InitUtils.runProcessor(errorProcessor, externalContext, pipelineContext);
            } catch (Exception e) {
                // Something bad happened
                serviceStaticError(externalContext, rootException, locationData);
            }
        } else {
            serviceStaticError(externalContext, rootException, locationData);
        }
    }

    private void serviceStaticError(ExternalContext externalContext, Throwable rootException, LocationData locationData) throws IOException {

        StringBuffer sb = new StringBuffer();
        ExternalContext.Response response = externalContext.getResponse();
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
        sb.append("<html><head><title>Presentation Server Error</title>");
        sb.append("<style>");
        Reader styleReader = null;
        try {
            ResourceManager resourceManager = new ClassLoaderResourceManagerImpl(this.getClass());
            styleReader = new InputStreamReader(resourceManager.getContentAsStream("error.css"));
            char[] buffer = new char[1024];
            while (true) {
                int length = styleReader.read(buffer);
                if (length == -1) break;
                sb.append(buffer, 0, length);
            }
        } catch (Exception e) {
            logger.error("Unable to load stylesheet error.css while serving static error page. Resuming.", e);
        } finally {
            styleReader.close();
        }
        sb.append("</style>");
        sb.append("</head>");

        // Title
        sb.append("<body>");
        sb.append("<h1>Presentation Server Error</h1>");
        sb.append("<table class=\"gridtable\">");


        // Message and exception
        sb.append("<tr><th>Type</th><td>")
                .append(rootException.getClass())
                .append("</td></tr>");
        sb.append("<tr><th>Message</th><td>")
                .append(XMLUtils.escapeHTML(rootException.getMessage())).
                append("</td></tr>");
        if (locationData != null) {
            sb.append("<tr><th>Location</th><td>")
                    .append(locationData.getSystemID())
                    .append("</td></tr>");
            sb.append("<tr><th>Line</th><td>")
                    .append(locationData.getLine())
                    .append("</td></tr>");
            sb.append("<tr><th>Column</th><td>")
                    .append(locationData.getCol())
                    .append("</td></tr>");
        }

        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        rootException.printStackTrace(printWriter);
        sb.append("<tr><th valign=\"top\">Stack Trace</th><td><pre>")
                .append(XMLUtils.escapeHTML(stringWriter.toString()))
                .append("</pre></td></tr></table></body></html>");

        // Get a Writer
        PrintWriter writer = null;
        try {
            writer = response.getWriter();
        } catch (IllegalStateException e) {
            // Try an OutputStream if getting the writer failed
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
