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

import org.apache.log4j.Logger;
import org.orbeon.oxf.cache.Cacheable;
import org.orbeon.oxf.cache.OutputCacheKey;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.xml.SAXStore;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

public class TeeProcessor extends org.orbeon.oxf.processor.ProcessorImpl {

    static private Logger logger = LoggerFactory.createLogger(TeeProcessor.class);
    private Exception creationException;
    private Exception resetException;
    private ProcessorKey resetProcessorKey;

    public TeeProcessor(LocationData locationData) {
        creationException = new ValidationException("", locationData);
        addInputInfo(new org.orbeon.oxf.processor.ProcessorInputOutputInfo(INPUT_DATA));
        addOutputInfo(new org.orbeon.oxf.processor.ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    public org.orbeon.oxf.processor.ProcessorOutput createOutput(String name) {
        org.orbeon.oxf.processor.ProcessorOutput output = new org.orbeon.oxf.processor.ProcessorImpl.ProcessorOutputImpl(getClass(), name) {
            public void readImpl(org.orbeon.oxf.pipeline.api.PipelineContext context, ContentHandler contentHandler) {
                try {
                    State state = (State) getState(context);
                    if (state.store == null) {
                        org.orbeon.oxf.processor.ProcessorInput input = getInputByName(INPUT_DATA);
                        state.store = new SAXStore(contentHandler);
                        readInputAsSAX(context, input, state.store);
                    } else {
                        state.store.replay(contentHandler);
                    }
                } catch (SAXException e) {
                    throw new OXFException(e);
                }
            }

            public OutputCacheKey getKeyImpl(org.orbeon.oxf.pipeline.api.PipelineContext context) {
                State state = null;
                try {
                    state = (State) getState(context);
                } catch (OXFException e) {
                    logger.error("creation", creationException);
                    logger.error("reset", resetException);
                    logger.error("current processor key: " + getProcessorKey(context));
                    logger.error("reset processor key: " + resetProcessorKey);
                    throw e;
                }
                if (state.outputCacheKey == null) {
                    org.orbeon.oxf.processor.ProcessorOutput output = getInputByName(INPUT_DATA).getOutput();
                    state.outputCacheKey = (output instanceof Cacheable) ? ((Cacheable) output).getKey(context) : null;
                }
                return state.outputCacheKey;
            }

            public Object getValidityImpl(org.orbeon.oxf.pipeline.api.PipelineContext context) {
                State state = (State) getState(context);
                if (state.validity == null) {
                    org.orbeon.oxf.processor.ProcessorOutput output = getInputByName(INPUT_DATA).getOutput();
                    state.validity = (output instanceof Cacheable) ? ((Cacheable) output).getValidity(context) : null;
                }
                return state.validity;
            }
        };
        addOutput(name, output);
        return output;
    }

    public void reset(org.orbeon.oxf.pipeline.api.PipelineContext context) {
        resetException = new Exception(Integer.toString(hashCode()));
        resetProcessorKey = getProcessorKey(context);
        setState(context, new State());
    }

    private static class State {
        public SAXStore store;
        public OutputCacheKey outputCacheKey;
        public Object validity;
    }
}
