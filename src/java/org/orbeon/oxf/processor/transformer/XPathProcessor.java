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
package org.orbeon.oxf.processor.transformer;

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.Node;
import org.dom4j.XPath;
import org.jaxen.SimpleNamespaceContext;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.processor.*;
import org.orbeon.oxf.processor.generator.DOMGenerator;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.oxf.xml.ForwardingContentHandler;
import org.orbeon.oxf.xml.OXFFunctionContext;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class XPathProcessor extends ProcessorImpl {

    private LocationData locationData;

    public XPathProcessor() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG));
        addInputInfo(new ProcessorInputOutputInfo(INPUT_DATA));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    public void setLocationData(LocationData locationData) {
        this.locationData = locationData;
    }

    public ProcessorOutput createOutput(String name) {
        ProcessorOutput output = new ProcessorImpl.CacheableTransformerOutputImpl(getClass(), name) {
            public void readImpl(org.orbeon.oxf.pipeline.api.PipelineContext context, ContentHandler contentHandler) {

                XPath xpath = (XPath) readCacheInputAsObject(context, getInputByName(INPUT_CONFIG), new CacheableInputReader() {
                    public Object read(final org.orbeon.oxf.pipeline.api.PipelineContext context, final ProcessorInput input) {
                        Document config = readInputAsDOM4J(context, INPUT_CONFIG);

                        // Get declared namespaces
                        Map namespaces = new HashMap();
                        for (Iterator i = config.getRootElement().selectNodes("/config/namespace").iterator(); i.hasNext();) {
                            Element namespaceElement = (Element) i.next();
                            namespaces.put(namespaceElement.attributeValue("prefix"),
                                    namespaceElement.attributeValue("uri"));
                        }

                        // Create xpath object
                        XPath xpath = XPathCache.createCacheXPath(context, (String) config.selectObject("string(/config/xpath)"));
                        xpath.setNamespaceContext(new SimpleNamespaceContext(namespaces));
                        xpath.setFunctionContext(new OXFFunctionContext());
                        return xpath;
                    }
                });

                List results = xpath.selectNodes(readCacheInputAsDOM4J(context, INPUT_DATA));
                try {
                    contentHandler.startDocument();
                    // WARNING: Here we break the rule that processors must output valid XML documents, because
                    // we potentially output several root nodes. This works because the XPathProcessor is always
                    // connected to an aggregator, which adds a new root node.
                    for (Iterator i = results.iterator(); i.hasNext();) {
                        Object result = i.next();
                        if (result != null) {
                            if (result instanceof Element || result instanceof Document) {
                                Node node = (Node) result;
                                DOMGenerator domGenerator = new DOMGenerator(node);
                                ProcessorOutput domOutput = domGenerator.createOutput(OUTPUT_DATA);
                                domOutput.read(context, new ForwardingContentHandler(contentHandler) {
                                    public void startDocument() {
                                    }

                                    public void endDocument() {
                                    }
                                });
                            } else if (result instanceof String) {
                                String stringValue = (String) result;
                                contentHandler.characters(stringValue.toCharArray(), 0, stringValue.length());
                            } else if (result instanceof Double) {
                                String stringValue = XMLUtils.removeScientificNotation(((Double) result).doubleValue());
                                contentHandler.characters(stringValue.toCharArray(), 0, stringValue.length());
                            } else {
                                String message = "Unsupported type returned by XPath expression: "
                                        + (result == null ? "null" : result.getClass().getName());
                                throw new ValidationException(message, locationData);
                            }
                        }
                    }
                    contentHandler.endDocument();
                } catch (SAXException e) {
                    throw new ValidationException(e, locationData);
                }
            }
        };
        addOutput(name, output);
        return output;
    }
}
