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

import org.dom4j.Document;
import org.exolab.castor.mapping.Mapping;
import org.exolab.castor.mapping.MappingException;
import org.exolab.castor.xml.Marshaller;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.pipeline.api.XMLReceiver;
import org.orbeon.oxf.processor.*;
import org.orbeon.oxf.processor.impl.DigestState;
import org.orbeon.oxf.processor.impl.DigestTransformerOutputImpl;
import org.orbeon.oxf.util.ISODateUtils;
import org.orbeon.oxf.xml.SAXStore;
import org.orbeon.oxf.xml.TransformerUtils;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationSAXWriter;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.ParserAdapter;

import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMSource;
import java.io.IOException;
import java.io.StringReader;

public class ScopeGenerator extends ScopeProcessorBase {

    private static SAXStore nullDocumentSAXStore;

    public static final String INPUT_MAPPING = "mapping";

    public ScopeGenerator() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG, SCOPE_CONFIG_NAMESPACE_URI));
        addInputInfo(new ProcessorInputOutputInfo(INPUT_MAPPING)); // optional
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

                        ContextConfig config = readConfig(pipelineContext);

                        // Get value from context
                        ExternalContext externalContext = (ExternalContext) pipelineContext.getAttribute(PipelineContext.EXTERNAL_CONTEXT);
                        if (externalContext == null)
                            throw new OXFException("Missing external context");
                        Object value = config.getContextType() == ScopeProcessorBase.REQUEST_CONTEXT
                                ? externalContext.getRequest().getAttributesMap().get(config.getKey())
                                : config.getContextType() == ScopeProcessorBase.SESSION_CONTEXT
                                ? ((config.getSessionScope() == -1)
                                    ? externalContext.getSession(true).getAttributesMap().get(config.getKey())
                                    : externalContext.getSession(true).getAttributesMap(config.getSessionScope()).get(config.getKey()))
                                : config.getContextType() == ScopeProcessorBase.APPLICATION_CONTEXT
                                ? externalContext.getAttributesMap().get(config.getKey())
                                : null;

                        if (value != null) {
                            if (value instanceof ScopeStore) {
                                // Use the stored key/validity as internal key/validity
                                final ScopeStore contextStore = (ScopeStore) value;
                                if (!config.isTestIgnoreStoredKeyValidity()) {
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
                                // Get mappings if present
                                final Mapping mapping;
                                if (getConnectedInputs().get(INPUT_MAPPING) == null) {
                                    mapping = new Mapping();
                                    mapping.loadMapping(new InputSource(new StringReader("<mapping/>")));
                                } else {
                                    mapping = readMapping(pipelineContext);
                                }

                                state.saxStore = getSAXStore(value, mapping, config.getContentType(), config.getKey());
                            }
                        } else {
                            // Store empty document
                            if (nullDocumentSAXStore == null) {
                                nullDocumentSAXStore = new SAXStore();
                                XMLUtils.streamNullDocument(nullDocumentSAXStore);
                            }
                            state.saxStore = nullDocumentSAXStore;
                        }

                        // Compute digest of the SAX Store
                        XMLUtils.DigestContentHandler digester = new XMLUtils.DigestContentHandler("MD5");
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

    public static SAXStore getSAXStore(Object value, Mapping mapping, String contentType, String key) throws SAXException, TransformerException, IOException, MappingException {
        if (ScopeProcessorBase.TEXT_PLAIN.equals(contentType)) {
            final SAXStore result = new SAXStore();
            if (value instanceof String) {
                // Creating a stream from the String! Better to extend the ProcessorUtils class to support String or StringReader or something...
                ProcessorUtils.readText((String) value, result, contentType, ISODateUtils.getCurrentTimeMillis());
            } else {
                logger.error("Content-type: " + ScopeProcessorBase.TEXT_PLAIN + " not applicable for key: " + key);
                XMLUtils.streamNullDocument(result);
            }
            return result;
        } else {
            return getSAXStore(value, mapping);
        }
    }

    public static SAXStore getSAXStore(Object value, Mapping mapping) throws SAXException, TransformerException, IOException, MappingException {
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
                    XMLUtils.stringToSAX((String) value, "", resultStore, XMLUtils.ParserConfiguration.PLAIN, true);
                } else {
                    // Consider the object a JavaBean
                    readBean(value, mapping, resultStore);
                }
            }
        }

        return resultStore;
    }

    protected static void readBean(Object bean, Mapping mapping, ContentHandler contentHandler) {
        try {
            contentHandler.startDocument();

            // Initialize Castor
            ParserAdapter adapter = new ParserAdapter(XMLUtils.newSAXParser(XMLUtils.ParserConfiguration.PLAIN).getParser());
            adapter.setContentHandler(contentHandler);
            Marshaller marshaller = new Marshaller(adapter);
            marshaller.setMarshalAsDocument(false);
            marshaller.setMapping(mapping);

            // Serialize with Castor
            marshaller.marshal(bean);

            contentHandler.endDocument();

        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    protected Mapping readMapping(PipelineContext pipelineContext) {
        return (Mapping) readCacheInputAsObject(pipelineContext, getInputByName(INPUT_MAPPING),
            new CacheableInputReader() {
                public Object read(PipelineContext context, ProcessorInput input) {
                    try {
                        Document mappingDocument = readInputAsDOM4J(context, input);
                        Mapping mapping = new Mapping();
                        mapping.loadMapping(new InputSource(new StringReader(Dom4jUtils.domToString(mappingDocument))));
                        return mapping;
                    } catch (Exception e) {
                        throw new OXFException(e);
                    }
                }
            });
    }

    public void reset(PipelineContext context) {
        setState(context, new State());
    }

    private static class State extends DigestState {
        public SAXStore saxStore;
    }
}
