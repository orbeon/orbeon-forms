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

import orbeon.apache.xml.utils.NamespaceSupport2;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.processor.ProcessorInputOutputInfo;
import org.orbeon.oxf.processor.ProcessorOutput;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xml.ForwardingContentHandler;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.util.Enumeration;

/**
 * This processor extracts XForms models and controls from an XHTML document and creates a static
 * state document for the request encoder.
 */
public class XFormsExtractor extends ProcessorImpl {

    public XFormsExtractor() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_DATA));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    public ProcessorOutput createOutput(String name) {
        ProcessorOutput output = new ProcessorImpl.CacheableTransformerOutputImpl(getClass(), name) {
            public void readImpl(PipelineContext pipelineContext, ContentHandler contentHandler) {

                readInputAsSAX(pipelineContext, INPUT_DATA, new ForwardingContentHandler(contentHandler) {

                    private final String HTML_QNAME = Dom4jUtils.buildExplodedQName(XMLConstants.XHTML_NAMESPACE_URI, "html");
                    private final String HEAD_QNAME = Dom4jUtils.buildExplodedQName(XMLConstants.XHTML_NAMESPACE_URI, "head");
                    private final String BODY_QNAME = Dom4jUtils.buildExplodedQName(XMLConstants.XHTML_NAMESPACE_URI, "body");

                    private int level;
                    private String element0;
                    private String element1;

                    private NamespaceSupport2 namespaceSupport = new NamespaceSupport2();

                    private boolean gotModel;
                    private boolean gotControl;

                    private boolean inModel;
                    private int modelLevel;
                    private boolean inControl;
                    private int controlLevel;

                    public void startDocument() throws SAXException {
                        super.startDocument();
                        super.startElement("", "static-state", "static-state", XMLUtils.EMPTY_ATTRIBUTES);
                    }

                    public void endDocument() throws SAXException {

                        // Close elements
                        if (gotModel && gotControl) {
                            super.endElement("", "controls", "controls");
                        } else if (gotModel) {
                            super.endElement("", "models", "models");
                            super.startElement("", "controls", "controls", XMLUtils.EMPTY_ATTRIBUTES);
                            super.endElement("", "controls", "controls");
                        }

                        super.endElement("", "static-state", "static-state");
                        super.endDocument();
                    }

                    public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {

                        namespaceSupport.pushContext();

                        // Remember first two levels of elements
                        if (level == 0) {
                            element0 = Dom4jUtils.buildExplodedQName(uri, localname);
                        } else if (level == 1) {
                            element1 = Dom4jUtils.buildExplodedQName(uri, localname);
                        } else if (level >= 2 && HTML_QNAME.equals(element0)) {
                            // We are under /xhtml:html
                             if (XFormsConstants.XFORMS_NAMESPACE_URI.equals(uri)) {
                                // This is an XForms element

                                if (!inModel && !inControl && localname.equals("model") && HEAD_QNAME.equals(element1)) {
                                    // Start extracting model
                                    inModel = true;
                                    modelLevel = level;

                                    if (gotControl)
                                        throw new OXFException("/xhtml:html/xhtml:head//xforms:model occurred after /xhtml:html/xhtml:body//xforms:*");

                                    if (!gotModel)
                                        super.startElement("", "models", "models", XMLUtils.EMPTY_ATTRIBUTES);

                                    gotModel = true;
                                    sendStartPrefixMappings();

                                } else if (!inModel && !inControl && BODY_QNAME.equals(element1)) {
                                    // Start extracting controls
                                    inControl = true;
                                    controlLevel = level;

                                    if (gotModel && !gotControl)
                                        super.endElement("", "models", "models");

                                    if (!gotControl)
                                        super.startElement("", "controls", "controls", XMLUtils.EMPTY_ATTRIBUTES);

                                    gotControl = true;
                                    sendStartPrefixMappings();
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

                    private void sendStartPrefixMappings() throws SAXException {
                        for (Enumeration e = namespaceSupport.getPrefixes(); e.hasMoreElements();) {
                            final String namespacePrefix = (String) e.nextElement();
                            final String namespaceURI = namespaceSupport.getURI(namespacePrefix);
                            super.startPrefixMapping(namespacePrefix, namespaceURI);
                        }
                    }

                    private void sendEndPrefixMappings() throws SAXException {
                        for (Enumeration e = namespaceSupport.getPrefixes(); e.hasMoreElements();) {
                            final String namespacePrefix = (String) e.nextElement();
                            super.endPrefixMapping(namespacePrefix);
                        }
                    }

                    public void endElement(String uri, String localname, String qName) throws SAXException {

                        level--;

                        if (inModel) {
                            super.endElement(uri, localname, qName);
                        } else if (inControl && XFormsConstants.XFORMS_NAMESPACE_URI.equals(uri)) {
                            super.endElement(uri, localname, qName);
                        }

                        namespaceSupport.popContext();

                        if (inModel && level == modelLevel) {
                            // Leaving model
                            inModel = false;
                            sendEndPrefixMappings();
                        } else if (inControl && level == controlLevel) {
                            // Leaving control
                            inControl = false;
                            sendEndPrefixMappings();
                        }
                    }

                    public void characters(char[] chars, int start, int length) throws SAXException {
                        if (inModel || inControl)
                            super.characters(chars, start, length);
                    }

                    public void startPrefixMapping(String prefix, String uri) throws SAXException {
                        namespaceSupport.declarePrefix(prefix, uri);
                        if (inModel || inControl)
                            super.startPrefixMapping(prefix, uri);
                    }

                    public void endPrefixMapping(String s) throws SAXException {
                        if (inModel || inControl)
                            super.endPrefixMapping(s);
                    }
                });
            }
        };
        addOutput(name, output);
        return output;
    }
}