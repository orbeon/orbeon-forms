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
package org.orbeon.oxf.processor.generator;

import org.dom4j.Document;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.pipeline.api.XMLReceiver;
import org.orbeon.oxf.processor.*;
import org.orbeon.oxf.processor.impl.ProcessorOutputImpl;
import org.orbeon.oxf.xml.SAXStore;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XPathUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

public class SessionGenerator extends ProcessorImpl {

    public SessionGenerator() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    @Override
    public ProcessorOutput createOutput(String name) {
        final ProcessorOutput output = new ProcessorOutputImpl(SessionGenerator.this, name) {
            public void readImpl(PipelineContext context, final XMLReceiver xmlReceiver) {
                try {
                    String key = (String) readCacheInputAsObject(context, getInputByName(INPUT_CONFIG),
                            new CacheableInputReader() {
                                public Object read(org.orbeon.oxf.pipeline.api.PipelineContext context, ProcessorInput input) {
                                    Document dom = readInputAsDOM4J(context, input);
                                    try {
                                        String key = XPathUtils.selectStringValueNormalize(dom, "/key");
                                        if (key == null)
                                            throw new ValidationException("Config input must contain a key element",
                                                    (LocationData) dom.getRootElement().getData());
                                        else
                                            return key;
                                    } catch (Exception e) {
                                        throw new OXFException(e);
                                    }
                                }
                            });
                    ExternalContext externalContext = (ExternalContext) context.getAttribute(org.orbeon.oxf.pipeline.api.PipelineContext.EXTERNAL_CONTEXT);

                    // If there is a session, try to read the object
                    ExternalContext.Session session = externalContext.getSession(false);
                    Object value = (session == null) ? null : session.getAttributesMap().get(key);
                    if (value == null) {
                        generateNullDocument(key, xmlReceiver);
                    } else if (value instanceof SAXStore) {
                        SAXStore store = (SAXStore) value;
                        store.replay(xmlReceiver);
                    } else
                        throw new OXFException("session object " + key + " is of unknown type: " + value.getClass().getName());

                } catch (Exception e) {
                    throw new OXFException(e);
                }
            }

        };
        addOutput(OUTPUT_DATA, output);
        return output;
    }

    private void generateNullDocument(String key, ContentHandler ch) throws SAXException {
        ch.startDocument();

        AttributesImpl attr = new AttributesImpl();
        attr.addAttribute(XMLConstants.XSI_URI, XMLConstants.XSI_NIL_ATTRIBUTE, XMLConstants.XSI_PREFIX + ":" + XMLConstants.XSI_NIL_ATTRIBUTE, "CDATA", "true");

        ch.startElement("", key, key, attr);
        ch.endElement("", key, key);

        ch.endDocument();
    }
}
