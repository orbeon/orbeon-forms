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

import org.apache.log4j.Logger;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.Version;
import org.orbeon.oxf.pipeline.InitUtils;
import org.orbeon.oxf.pipeline.api.*;
import org.orbeon.oxf.processor.Processor;
import org.orbeon.oxf.util.AttributesToMap;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.webapp.ProcessorService;

import javax.portlet.*;
import java.io.IOException;
import java.util.*;

/**
 * OrbeonPortlet2 and OrbeonPortlet2Delegate are the Portlet (JSR-268) entry point of Orbeon. OrbeonPortlet2 simply
 * delegates to OrbeonPortlet2Delegate and provides an option of using the Orbeon Class Loader.
 *
 * Several OrbeonServlet and OrbeonPortlet2 instances can be used in the same Web or Portlet application.
 * They all share the same Servlet context initialization parameters, but each Portlet can be
 * configured with its own main processor and inputs.
 *
 * All OrbeonServlet and OrbeonPortlet2 instances in a given Web application share the same resource manager.
 *
 * WARNING: OrbeonPortlet2 must only depend on the Servlet API and the Orbeon Class Loader.
 */
public class OrbeonPortlet2DelegateBase extends GenericPortlet {

    private static final String INIT_PROCESSOR_PROPERTY_PREFIX = "oxf.portlet-initialized-processor.";
    private static final String INIT_PROCESSOR_INPUT_PROPERTY = "oxf.portlet-initialized-processor.input.";
    private static final String DESTROY_PROCESSOR_PROPERTY_PREFIX = "oxf.portlet-destroyed-processor.";
    private static final String DESTROY_PROCESSOR_INPUT_PROPERTY = "oxf.portlet-destroyed-processor.input.";

    private static final String LOG_MESSAGE_PREFIX = "Portlet";

    protected ProcessorService processorService;

    private static final String OXF_PORTLET_OUTPUT = "org.orbeon.oxf.buffered-response";
    private static final String OXF_PORTLET_OUTPUT_PARAMS = "org.orbeon.oxf.buffered-response-params";

    // Servlet context initialization parameters set in web.xml
    private Map contextInitParameters = null;
    public static final String PORTLET_CONFIG = "portlet-config"; // used only for pipelines called within portlets

    public ProcessorService getProcessorService() {
        return processorService;
    }

    public Map<String, String> getContextInitParameters() {
        return contextInitParameters;
    }

    @Override
    public void init() throws PortletException {

        // This is a PE feature
        Version.instance().checkPEFeature("Orbeon Forms portlet");

        // NOTE: Here we assume that an Orbeon Forms WebAppContext context has already
        // been initialized. This can be done by another Servlet or Filter. The only reason we
        // cannot use the WebAppContext appears to be that it has to pass the ServletContext to
        // the resource manager, which uses in turn to read resources from the Web app classloader.

        // Create context initialization parameters Map
        final PortletContext portletContext = getPortletContext();
        contextInitParameters = createServletInitParametersMap(portletContext);

        // Get main processor definition
        ProcessorDefinition mainProcessorDefinition;
        {
            // Try to obtain a local processor definition
            mainProcessorDefinition
                    = InitUtils.getDefinitionFromMap(new PortletInitMap(this), ProcessorService.MAIN_PROCESSOR_PROPERTY_PREFIX,
                            ProcessorService.MAIN_PROCESSOR_INPUT_PROPERTY_PREFIX);
            // Try to obtain a processor definition from the properties
            if (mainProcessorDefinition == null)
                mainProcessorDefinition = InitUtils.getDefinitionFromProperties(ProcessorService.MAIN_PROCESSOR_PROPERTY_PREFIX,
                        ProcessorService.MAIN_PROCESSOR_INPUT_PROPERTY_PREFIX);
            // Try to obtain a processor definition from the context
            if (mainProcessorDefinition == null)
                mainProcessorDefinition = InitUtils.getDefinitionFromMap(new PortletContextInitMap(portletContext), ProcessorService.MAIN_PROCESSOR_PROPERTY_PREFIX,
                        ProcessorService.MAIN_PROCESSOR_INPUT_PROPERTY_PREFIX);
        }
        // Get error processor definition
        ProcessorDefinition errorProcessorDefinition;
        {
            // Try to obtain a local processor definition
            errorProcessorDefinition
                    = InitUtils.getDefinitionFromMap(new PortletInitMap(this), ProcessorService.ERROR_PROCESSOR_PROPERTY_PREFIX,
                            ProcessorService.ERROR_PROCESSOR_INPUT_PROPERTY_PREFIX);
            // Try to obtain a processor definition from the properties
            if (errorProcessorDefinition == null)
                errorProcessorDefinition = InitUtils.getDefinitionFromProperties(ProcessorService.ERROR_PROCESSOR_PROPERTY_PREFIX,
                        ProcessorService.ERROR_PROCESSOR_INPUT_PROPERTY_PREFIX);
            // Try to obtain a processor definition from the context
            if (errorProcessorDefinition == null)
                errorProcessorDefinition = InitUtils.getDefinitionFromMap(new PortletContextInitMap(portletContext), ProcessorService.ERROR_PROCESSOR_PROPERTY_PREFIX,
                        ProcessorService.ERROR_PROCESSOR_INPUT_PROPERTY_PREFIX);
        }

        try {
            // Create and initialize service
            processorService = new ProcessorService();
            processorService.init(mainProcessorDefinition, errorProcessorDefinition);
        } catch (Exception e) {
            throw new PortletException(OXFException.getRootThrowable(e));
        }

        // Run listeners
        try {
            runListenerProcessor(new PortletInitMap(this), new PortletContextInitMap(portletContext), ProcessorService.logger,
                    LOG_MESSAGE_PREFIX, "Portlet initialized.", INIT_PROCESSOR_PROPERTY_PREFIX, INIT_PROCESSOR_INPUT_PROPERTY);
        } catch (Exception e) {
            ProcessorService.logger.error(LOG_MESSAGE_PREFIX + " - Exception when running Portlet initialization processor.", OXFException.getRootThrowable(e));
            throw new OXFException(e);
        }
    }

    private void runListenerProcessor(Map localMap, Map contextMap,
                                             Logger logger, String logMessagePrefix, String message,
                                             String uriNamePropertyPrefix, String processorInputProperty) throws Exception {
        // Log message if provided
        if (message != null)
            logger.info(logMessagePrefix + " - " + message);

        ProcessorDefinition processorDefinition = null;
        // Try to obtain a local processor definition
        if (localMap != null) {
            processorDefinition = InitUtils.getDefinitionFromMap(localMap, uriNamePropertyPrefix, processorInputProperty);
        }

        // Try to obtain a processor definition from the properties
        if (processorDefinition == null)
            processorDefinition = InitUtils.getDefinitionFromProperties(uriNamePropertyPrefix, processorInputProperty);

        // Try to obtain a processor definition from the context
        if (processorDefinition == null)
            processorDefinition = InitUtils.getDefinitionFromMap(contextMap, uriNamePropertyPrefix, processorInputProperty);

        // Create and run processor
        if (processorDefinition != null) {
            logger.info(logMessagePrefix + " - About to run processor: " +  processorDefinition.toString());
            final Processor processor = InitUtils.createProcessor(processorDefinition);

            final ExternalContext externalContext = new PortletContextExternalContext(getPortletContext());
            InitUtils.runProcessor(processor, externalContext, new PipelineContext(), logger);

        }
        // Otherwise, just don't do anything
    }

    @Override
    public void processAction(ActionRequest actionRequest, ActionResponse response) throws PortletException, IOException {
        // If we get a request for an action, we run the service without a
        // response. The result, if any, is stored into a buffer. Otherwise it
        // must be a redirect.
        try {
            // Make sure the previously cached output is cleared, if there is
            // any. We potentially keep the result of only one action.
            actionRequest.getPortletSession().removeAttribute(OXF_PORTLET_OUTPUT);
            actionRequest.getPortletSession().removeAttribute(OXF_PORTLET_OUTPUT_PARAMS);

            // Call service
            final PipelineContext pipelineContext = new PipelineContext();
            pipelineContext.setAttribute(PORTLET_CONFIG, getPortletConfig());
            final Portlet2ExternalContext externalContext = new Portlet2ExternalContext(pipelineContext, getPortletContext(), contextInitParameters, actionRequest);
            try {

                processorService.service(externalContext, pipelineContext);
            } finally {

            }

            // Check whether a redirect was issued, or some output was generated
            Portlet2ExternalContext.BufferedResponse bufferedResponse = (Portlet2ExternalContext.BufferedResponse) externalContext.getResponse();
            if (bufferedResponse.isRedirect()) {
                // A redirect was issued

                if (bufferedResponse.isRedirectIsExitPortal()) {
                    // Send a portlet response redirect
                    response.sendRedirect(NetUtils.pathInfoParametersToPathInfoQueryString(bufferedResponse.getRedirectPathInfo(), bufferedResponse.getRedirectParameters()));
                } else {
                    // NOTE: We take the liberty to modify the Map, as nobody will use it anymore
                    Map redirectParameters = bufferedResponse.getRedirectParameters();
                    if (redirectParameters == null)
                        redirectParameters = new HashMap();
                    redirectParameters.put(OrbeonPortletXFormsFilter.PATH_PARAMETER_NAME, new String[]{bufferedResponse.getRedirectPathInfo()});

                    // Set the new parameters for the subsequent render requests
                    response.setRenderParameters(redirectParameters);
                }

            } else if (bufferedResponse.isContent()) {
                // Content was written, keep it in the session for subsequent
                // render requests with the current action parameters.

                Map actionParameters = actionRequest.getParameterMap();
                response.setRenderParameters(actionParameters);
                PortletSession session = actionRequest.getPortletSession();

                session.setAttribute(OXF_PORTLET_OUTPUT, bufferedResponse);
                session.setAttribute(OXF_PORTLET_OUTPUT_PARAMS, actionParameters);
            } else {
                // Nothing happened, throw an exception (or should we just ignore?)
                throw new IllegalStateException("Processor execution did not return content or issue a redirect.");
            }
        } catch (Exception e) {
            throw new PortletException(OXFException.getRootThrowable(e));
        }
    }

    @Override
    public void render(RenderRequest request, RenderResponse response) throws PortletException, IOException {
        try {
            final Portlet2ExternalContext.BufferedResponse bufferedResponse
                    = (Portlet2ExternalContext.BufferedResponse) request.getPortletSession().getAttribute(OXF_PORTLET_OUTPUT);
            final Map bufferedResponseParameters
                    = (Map) request.getPortletSession().getAttribute(OXF_PORTLET_OUTPUT_PARAMS);

            // NOTE: Compare for deep equality as it seems that portlet containers may copy/update the parameter Map
			if (bufferedResponse != null && deepEquals(request.getParameterMap(), bufferedResponseParameters)) {
                // The result of an action with the current parameters was a
                // stream that we cached. Replay that stream and replace URLs.
                // CHECK: what about mode / state? If they change, we ignore them totally.
                if (bufferedResponse.getContentType() != null)// NPE in portal otherwise
                    response.setContentType(bufferedResponse.getContentType());
                response.setTitle(bufferedResponse.getTitle() != null ? bufferedResponse.getTitle() : getTitle(request));
                bufferedResponse.write(response);
            } else {
                final // Call service
                PipelineContext pipelineContext = new PipelineContext();
                pipelineContext.setAttribute(PORTLET_CONFIG, getPortletConfig());
                final ExternalContext externalContext = new Portlet2ExternalContext(pipelineContext, getPortletContext(), contextInitParameters, request, response);
                processorService.service(externalContext, pipelineContext);
                // TEMP: The response is also buffered, because our
                // rewriting algorithm only operates on Strings for now.
                final Portlet2ExternalContext.DirectResponseTemp directResponse
                        = (Portlet2ExternalContext.DirectResponseTemp) externalContext.getResponse();
                if (directResponse.getContentType() != null)// NPE in portal otherwise
                    response.setContentType(directResponse.getContentType());
                response.setTitle(directResponse.getTitle() != null ? directResponse.getTitle() : getTitle(request));
                directResponse.write(response);
            }
        } catch (Exception e) {
            throw new PortletException(OXFException.getRootThrowable(e));
        }
    }

    // JSR-286 method
    @Override
    public void serveResource(ResourceRequest request, ResourceResponse response) throws PortletException, IOException {
        try {
            // Call service
            final PipelineContext pipelineContext = new PipelineContext();
            pipelineContext.setAttribute(PORTLET_CONFIG, getPortletConfig());
            final ExternalContext externalContext = new Portlet2ExternalContext(pipelineContext, getPortletContext(), contextInitParameters, request, response);
            processorService.service(externalContext, pipelineContext);
            // TEMP: The response is also buffered, because our
            // rewriting algorithm only operates on Strings for now.
            final Portlet2ExternalContext.DirectResponseTemp directResponse
                    = (Portlet2ExternalContext.DirectResponseTemp) externalContext.getResponse();
            if (directResponse.getContentType() != null)// NPE in portal otherwise
                response.setContentType(directResponse.getContentType());
            directResponse.write(response);
        } catch (Exception e) {
            throw new PortletException(OXFException.getRootThrowable(e));
        }
    }

    /**
	 * Checking two maps for deep equality.
	 */
    private static boolean deepEquals(Map map1, Map map2) {

        if ((map1 == null) && (map2 == null)) {
            return true;
        }

        if ((map1 == null) || (map2 == null)) {
            return false;
        }

        final Set keySet1 = map1.keySet();
        final Set keySet2 = map2.keySet();

        if (keySet1.size() != keySet2.size()) {
            return false;
        }

        final Iterator it1 = keySet1.iterator();
        final Iterator it2 = keySet1.iterator();

        while (it1.hasNext()) {

            final Object key1 = it1.next();
            final Object value1 = map1.get(key1);
            final Object key2 = it2.next();
            final Object value2 = map2.get(key2);

            if (!key1.getClass().getName().equals(key2.getClass().getName())) {
                return false;
            }

            if (!value1.getClass().getName().equals(value2.getClass().getName())) {
                return false;
            }

            if (value1 instanceof Object[]) {
                if (!java.util.Arrays.equals((Object[]) value1, (Object[]) value2)) {
                    return false;
                }
            } else {
                if (!value1.equals(value2)) {
                    return false;
                }
            }
        }

        return true;
    }

    @Override
    public void destroy() {

        // Run listeners
        try {
            runListenerProcessor(new PortletInitMap(this), new PortletContextInitMap(getPortletContext()), ProcessorService.logger, LOG_MESSAGE_PREFIX,
                    "Portlet destroyed.", DESTROY_PROCESSOR_PROPERTY_PREFIX, DESTROY_PROCESSOR_INPUT_PROPERTY);
        } catch (Exception e) {
            ProcessorService.logger.error(LOG_MESSAGE_PREFIX + " - Exception when running Portlet destruction processor.", OXFException.getRootThrowable(e));
            throw new OXFException(e);
        }

        processorService.destroy();
    }

    /**
     * Return an unmodifiable Map of the Servlet initialization parameters.
     */
    public static Map createServletInitParametersMap(PortletContext portletContext) {
        Map result = new HashMap();
        for (Enumeration e = portletContext.getInitParameterNames(); e.hasMoreElements();) {
            String name = (String) e.nextElement();
            result.put(name, portletContext.getInitParameter(name));
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * Present a read-only view of the Portlet initialization parameters as a Map.
     */
    public class PortletInitMap extends AttributesToMap<String> {
        public PortletInitMap(final OrbeonPortlet2DelegateBase portletDelegate) {
            super(new Attributeable<String>() {
                public String getAttribute(String s) {
                    return portletDelegate.getInitParameter(s);
                }

                public Enumeration getAttributeNames() {
                    return portletDelegate.getInitParameterNames();
                }

                public void removeAttribute(String s) {
                    throw new UnsupportedOperationException();
                }

                public void setAttribute(String s, String o) {
                    throw new UnsupportedOperationException();
                }
            });
        }
    }

    /**
     * Present a read-only view of the PortletContext initialization parameters as a Map.
     */
    public static class PortletContextInitMap extends AttributesToMap<String> {
        public PortletContextInitMap(final PortletContext portletContext) {
            super(new Attributeable<String>() {
                public String getAttribute(String s) {
                    return portletContext.getInitParameter(s);
                }

                public Enumeration getAttributeNames() {
                    return portletContext.getInitParameterNames();
                }

                public void removeAttribute(String s) {
                    throw new UnsupportedOperationException();
                }

                public void setAttribute(String s, String o) {
                    throw new UnsupportedOperationException();
                }
            });
        }
    }
}
