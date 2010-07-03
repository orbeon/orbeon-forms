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
package org.orbeon.oxf.processor.test;

import org.apache.log4j.Logger;
import org.orbeon.oxf.cache.InputCacheKey;
import org.orbeon.oxf.cache.OutputCacheKey;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.pipeline.api.XMLReceiver;
import org.orbeon.oxf.processor.*;
import org.orbeon.oxf.processor.impl.ProcessorOutputImpl;
import org.orbeon.oxf.util.LoggerFactory;

public class CacheViewProcessor extends ProcessorImpl {

    private static Logger logger = LoggerFactory.createLogger(CacheViewProcessor.class);

    public CacheViewProcessor() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_DATA));
        addInputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    @Override
    public ProcessorOutput createOutput(final String name) {
        final ProcessorOutput output = new ProcessorOutputImpl(CacheViewProcessor.this, name) {
            public void readImpl(PipelineContext context, XMLReceiver xmlReceiver) {

                final ProcessorInput input = getInputByName(INPUT_DATA);

                final OutputCacheKey outputCacheKey = getInputKey(context, input);
                if (outputCacheKey != null) {
                    final InputCacheKey inputCacheKey = new InputCacheKey(input, outputCacheKey);

                    logger.info(inputCacheKey);

                    final Object inputValidity = getInputValidity(context, input);
                    if (inputValidity != null) {
                        logger.info(inputValidity);
                    } else {
                        logger.info("validity is null");
                    }
                } else {
                    logger.info("key is null");
                }
                
                readInputAsSAX(context, INPUT_DATA, xmlReceiver);
            }

            @Override
            public OutputCacheKey getKeyImpl(PipelineContext pipelineContext) {
                return getInputKey(pipelineContext, getInputByName(INPUT_DATA));
            }

            @Override
            public Object getValidityImpl(PipelineContext pipelineContext) {
                return getInputValidity(pipelineContext, getInputByName(INPUT_DATA));
            }
        };
        addOutput(name, output);
        return output;
    }
}
