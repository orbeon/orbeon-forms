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
package org.orbeon.oxf.processor.scope;

import org.orbeon.oxf.cache.Cache;
import org.orbeon.oxf.cache.InternalCacheKey;
import org.orbeon.oxf.cache.ObjectCache;
import org.orbeon.oxf.cache.OutputCacheKey;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.processor.ProcessorInput;
import org.orbeon.oxf.processor.ProcessorInputOutputInfo;
import org.orbeon.oxf.processor.ProcessorOutput;
import org.orbeon.oxf.xml.SAXStore;
import org.orbeon.oxf.xml.TransformerUtils;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.LocationSAXWriter;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import javax.xml.transform.Transformer;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;
import java.util.Arrays;

public class ScopeGenerator extends ScopeConfigReader {

    private InternalCacheKey DEFAULT_LOCAL_KEY = new InternalCacheKey(this, "constant", "constant");

    public ScopeGenerator() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    public ProcessorOutput createOutput(String name) {
        ProcessorOutput output = new ProcessorImpl.CacheableTransformerOutputImpl(getClass(), name) {
            public void readImpl(org.orbeon.oxf.pipeline.api.PipelineContext context, final ContentHandler contentHandler) {
                try {
                    State state = computeState(context);
                    if (state.saxStore == null) {
                        // Send empty document
                        LocationSAXWriter locationSAXWriter = new LocationSAXWriter();
                        locationSAXWriter.setContentHandler(contentHandler);
                        locationSAXWriter.write(XMLUtils.NULL_DOCUMENT);
                    } else {
                        state.saxStore.replay(contentHandler);
                    }
                } catch (SAXException e) {
                    throw new OXFException(e);
                }
            }

            protected boolean supportsLocalKeyValidity() {
                return true;
            }

            protected InternalCacheKey getLocalKey(PipelineContext context) {
                State state = computeState(context);
                return state.key == null ? DEFAULT_LOCAL_KEY : new InternalCacheKey(ScopeGenerator.this,
                        Arrays.asList(new Object[] {state.key}));
            }

            protected Object getLocalValidity(PipelineContext context) {
                State state = computeState(context);
                return state.validity;
            }

            private State computeState(PipelineContext context) {
                try {
                    State state = (State) getState(context);
                    if (!state.computed) {

                        state.computed = true;
                        ContextConfig config = readConfig(context);

                        // Get value from context
                        ExternalContext externalContext = (ExternalContext) context.getAttribute(PipelineContext.EXTERNAL_CONTEXT);
                        Object value = config.getContextType() ==  ScopeConfigReader.REQUEST_CONTEXT
                                ? externalContext.getRequest().getAttributesMap().get(config.getKey())
                                : config.getContextType() ==  ScopeConfigReader.SESSION_CONTEXT
                                ? externalContext.getSession(true).getAttributesMap().get(config.getKey())
                                : config.getContextType() ==  ScopeConfigReader.APPLICATION_CONTEXT
                                ? externalContext.getAttributesMap().get(config.getKey())
                                : null;

                        if (value != null) {
                            if (value instanceof ScopeStore) {

                                // Case 1: use the stored key/validity as internal key/validity
                                ScopeStore contextStore = (ScopeStore) value;
                                state.saxStore = contextStore.getSaxStore();
                                state.key = contextStore.getKey();
                                state.validity = contextStore.getValidity();

                            } else {

                                // Case 2: "generate the validity from object" (similar to what is done in the BeanGenerator)

                                if (value instanceof SAXStore) {
                                    state.saxStore = (SAXStore) value;
                                } else {
                                    // Write "foreign" object to new SAX store
                                    state.saxStore = new SAXStore();
                                    if (value instanceof org.dom4j.Document) {
                                        LocationSAXWriter saxWriter = new LocationSAXWriter();
                                        saxWriter.setContentHandler(state.saxStore);
                                        saxWriter.write((org.dom4j.Document) value);
                                    } else if (value instanceof org.w3c.dom.Document) {
                                        Transformer identity = TransformerUtils.getIdentityTransformer();
                                        identity.transform(new DOMSource((org.w3c.dom.Document) value), new SAXResult(state.saxStore));
                                    } else if (value instanceof String) {
                                        XMLUtils.stringToSAX((String) value, "", state.saxStore, false);
                                    } else {
                                        throw new OXFException("Session object " + config.getKey()
                                                + " is of unknown type: " + value.getClass().getName());
                                    }
                                }

                                ProcessorInput configInput = getInputByName(INPUT_CONFIG);
                                OutputCacheKey configKey = getInputKey(context, configInput);
                                Object configValidity = getInputValidity(context, configInput);
                                if (configKey != null && configValidity != null) {

                                    // We store in cache: (config) -> (digest, validity)
                                    InternalCacheKey internalCacheKey = new InternalCacheKey
                                            (ScopeGenerator.this, Arrays.asList(new Object[] {configKey}));

                                    // Compute digest of the SAX Store (the output of this processor)
                                    byte[] digest; {
                                        XMLUtils.DigestContentHandler digestContentHandler = new XMLUtils.DigestContentHandler("MD5");
                                        state.saxStore.replay(digestContentHandler);
                                        digest = digestContentHandler.getResult();
                                    }

                                    // Do we have a validity for this digest in cache?
                                    Cache cache = ObjectCache.instance();
                                    DigestValidity digestValidity = (DigestValidity) cache.findValid(context, internalCacheKey, configValidity);
                                    if (digestValidity != null && digest.equals(digestValidity.digest)) {
                                        state.validity = digestValidity.lastModified;
                                    } else {
                                        Long currentValidity = new Long(System.currentTimeMillis());
                                        cache.add(context, internalCacheKey, configValidity, new DigestValidity(digest, currentValidity));
                                        state.validity = currentValidity;
                                    }
                                }
                            }
                        }

                    }
                    return state;

                } catch (Exception e) {
                    throw new OXFException(e);
                }
            }
        };
        addOutput(OUTPUT_DATA, output);
        return output;
    }

    public void reset(PipelineContext context) {
        setState(context, new State());
    }

    private static class State {
        public boolean computed = false;
        public SAXStore saxStore;
        public OutputCacheKey key;
        public Object validity;
    }

    private static class DigestValidity {
        public byte[] digest;
        public Long lastModified;

        public DigestValidity(byte[] digest, Long lastModified) {
            this.digest = digest;
            this.lastModified = lastModified;
        }
    }
}
