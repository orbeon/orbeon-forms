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
package org.orbeon.oxf.portlet;

import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.InitUtils;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.pipeline.api.ProcessorDefinition;
import org.orbeon.oxf.servlet.ProcessorService;
import org.orbeon.oxf.util.AttributesToMap;
import org.orbeon.oxf.util.NetUtils;

import javax.portlet.*;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

public class OXFPortlet extends GenericPortlet {

    private ProcessorService processorService;

    private static final String OXF_PORTLET_OUTPUT = "org.orbeon.oxf.buffered-response";
    private static final String OXF_PORTLET_OUTPUT_PARAMS = "org.orbeon.oxf.buffered-response-params";

    // Servlet context initialization parameters set in web.xml
    private Map contextInitParameters = null;

    public void init() throws PortletException {
        // NOTE: Here we assume that an Presentation Server WebAppContext context has already
        // been initialized. This can be done by another Servlet or Filter. The only reason we
        // cannot use the WebAppContext appears to be that it has to pass the ServletContext to
        // the resource manager, which uses in turn to read resources from the Web app classloader.

        // Create context initialization parameters Map
        PortletContext portletContext = getPortletContext();
        contextInitParameters = createServletInitParametersMap(portletContext);

        // Try to obtain a local processor definition
        ProcessorDefinition mainProcessorDefinition
                = InitUtils.getDefinitionFromMap(new PortletInitMap(), ProcessorService.MAIN_PROCESSOR_PROPERTY_PREFIX,
                        ProcessorService.MAIN_PROCESSOR_INPUT_PROPERTY_PREFIX);
        // Try to obtain a processor definition from the properties
        if (mainProcessorDefinition == null)
            mainProcessorDefinition = InitUtils.getDefinitionFromProperties(ProcessorService.MAIN_PROCESSOR_PROPERTY_PREFIX,
                    ProcessorService.MAIN_PROCESSOR_INPUT_PROPERTY_PREFIX);
        // Try to obtain a processor definition from the context
        if (mainProcessorDefinition == null)
            mainProcessorDefinition = InitUtils.getDefinitionFromMap(new PortletContextInitMap(portletContext), ProcessorService.MAIN_PROCESSOR_PROPERTY_PREFIX,
                    ProcessorService.MAIN_PROCESSOR_INPUT_PROPERTY_PREFIX);

        try {
            // Create and initialize service
            processorService = new ProcessorService();
            processorService.init(mainProcessorDefinition);
        } catch (Exception e) {
            throw new PortletException(OXFException.getRootThrowable(e));
        }
    }

    public void processAction(ActionRequest request, ActionResponse response) throws PortletException, IOException {
        // If we get a request for an action, we run the service without a
        // response. The result, if any, is stored into a buffer. Otherwise it
        // must be a redirect.
        try {
            PortletExternalContext externalContext = new PortletExternalContext(getPortletContext(), contextInitParameters, request);

            // Make sure the previously cached output is cleared, if there is
            // any. We potentially keep the result of only one action.
            request.getPortletSession().removeAttribute(OXF_PORTLET_OUTPUT);
            request.getPortletSession().removeAttribute(OXF_PORTLET_OUTPUT_PARAMS);

            // Call service
            PipelineContext pipelineContext = new PipelineContext();
            pipelineContext.setAttribute(PipelineContext.PORTLET_CONFIG, getPortletConfig());
            processorService.service(true, externalContext, pipelineContext);

            // Check whether a redirect was issued, or some output was generated
            PortletExternalContext.BufferedResponse bufferedResponse = (PortletExternalContext.BufferedResponse) externalContext.getResponse();
            if (bufferedResponse.isRedirect()) {
                // A redirect was issued

                if (bufferedResponse.isRedirectIsExitPortal()) {
                    // Send a portlet response redirect
                    response.sendRedirect(NetUtils.pathInfoParametersToRelativeURL(bufferedResponse.getRedirectPathInfo(), bufferedResponse.getRedirectParameters()));
                } else {
                    // NOTE: We take the liberty to modify the Map, as nobody will use it anymore
                    Map redirectParameters = bufferedResponse.getRedirectParameters();
                    redirectParameters.put(PortletExternalContext.PATH_PARAMETER_NAME, new String[]{bufferedResponse.getRedirectPathInfo()});

                    // Set the new parameters for the subsequent render requests
                    response.setRenderParameters(redirectParameters);
                }

            } else if (bufferedResponse.isContent()) {
                // Content was written, keep it in the session for subsequent
                // render requests with the current action parameters.

                Map actionParameters = request.getParameterMap();
                response.setRenderParameters(actionParameters);
                PortletSession session = request.getPortletSession();

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

    public void render(RenderRequest request, RenderResponse response) throws PortletException, IOException {
        try {
            PortletExternalContext.BufferedResponse bufferedResponse
                    = (PortletExternalContext.BufferedResponse) request.getPortletSession().getAttribute(OXF_PORTLET_OUTPUT);
            Map bufferedResponseParameters
                    = (Map) request.getPortletSession().getAttribute(OXF_PORTLET_OUTPUT_PARAMS);

            if (bufferedResponse != null && request.getParameterMap().equals(bufferedResponseParameters)) {
                // The result of an action with the current parameters was a
                // stream that we cached. Replay that stream and replace URLs.
                // CHECK: what about mode / state? If they change, we ignore them totally.
                response.setContentType(bufferedResponse.getContentType());
                response.setTitle(bufferedResponse.getTitle() != null ? bufferedResponse.getTitle() : getTitle(request));
                bufferedResponse.write(response);
            } else {
                // Call service
                ExternalContext externalContext = new PortletExternalContext(getPortletContext(), contextInitParameters, request, response);
                PipelineContext pipelineContext = new PipelineContext();
                pipelineContext.setAttribute(PipelineContext.PORTLET_CONFIG, getPortletConfig());
                processorService.service(true, externalContext, pipelineContext);
                // TEMP: The response is also buffered, because our
                // rewriting algorithm only operates on Strings for now.
                PortletExternalContext.DirectResponseTemp directResponse
                        = (PortletExternalContext.DirectResponseTemp) externalContext.getResponse();
                response.setContentType(directResponse.getContentType());
                response.setTitle(directResponse.getTitle() != null ? directResponse.getTitle() : getTitle(request));
                directResponse.write(response);
            }
        } catch (Exception e) {
            throw new PortletException(OXFException.getRootThrowable(e));
        }
    }

    public void destroy() {
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
    public class PortletInitMap extends AttributesToMap {
        public PortletInitMap() {
            super(new Attributeable() {
                public Object getAttribute(String s) {
                    return OXFPortlet.this.getInitParameter(s);
                }

                public Enumeration getAttributeNames() {
                    return OXFPortlet.this.getInitParameterNames();
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

    /**
     * Present a read-only view of the PortletContext initialization parameters as a Map.
     */
    public static class PortletContextInitMap extends AttributesToMap {
        public PortletContextInitMap(final PortletContext portletContext) {
            super(new Attributeable() {
                public Object getAttribute(String s) {
                    return portletContext.getInitParameter(s);
                }

                public Enumeration getAttributeNames() {
                    return portletContext.getInitParameterNames();
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
