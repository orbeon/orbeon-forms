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
package org.orbeon.oxf.processor.impl;

import org.orbeon.oxf.cache.*;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.processor.ProcessorInput;

import java.util.*;

/**
 * Implementation of a caching transformer output that assumes that an output simply depends on
 * all the inputs plus optional local information.
 *
 * It is possible to implement local key and validity information as well, that represent data
 * not coming from an XML input. If any input is connected to an output that is not cacheable,
 * a null key is returned.
 *
 * Use DigestTransformerOutputImpl whenever possible.
 */
public abstract class CacheableTransformerOutputImpl extends ProcessorOutputImpl {

    public CacheableTransformerOutputImpl(ProcessorImpl processor, String name) {
        super(processor, name);
    }

    /**
     * Processor outputs that use the local key/validity feature must
     * override this method and return true.
     */
    protected boolean supportsLocalKeyValidity() {
        return false;
    }

    protected CacheKey getLocalKey(PipelineContext pipelineContext) {
        throw new UnsupportedOperationException();
    }

    protected Object getLocalValidity(PipelineContext pipelineContext) {
        throw new UnsupportedOperationException();
    }

    @Override
    public OutputCacheKey getKeyImpl(PipelineContext pipelineContext) {

        // NOTE: This implementation assumes that there is only one input with a given name

        // Create input information
        final Collection<List<ProcessorInput>> connectedInputs = getProcessor(pipelineContext).getConnectedInputs().values();
        final int keyCount = connectedInputs.size() + (supportsLocalKeyValidity() ? 1 : 0);
        final CacheKey[] outputKeys = new CacheKey[keyCount];

        int keyIndex = 0;
        for (final List<ProcessorInput> inputs : connectedInputs) {
            for (final ProcessorInput input : inputs) {
                final OutputCacheKey outputKey = ProcessorImpl.getInputKey(pipelineContext, input);
                if (outputKey == null) {
                    return null;
                }
                outputKeys[keyIndex++] = outputKey;
            }
        }

        // Add local key if needed
        if (supportsLocalKeyValidity()) {
            final CacheKey localKey = getLocalKey(pipelineContext);
            if (localKey == null) return null;
            outputKeys[keyIndex++] = localKey;
        }

        // Concatenate current processor info and input info
        final Class processorClass = getProcessorClass();
        final String outputName = getName();
        return new CompoundOutputCacheKey(processorClass, outputName, outputKeys);
    }

    @Override
    public Object getValidityImpl(PipelineContext pipelineContext) {
        final List<Object> validityObjects = new ArrayList<Object>();

        for (final List<ProcessorInput> inputs : getProcessor(pipelineContext).getConnectedInputs().values()) {
            for (final ProcessorInput input : inputs) {
                final Object validity = ProcessorImpl.getInputValidity(pipelineContext, input);
                if (validity == null)
                    return null;
                validityObjects.add(validity);
            }
        }

        // Add local validity if needed
        if (supportsLocalKeyValidity()) {
            final Object localValidity = getLocalValidity(pipelineContext);
            if (localValidity == null) return null;
            validityObjects.add(localValidity);
        }

        return validityObjects;
    }
}
