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
package org.orbeon.oxf.processor.pipeline;

import org.apache.log4j.Logger;
import org.orbeon.oxf.cache.CacheableInputOutput;
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
 * This internal processor handles the tee-ing functionality of XPL, i.e. sending an XML infoset to multiple readers.
 */
public class TeeProcessor extends ProcessorImpl {

    static private Logger logger = LoggerFactory.createLogger(TeeProcessor.class);
    private Exception creationException;
    private Exception resetException;
    private ProcessorKey resetProcessorKey;

    public TeeProcessor(LocationData locationData) {
        if (logger.isDebugEnabled()) {
            creationException = new ValidationException("", locationData);
        }
        addInputInfo(new ProcessorInputOutputInfo(INPUT_DATA));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    /**
     * Standard createOutput().
     */
    public ProcessorOutput createOutput(String name) {
        return createOutput(name, false);
    }

    /**
     * Tee-specific createOutput().
     */
    public ProcessorOutput createOutput(String name, boolean isMultipleReads) {
        final ProcessorOutput output = new TeeProcessorOutputImpl(getClass(), name, isMultipleReads);
        addOutput(name, output);
        return output;
    }

    public class TeeProcessorOutputImpl extends ProcessorImpl.ProcessorOutputImpl {

        private boolean isMultipleReads;

        private TeeProcessorOutputImpl(Class clazz, String name, boolean isMultipleReads) {
            super(clazz, name);
            this.isMultipleReads = isMultipleReads;
        }

        public void readImpl(PipelineContext context, ContentHandler contentHandler) {
            try {
                final State state = (State) getState(context);
                if (state.store == null) {
                    // Tee hasn't been read yet

                    if (state.stateWasCleared) {
                        final ProcessorOutput output = getInputByName(INPUT_DATA).getOutput();
                        logger.error("Tee state was cleared and re-read for output: " + output.getName());
                    }

                    // Create SAXStore and read input through it
                    final ProcessorInput input = getInputByName(INPUT_DATA);
                    state.store = new SAXStore(contentHandler);
                    readInputAsSAX(context, input, state.store);
                } else {
                    state.store.replay(contentHandler);
                }

                // If this output can be read only once, increase read count
                if (!isMultipleReads) {
                    state.readCount++;
                }

                // If possible, free the SAXStore after the last read
                freeSAXStoreIfNeeded(state);

            } catch (SAXException e) {
                throw new OXFException(e);
            }
        }

        /**
         * This is called specifically by p:for-each once an input has been entirely read.
         */
        public void doneReading(PipelineContext context) {
            // This output can be read more than once, so increase read count only when needed
            final State state = (State) getState(context);
            state.readCount++;
            // If possible, free the SAXStore after the last read
            freeSAXStoreIfNeeded(state);
        }

        private void freeSAXStoreIfNeeded(State state) {
            if (state.readCount == getOutputCount()) {
                final SAXStore freedStore = state.store;
                state.store = null;
                state.stateWasCleared = true;

                final ProcessorOutput output = getInputByName(INPUT_DATA).getOutput();
                if (logger.isDebugEnabled()) {
                    final long saxStoreSize = freedStore.getApproximateSize();
                    logger.debug("Freed SAXStore for output id: " + output.getName() + "; approximate size: " + saxStoreSize + " bytes");
                }
            }
        }

        public OutputCacheKey getKeyImpl(PipelineContext context) {
            final State state;
            try {
                state = (State) getState(context);
            } catch (OXFException e) {
                if (logger.isDebugEnabled()) {
                    logger.error("creation", creationException);
                    logger.error("reset", resetException);
                    logger.error("current processor key: " + getProcessorKey(context));
                    logger.error("reset processor key: " + resetProcessorKey);
                }
                throw e;
            }
            if (state.outputCacheKey == null) {
                final ProcessorOutput output = getInputByName(INPUT_DATA).getOutput();
                state.outputCacheKey = (output instanceof CacheableInputOutput) ? ((CacheableInputOutput) output).getKey(context) : null;
            }
            return state.outputCacheKey;
        }

        public Object getValidityImpl(PipelineContext context) {
            final State state = (State) getState(context);
            if (state.validity == null) {
                final ProcessorOutput output = getInputByName(INPUT_DATA).getOutput();
                state.validity = (output instanceof CacheableInputOutput) ? ((CacheableInputOutput) output).getValidity(context) : null;
            }
            return state.validity;
        }
    }

    public void reset(PipelineContext context) {
        if (logger.isDebugEnabled()) {
            resetException = new Exception(Integer.toString(hashCode()));
            resetProcessorKey = getProcessorKey(context);
        }
        setState(context, new State());
    }

    private static class State {
        public SAXStore store;
        public int readCount;
        public OutputCacheKey outputCacheKey;
        public Object validity;

        public boolean stateWasCleared;
    }
}
