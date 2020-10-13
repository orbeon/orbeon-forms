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
package org.orbeon.oxf.processor.scope;

import org.orbeon.dom.Document;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.externalcontext.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.BinaryTextSupport;
import org.orbeon.oxf.processor.ProcessorInputOutputInfo;
import org.orbeon.oxf.processor.ProcessorOutput;
import org.orbeon.oxf.processor.impl.DigestState;
import org.orbeon.oxf.processor.impl.DigestTransformerOutputImpl;
import org.orbeon.oxf.xml.*;
import org.orbeon.oxf.xml.dom.LocationSAXWriter;
import org.xml.sax.SAXException;

import javax.xml.transform.dom.DOMSource;

public class ScopeGenerator extends ScopeProcessorBase {

    private static SAXStore nullDocumentSAXStore;


    public ScopeGenerator() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG, ScopeProcessorBase.ScopeConfigNamespaceUri()));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    public ProcessorOutput createOutput(String name) {
        final ProcessorOutput output = new DigestTransformerOutputImpl(ScopeGenerator.this, name) {
            public void readImpl(PipelineContext pipelineContext, final XMLReceiver xmlReceiver) {
                try {
                    State state = (State) getFilledOutState(pipelineContext);
                    state.saxStore.replay(xmlReceiver);
                } catch (SAXException e) {
                    throw new OXFException(e);
                }
            }

            protected byte[] computeDigest(PipelineContext pipelineContext, DigestState digestState) {
                if (digestState.digest == null) {
                    fillOutState(pipelineContext, digestState);
                }
                return digestState.digest;
            }

            protected boolean fillOutState(PipelineContext pipelineContext, DigestState digestState) {
                try {
                    State state = (State) digestState;
                    if (state.saxStore == null) {

                        ScopeProcessorBase.ContextConfig config = readConfig(pipelineContext);

                        // Get value from context
                        ExternalContext externalContext = (ExternalContext) pipelineContext.getAttribute(PipelineContext.EXTERNAL_CONTEXT);
                        if (externalContext == null)
                            throw new OXFException("Missing external context");
                        Object value = config.javaIsRequestScope()
                                ? externalContext.getRequest().getAttributesMap().get(config.key())
                                : config.javaIsSessionScope()
                                ? externalContext.getSession(true).javaGetAttribute(config.key(), config.sessionScope())
                                : config.javaIsApplicationScope()
                                ? externalContext.getWebAppContext().getAttributesMap().get(config.key())
                                : null;

                        if (value != null) {
                            if (value instanceof ScopeStore) {
                                // Use the stored key/validity as internal key/validity
                                final ScopeStore contextStore = (ScopeStore) value;
                                if (!config.testIgnoreStoredKeyValidity()) {
                                    // Regular case
                                    state.key = contextStore.getKey();
                                    state.validity = contextStore.getValidity();
                                } else {
                                    // Special test mode (will use digest)
                                    state.key = null;
                                    state.validity = null;
                                }

                                // Just get SAXStore from ScopeStore
                                state.saxStore = contextStore.getSaxStore();
                            } else {
                                state.saxStore = getSAXStore(value, config.isTextPlain() ? ScopeProcessorBase.TextPlain() : null, config.key());
                            }
                        } else {
                            // Store empty document
                            if (nullDocumentSAXStore == null) {
                                nullDocumentSAXStore = new SAXStore();
                                SAXUtils.streamNullDocument(nullDocumentSAXStore);
                            }
                            state.saxStore = nullDocumentSAXStore;
                        }

                        // Compute digest of the SAX Store
                        DigestContentHandler digester = new DigestContentHandler();
                        state.saxStore.replay(digester);
                        state.digest = digester.getResult();
                    }
                    return true;

                } catch (Exception e) {
                    throw new OXFException(e);
                }
            }
        };
        addOutput(OUTPUT_DATA, output);
        return output;
    }

    public static SAXStore getSAXStore(Object value, String contentType, String key) throws SAXException {
        if (ScopeProcessorBase.TextPlain().equals(contentType)) {
            final SAXStore result = new SAXStore();
            if (value instanceof String) {
                // Creating a stream from the String! Better to extend the ProcessorUtils class to support String or StringReader or something...
                BinaryTextSupport.readText((String) value, result, contentType, System.currentTimeMillis());
            } else {
                logger.error("Content-type: " + ScopeProcessorBase.TextPlain() + " not applicable for key: " + key);
                SAXUtils.streamNullDocument(result);
            }
            return result;
        } else {
            return getSAXStore(value);
        }
    }

    private static SAXStore getSAXStore(Object value) {
        final SAXStore resultStore;
        if (value instanceof ScopeStore) {
            final ScopeStore contextStore = (ScopeStore) value;
            resultStore = contextStore.getSaxStore();
        } else {
            if (value instanceof SAXStore) {
                resultStore = (SAXStore) value;
            } else {
                // Write "foreign" object to new SAX store
                resultStore = new SAXStore();
                if (value instanceof Document) {
                    // dom4j document
                    final LocationSAXWriter saxWriter = new LocationSAXWriter();
                    saxWriter.setContentHandler(resultStore);
                    saxWriter.setLexicalHandler(resultStore);
                    saxWriter.write((Document) value);
                } else if (value instanceof org.w3c.dom.Document) {
                    // W3C DOM document
                    TransformerUtils.sourceToSAX(new DOMSource((org.w3c.dom.Document) value), resultStore);
                } else if (value instanceof String) {
                    // Consider the String containing a document to parse
                    XMLParsing.stringToSAX((String) value, "", resultStore, ParserConfiguration.Plain(), true);
                } else {
                    throw new OXFException("Unknown value type: " + value.getClass().getName());
                }
            }
        }

        return resultStore;
    }

    public void reset(PipelineContext context) {
        setState(context, new State());
    }

    private static class State extends DigestState {
        public SAXStore saxStore;
    }
}
