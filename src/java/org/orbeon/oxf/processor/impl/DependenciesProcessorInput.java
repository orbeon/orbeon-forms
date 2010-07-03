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

import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.pipeline.api.XMLReceiver;
import org.orbeon.oxf.processor.*;

/**
 * This special input is able to handle dependencies on URLs.
 */
public abstract class DependenciesProcessorInput extends DelegatingProcessorInput {

    public DependenciesProcessorInput(ProcessorImpl processor, String originalName, ProcessorInput originalInput) {
        super(processor, originalName);

        // Custom processor handling input dependencies
        final ProcessorImpl dependencyProcessor = new ProcessorImpl() {
            @Override
            public ProcessorOutput createOutput(String outputName) {
                final ProcessorOutput output = new URIProcessorOutputImpl(this, outputName, INPUT_CONFIG) {
                    @Override
                    protected void readImpl(PipelineContext pipelineContext, final XMLReceiver xmlReceiver) {
                        final boolean[] foundInCache = new boolean[] { false };
                        readCacheInputAsObject(pipelineContext, getInputByName(INPUT_CONFIG), new CacheableInputReader() {
                            @Override
                            public Object read(PipelineContext pipelineContext, ProcessorInput processorInput) {
                                // Read the input directly into the output
                                readInputAsSAX(pipelineContext, processorInput, xmlReceiver);

                                // Return dependencies object
                                return getURIReferences(pipelineContext);
                            }

                            @Override
                            public void foundInCache() {
                                foundInCache[0] = true;
                            }
                        });

                        // Finding the dependencies in cache doesn't mean we don't read to the output: after all,
                        // we were asked to.
                        if (foundInCache[0]) {
                            readInputAsSAX(pipelineContext, getInputByName(INPUT_CONFIG), xmlReceiver);
                        }
                    }
                };
                addOutput(outputName, output);
                return output;
            }
        };

        // Create data input and output
        final ProcessorInput dependencyInput = dependencyProcessor.createInput(ProcessorImpl.INPUT_CONFIG);
        final ProcessorOutput dependencyOutput = dependencyProcessor.createOutput(ProcessorImpl.OUTPUT_DATA);

        setDelegateInput(dependencyInput);
        setDelegateOutput(dependencyOutput);

        // Connect output of dependency processor to original input
        {
            dependencyOutput.setInput(originalInput);
            originalInput.setOutput(dependencyOutput);
        }
    }

    /**
     * Get URI references on which this input depends. This is called right after the original input has been read.
     *
     * @param pipelineContext   current context
     * @return                  URI references
     */
    protected abstract URIProcessorOutputImpl.URIReferences getURIReferences(PipelineContext pipelineContext);
}
