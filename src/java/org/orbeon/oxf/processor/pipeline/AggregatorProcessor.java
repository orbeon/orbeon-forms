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
package org.orbeon.oxf.processor.pipeline;

import org.dom4j.Element;
import org.orbeon.oxf.cache.*;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.pipeline.api.XMLReceiver;
import org.orbeon.oxf.processor.*;
import org.orbeon.oxf.processor.impl.CacheableTransformerOutputImpl;
import org.orbeon.oxf.xml.EmbeddedDocumentXMLReceiver;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.xml.sax.SAXException;

import java.util.*;

public class AggregatorProcessor extends ProcessorImpl {

    public static final String AGGREGATOR_NAMESPACE_URI = "http://www.orbeon.com/oxf/pipeline/aggregator";

    public AggregatorProcessor() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG, AGGREGATOR_NAMESPACE_URI));
        addInputInfo(new ProcessorInputOutputInfo(INPUT_DATA));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    @Override
    public ProcessorOutput createOutput(String name) {
        final ProcessorOutput output = new CacheableTransformerOutputImpl(AggregatorProcessor.this, name) {
            public void readImpl(PipelineContext context, XMLReceiver xmlReceiver) {

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
                    xmlReceiver.startDocument();
                    if (!rootNamespaceURI.equals(""))
                        xmlReceiver.startPrefixMapping(rootPrefix, rootNamespaceURI);
                    xmlReceiver.startElement(rootNamespaceURI, rootLocalName, rootQName, XMLUtils.EMPTY_ATTRIBUTES);

                    // Processor input processors
                    for (Iterator i = getInputsByName(INPUT_DATA).iterator(); i.hasNext();) {
                        ProcessorInput input = (ProcessorInput) i.next();
                        readInputAsSAX(context, input, new EmbeddedDocumentXMLReceiver(xmlReceiver));
                    }

                    // End document
                    xmlReceiver.endElement(rootNamespaceURI, rootLocalName, rootQName);
                    if (!rootNamespaceURI.equals(""))
                        xmlReceiver.endPrefixMapping(rootPrefix);
                    xmlReceiver.endDocument();
                } catch (SAXException e) {
                    throw new OXFException(e);
                }
            }

            @Override
            public OutputCacheKey getKeyImpl(PipelineContext pipelineContext) {

                // Create input information
                final List<CacheKey> keys = new ArrayList<CacheKey>();
                for (final List<ProcessorInput> inputs : getConnectedInputs().values()) {
                    for (final ProcessorInput input : inputs) {
                        final OutputCacheKey outputKey = getInputKey(pipelineContext, input);
                        if (outputKey == null) {
                            return null;
                        }
                        keys.add(outputKey);
                    }
                }

                // Add local key if needed
                if (supportsLocalKeyValidity()) {
                    final CacheKey localKey = getLocalKey(pipelineContext);
                    if (localKey == null) {
                        return null;
                    }
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
