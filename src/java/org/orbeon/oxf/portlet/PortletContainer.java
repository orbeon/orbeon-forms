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
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.StreamInterceptor;

import javax.portlet.*;
import java.io.Serializable;
import java.util.*;

/**
 *
 */
public class PortletContainer implements Serializable {

    private static final String PORTLET_CONTAINER_SESSION_NAME = "org.orbeon.oxf.portlet-container";

    private static final String PORTLET_PARAMETERS_ATTRIBUTE = "org.orbeon.oxf.portlet-parameters";
    private static final String PORTLET_STATE_PREFIX = "org.orbeon.oxf.portlet-state.";

    private Map portletNameToConfig = new HashMap();
    private List declaredPortlets = new ArrayList();

    private String containerId;
    private boolean initialized;
    private boolean instanciated;
    private Map portletIdToName = new HashMap();
    private Map portletInstances = new HashMap();
    private Map portletIdToState = new HashMap();

    private int currentId = 0;

    public static synchronized PortletContainer instance(ExternalContext externalContext, String containerId) {
        // The PortletContainer is stored into a Map in the session. Each session has an instance
        // of PortletContainer, which means that each user will see a different PortletContainer
        // instance.
        ExternalContext.Session session = externalContext.getSession(true);
        Map portletContainerMap = (Map) session.getAttributesMap().get(PORTLET_CONTAINER_SESSION_NAME);
        if (portletContainerMap == null) {
            portletContainerMap = new HashMap();
            session.getAttributesMap().put(PORTLET_CONTAINER_SESSION_NAME, portletContainerMap);
        }

        PortletContainer portletContainer = (PortletContainer) portletContainerMap.get(containerId);
        if (portletContainer == null) {
            portletContainer = new PortletContainer(containerId);
            portletContainerMap.put(containerId, portletContainer);
        }
        return portletContainer;
    }

    private PortletContainer(String containerId) {
        this.containerId = containerId;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void setInitialized(boolean initialized) {
        this.initialized = initialized;
    }

    /**
     * Make a new portlet known to the container.
     */
    public void declarePortlet(PortletConfigImpl portletConfig) {
        // Store the portlet config and makes sure the name is unique
        String portletName = portletConfig.getPortletName();
        if (portletNameToConfig.get(portletName) != null)
            throw new OXFException("Cannot declare two portlets with the name: " + portletName);
        declaredPortlets.add(portletConfig);
        portletNameToConfig.put(portletName, portletConfig);
    }

    /**
     * Instanciate a portlet. This method may be called serveral times for a
     * given portlet name.
     *
     * @return the newly created portlet id
     */
    public int instanciatePortlet(String portletName, WindowState windowState, PortletMode portletMode) {
        currentId++;
        Integer currentIdInteger = new Integer(currentId);
        portletIdToName.put(currentIdInteger, portletName);
        portletIdToState.put(currentIdInteger, new PortletState(windowState, portletMode));
        return currentId;
    }

    public boolean isInstanciated() {
        return instanciated;
    }

    public void setInstanciated(boolean instanciated) {
        this.instanciated = instanciated;
    }

    /**
     * Return a list of portlet ids in ascending order.
     */
    public List listPortletIds() {

        List ids = new ArrayList(portletIdToName.keySet());
        Collections.sort(ids);

        return ids;
    }

    /**
     * Render a portlet into an OutputParser, given a portet id.
     *
     * The renderParameters Map is optional. If non-null, the parameters override the parameters of
     * the request.
     */
    public void renderPortlet(int portletId, ExternalContext externalContext, StreamInterceptor streamInterceptor, Map renderParameters, String windowState, String portletMode) {

        ExternalContext.Request request = externalContext.getRequest();
        PortletURLImpl.RequestParameters requestParameters = getRequestParameters(request);

        // Find portlet instance for the given id
        Portlet portlet = getPortletInstance(portletId);
        if (portlet == null)
            throw new OXFException("No portlet declared for id: " + portletId);
        PortletConfigImpl portletConfig = getPortletConfig(portletId);
        PortletState portletState = getPortletState(externalContext, portletId);

        // The current portlet is the target of the rendering but not the current target of the action
        // Use parameters from the request
        if (requestParameters.getTargetPortletId() == portletId && !requestParameters.isTargetAction()) {
            // Update window state and portlet mode
            WindowState requestedWindowState = requestParameters.getWindowState(portletId);
            PortletMode requestedPortletMode = requestParameters.getPortletMode(portletId);

            if (requestedWindowState != null)
                portletState.setWindowState(requestedWindowState);
            if (requestedPortletMode != null)
                portletState.setPortletMode(requestedPortletMode);

            // Set parameters
            if (renderParameters == null)
                portletState.setRenderParameters(requestParameters.getUserParameters(portletId));
        }

        // Handle user overrides for this particular portlet
        {
            if (windowState != null)
                portletState.setWindowState(new WindowState(windowState));
            if (portletMode != null)
                portletState.setPortletMode(new PortletMode(portletMode));
            if (renderParameters != null)
                portletState.setRenderParameters(renderParameters);
        }

        // Render the portlet only if its state is not minimized
        if (!portletState.getWindowState().equals(WindowState.MINIMIZED)) {
            // Create request and response
            RenderRequestImpl renderRequest = new RenderRequestImpl(externalContext, portletConfig, portletId, request, portletState);
            RenderResponseImpl renderResponse = new RenderResponseImpl(portletId, externalContext, renderRequest, streamInterceptor);

            try {
                // Render portlet
                portlet.render(renderRequest, renderResponse);

                // NOTE: All this is done after the portlet is rendered. Should make it happen before.
                PortalContext portalContext = renderRequest.getPortalContext();
                {
                    // Create URLs for all relevant window states
                    PortletURL url = renderResponse.createRenderURL();
                    url.setParameters(portletState.getRenderParameters());

                    Map windowStatesURLs = new HashMap();
                    for (Enumeration i = portalContext.getSupportedWindowStates(); i.hasMoreElements();) {
                        WindowState state = (WindowState) i.nextElement();
                        url.setWindowState(state);
                        windowStatesURLs.put(state, url.toString());
                    }
                    portletState.setWindowStatesURLs(windowStatesURLs);
                }

                {
                    // Create URLs for all relevant portlet modes
                    PortletURL url = renderResponse.createRenderURL();
                    url.setParameters(portletState.getRenderParameters());

                    Map portletModesURLs = new HashMap();
                    for (Enumeration i = portalContext.getSupportedPortletModes(); i.hasMoreElements();) {
                        PortletMode mode = (PortletMode) i.nextElement();
                        if (portletConfig.supportsPortletMode(mode)) {
                            url.setPortletMode(mode);
                            portletModesURLs.put(mode, url.toString());
                        }
                    }
                    portletState.setPortletModesURLs(portletModesURLs);
                }

                // Get title
                portletState.setTitle(renderResponse.getTitle());

            } catch (PortletException e) {
                throw new OXFException(e);
            } catch (Exception e) {
                // FIXME, also check when portlet must be disabled
                throw new OXFException(e);
            }
        }
    }

    /**
     * Execute a portlet action, if any.
     * @return true if a redirect was sent
     */
    public boolean processPortletAction(ExternalContext externalContext, PipelineContext pipelineContext) {

        PortletURLImpl.RequestParameters requestParameters = getRequestParameters(externalContext.getRequest());

        // Handle action if necessary
        if (requestParameters.isTargetAction()) {
            int portletId = requestParameters.getTargetPortletId();
            Portlet portlet = getPortletInstance(portletId);
            if (portlet == null)
                throw new OXFException("No portlet declared for id: " + portletId);
            PortletConfigImpl portletConfig = getPortletConfig(portletId);
            PortletState portletState = getPortletState(externalContext, portletId);

            // Handle request parameters. We do NOT set the render parameters at this point
            WindowState requestedWindowState = requestParameters.getWindowState(portletId);
            PortletMode requestedPortletMode = requestParameters.getPortletMode(portletId);
            if (requestedWindowState != null)
                portletState.setWindowState(requestedWindowState);
            if (requestedPortletMode != null)
                portletState.setPortletMode(requestedPortletMode);
            portletState.setActionParameters(requestParameters.getUserParameters(portletId));

            // Create request and response
            ActionRequestImpl actionRequest = new ActionRequestImpl(externalContext, portletConfig, portletId, externalContext.getRequest(), portletState);
            ActionResponseImpl actionResponse = new ActionResponseImpl(portletId, externalContext, actionRequest, requestParameters);

            // Call the Portlet.processAction(). This can modify the request parameters used in Portlet.render().
            try {
                portlet.processAction(actionRequest, actionResponse);

                // Check whether a redirect was asked for
                if (actionResponse.getRedirectLocation() != null) {
                    // Redirect the whole portal
                    externalContext.getResponse().sendRedirect(actionResponse.getRedirectLocation(), null, false, false);
                    return true;
                } else {
                    // Check if the render parameters have changed
                    if (actionResponse.isParametersChanged()) {
                        // If so update the portlet state
                        portletState.setRenderParameters(actionResponse.getParameters());
                    }
                    // Update portlet mode and window state if needed
                    if (actionResponse.getWindowState() != null)
                        portletState.setWindowState(actionResponse.getWindowState());
                    if (actionResponse.getPortletMode() != null)
                        portletState.setPortletMode(actionResponse.getPortletMode());
                }
            } catch (Exception e) {
                // FIXME, also check when portlet must be disabled
                throw new OXFException(e);
            }
        }

        // No redirect was sent
        return false;
    }

    private PortletURLImpl.RequestParameters getRequestParameters(ExternalContext.Request request) {
        Map requestAttributes = request.getAttributesMap();
        PortletURLImpl.RequestParameters requestParameters = (PortletURLImpl.RequestParameters) requestAttributes.get(PORTLET_PARAMETERS_ATTRIBUTE);
        if (requestParameters == null) {
            // Retrieve and store parameters in pipeline context so that somebody else may retrieve them later
            requestParameters = PortletURLImpl.extractParameters(request);
            requestAttributes.put(PORTLET_PARAMETERS_ATTRIBUTE, requestParameters);
        }
        return requestParameters;
    }

    public PortletConfigImpl getPortletConfig(int portletId) {
        return (PortletConfigImpl) portletNameToConfig.get(portletIdToName.get(new Integer(portletId)));
    }

    /**
     * Return an instance of Portlet given a portlet id.
     *
     * If necessary, the portlet is instanciated and initialized. Otherwise an
     * existing instance is returned.
     */
    private Portlet getPortletInstance(int portletId) {
        Integer portletIdInteger = new Integer(portletId);
        Portlet portlet = (Portlet) portletInstances.get(portletIdInteger);

        // Make sure instance exists
        if (portlet == null) {
            PortletConfigImpl config = getPortletConfig(portletId);

            // Config not found
            if (config == null)
                return null;

            try {
                Class portletClass = getClass().getClassLoader().loadClass(config.getClassName());
                portlet = (Portlet) portletClass.newInstance();
            } catch (Exception e) {
                // FIXME
                throw new OXFException(e);
            }
            // Initialize portlet
            try {
                portlet.init(config);
            } catch (Exception e) {
                // FIXME
                throw new OXFException(e);
            }
            // Add to instances only in case of success
            portletInstances.put(portletIdInteger, portlet);
        }
        return portlet;
    }

    public PortletState getPortletState(ExternalContext externalContext, int portletId) {
        // The portlet state is currently stored into the session
        ExternalContext.Session session = externalContext.getSession(true);
        PortletState state = (PortletState) session.getAttributesMap().get(PORTLET_STATE_PREFIX + portletId);
        if (state == null) {
            state = new PortletState(WindowState.NORMAL, PortletMode.VIEW);// FIXME: defaults
            session.getAttributesMap().put(PORTLET_STATE_PREFIX + portletId, state);
        }
        return state;
    }

    /**
     * PortletState represents the state of a portlet between requests.
     */
    public static class PortletState {
        private WindowState windowState;
        private PortletMode portletMode;
        private Map renderParameters = new HashMap();
        private Map actionParameters;

        private Map portletModesURLs;
        private Map windowStatesURLs;

        private String title;

        public PortletState(WindowState windowState, PortletMode portletMode) {
            this.portletMode = portletMode;
            this.windowState = windowState;
        }

        public PortletMode getPortletMode() {
            return portletMode;
        }

        public void setPortletMode(PortletMode portletMode) {
            this.portletMode = portletMode;
        }

        public WindowState getWindowState() {
            return windowState;
        }

        public void setWindowState(WindowState windowState) {
            this.windowState = windowState;
        }

        public Map getRenderParameters() {
            return renderParameters;
        }

        public void setRenderParameters(Map renderParameters) {
            this.renderParameters = renderParameters;
        }

        public Map getActionParameters() {
            return actionParameters;
        }

        public void setActionParameters(Map actionParameters) {
            this.actionParameters = actionParameters;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public Map getWindowStatesURLs() {
            return windowStatesURLs;
        }

        public void setWindowStatesURLs(Map windowStatesURLs) {
            this.windowStatesURLs = windowStatesURLs;
        }

        public void setPortletModesURLs(Map portletModesURLs) {
            this.portletModesURLs = portletModesURLs;
        }

        public Map getPortletModesURLs() {
            return portletModesURLs;
        }
    }
}
