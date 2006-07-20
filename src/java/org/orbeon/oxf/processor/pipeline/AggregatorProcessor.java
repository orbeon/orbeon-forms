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
package org.orbeon.oxf.processor.pipeline;

import org.dom4j.Element;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.processor.ProcessorInput;
import org.orbeon.oxf.processor.ProcessorInputOutputInfo;
import org.orbeon.oxf.processor.ProcessorOutput;
import org.orbeon.oxf.xml.EmbeddedDocumentContentHandler;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.oxf.cache.OutputCacheKey;
import org.orbeon.oxf.cache.CacheKey;
import org.orbeon.oxf.cache.CompoundOutputCacheKey;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

public class AggregatorProcessor extends ProcessorImpl {

    public static final String AGGREGATOR_NAMESPACE_URI = "http://www.orbeon.com/oxf/pipeline/aggregator";

    public AggregatorProcessor() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG, AGGREGATOR_NAMESPACE_URI));
        addInputInfo(new ProcessorInputOutputInfo(INPUT_DATA));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    public ProcessorOutput createOutput(String name) {
        ProcessorOutput output = new CacheableTransformerOutputImpl(getClass(), name) {
            public void readImpl(PipelineContext context, ContentHandler contentHandler) {

                try {
                    // Read config
                    final Element config = readCacheInputAsDOM4J(context, INPUT_CONFIG).getRootElement();
                    final String rootQName = config.element("root").getText();
                    final String rootPrefix;
                    final String rootLocalName;
                    final String rootNamespaceURI;

                    // Get declared namespaces
                    int columnPosition = rootQName.indexOf(':');
                    if (columnPosition == -1) {
                        rootPrefix = "";
                        rootLocalName = rootQName;
                        rootNamespaceURI = "";
                    } else {
                        rootPrefix = rootQName.substring(0, columnPosition);
                        rootLocalName = rootQName.substring(columnPosition + 1);
                        String tempNamespaceURI = null;
                        for (Iterator i = config.elements("namespace").iterator(); i.hasNext();) {
                            Element namespaceElement = (Element) i.next();
                            if (namespaceElement.attributeValue("prefix").equals(rootPrefix)) {
                                tempNamespaceURI = namespaceElement.attributeValue("uri");
                                break;
                            }
                        }
                        if (tempNamespaceURI == null)
                            throw new ValidationException("Undeclared namespace prefix '" + rootPrefix + "'",
                                    (LocationData) config.getData());

                        rootNamespaceURI = tempNamespaceURI;
                    }

                    // Start document
                    contentHandler.startDocument();
                    if (!rootNamespaceURI.equals(""))
                        contentHandler.startPrefixMapping(rootPrefix, rootNamespaceURI);
                    contentHandler.startElement(rootNamespaceURI, rootLocalName, rootQName, XMLUtils.EMPTY_ATTRIBUTES);

                    // Processor input processors
                    for (Iterator i = getInputsByName(INPUT_DATA).iterator(); i.hasNext();) {
                        ProcessorInput input = (ProcessorInput) i.next();
                        readInputAsSAX(context, input, new EmbeddedDocumentContentHandler(contentHandler));
                    }

                    // End document
                    contentHandler.endElement(rootNamespaceURI, rootLocalName, rootQName);
                    if (!rootNamespaceURI.equals(""))
                        contentHandler.endPrefixMapping(rootPrefix);
                    contentHandler.endDocument();
                } catch (SAXException e) {
                    throw new OXFException(e);
                }
            }

            public OutputCacheKey getKeyImpl(PipelineContext pipelineContext) {

                // Create input information
                final List keys = new ArrayList();
                final Map inputsMap = getConnectedInputs();
                for (Iterator i = inputsMap.keySet().iterator(); i.hasNext();) {
                    final List currentInputs = (List) inputsMap.get(i.next());
                    for (Iterator j = currentInputs.iterator(); j.hasNext();) {
                        final OutputCacheKey outputKey = getInputKey(pipelineContext, (ProcessorInput) j.next());
                        if (outputKey == null) return null;
                        keys.add(outputKey);
                    }
                }

                // Add local key if needed
                if (supportsLocalKeyValidity()) {
                    final CacheKey localKey = getLocalKey(pipelineContext);
                    if (localKey == null) return null;
                    keys.add(localKey);
                }

                // Concatenate current processor info and input info
                final CacheKey[] outputKeys = new CacheKey[ keys.size() ];
                keys.toArray(outputKeys);
                final Class processorClass = getProcessorClass();
                final String outputName = getName();
                return new CompoundOutputCacheKey(processorClass, outputName, outputKeys);
            }
        };
        addOutput(name, output);
        return output;
    }
}
