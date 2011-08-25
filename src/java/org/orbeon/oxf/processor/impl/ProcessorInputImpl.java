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
import org.orbeon.oxf.processor.ProcessorInput;
import org.orbeon.oxf.processor.ProcessorOutput;
import org.orbeon.oxf.xml.dom4j.LocationData;

/**
 * Basic implementation of ProcessorInput.
 */
public class ProcessorInputImpl implements ProcessorInput {

    private final Processor processor;
    private final Class clazz;
    private final String name;

    private ProcessorOutput output;
    private String id;
    private String schema;
    private String debugMessage;
    private LocationData locationData;
    private String systemId;

    public ProcessorInputImpl(Class clazz, String name) {
        this.processor = null;
        this.clazz = clazz;
        this.name = name;
    }

    public ProcessorInputImpl(Processor processor, String name) {

        assert processor != null;

        this.processor = processor;
        this.clazz = processor.getClass();
        this.name = name;
    }

    public Processor getProcessor(PipelineContext pipelineContext) {
        return processor;
    }

    public ProcessorOutput getOutput() {
        return output;
    }

    public void setOutput(ProcessorOutput output) {
        this.output = output;
    }

    public Class getProcessorClass() {
        return clazz;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    public String getDebugMessage() {
        return debugMessage;
    }

    public void setLocationData(LocationData locationData) {
        this.locationData = locationData;
    }

    public LocationData getLocationData() {
        return locationData;
    }

    public void setDebug(String debugMessage) {
        this.debugMessage = debugMessage;
    }

    public String getSystemId() {
        return systemId;
    }

    public void setSystemId(String systemId) {
        this.systemId = systemId;
    }
}
