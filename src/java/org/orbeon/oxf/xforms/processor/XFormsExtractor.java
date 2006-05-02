/**
 *  Copyright (C) 2005 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.processor;

import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.processor.ProcessorInputOutputInfo;
import org.orbeon.oxf.processor.ProcessorOutput;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xml.*;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Enumeration;
import java.util.Stack;

/**
 * This processor extracts XForms models and controls from an XHTML document and creates a static state document for the
 * request encoder. xml:base attributes are added on the models and root control elements.
 */
public class XFormsExtractor extends ProcessorImpl {

    public XFormsExtractor() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_DATA));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    public ProcessorOutput createOutput(String name) {
        ProcessorOutput output = new ProcessorImpl.CacheableTransformerOutputImpl(getClass(), name) {
            public void readImpl(PipelineContext pipelineContext, ContentHandler contentHandler) {
                readInputAsSAX(pipelineContext, INPUT_DATA, new XFormsExtractorContentHandler(pipelineContext, contentHandler));
            }
        };
        addOutput(name, output);
        return output;
    }

    public static class XFormsExtractorContentHandler extends ForwardingContentHandler {

        private final String HTML_QNAME = XMLUtils.buildExplodedQName(XMLConstants.XHTML_NAMESPACE_URI, "html");
        private final String HEAD_QNAME = XMLUtils.buildExplodedQName(XMLConstants.XHTML_NAMESPACE_URI, "head");
        private final String BODY_QNAME = XMLUtils.buildExplodedQName(XMLConstants.XHTML_NAMESPACE_URI, "body");

        private Locator locator;

        private String stateHandling;

        private int level;
        private String element0;
        private String element1;

        private NamespaceSupport3 namespaceSupport = new NamespaceSupport3();

        private boolean gotModel;
        private boolean gotControl;

        private boolean inModel;
        private int modelLevel;
        private boolean inControl;
        private int controlLevel;

        private boolean mustOutputFirstElement = true;

        private final ExternalContext externalContext;
        private Stack xmlBaseStack = new Stack();

        public XFormsExtractorContentHandler(PipelineContext pipelineContext, ContentHandler contentHandler) {
            super(contentHandler);

            this.externalContext = ((ExternalContext) pipelineContext.getAttribute(PipelineContext.EXTERNAL_CONTEXT));

            // Create xml:base stack
            try {
                final String rootXMLBase = externalContext.getRequest().getRequestPath();
                xmlBaseStack.push(new URI(rootXMLBase));
            } catch (URISyntaxException e) {
                throw new ValidationException(e, new LocationData(locator));
            }
        }

        public void startDocument() throws SAXException {
            super.startDocument();
        }

        private void outputFirstElementIfNeeded() throws SAXException {
            if (mustOutputFirstElement) {
                final AttributesImpl attributesImp = new AttributesImpl();
                // Add xml:base attribute
                attributesImp.addAttribute(XMLConstants.XML_URI, "base", "xml:base", ContentHandlerHelper.CDATA, ((URI) xmlBaseStack.get(0)).toString());
                // Add container-type attribute
                attributesImp.addAttribute("", "container-type", "container-type", ContentHandlerHelper.CDATA, externalContext.getRequest().getContainerType());
                // Add state-handling attribute
                if (stateHandling != null)
                    attributesImp.addAttribute("", "state-handling", "state-handling", ContentHandlerHelper.CDATA, stateHandling);

                super.startElement("", "static-state", "static-state", attributesImp);
                mustOutputFirstElement = false;
            }
        }

        public void endDocument() throws SAXException {

            // Start and close elements
            if (!gotModel && !gotControl) {
                outputFirstElementIfNeeded();
                super.startElement("", "models", "models", XMLUtils.EMPTY_ATTRIBUTES);
            }

            if (gotModel && !gotControl) {
                super.endElement("", "models", "models");
                super.startElement("", "controls", "controls", XMLUtils.EMPTY_ATTRIBUTES);
                super.endElement("", "controls", "controls");
            }

            if (gotModel && gotControl) {
                super.endElement("", "controls", "controls");
            }

            super.endElement("", "static-state", "static-state");
            super.endDocument();
        }

        public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {

            namespaceSupport.startElement();

            if (!inModel && !inControl) {
                // Handle xml:base
                {
                    final String xmlBaseAttribute = attributes.getValue(XMLConstants.XML_URI, "base");
                    if (xmlBaseAttribute == null) {
                        xmlBaseStack.push(xmlBaseStack.peek());
                    } else {
                        try {
                            final URI currentXMLBaseURI = (URI) xmlBaseStack.peek();
                            xmlBaseStack.push(currentXMLBaseURI.resolve(new URI(xmlBaseAttribute)));
                        } catch (URISyntaxException e) {
                            throw new ValidationException("Error creating URI from: '" + xmlBaseStack.peek() + "' and '" + xmlBaseAttribute + "'.", e, new LocationData(locator));
                        }
                    }
                }
                // Handle preferences
                if (stateHandling == null) {
                    final String xxformsStateHandling = attributes.getValue(XFormsConstants.XXFORMS_NAMESPACE_URI, XFormsConstants.XXFORMS_STATE_HANDLING_ATTRIBUTE_NAME);
                    if (xxformsStateHandling != null) {
                        if (!(xxformsStateHandling.equals(XFormsConstants.XXFORMS_STATE_HANDLING_CLIENT_VALUE) || xxformsStateHandling.equals(XFormsConstants.XXFORMS_STATE_HANDLING_SESSION_VALUE)))
                            throw new ValidationException("Invalid " + XFormsConstants.XXFORMS_STATE_HANDLING_ATTRIBUTE_NAME + " attribute value: " + xxformsStateHandling, new LocationData(locator));

                        stateHandling = xxformsStateHandling;
                    }
                }
            }

            // Remember first two levels of elements
            if (level == 0) {
                element0 = XMLUtils.buildExplodedQName(uri, localname);
            } else if (level == 1) {
                element1 = XMLUtils.buildExplodedQName(uri, localname);
            } else if (level >= 2 && HTML_QNAME.equals(element0)) {
                // We are under /xhtml:html
                if (XFormsConstants.XFORMS_NAMESPACE_URI.equals(uri)) {
                    // This is an XForms element

                    if (!inModel && !inControl && localname.equals("model") && HEAD_QNAME.equals(element1)) {
                        // Start extracting model
                        inModel = true;
                        modelLevel = level;

                        if (gotControl)
                            throw new ValidationException("/xhtml:html/xhtml:head//xforms:model occurred after /xhtml:html/xhtml:body//xforms:*", new LocationData(locator));

                        if (!gotModel) {
                            outputFirstElementIfNeeded();
                            super.startElement("", "models", "models", XMLUtils.EMPTY_ATTRIBUTES);
                        }

                        gotModel = true;

                        // Add xml:base on element
                        attributes = XMLUtils.addOrReplaceAttribute(attributes, XMLConstants.XML_URI, "xml", "base", getCurrentBaseURI());

                        sendStartPrefixMappings();

                    } else if (!inModel && !inControl && BODY_QNAME.equals(element1)) {
                        // Start extracting controls
                        inControl = true;
                        controlLevel = level;

                        if (!gotControl) {
                            if (gotModel) {
                                super.endElement("", "models", "models");
                            } else {
                                outputFirstElementIfNeeded();
                                super.startElement("", "models", "models", XMLUtils.EMPTY_ATTRIBUTES);
                                super.endElement("", "models", "models");
                            }
                            super.startElement("", "controls", "controls", XMLUtils.EMPTY_ATTRIBUTES);
                        }

                        gotControl = true;

                        // Add xml:base on element
                        attributes = XMLUtils.addOrReplaceAttribute(attributes, XMLConstants.XML_URI, "xml", "base", getCurrentBaseURI());

                        sendStartPrefixMappings();
                    }

                    if (inControl) {
                        super.startElement(uri, localname, qName, attributes);
                    }
                } else if (XFormsConstants.XXFORMS_NAMESPACE_URI.equals(uri)) {

                    if (BODY_QNAME.equals(element1)) {// NOTE: This test is a little harsh, as the user may use xxforms:* elements for examples, etc.
                        if (!("img".equals(localname) || "size".equals(localname)))
                            throw new ValidationException("Invalid element in XForms document: xxforms:" + localname, new LocationData(locator));
                    }

                    if (inControl) {
                        super.startElement(uri, localname, qName, attributes);
                    }
                }

                if (inModel) {
                    super.startElement(uri, localname, qName, attributes);
                }
            }

            level++;
        }

        private String getCurrentBaseURI() {
            final URI currentXMLBaseURI = (URI) xmlBaseStack.peek();
            return currentXMLBaseURI.toString();
        }

        private void sendStartPrefixMappings() throws SAXException {
            for (Enumeration e = namespaceSupport.getPrefixes(); e.hasMoreElements();) {
                final String namespacePrefix = (String) e.nextElement();
                final String namespaceURI = namespaceSupport.getURI(namespacePrefix);
                if (!namespacePrefix.startsWith("xml"))
                    super.startPrefixMapping(namespacePrefix, namespaceURI);
            }
        }

        private void sendEndPrefixMappings() throws SAXException {
            for (Enumeration e = namespaceSupport.getPrefixes(); e.hasMoreElements();) {
                final String namespacePrefix = (String) e.nextElement();
                if (!namespacePrefix.startsWith("xml"))
                    super.endPrefixMapping(namespacePrefix);
            }
        }

        public void endElement(String uri, String localname, String qName) throws SAXException {

            level--;

            if (inModel) {
                super.endElement(uri, localname, qName);
            } else if (inControl && (XFormsConstants.XFORMS_NAMESPACE_URI.equals(uri) || XFormsConstants.XXFORMS_NAMESPACE_URI.equals(uri))) {
                super.endElement(uri, localname, qName);
            }

            if (inModel && level == modelLevel) {
                // Leaving model
                inModel = false;
                sendEndPrefixMappings();
            } else if (inControl && level == controlLevel) {
                // Leaving control
                inControl = false;
                sendEndPrefixMappings();
            }

            if (!inModel && !inControl) {
                xmlBaseStack.pop();
            }

            namespaceSupport.endElement();
        }

        public void characters(char[] chars, int start, int length) throws SAXException {
            if (inModel || inControl)
                super.characters(chars, start, length);
        }

        public void startPrefixMapping(String prefix, String uri) throws SAXException {
            namespaceSupport.startPrefixMapping(prefix, uri);
            if (inModel || inControl)
                super.startPrefixMapping(prefix, uri);
        }

        public void endPrefixMapping(String s) throws SAXException {
            if (inModel || inControl)
                super.endPrefixMapping(s);
        }

        public void setDocumentLocator(Locator locator) {
            this.locator = locator;
            super.setDocumentLocator(locator);
        }
    }
}