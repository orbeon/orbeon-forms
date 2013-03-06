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

import org.apache.log4j.Logger;
import org.dom4j.Document;
import org.dom4j.Element;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.pipeline.api.XMLReceiver;
import org.orbeon.oxf.processor.impl.ProcessorOutputImpl;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.xml.SAXStore;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.oxf.xml.dom4j.LocationSAXContentHandler;
import org.xml.sax.SAXException;

public class DebugProcessor extends ProcessorImpl {

    static public Logger logger = LoggerFactory.createLogger(DebugProcessor.class);

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
                            final Element configElement = readInputAsDOM4J(pipelineContext, INPUT_CONFIG).getRootElement();
                            debugMessage = configElement.element("message").getText();
                            if (configElement.element("system-id") != null) {
                                debugLocationData = new LocationData(configElement.element("system-id").getText(),
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

                        // Send to log4j
                        logger.info(debugMessage + ":\n"
                                + (debugLocationData != null ? debugLocationData.toString() + "\n" : "")
                                + Dom4jUtils.domToPrettyString(loggedDocument));

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
