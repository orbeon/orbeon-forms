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
import org.orbeon.oxf.xml.ForwardingContentHandler;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.util.Iterator;

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
                    Element config = readCacheInputAsDOM4J(context, INPUT_CONFIG).getRootElement();
                    String qname = config.element("root").getText();
                    String namespaceURI;
                    String localName;

                    // Get declared namespaces
                    int columnPosition = qname.indexOf(':');
                    if (columnPosition == -1) {
                        namespaceURI = "";
                        localName = qname;
                    } else {
                        String prefix = qname.substring(0, columnPosition);
                        localName = qname.substring(columnPosition + 1);
                        namespaceURI = null;
                        for (Iterator i = config.elements("namespace").iterator(); i.hasNext();) {
                            Element namespaceElement = (Element) i.next();
                            if (namespaceElement.attributeValue("prefix").equals(prefix)) {
                                namespaceURI = namespaceElement.attributeValue("uri");
                                break;
                            }
                        }
                        if (namespaceURI == null)
                            throw new ValidationException("Undeclared namespace prefix '" + prefix + "'",
                                    (LocationData) config.getData());
                    }

                    // Start document
                    contentHandler.startDocument();
                    contentHandler.startElement(namespaceURI, localName, qname, XMLUtils.EMPTY_ATTRIBUTES);

                    // Processor input processors
                    for (Iterator i = getInputsByName(INPUT_DATA).iterator(); i.hasNext();) {
                        ProcessorInput input = (ProcessorInput) i.next();
                        readInputAsSAX(context, input, new StartEndDocumentEater(contentHandler));
                    }

                    // End document
                    contentHandler.endElement(namespaceURI, localName, qname);
                    contentHandler.endDocument();
                } catch (SAXException e) {
                    throw new OXFException(e);
                }
            }
        };
        addOutput(name, output);
        return output;
    }

    /**
     * Forwards all the SAX events to a content handler, except for
     * startDocument and endDocument.
     */
    private static class StartEndDocumentEater extends ForwardingContentHandler {
        public StartEndDocumentEater(ContentHandler contentHandler) {
            super(contentHandler);
        }

        public void startDocument() throws SAXException {}
        public void endDocument() throws SAXException {}
    };
}
