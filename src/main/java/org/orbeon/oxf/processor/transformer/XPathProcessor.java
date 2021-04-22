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
package org.orbeon.oxf.processor.transformer;

import org.orbeon.dom.Document;
import org.orbeon.dom.Element;
import org.orbeon.dom.ProcessingInstruction;
import org.orbeon.dom.Text;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.*;
import org.orbeon.oxf.processor.generator.DOMGenerator;
import org.orbeon.oxf.processor.impl.CacheableTransformerOutputImpl;
import org.orbeon.oxf.util.PooledXPathExpression;
import org.orbeon.oxf.util.XPath;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.oxf.xml.EmbeddedDocumentXMLReceiver;
import org.orbeon.xml.NamespaceMapping;
import org.orbeon.oxf.xml.TransformerUtils;
import org.orbeon.oxf.xml.XMLReceiver;
import org.orbeon.datatypes.LocationData;
import org.orbeon.saxon.om.DocumentInfo;
import org.orbeon.saxon.om.NodeInfo;
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

    @Override
    public void setLocationData(LocationData locationData) {
        if (locationData != null)
            this.locationData = locationData;
    }

    @Override
    public ProcessorOutput createOutput(String name) {
        final ProcessorOutput output = new CacheableTransformerOutputImpl(XPathProcessor.this, name) {
            public void readImpl(PipelineContext context, XMLReceiver xmlReceiver) {

               Config config = readCacheInputAsObject(context, getInputByName(INPUT_CONFIG), new CacheableInputReader<Config>() {
                    public Config read(PipelineContext context, final ProcessorInput input) {
                        final Document config = readInputAsOrbeonDom(context, INPUT_CONFIG);

                        // Get declared namespaces
                        final Map<String, String> namespaces = new HashMap<String, String>();
                        for (Iterator i = config.getRootElement().jElements("namespace").iterator(); i.hasNext();) {
                            Element namespaceElement = (Element) i.next();
                            namespaces.put(namespaceElement.attributeValue("prefix"),
                                    namespaceElement.attributeValue("uri"));
                        }
                        return new Config(NamespaceMapping.apply(namespaces), config.getRootElement().jElements("xpath").get(0).getStringValue());
                    }
                });

                final DocumentInfo documentInfo = readCacheInputAsTinyTree(context, XPath.GlobalConfiguration(), INPUT_DATA);
                PooledXPathExpression xpath = null;
                try {
                    final String baseURI = (locationData == null) ? null : locationData.file();
                    xpath = XPathCache.getXPathExpression(documentInfo.getConfiguration(), documentInfo,
                            config.getExpression(), config.getNamespaces(), null, org.orbeon.oxf.pipeline.api.FunctionLibrary.instance(), baseURI, locationData);
                    List<Object> results = xpath.evaluateToJavaReturnToPool();
                    xmlReceiver.startDocument();
                    // WARNING: Here we break the rule that processors must output valid XML documents, because
                    // we potentially output several root nodes. This works because the XPathProcessor is always
                    // connected to an aggregator, which adds a new root node.
                    for (Iterator i = results.iterator(); i.hasNext();) {
                        Object result = i.next();
                        if (result == null)
                            continue;

                        streamResult(context, xmlReceiver, result, locationData);
                    }
                    xmlReceiver.endDocument();
                } catch (SAXException e) {
                    throw new ValidationException(e, locationData);
                }
            }
        };
        addOutput(name, output);
        return output;
    }

    public static void streamResult(PipelineContext context, XMLReceiver xmlReceiver, Object result, LocationData locationData) throws SAXException {

        String strVal = null;
        if (result instanceof Element || result instanceof Document) {
            // If element or document, serialize it to content handler
            final Element element = result instanceof Element
                    ? (Element) result
                    : ((Document) result).getRootElement();
            final String systemId = ProcessorSupport.makeSystemId(element);
            // TODO: Should probably use Dom4jUtils.createDocumentCopyParentNamespaces() or equivalent to handle namespaces better
            // -> could maybe simply get the list of namespaces in scope on both sides, and output start/endPrefixMapping()
            final DOMGenerator domGenerator = new DOMGenerator
                    (element, "xpath result doc", DOMGenerator.ZeroValidity, systemId);
            final ProcessorOutput domOutput = domGenerator.createOutput(OUTPUT_DATA);
            domOutput.read(context, new EmbeddedDocumentXMLReceiver(xmlReceiver));
        } else if (result instanceof NodeInfo) {
            final NodeInfo nodeInfo = (NodeInfo) result;
            TransformerUtils.writeTinyTree(nodeInfo, new EmbeddedDocumentXMLReceiver(xmlReceiver));
        } else if (result instanceof ProcessingInstruction) {
            ProcessingInstruction processingInstruction = (ProcessingInstruction) result;
            xmlReceiver.processingInstruction(processingInstruction.getTarget(), processingInstruction.getText());
        } else if (result instanceof Text) {
            strVal = ((Text) result).getText();
        } else if (result instanceof String) {
            strVal = (String) result;
        } else if (result instanceof Long) {
            strVal = Long.toString((Long) result);
        } else if (result instanceof Double) {
            final double d = ((Double) result).doubleValue();
            strVal = Double.toString(d);
        } else if (result instanceof Boolean) {
            strVal = (result.toString());
        } else if (result instanceof StringBuilder) {
            strVal = result.toString();
        } else {
            String message = "Unsupported type returned by XPath expression: "
                    + (result == null ? "null" : result.getClass().getName());
            throw new ValidationException(message, locationData);
        }

        // Send string representation of simple type to content handler
        if (strVal != null) {
            final char[] ch = strVal.toCharArray();
            final int len = strVal.length();
            xmlReceiver.characters( ch, 0, len );
        }
    }

    protected static class Config {
        private final NamespaceMapping namespaces;
        private final String expression;

        public Config(NamespaceMapping namespaces, String expression) {
            this.namespaces = namespaces;
            this.expression = expression;
        }

        public String getExpression() {
            return expression;
        }

        public NamespaceMapping getNamespaces() {
            return namespaces;
        }
    }
}
