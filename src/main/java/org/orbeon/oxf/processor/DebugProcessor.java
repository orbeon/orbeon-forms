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
package org.orbeon.oxf.processor;

import org.orbeon.datatypes.BasicLocationData;
import org.orbeon.datatypes.LocationData;
import org.orbeon.dom.Document;
import org.orbeon.dom.Element;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.xml.SAXStore;
import org.orbeon.oxf.xml.XMLReceiver;
import org.orbeon.oxf.xml.dom.IOSupport;
import org.orbeon.oxf.xml.dom.LocationSAXContentHandler;
import org.xml.sax.SAXException;

public class DebugProcessor extends ProcessorImpl {

    public static final org.slf4j.Logger logger = LoggerFactory.createLoggerJava(DebugProcessor.class);

    public DebugProcessor() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_DATA));
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG));
        addInputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    public ProcessorOutput createOutput(final String name) {
        final ProcessorOutput output = new ProcessorOutputImpl(DebugProcessor.this, name) {
            public void readImpl(PipelineContext pipelineContext, XMLReceiver xmlReceiver) {
                try {
                    if (logger.isInfoEnabled()) {

                        // Extract information to be logged
                        final String debugMessage;
                        final LocationData debugLocationData;
                        {
                            final Element configElement = readInputAsOrbeonDom(pipelineContext, INPUT_CONFIG).getRootElement();
                            debugMessage = configElement.element("message").getText();
                            if (configElement.element("system-id") != null) {
                                debugLocationData = BasicLocationData.apply(configElement.element("system-id").getText(),
                                        Integer.parseInt(configElement.element("line").getText()),
                                        Integer.parseInt(configElement.element("column").getText()));
                            } else {
                                debugLocationData = null;
                            }
                        }
                        final Document loggedDocument;
                        final SAXStore saxStore = new SAXStore();
                        {
                            readInputAsSAX(pipelineContext, name, saxStore);
                            final LocationSAXContentHandler saxContentHandler = new LocationSAXContentHandler();
                            saxStore.replay(saxContentHandler);
                            loggedDocument = saxContentHandler.getDocument();
                            if (loggedDocument.getRootElement() == null)
                                throw new OXFException("Null document for debug '" + debugMessage + "'");
                        }

                        // Log
                        logger.info(debugMessage + ":\n"
                                + (debugLocationData != null ? debugLocationData.toString() + "\n" : "")
                                + IOSupport.domToPrettyStringJava(loggedDocument));

                        // Set to output
                        saxStore.replay(xmlReceiver);

                    } else {
                        // No debugging, just pass-through
                        readInputAsSAX(pipelineContext, name, xmlReceiver);
                    }
                } catch (SAXException e) {
                    throw new OXFException(e);
                }
            }
        };
        addOutput(name, output);
        return output;
    }
}
