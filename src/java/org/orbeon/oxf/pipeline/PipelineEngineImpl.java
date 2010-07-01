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
package org.orbeon.oxf.pipeline;

import org.apache.log4j.Logger;
import org.orbeon.oxf.pipeline.api.*;

/**
 * Default implementation of the PipelineEngine interface.
 */
public class PipelineEngineImpl implements PipelineEngine {
    public void executePipeline(ProcessorDefinition processorDefinition, ExternalContext externalContext, PipelineContext pipelineContext, Logger logger) throws Exception {
        // Register processor definitions with the default XML Processor Registry. This defines the
        // mapping of processor names to class names.
        InitUtils.initializeProcessorDefinitions();
        // Run the processor
        InitUtils.runProcessor(InitUtils.createProcessor(processorDefinition), externalContext, pipelineContext, logger);
    }
}
