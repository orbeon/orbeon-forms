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
import org.orbeon.oxf.processor.Processor;
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.processor.ProcessorInput;
import org.orbeon.oxf.processor.ProcessorOutput;
import org.orbeon.oxf.xml.dom4j.LocationData;

public class DelegatingProcessorInput implements ProcessorInput {

    private final String originalName;
    private ProcessorInput delegateInput;
    private ProcessorOutput delegateOutput;
    private ProcessorImpl processor;

    public DelegatingProcessorInput(ProcessorImpl processor, String originalName, ProcessorInput delegateInput, ProcessorOutput delegateOutput) {
        this.processor = processor;
        this.originalName = originalName;
        this.delegateInput = delegateInput;
        this.delegateOutput = delegateOutput;
    }

    DelegatingProcessorInput(ProcessorImpl processor, String originalName) {
        this.processor = processor;
        this.originalName = originalName;
    }

    public Processor getProcessor(PipelineContext pipelineContext) {
        return processor;
    }

    public void setDelegateInput(ProcessorInput delegateInput) {
        this.delegateInput = delegateInput;
    }

    public void setDelegateOutput(ProcessorOutput delegateOutput) {
        this.delegateOutput = delegateOutput;
    }

    public void setOutput(ProcessorOutput output) {
        delegateInput.setOutput(output);
    }

    public ProcessorOutput getOutput() {
        // Not sure why the input validation stuff expects another output here. For now, allow caller to specify
        // which output is returned. Once we are confident, switch to delegateInput.getOutput().
        return delegateOutput;
//            return delegateInput.getOutput();
    }

    public String getSchema() {
        return delegateInput.getSchema();
    }

    public void setSchema(String schema) {
        delegateInput.setSchema(schema);
    }

    public Class getProcessorClass() {
        return processor.getClass();
    }

    public String getName() {
        return originalName;
    }

    public void setDebug(String debugMessage) {
        delegateInput.setDebug(debugMessage);
    }

    public void setLocationData(LocationData locationData) {
        delegateInput.setLocationData(locationData);
    }

    public String getDebugMessage() {
        return delegateInput.getDebugMessage();
    }

    public LocationData getLocationData() {
        return delegateInput.getLocationData();
    }
}
