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
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.processor.ProcessorInput;
import org.orbeon.oxf.processor.ProcessorInputOutputInfo;
import org.orbeon.oxf.processor.ProcessorOutput;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.xml.SAXStore;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * This internal processor handles the tee-ing functionality of XPL, i.e. sending an XML infoset to
 * multiple readers.
 */
public class TeeProcessor extends ProcessorImpl {

    static private Logger logger = LoggerFactory.createLogger(TeeProcessor.class);
    private Exception creationException;
    private Exception resetException;
    private ProcessorKey resetProcessorKey;

    public TeeProcessor(LocationData locationData) {
        creationException = new ValidationException("", locationData);
        addInputInfo(new ProcessorInputOutputInfo(INPUT_DATA));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    public ProcessorOutput createOutput(String name) {
        ProcessorOutput output = new ProcessorImpl.ProcessorOutputImpl(getClass(), name) {
            public void readImpl(PipelineContext context, ContentHandler contentHandler) {
                try {
                    State state = (State) getState(context);
                    if (state.store == null) {
                        ProcessorInput input = getInputByName(INPUT_DATA);
                        state.store = new SAXStore(contentHandler);
                        readInputAsSAX(context, input, state.store);
                    } else {
                        state.store.replay(contentHandler);
                    }
                } catch (SAXException e) {
                    throw new OXFException(e);
                }
            }

            public OutputCacheKey getKeyImpl(PipelineContext context) {
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
                    ProcessorOutput output = getInputByName(INPUT_DATA).getOutput();
                    state.outputCacheKey = (output instanceof Cacheable) ? ((Cacheable) output).getKey(context) : null;
                }
                return state.outputCacheKey;
            }

            public Object getValidityImpl(PipelineContext context) {
                State state = (State) getState(context);
                if (state.validity == null) {
                    ProcessorOutput output = getInputByName(INPUT_DATA).getOutput();
                    state.validity = (output instanceof Cacheable) ? ((Cacheable) output).getValidity(context) : null;
                }
                return state.validity;
            }
        };
        addOutput(name, output);
        return output;
    }

    public void reset(PipelineContext context) {
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
