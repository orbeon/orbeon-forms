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
package org.orbeon.oxf.portlet.processor;

import org.dom4j.Document;
import org.dom4j.Node;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.portlet.PortletConfigImpl;
import org.orbeon.oxf.portlet.PortletContainer;
import org.orbeon.oxf.portlet.PortletContextImpl;
import org.orbeon.oxf.processor.*;
import org.orbeon.oxf.resources.ResourceManager;
import org.orbeon.oxf.resources.WebAppResourceManagerImpl;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.XPathUtils;
import org.xml.sax.ContentHandler;

import javax.portlet.PortletMode;
import javax.portlet.WindowState;
import javax.servlet.ServletContext;
import java.util.*;

/**
 * This processor handles the configuration of the portlet container.
 *
 * It reads the standard portlet deployment descriptor and maintains a
 * collection of portlet instances.
 */
public class PortletContainerProcessor extends ProcessorImpl {

    public static final String PORTLET_CONTAINER_NAMESPACE_URI = "http://orbeon.org/oxf/xml/portlet-container";

    private static final String INPUT_PORTAL_CONFIG = "portal-config";
    private static final String INPUT_PORTAL_STATUS = "portal-status";

    public PortletContainerProcessor() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_PORTAL_CONFIG, PORTLET_CONTAINER_NAMESPACE_URI));
        addOutputInfo(new ProcessorInputOutputInfo(INPUT_PORTAL_STATUS));
    }

    public ProcessorOutput createOutput(String name) {
        ProcessorOutput output = new ProcessorImpl.ProcessorOutputImpl(getClass(), name) {
            public void readImpl(PipelineContext pipelineContext, ContentHandler contentHandler) {

                final ExternalContext externalContext = (ExternalContext) pipelineContext.getAttribute(PipelineContext.EXTERNAL_CONTEXT);
                if (externalContext == null)
                    throw new OXFException("Can't find external context in pipeline context");

                // Read the portal configuration
                Config config = (Config) readCacheInputAsObject(pipelineContext, getInputByName(INPUT_PORTAL_CONFIG), new CacheableInputReader() {
                    public Object read(PipelineContext context, ProcessorInput input) {
                        Document configNode = readInputAsDOM4J(context, input);

                        Config config = new Config();

                        // Check whether an embedded "portlet.xml" is present
                        Node portletAppNode = XPathUtils.selectSingleNode(configNode, "/*/portlet-app");
                        if (portletAppNode != null)
                            config.setPortletAppNode(portletAppNode);

                        // Get portal id
                        config.setPortalId(XPathUtils.selectStringValueNormalize(configNode, "/*/portal-id"));

                        // Add portlet instances
                        for (Iterator i = XPathUtils.selectIterator(configNode, "/*/portlet-instance"); i.hasNext();) {
                            Node portletNode = (Node) i.next();

                            String portletName = XPathUtils.selectStringValueNormalize(portletNode, "portlet-name");
                            String windowState = XPathUtils.selectStringValueNormalize(portletNode, "window-state");
                            String portletMode = XPathUtils.selectStringValueNormalize(portletNode, "portlet-mode");

                            config.addPortletInstance(new PortletInstance(portletName, windowState, portletMode));
                        }

                        return config;
                    }
                });

                // Get instance of PortletContainer
                final PortletContainer portletContainer = PortletContainer.instance(externalContext, config.getPortalId());

                // Initialize once if needed
                if (!portletContainer.isInitialized()) {

                    Object nativeContext = externalContext.getNativeContext();
                    if (!(nativeContext instanceof ServletContext))
                        throw new OXFException("This processor can only be used within a Servlet");

                    // Try to initialize with embedded "portlet.xml"
                    Node portletAppNode = config.getPortletAppNode();

                    // Otherwise, read default resource
                    if (portletAppNode == null) {
                        // Initialize our own resource manager to access WEB-INF/portlet.xml
                        Map props = new HashMap();
                        props.put(WebAppResourceManagerImpl.SERVLET_CONTEXT_KEY, nativeContext);
                        props.put(WebAppResourceManagerImpl.ROOT_DIR, "/");
                        ResourceManager resourceManager = new WebAppResourceManagerImpl(props);

                        // Get resource
                        portletAppNode = resourceManager.getContentAsDOM4J("/WEB-INF/portlet.xml").getRootElement();
                    }

                    // Iterate through all portlet configurations
                    for (Iterator i = XPathUtils.selectIterator(portletAppNode, "portlet"); i.hasNext();) {
                        Node portletNode = (Node) i.next();

                        // Create a new configuration for this portlet
                        PortletConfigImpl portletConfig = new PortletConfigImpl();

                        portletConfig.setPortletName(XPathUtils.selectStringValueNormalize(portletNode, "portlet-name"));
                        portletConfig.setClassName(XPathUtils.selectStringValueNormalize(portletNode, "portlet-class"));

                        for (Iterator j = XPathUtils.selectIterator(portletNode, "init-param"); j.hasNext();) {
                            Node initParamNode = (Node) j.next();
                            String name = XPathUtils.selectStringValue(initParamNode, "name");
                            String value = XPathUtils.selectStringValue(initParamNode, "value");
                            portletConfig.setInitParameter(name, value);
                        }

                        for (Iterator j = XPathUtils.selectIterator(portletNode, "supports/mime-type"); j.hasNext();)
                            portletConfig.addMimeType(XPathUtils.selectStringValueNormalize((Node) j.next(), "."));
                        for (Iterator j = XPathUtils.selectIterator(portletNode, "supports/portlet-mode"); j.hasNext();)
                            portletConfig.addPortletMode(XPathUtils.selectStringValueNormalize((Node) j.next(), "."));
                        for (Iterator j = XPathUtils.selectIterator(portletNode, "portlet-preferences/preference"); j.hasNext();) {
                            Node preferenceNode = (Node) j.next();
                            String name = XPathUtils.selectStringValue(preferenceNode, "name");
                            List values = new ArrayList();
                            for (Iterator k = XPathUtils.selectIterator(preferenceNode, "value"); k.hasNext();)
                                values.add(XPathUtils.selectStringValue((Node) k.next(), "."));
                            String[] arrayValues = new String[values.size()];
                            values.toArray(arrayValues);
                            portletConfig.setPreferences(name, arrayValues);
                        }

                        portletConfig.setTitle(XPathUtils.selectStringValueNormalize(portletNode, "portlet-info/title"));
                        portletConfig.setShortTitle(XPathUtils.selectStringValueNormalize(portletNode, "portlet-info/short-title"));
                        portletConfig.setKeywords(XPathUtils.selectStringValueNormalize(portletNode, "portlet-info/keywords"));

                        // Create context and link to config
                        PortletContextImpl portletContext = new PortletContextImpl(externalContext, portletConfig);
                        portletConfig.setPortletContext(portletContext);

                        // Declare this portlet
                        portletContainer.declarePortlet(portletConfig);
                    }

                    // Mark the portlet container as initialized
                    portletContainer.setInitialized(true);
                }

                // Instanciate portlets with window states and portlet modes if needed
                if (!portletContainer.isInstanciated()) {
                    for (Iterator i = config.getPortletInstances().iterator(); i.hasNext();) {
                        PortletInstance portletInstance = (PortletInstance) i.next();
                        portletContainer.instanciatePortlet(portletInstance.portletName,
                                (portletInstance.windowState == null) ? WindowState.NORMAL : new WindowState(portletInstance.windowState),
                                (portletInstance.portletMode == null) ? PortletMode.VIEW : new PortletMode(portletInstance.portletMode));
                    }
                    portletContainer.setInstanciated(true);
                }

                // Process the action if any
                boolean redirected = portletContainer.processPortletAction(pipelineContext, externalContext);

                // Generate portal status output
                ContentHandlerHelper helper = new ContentHandlerHelper(contentHandler);

                helper.startDocument();
                String namespace = ""; // PortletIncludeGenerator.PORTLET_NAMESPACE_URI
                helper.startElement(namespace, "portal-status");

                helper.element("redirected", Boolean.toString(redirected));

                // For each instanciated portlet, show id, config, and state
                for (Iterator i = portletContainer.listPortletIds().iterator(); i.hasNext();) {
                    Integer portletIdInteger = (Integer) i.next();
                    int portletId = portletIdInteger.intValue();

                    PortletConfigImpl portletConfig = portletContainer.getPortletConfig(portletIdInteger.intValue());

                    helper.startElement(namespace, "portlet-instance");

                    // Portlet id
                    helper.element(namespace, "portlet-id", portletId);
                    if (portletConfig != null) {
                        // Configuration parameters
                        helper.element(namespace, "portlet-name", portletConfig.getPortletName());
                        if (portletConfig.getTitle() != null)
                            helper.element(namespace, "title", portletConfig.getTitle());
                        if (portletConfig.getShortTitle() != null)
                            helper.element(namespace, "short-title", portletConfig.getShortTitle());

                        PortletContainer.PortletState portletState = portletContainer.getPortletState(externalContext, portletId);

                        // Current portlet mode and state
                        helper.element(namespace, "portlet-mode", portletState.getPortletMode().toString());
                        helper.element(namespace, "window-state", portletState.getWindowState().toString());

                        // Current render parameters
                        Map renderParameters = portletState.getRenderParameters();
                        if (renderParameters.size() > 0) {
                            helper.startElement(namespace, "render-parameters");
                            for (Iterator j = renderParameters.keySet().iterator(); j.hasNext();) {
                                String name = (String) j.next();
                                String[] values = (String[]) renderParameters.get(name);
                                helper.startElement(namespace, "param");
                                helper.element(namespace, "name", name);
                                for (int k = 0; k < values.length; k++) {
                                    helper.element(namespace, "value", values[k]);
                                }
                                helper.endElement();
                            }
                            helper.endElement();
                        }
                    }

                    helper.endElement();
                }

                helper.endElement();
                helper.endDocument();
            }

        };
        addOutput(name, output);
        return output;
    }

    private static class PortletInstance {
        public String portletName;
        public String windowState;
        public String portletMode;

        public PortletInstance(String portletName, String windowState, String portletMode) {
            this.portletName = portletName;
            this.windowState = windowState;
            this.portletMode = portletMode;
        }
    }

    private static class Config {
        private String portalId;
        private Node portletAppNode;
        private List portletInstances = new ArrayList();

        public String getPortalId() {
            return portalId;
        }

        public void setPortalId(String portalId) {
            this.portalId = portalId;
        }

        public Node getPortletAppNode() {
            return portletAppNode;
        }

        public void setPortletAppNode(Node portletAppNode) {
            this.portletAppNode = portletAppNode;
        }

        public void addPortletInstance(PortletInstance portletInstance) {
            portletInstances.add(portletInstance);
        }

        public List getPortletInstances() {
            return portletInstances;
        }
    }
}
