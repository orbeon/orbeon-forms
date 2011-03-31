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
import org.orbeon.oxf.util.NumberUtils;

import java.util.Arrays;

/**
 * Implementation of a caching transformer output that assumes that an output simply depends on
 * all the inputs plus optional local information that can be digested.
 */
public abstract class DigestTransformerOutputImpl extends CacheableTransformerOutputImpl {

    private static final Long DEFAULT_VALIDITY = 0L;

    public DigestTransformerOutputImpl(ProcessorImpl processor, String name) {
        super(processor, name);
    }

    @Override
    protected final boolean supportsLocalKeyValidity() {
        return true;
    }

    @Override
    protected CacheKey getLocalKey(PipelineContext pipelineContext) {
        for (final String inputName : getProcessor(pipelineContext).getInputNames()) {
            if (!getProcessor(pipelineContext).isInputInCache(pipelineContext, inputName))// NOTE: We don't really support multiple inputs with the same name.
                return null;
        }
        return getFilledOutState(pipelineContext).key;
    }

    @Override
    protected final Object getLocalValidity(PipelineContext pipelineContext) {
        for (final String inputName : getProcessor(pipelineContext).getInputNames()) {
            if (!getProcessor(pipelineContext).isInputInCache(pipelineContext, inputName))// NOTE: We don't really support multiple inputs with the same name.
                return null;
        }
        return getFilledOutState(pipelineContext).validity;
    }

    /**
     * Fill-out user data into the state, if needed. Return caching information.
     *
     * @param pipelineContext the current PipelineContext
     * @param digestState state set during processor start() or reset()
     * @return false if private information is known that requires disabling caching, true otherwise
     */
    protected abstract boolean fillOutState(PipelineContext pipelineContext, DigestState digestState);

    /**
     * Compute a digest of the internal document on which the output depends.
     *
     * @param digestState state set during processor start() or reset()
     * @return the digest
     */
    protected abstract byte[] computeDigest(PipelineContext pipelineContext, DigestState digestState);

    protected final DigestState getFilledOutState(PipelineContext pipelineContext) {
        // This is called from both readImpl and getLocalValidity. Based on the assumption that
        // a getKeyImpl will be followed soon by a readImpl if it fails, we compute key,
        // validity, and user-defined data.

        final DigestState state = (DigestState) getProcessor(pipelineContext).getState(pipelineContext);

        // Create request document
        final boolean allowCaching = fillOutState(pipelineContext, state);

        // Compute key and validity if possible
        if ((state.validity == null || state.key == null) && allowCaching) {
            // Compute digest
            if (state.digest == null) {
                state.digest = computeDigest(pipelineContext, state);
            }
            // Compute local key
            if (state.key == null) {
                state.key = new InternalCacheKey(getProcessor(pipelineContext), "requestHash", NumberUtils.toHexString(state.digest));
            }
            // Compute local validity
            if (state.validity == null) {
                state.validity = DEFAULT_VALIDITY; // HACK so we don't recurse at the next line
                final OutputCacheKey outputCacheKey = getKeyImpl(pipelineContext);
                if (outputCacheKey != null) {
                    final Cache cache = ObjectCache.instance();
                    final DigestValidity digestValidity = (DigestValidity) cache.findValid(outputCacheKey, DEFAULT_VALIDITY);
                    if (digestValidity != null && Arrays.equals(state.digest, digestValidity.digest)) {
                        state.validity = digestValidity.lastModified;
                    } else {
                        final Long currentValidity = new Long(System.currentTimeMillis());
                        cache.add(outputCacheKey, DEFAULT_VALIDITY, new DigestValidity(state.digest, currentValidity));
                        state.validity = currentValidity;
                    }
                } else {
                    state.validity = null; // HACK restore
                }
            }
        }

        return state;
    }
}
