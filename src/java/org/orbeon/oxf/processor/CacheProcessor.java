/**
 *  Copyright (C) 2007 Orbeon, Inc.
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
package org.orbeon.oxf.processor;

import org.orbeon.oxf.cache.InternalCacheKey;
import org.orbeon.oxf.cache.ObjectCache;
import org.orbeon.oxf.cache.OutputCacheKey;
import org.orbeon.oxf.cache.SimpleOutputCacheKey;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.pipeline.api.XMLReceiver;
import org.orbeon.oxf.util.ISODateUtils;
import org.orbeon.oxf.xml.SAXStore;
import org.orbeon.oxf.xml.XMLReceiverAdapter;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.value.DurationValue;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

import java.util.Date;


public class CacheProcessor extends ProcessorImpl {

    public static String INPUT_KEY = "key";
    public static String INPUT_VALIDITY = "validity";

    public CacheProcessor() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_KEY));
        addInputInfo(new ProcessorInputOutputInfo(INPUT_VALIDITY));
        addInputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    public ProcessorOutput createOutput(String name) {
        ProcessorOutput output = new ProcessorImpl.ProcessorOutputImpl(getClass(), name) {
            public void readImpl(PipelineContext pipelineContext, final XMLReceiver receiver) {
                try {
                    // Get key validity provided by caller
                    final State state = initKeyValidity(pipelineContext);

                    if (state.validity != null) {
                        // Cache data by validity
                        InternalCacheKey internalKey = new InternalCacheKey(CacheProcessor.this, "keyDigest", state.keyDigest);
                        SAXStore dataSaxStore = (SAXStore) ObjectCache.instance().findValid(internalKey, state.validity);
                        if (dataSaxStore == null) {
                            // Can't find data in cache, read it and store it in cache
                            dataSaxStore = new SAXStore();
                            readInputAsSAX(pipelineContext, INPUT_DATA, dataSaxStore);
                            ObjectCache.instance().add(internalKey, state.validity, dataSaxStore);
                        }
                        dataSaxStore.replay(receiver);
                    } else {
                        // Don't cache
                        readInputAsSAX(pipelineContext, INPUT_DATA, receiver);
                    }
                    

                } catch (SAXException e) {
                    throw new OXFException(e);
                }
            }

            public OutputCacheKey getKeyImpl(PipelineContext context) {
                State state = initKeyValidity(context);
                return new SimpleOutputCacheKey(CacheProcessor.class, OUTPUT_DATA, state.keyDigest);
            }

            public Object getValidityImpl(PipelineContext context) {
                State state = initKeyValidity(context);
                return state.validity;
            }
        };
        addOutput(OUTPUT_DATA, output);
        return output;

    }

    private State initKeyValidity(PipelineContext context) {
        State state = (State) getState(context);
        if (state.keyDigest == null) {

            // Get a digest of the key input, if possible from cache
            state.keyDigest = (String) readCacheInputAsObject
                    (context, getInputByName(INPUT_KEY), new CacheableInputReader() {
                public Object read(PipelineContext context, ProcessorInput input) {
                    XMLUtils.DigestContentHandler digestContentHandler = new XMLUtils.DigestContentHandler("MD5");
                    readInputAsSAX(context, input, digestContentHandler);
                    return new String(digestContentHandler.getResult());
                }
            });

            // Get validity based on date in validity input, if possible from cache
            CachedValidity validity =  (CachedValidity) readCacheInputAsObject
                    (context, getInputByName(INPUT_VALIDITY), new CacheableInputReader() {
                public Object read(PipelineContext context, ProcessorInput input) {
                    final StringBuffer validityBuffer = new StringBuffer();
                    final CachedValidity cachedValidity = new CachedValidity();
                    readInputAsSAX(context, input, new XMLReceiverAdapter() {
                        Locator locator;
                        public void characters(char[] chars, int start, int length) throws SAXException {
                            // Save location in case we need to use to signal an error
                            if (cachedValidity.locationData == null)
                                cachedValidity.locationData = new LocationData(locator);
                            validityBuffer.append(chars, start, length);
                        }
                        public void setDocumentLocator(Locator locator) {
                            this.locator = locator;
                        }

                    });
                    cachedValidity.validity = validityBuffer.toString();
                    return cachedValidity;
                }
            });

            // Compute data based on validity
            if ("null".equals(validity.validity) || "none".equals(validity.validity)) {
                // We won't be caching
                state.validity = null;
            } else if (validity.validity.length() == 0) {
                // No duration specified
                state.validity = new Long(0);
            } else if (validity.validity.startsWith("P")) {
                // Duration specified
                try {
                    long currentTime = new Date().getTime();
                    long length = (long) (((DurationValue) DurationValue.makeDuration(validity.validity)).getLengthInSeconds() * 1000.0);
                    state.validity = new Long(currentTime - (currentTime % length));
                } catch (Exception e) {
                    throw new ValidationException("Can't parse duration: " + validity.validity, validity.locationData);
                }
            } else {
                // Validity is a date
                state.validity = new Long(ISODateUtils.parseDate(validity.validity).getTime());
            }
        }
        return state;
    }

    public void reset(PipelineContext context) {
        setState(context, new State());
    }

    public static class State {
        public String keyDigest;
        public Long validity;
    }

    public static class CachedValidity {
        public String validity;
        public LocationData locationData;
    }

}
