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

import org.dom4j.Element;
import org.orbeon.oxf.cache.OutputCacheKey;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.debugger.api.BreakpointKey;
import org.orbeon.oxf.pipeline.api.*;
import org.orbeon.oxf.processor.*;
import org.orbeon.oxf.processor.generator.DOMGenerator;
import org.orbeon.oxf.processor.validation.MSVValidationProcessor;
import org.orbeon.oxf.properties.Properties;
import org.orbeon.oxf.properties.PropertySet;
import org.orbeon.oxf.util.PipelineUtils;
import org.orbeon.oxf.xml.*;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.oxf.xml.dom4j.NonLazyUserDataDocument;

/**
 * Base class for all built-in ProcessorOutput implementations.
 */
public abstract class ProcessorOutputImpl implements ProcessorOutput {

    private final Processor processor;
    private final Class processorClass;
    private final String name;

    private ProcessorInput input;
    private String id;
    private String schema;
    private String debugMessage;
    private LocationData locationData;
//    private BreakpointKey breakpointKey;

    public ProcessorOutputImpl(Class processorClass, String name) {
        this.processor = null;
        this.processorClass = processorClass;
        this.name = name;
    }

    public ProcessorOutputImpl(Processor processor, String name) {

        assert processor != null;

        this.processor = processor;
        this.processorClass = processor.getClass();
        this.name = name;
    }

    public Processor getProcessor(PipelineContext pipelineContext) {
        return processor;
    }

    public void setInput(ProcessorInput input) {
        this.input = input;
    }

    public ProcessorInput getInput() {
        return input;
    }

    public Class getProcessorClass() {
        return processorClass;
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

    public LocationData getLocationData() {
        return locationData;
    }

    public void setDebug(String debugMessage) {
        this.debugMessage = debugMessage;
    }

    public void setLocationData(LocationData locationData) {
        this.locationData = locationData;
    }

    public void setBreakpointKey(BreakpointKey breakpointKey) {
//        this.breakpointKey = breakpointKey;
    }

    /**
     * Read method that subclasses must implement.
     *
     * @param pipelineContext   context
     * @param xmlReceiver       receiver
     */
    protected abstract void readImpl(PipelineContext pipelineContext, XMLReceiver xmlReceiver);

    /**
     * Get the cache key. May be implemented by subclass.
     *
     * @param pipelineContext   context
     * @return                  cache key or null
     */
    protected OutputCacheKey getKeyImpl(PipelineContext pipelineContext) {
        return null;
    }

    /**
     * Get the cache validity. May be implemented by subclass.
     *
     * @param pipelineContext   context
     * @return                  cache validity or null
     */
    protected Object getValidityImpl(PipelineContext pipelineContext) {
        return null;
    }

    private abstract class RuntimeOutputFilter implements ProcessorOutput {

        public Processor getProcessor(PipelineContext pipelineContext) {
            return ProcessorOutputImpl.this.getProcessor(pipelineContext);
        }

        // None of the methods implemented here should be called

        public void setInput(ProcessorInput processorInput) {
            throw new OXFException("This method should never be called!");
        }

        public ProcessorInput getInput() {
            throw new OXFException("This method should never be called!");
        }

        public void setSchema(String schema) {
            throw new OXFException("This method should never be called!");
        }

        public String getSchema() {
            throw new OXFException("This method should never be called!");
        }

        public Class getProcessorClass() {
            throw new OXFException("This method should never be called!");
        }

        public String getId() {
            throw new OXFException("This method should never be called!");
        }

        public String getName() {
            throw new OXFException("This method should never be called!");
        }

        public void setDebug(String debugMessage) {
            throw new OXFException("This method should never be called!");
        }

        public String getDebugMessage() {
            throw new OXFException("This method should never be called!");
        }

        public void setLocationData(LocationData locationData) {
            throw new OXFException("This method should never be called!");
        }

        public LocationData getLocationData() {
            throw new OXFException("This method should never be called!");
        }

        public void setBreakpointKey(BreakpointKey breakpointKey) {
            throw new OXFException("This method should never be called!");
        }

        public void read(PipelineContext pipelineContext, XMLReceiver xmlReceiver) {
            throw new OXFException("This method should never be called!");
        }
    }

    /**
     * Constructor takes: (1) the input and output of a processor and (2) a
     * "previousOutput". It connects the input to the previousOutput and
     * when read, reads the output.
     *
     * Semantic: creates an output that is just like previousOutput on which
     * a processor is applies.
     */
    private class ConcreteRuntimeOutputFilter extends RuntimeOutputFilter {

        private ProcessorOutput processorOutput;
        private ProcessorOutput previousProcessorOutput;

        private class ForwarderRuntimeOutputOutput extends RuntimeOutputFilter {
            @Override
            public void read(PipelineContext pipelineContext, XMLReceiver xmlReceiver) {
                previousProcessorOutput.read(pipelineContext, xmlReceiver);
            }

            public OutputCacheKey getKey(PipelineContext pipelineContext) {
                return previousProcessorOutput.getKey(pipelineContext);
            }

            public Object getValidity(PipelineContext pipelineContext) {
                return previousProcessorOutput.getValidity(pipelineContext);
            }

        }

        public ConcreteRuntimeOutputFilter(ProcessorInput processorInput,
                                       ProcessorOutput processorOutput,
                                       final ProcessorOutput previousOutput) {
            this.processorOutput = processorOutput;
            this.previousProcessorOutput = previousOutput;
            processorInput.setOutput(new ForwarderRuntimeOutputOutput());
        }

        @Override
        public void read(PipelineContext pipelineContext, XMLReceiver xmlReceiver) {
            processorOutput.read(pipelineContext, xmlReceiver);
        }

        public OutputCacheKey getKey(PipelineContext pipelineContext) {
            return processorOutput.getKey(pipelineContext);
        }

        public Object getValidity(PipelineContext pipelineContext) {
            return processorOutput.getValidity(pipelineContext);
        }
    }

    private class TopLevelOutputFilter extends RuntimeOutputFilter {
        @Override
        public void read(PipelineContext pipelineContext, XMLReceiver xmlReceiver) {
            // Read the current processor output
            readImpl(pipelineContext, xmlReceiver);
        }

        public OutputCacheKey getKey(PipelineContext pipelineContext) {
            return getKeyImpl(pipelineContext);
        }

        public Object getValidity(PipelineContext pipelineContext) {
            return getValidityImpl(pipelineContext);
        }
    };

    private RuntimeOutputFilter createFilter() {

        // Get inspector instance

        // Final filter (i.e. at the top, executed last)
        RuntimeOutputFilter outputFilter = new TopLevelOutputFilter();

        // Handle debug
        if (getDebugMessage() != null || (getInput() != null && getInput().getDebugMessage() != null)) {
            ProcessorFactory debugProcessorFactory = ProcessorFactoryRegistry.lookup(XMLConstants.DEBUG_PROCESSOR_QNAME);
            if (debugProcessorFactory == null)
                throw new OXFException("Cannot find debug processor factory for QName: " + XMLConstants.DEBUG_PROCESSOR_QNAME);

            for (int i = 0; i < 2; i++) {
                String debugMessage = i == 0 ? getDebugMessage() :
                        getInput() == null ? null : getInput().getDebugMessage();
                LocationData debugLocationData = i == 0 ? getLocationData() :
                        getInput() == null ? null : getInput().getLocationData();
                if (debugMessage != null) {
                    Processor debugProcessor = debugProcessorFactory.createInstance();
                    debugProcessor.createInput(ProcessorImpl.INPUT_DATA);
                    debugProcessor.createOutput(ProcessorImpl.OUTPUT_DATA);

                    // Create config document for Debug processor
                    final org.dom4j.Document debugConfigDocument;
                    {
                        debugConfigDocument = new NonLazyUserDataDocument();
                        Element configElement = debugConfigDocument.addElement("config");
                        configElement.addElement("message").addText(debugMessage);
                        if (debugLocationData != null) {
                            Element systemIdElement = configElement.addElement("system-id");
                            if (debugLocationData.getSystemID() != null)
                                systemIdElement.addText(debugLocationData.getSystemID());
                            configElement.addElement("line").addText(Integer.toString(debugLocationData.getLine()));
                            configElement.addElement("column").addText(Integer.toString(debugLocationData.getCol()));
                        }
                    }
                    final DOMGenerator dg = new DOMGenerator
                        ( debugConfigDocument, "debug filter"
                          , DOMGenerator.ZeroValidity, DOMGenerator.DefaultContext );
                    PipelineUtils.connect( dg, "data", debugProcessor, "config");
                    final ProcessorOutput dbgOut
                        = debugProcessor.getOutputByName( ProcessorImpl.OUTPUT_DATA );
                    final ProcessorInput dbgIn = debugProcessor.getInputByName( ProcessorImpl.INPUT_DATA );
                    outputFilter = new ConcreteRuntimeOutputFilter( dbgIn, dbgOut, outputFilter);
                }
            }
        }

        // The PropertySet can be null during properties initialization. This should be one of the
        // rare places where this should be tested on.
        final PropertySet propertySet = Properties.instance().getPropertySet();

        // Create and hook-up output validation processor if needed
        final Boolean isUserValidation = (propertySet == null) ? null : propertySet.getBoolean(ProcessorImpl.USER_VALIDATION_FLAG, true);
        if (isUserValidation != null && isUserValidation.booleanValue() && getSchema() != null) {
            final Processor outputValidator = new MSVValidationProcessor(getSchema());
            // Create data input and output
            final ProcessorInput input = outputValidator.createInput(ProcessorImpl.INPUT_DATA);
            final ProcessorOutput output = outputValidator.createOutput(ProcessorImpl.OUTPUT_DATA);
            // Create and connect config input
            final Processor resourceGenerator = PipelineUtils.createURLGenerator(getSchema());
            PipelineUtils.connect(resourceGenerator, ProcessorImpl.OUTPUT_DATA, outputValidator, MSVValidationProcessor.INPUT_SCHEMA);
            PipelineUtils.connect(MSVValidationProcessor.NO_DECORATION_CONFIG, ProcessorImpl.OUTPUT_DATA,
                    outputValidator, ProcessorImpl.INPUT_CONFIG);
            outputFilter = new ConcreteRuntimeOutputFilter(input, output, outputFilter);
        }

        // Hook-up input validation processor if needed
        if (isUserValidation != null && isUserValidation.booleanValue() &&
                getInput() != null && getInput().getSchema() != null) {
            final Processor inputValidator = new MSVValidationProcessor(getInput().getSchema());
            // Create data input and output
            final ProcessorInput input = inputValidator.createInput(ProcessorImpl.INPUT_DATA);
            final ProcessorOutput output = inputValidator.createOutput(ProcessorImpl.OUTPUT_DATA);
            // Create and connect config input

            final Processor resourceGenerator = PipelineUtils.createURLGenerator(getInput().getSchema());
            PipelineUtils.connect(resourceGenerator, ProcessorImpl.OUTPUT_DATA, inputValidator, MSVValidationProcessor.INPUT_SCHEMA);
            PipelineUtils.connect(MSVValidationProcessor.NO_DECORATION_CONFIG, ProcessorImpl.OUTPUT_DATA,
                inputValidator, ProcessorImpl.INPUT_CONFIG);
            outputFilter = new ConcreteRuntimeOutputFilter(input, output, outputFilter);
        }

        // Perform basic inspection of SAX events
        Boolean isSAXInspection = (propertySet == null) ? null : propertySet.getBoolean(ProcessorImpl.SAX_INSPECTION_FLAG, false);
        if (isSAXInspection != null && isSAXInspection.booleanValue()) {
            final RuntimeOutputFilter previousOutputFilter = outputFilter;
            outputFilter = new RuntimeOutputFilter() {
                @Override
                public void read(PipelineContext pipelineContext, XMLReceiver xmlReceiver) {
                    InspectingContentHandler inspectingContentHandler = new InspectingContentHandler(xmlReceiver);
                    previousOutputFilter.read(pipelineContext, inspectingContentHandler);
                }

                public OutputCacheKey getKey(PipelineContext pipelineContext) {
                    return previousOutputFilter.getKey(pipelineContext);
                }

                public Object getValidity(PipelineContext pipelineContext) {
                    return previousOutputFilter.getValidity(pipelineContext);
                }
            };
        }

        return outputFilter;
    }

    /**
     * NOTE: We should never use processor instance variables. Here, the creation may not be thread safe in that
     * the filter may be initialized several times. This should not be a real problem, and the execution should not
     * be problematic either. It may be safer to synchronize getRuntimeFilter().
     */
    private RuntimeOutputFilter outputFilter = null;

    private RuntimeOutputFilter getRuntimeFilter() {
        if (outputFilter == null)
            outputFilter = createFilter();
        return outputFilter;
    }

    public final void read(PipelineContext pipelineContext, XMLReceiver xmlReceiver) {
        TraceEntry traceEntry = null;
        try {
            // Update trace if needed
            if (pipelineContext.getTraceForUpdate() != null) {
                traceEntry = pipelineContext.getTraceForUpdate().getTraceEntry(this);
                traceEntry.outputReadCalled();
            }

            // Delegate
            getRuntimeFilter().read(pipelineContext, xmlReceiver);
        } catch (Exception e) {
            throw ValidationException.wrapException(e, getLocationData());
        } finally {
            if (traceEntry != null)
                traceEntry.end = System.nanoTime();
        }

        // Ensure getKey() is called if read() is called()
        if (traceEntry != null && !traceEntry.outputGetKeyCalled) {
            getKey(pipelineContext);
        }
    }

    public final OutputCacheKey getKey(PipelineContext pipelineContext) {
        return getRuntimeFilter().getKey(pipelineContext);
//        // In debug mode, force read of input if not read yet
//        if (pipelineContext.isKeyDebugging() && pipelineContext.getTraceForUpdate() != null) {
//            final TraceEntry traceEntry = pipelineContext.getTraceForUpdate().getTraceEntry(this);
//            if (!traceEntry.outputReadCalled)
//                read(pipelineContext, new XMLReceiverAdapter());
//        }
//
//        // Delegate
//        final OutputCacheKey outputCacheKey = getRuntimeFilter().getKey(pipelineContext);
//
//        // Update trace if needed
//        if (pipelineContext.isKeyDebugging() && pipelineContext.getTraceForUpdate() != null) {
//            final TraceEntry traceEntry = pipelineContext.getTraceForUpdate().getTraceEntry(this);
//            traceEntry.outputGetKeyCalled(outputCacheKey == null);
//        }
//
//        return outputCacheKey;
    }

    public final Object getValidity(PipelineContext pipelineContext) {
        return getRuntimeFilter().getValidity(pipelineContext);
    }
    
    public void toXML(PipelineContext pipelineContext, ContentHandlerHelper helper) {

        final ProcessorInput input = getInput();

        // Input connected to the output
        if (input != null) {
            helper.startElement("input", new String[] {
                    "name", input.getName(),
                    "processor-class", input.getProcessorClass() != null ? input.getProcessorClass().getName() : null,
                    "processor-name", input.getProcessor(pipelineContext) != null && input.getProcessor(pipelineContext).getName() != null ? input.getProcessor(pipelineContext).getName().getQualifiedName() : null,
                    "processor-object", input.getProcessor(pipelineContext) != null ? Integer.toString(input.getProcessor(pipelineContext).getSequenceNumber()) : null,

                    "system-id", (input.getLocationData() != null) ? input.getLocationData().getSystemID() : null,
                    "line", (input.getLocationData() != null) ? Integer.toString(input.getLocationData().getLine()) : null,
                    "column", (input.getLocationData() != null) ? Integer.toString(input.getLocationData().getCol()) : null
            });
        }

        // Output connected to the input
        helper.element("output", new String[] {
                "id", getId(),

                "name", getName(),
                "processor-class", getProcessorClass() != null ? getProcessorClass().getName() : null,
                "processor-name", getProcessor(pipelineContext) != null && getProcessor(pipelineContext).getName() != null ? getProcessor(pipelineContext).getName().getQualifiedName() : null,
                "processor-object", getProcessor(pipelineContext) != null ? Integer.toString(getProcessor(pipelineContext).getSequenceNumber()) : null,

                "system-id", (getLocationData() != null) ? getLocationData().getSystemID() : null,
                "line", (getLocationData() != null) ? Integer.toString(getLocationData().getLine()) : null,
                "column", (getLocationData() != null) ? Integer.toString(getLocationData().getCol()) : null,

                "schema", getSchema(),
                "debug", getDebugMessage()
        });

        if (input != null) {
            helper.endElement();
        }
    }
}
