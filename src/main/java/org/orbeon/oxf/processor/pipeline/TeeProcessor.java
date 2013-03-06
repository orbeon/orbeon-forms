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
import org.orbeon.oxf.cache.OutputCacheKey;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.pipeline.api.XMLReceiver;
import org.orbeon.oxf.processor.*;
import org.orbeon.oxf.processor.impl.ProcessorOutputImpl;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.xml.SAXStore;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.xml.sax.SAXException;

/**
 * This internal processor handles the tee-ing functionality of XPL, i.e. sending an XML infoset to multiple readers.
 */
public class TeeProcessor extends ProcessorImpl {

    private static final Logger logger = LoggerFactory.createLogger(TeeProcessor.class);
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
    @Override
    public ProcessorOutput createOutput(String name) {
        return createOutput(name, false);
    }

    /**
     * Tee-specific createOutput().
     */
    public ProcessorOutput createOutput(String name, boolean isMultipleReads) {
        final ProcessorOutput output = new TeeProcessorOutputImpl(name, isMultipleReads);
        addOutput(name, output);
        return output;
    }

    public class TeeProcessorOutputImpl extends ProcessorOutputImpl {

        private boolean isMultipleReads;

        private TeeProcessorOutputImpl(String name, boolean isMultipleReads) {
            super(TeeProcessor.this, name);
            this.isMultipleReads = isMultipleReads;

        }

        @Override
        public void readImpl(PipelineContext context, XMLReceiver xmlReceiver) {
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
                    state.store = new SAXStore(xmlReceiver);
                    readInputAsSAX(context, input, state.store);
                } else {
                    state.store.replay(xmlReceiver);
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

        @Override
        public OutputCacheKey getKeyImpl(PipelineContext pipelineContext) {
            final State state;
            try {
                state = (State) getState(pipelineContext);
            } catch (OXFException e) {
                if (logger.isDebugEnabled()) {
                    logger.error("creation", creationException);
                    logger.error("reset", resetException);
                    logger.error("current processor key: " + getProcessorKey(pipelineContext));
                    logger.error("reset processor key: " + resetProcessorKey);
                }
                throw e;
            }
            if (state.outputCacheKey == null) {
                final ProcessorOutput output = getInputByName(INPUT_DATA).getOutput();
                state.outputCacheKey = output.getKey(pipelineContext);
            }
            return state.outputCacheKey;
        }

        @Override
        public Object getValidityImpl(PipelineContext pipelineContext) {
            final State state = (State) getState(pipelineContext);
            if (state.validity == null) {
                final ProcessorOutput output = getInputByName(INPUT_DATA).getOutput();
                state.validity = output.getValidity(pipelineContext);
            }
            return state.validity;
        }
    }

    @Override
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
