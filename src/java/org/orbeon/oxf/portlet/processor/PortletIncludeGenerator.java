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

import org.apache.log4j.Logger;
import org.dom4j.Document;
import org.dom4j.Node;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.portlet.PortletConfigImpl;
import org.orbeon.oxf.portlet.PortletContainer;
import org.orbeon.oxf.processor.*;
import org.orbeon.oxf.processor.generator.TidyConfig;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.XPathUtils;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import javax.portlet.PortletMode;
import javax.portlet.WindowState;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class PortletIncludeGenerator extends ProcessorImpl {

    private static Logger logger = LoggerFactory.createLogger(PortletIncludeGenerator.class);

    public static final String PORTLET_INCLUDE_NAMESPACE_URI = "http://www.orbeon.org/oxf/portlet-include";
    public static final String PORTLET_NAMESPACE_URI = "http://www.orbeon.org/oxf/portlet";

    public PortletIncludeGenerator() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG, PORTLET_INCLUDE_NAMESPACE_URI));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    public ProcessorOutput createOutput(String name) {
        ProcessorOutput output = new ProcessorImpl.ProcessorOutputImpl(getClass(), name) {
            public void readImpl(PipelineContext pipelineContext, ContentHandler contentHandler) {
                // Read config
                final Config config = (Config) readCacheInputAsObject(pipelineContext, getInputByName(INPUT_CONFIG), new CacheableInputReader() {
                    public Object read(PipelineContext context, ProcessorInput input) {
                        Document configNode = readInputAsDOM4J(context, input);

                        // Get portal id
                        String portalId = XPathUtils.selectStringValueNormalize(configNode, "/*/portal-id");

                        int[] portletIds = new int[XPathUtils.selectIntegerValue(configNode, "count(/*/portlet-id)").intValue()];

                        // Get portlet ids [NOTE: The config has now been changed so that only one portlet id can be set]
                        int index = 0;
                        for (Iterator i = XPathUtils.selectIterator(configNode, "/*/portlet-id"); i.hasNext();) {
                            Node node = (Node) i.next();
                            int portletId = XPathUtils.selectIntegerValue(node, ".").intValue();
                            portletIds[index++] = portletId;
                        }

                        // Get override render parameters if any
                        Map renderParameters = null;
                        if (XPathUtils.selectBooleanValue(configNode, "/*/render-parameters/param").booleanValue()) {
                            renderParameters = new HashMap();
                            for (Iterator i = XPathUtils.selectIterator(configNode, "/*/render-parameters/param"); i.hasNext();) {
                                Node node = (Node) i.next();
                                String name = XPathUtils.selectStringValue(node, "name");
                                String value = XPathUtils.selectStringValue(node, "value");
                                NetUtils.addValueToStringArrayMap(renderParameters, name, value);
                            }
                        }

                        String windowState = XPathUtils.selectStringValueNormalize(configNode, "/*/window-state");
                        String portletMode = XPathUtils.selectStringValueNormalize(configNode, "/*/portlet-mode");

                        // Get Tidy config (will only apply if content-type is text/html)
                        TidyConfig tidyConfig = new TidyConfig(XPathUtils.selectSingleNode(configNode, "/config/tidy-options"));

                        return new Config(portalId, portletIds, tidyConfig, renderParameters, windowState, portletMode);
                    }
                });

                ExternalContext externalContext = (ExternalContext) pipelineContext.getAttribute(PipelineContext.EXTERNAL_CONTEXT);
                if (externalContext == null)
                    throw new OXFException("Can't find external context in pipeline context");

                PortletContainer portletContainer = PortletContainer.instance(externalContext, config.getPortalId());
                try {

                    contentHandler.startDocument();
                    contentHandler.startPrefixMapping("portlet", PORTLET_NAMESPACE_URI);
                    contentHandler.startElement(PORTLET_NAMESPACE_URI, "portlets", "portlet:portlets", XMLUtils.EMPTY_ATTRIBUTES);

                    // Output portlet fragments
                    // [NOTE: The config has now been changed so that only one portlet id can be set]
                    for (int i = 0; i < config.getPortletIds().length; i++) {
                        int portletId = config.getPortletIds()[i];

                        StreamInterceptor streamInterceptor = new StreamInterceptor();
                        portletContainer.renderPortlet(pipelineContext, portletId, externalContext, streamInterceptor, config.getRenderParameters(), config.getWindowState(), config.getPortletMode());

                        PortletContainer.PortletState portletState = portletContainer.getPortletState(externalContext, portletId);
                        WindowState windowState = portletState.getWindowState();
                        PortletMode portletMode = portletState.getPortletMode();

                        PortletConfigImpl portletConfig = portletContainer.getPortletConfig(portletId);

                        AttributesImpl atts = new AttributesImpl();
                        atts.addAttribute("", "id", "id", "CDATA", Integer.toString(portletId));
                        atts.addAttribute("", "name", "name", "CDATA", portletConfig.getPortletName());
                        if (portletConfig.getTitle() != null)
                            atts.addAttribute("", "title", "title", "CDATA", portletConfig.getTitle());
                        if (portletConfig.getShortTitle() != null)
                            atts.addAttribute("", "short-title", "short-title", "CDATA", portletConfig.getShortTitle());
                        atts.addAttribute("", "window-state", "window-state", "CDATA", windowState.toString());
                        atts.addAttribute("", "portlet-mode", "portlet-mode", "CDATA", portletMode.toString());

                        contentHandler.startElement(PORTLET_NAMESPACE_URI, "portlet", "portlet:portlet", atts);

                        // Output window states URLs
                        {
                            Map windowStatesURLs = portletState.getWindowStatesURLs();
                            if (windowStatesURLs != null) {
                                contentHandler.startElement(PORTLET_NAMESPACE_URI, "window-states", "portlet:window-states", XMLUtils.EMPTY_ATTRIBUTES);
                                for (Iterator j = windowStatesURLs.keySet().iterator(); j.hasNext();) {
                                    WindowState state = (WindowState) j.next();

                                    contentHandler.startElement(PORTLET_NAMESPACE_URI, "window-state", "portlet:window-state", XMLUtils.EMPTY_ATTRIBUTES);

                                    contentHandler.startElement(PORTLET_NAMESPACE_URI, "name", "portlet:name", XMLUtils.EMPTY_ATTRIBUTES);
                                    char[] chars = state.toString().toCharArray();
                                    contentHandler.characters(chars, 0, chars.length);
                                    contentHandler.endElement(PORTLET_NAMESPACE_URI, "name", "portlet:name");

                                    contentHandler.startElement(PORTLET_NAMESPACE_URI, "url", "portlet:url", XMLUtils.EMPTY_ATTRIBUTES);
                                    chars = ((String) windowStatesURLs.get(state)).toCharArray();
                                    contentHandler.characters(chars, 0, chars.length);
                                    contentHandler.endElement(PORTLET_NAMESPACE_URI, "url", "portlet:url");

                                    contentHandler.endElement(PORTLET_NAMESPACE_URI, "window-state", "portlet:window-state");
                                }
                                contentHandler.endElement(PORTLET_NAMESPACE_URI, "window-states", "portlet:window-states");
                            }
                        }

                        // Output portlet modes URLs
                        Map portletModesURLs = portletState.getPortletModesURLs();
                        if (portletModesURLs != null) {
                            contentHandler.startElement(PORTLET_NAMESPACE_URI, "portlet-modes", "portlet:portlet-modes", XMLUtils.EMPTY_ATTRIBUTES);
                            for (Iterator j = portletModesURLs.keySet().iterator(); j.hasNext();) {
                                PortletMode mode = (PortletMode) j.next();

                                contentHandler.startElement(PORTLET_NAMESPACE_URI, "portlet-mode", "portlet:portlet-mode", XMLUtils.EMPTY_ATTRIBUTES);
                                contentHandler.startElement(PORTLET_NAMESPACE_URI, "name", "portlet:name", XMLUtils.EMPTY_ATTRIBUTES);
                                char[] chars = mode.toString().toCharArray();
                                contentHandler.characters(chars, 0, chars.length);
                                contentHandler.endElement(PORTLET_NAMESPACE_URI, "name", "portlet:name");

                                contentHandler.startElement(PORTLET_NAMESPACE_URI, "url", "portlet:url", XMLUtils.EMPTY_ATTRIBUTES);
                                chars = ((String) portletModesURLs.get(mode)).toCharArray();
                                contentHandler.characters(chars, 0, chars.length);
                                contentHandler.endElement(PORTLET_NAMESPACE_URI, "url", "portlet:url");

                                contentHandler.endElement(PORTLET_NAMESPACE_URI, "portlet-mode", "portlet:portlet-mode");
                            }
                            contentHandler.endElement(PORTLET_NAMESPACE_URI, "portlet-modes", "portlet:portlet-modes");
                        }

                        if (portletState.getTitle() != null) {
                            contentHandler.startElement(PORTLET_NAMESPACE_URI, "portlet-title", "portlet:portlet-title", XMLUtils.EMPTY_ATTRIBUTES);
                            char[] chars = portletState.getTitle().toCharArray();
                            contentHandler.characters(chars, 0, chars.length);
                            contentHandler.endElement(PORTLET_NAMESPACE_URI, "portlet-title", "portlet:portlet-title");
                        }

                        // Output portlet body
                        contentHandler.startElement(PORTLET_NAMESPACE_URI, "body", "portlet:body", XMLUtils.EMPTY_ATTRIBUTES);
                        if (!portletContainer.getPortletState(externalContext, portletId).getWindowState().equals(WindowState.MINIMIZED))
                            streamInterceptor.parse(contentHandler, config.getTidyConfig(), true);
                        contentHandler.endElement(PORTLET_NAMESPACE_URI, "body", "portlet:body");

                        contentHandler.endElement(PORTLET_NAMESPACE_URI, "portlet", "portlet:portlet");
                    }

                    contentHandler.endElement(PORTLET_NAMESPACE_URI, "portlets", "portlet:portlets");
                    contentHandler.endPrefixMapping("portlet");
                    contentHandler.endDocument();
                } catch (SAXException e) {
                    throw new OXFException(e);
                }
            }

        };
        addOutput(name, output);
        return output;
    }

    private static class Config {
        private String portalId;
        private int[] portletIds;
        private TidyConfig tidyConfig;
        private Map renderParameters;
        private String windowState;
        private String portletMode;

        public Config(String portalId, int[] portletIds, TidyConfig tidyConfig, Map renderParameters, String windowState, String portletMode) {
            this.portalId = portalId;
            this.portletIds = portletIds;
            this.tidyConfig = tidyConfig;
            this.renderParameters = renderParameters;
            this.windowState = windowState;
            this.portletMode = portletMode;
        }

        public String getPortalId() {
            return portalId;
        }

        public int[] getPortletIds() {
            return portletIds;
        }

        public TidyConfig getTidyConfig() {
            return tidyConfig;
        }

        public Map getRenderParameters() {
            return renderParameters;
        }

        public String getWindowState() {
            return windowState;
        }

        public String getPortletMode() {
            return portletMode;
        }
    }
}
