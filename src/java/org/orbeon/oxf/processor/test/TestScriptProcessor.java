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

import org.dom4j.Document;
import org.dom4j.Element;
import org.orbeon.oxf.cache.ObjectCache;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.*;
import org.orbeon.oxf.util.PipelineUtils;
import org.orbeon.oxf.xml.XMLReceiverAdapter;
import org.orbeon.oxf.xml.XPathUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;

import java.io.File;
import java.util.Iterator;
import java.util.Map;

/**
 *
 */
public class TestScriptProcessor extends ProcessorImpl {

    public static final String TEST_NAMESPACE_URI = "http://www.orbeon.org/oxf/xml/schemas/test-processor";

    public TestScriptProcessor() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG, TEST_NAMESPACE_URI));
    }

    private static class ExecutionContext {
        public Processor mainProcessor;
        public OutputProcessor outputProcessor;
        public ExternalContext externalContext;
    }

    @Override
    public void start(PipelineContext pipelineContext) {
        // Read configuration script
        Document config = readInputAsDOM4J(pipelineContext, INPUT_CONFIG);

        // Create execution context
        ExecutionContext executionContext = new ExecutionContext();

        // Iterate through actions
        for (Iterator i = XPathUtils.selectIterator(config, "/*/*"); i.hasNext();) {
            Element commandElement = (Element) i.next();

            String commandName = commandElement.getName();

            try {
                if (commandName.equals("processor")) {
                    handleProcessorCommand(executionContext, commandElement);
                } else if (commandName.equals("cache-value")) {
                    handleCacheValueCommand(executionContext, commandElement);
                } else if (commandName.equals("assert")) {
                    handleAssertCommand(executionContext, commandElement);
                } else if (commandName.equals("touch")) {
                    handleTouchCommand(executionContext, commandElement);
                } else if (commandName.equals("wait")) {
                    handleWaitCommand(executionContext, commandElement);
                } else if (commandName.equals("read")) {
                    handleReadCommand(executionContext, commandElement);
                } else if (commandName.equals("run-processor")) {
                    handleRunCommand(executionContext, commandElement);
                } else if (commandName.equals("set-request")) {
                    handleSetRequestCommand(executionContext, commandElement, pipelineContext);
                }
            } catch (Exception e) {
                throw new ValidationException(e, (LocationData) commandElement.getData());
            }
        }
    }

    private void handleProcessorCommand(ExecutionContext executionContext, Element commandElement) {
        Processor mainProcessor = ProcessorUtils.createProcessorWithInputs(commandElement);
        mainProcessor.setLocationData((LocationData) commandElement.getData());
        mainProcessor.setId("Main Test Processor");
        executionContext.mainProcessor = mainProcessor;
        executionContext.outputProcessor = null;
    }

    private void handleCacheValueCommand(ExecutionContext executionContext, Element commandElement) {
        final String outputName = commandElement.attributeValue("output-name");
        final String value = commandElement.attributeValue("value");

        // Make sure output to read is connected
        ensureOutputConnected(executionContext, outputName);

        // Tell output processor to read and cache value
        final PipelineContext pipelineContext = new PipelineContext();
        boolean success = false;
        try {
            if (executionContext.externalContext != null)
                pipelineContext.setAttribute(PipelineContext.EXTERNAL_CONTEXT, executionContext.externalContext);
            executionContext.mainProcessor.reset(pipelineContext);
            executionContext.outputProcessor.readCacheInputWithValue(pipelineContext, outputName, value);
            success = true;
        } finally {
            pipelineContext.destroy(success);
        }
    }

    private void ensureOutputConnected(ExecutionContext executionContext, String outputName) {
        // Create output processor if needed
        if (executionContext.outputProcessor == null)
            executionContext.outputProcessor = new OutputProcessor();

        // Connect output if needed
        Map connectedOutputs = executionContext.mainProcessor.getConnectedOutputs();
        if (connectedOutputs.get(outputName) == null)
            PipelineUtils.connect(executionContext.mainProcessor, outputName, executionContext.outputProcessor, outputName);
    }

    private void handleAssertCommand(ExecutionContext executionContext, Element commandElement) {
        final String outputName = commandElement.attributeValue("output-name");
        final String condition = commandElement.attributeValue("condition");
        final String value = commandElement.attributeValue("value");

        // Make sure output to read is connected
        if (outputName != null)
            ensureOutputConnected(executionContext, outputName);

        final PipelineContext pipelineContext = new PipelineContext();
        boolean success = false;
        try {
            if (executionContext.externalContext != null)
                pipelineContext.setAttribute(PipelineContext.EXTERNAL_CONTEXT, executionContext.externalContext);
            executionContext.mainProcessor.reset(pipelineContext);

            if (condition.equals("output-cached")) {
                if (!executionContext.outputProcessor.isInputInCache(pipelineContext, outputName))
                    throw new OXFException("Assertion failed: output '" + outputName + "' is not cached, but was expected to be.");
            } else if (condition.equals("output-not-cached")) {
                if (executionContext.outputProcessor.isInputInCache(pipelineContext, outputName))
                    throw new OXFException("Assertion failed: output '" + outputName + "' is cached, but was expected not to be.");
            } else if (condition.equals("output-cacheable")) {
                if (executionContext.outputProcessor.getInputKeyValidity(pipelineContext, outputName) == null)
                    throw new OXFException("Assertion failed: output '" + outputName + "' is not cacheable, but was expected to be.");
            } else if (condition.equals("output-not-cacheable")) {
                if (executionContext.outputProcessor.getInputKeyValidity(pipelineContext, outputName) != null)
                    throw new OXFException("Assertion failed: output '" + outputName + "' is cacheable, but was expected not to be.");
            } else if (condition.equals("cached-value-equal")) {
                Object result = executionContext.outputProcessor.getCachedValue(pipelineContext, outputName);
                if (!value.equals(result))
                    throw new OXFException("Assertion failed: output '" + outputName + "' caches '" + result +  " ', but expected '" + value + "'.");
            } else if (condition.equals("cached-value-not-equal")) {
                Object result = executionContext.outputProcessor.getCachedValue(pipelineContext, outputName);
                if (value.equals(result))
                    throw new OXFException("Assertion failed: output '" + outputName + "' caches '" + result +  " ', but was expected to be different.");
            } else if (condition.equals("output-equals")) {

                final Document actualDocument = executionContext.outputProcessor.readInputAsDOM4J(pipelineContext, outputName);
                final Document expectedDocument = ProcessorUtils.createDocumentFromEmbeddedOrHref(commandElement, XPathUtils.selectStringValue(commandElement, "@href"));

                final String expectedDataString = Dom4jUtils.domToCompactString(expectedDocument);
                final String actualDataString = Dom4jUtils.domToCompactString(actualDocument);

                if (!expectedDataString.equals(actualDataString))
                    throw new OXFException("Assertion failed: output '" + outputName + "' got '" + actualDataString +  " ', but expected '" + expectedDataString + "'.");

            } else {
                throw new IllegalArgumentException("Not implemented yet.");
            }

            success = true;
        } finally {
            pipelineContext.destroy(success);
        }
    }

    private void handleTouchCommand(ExecutionContext executionContext, Element commandElement) {
        String urlAttributeValue = commandElement.attributeValue("url");
        File file = JavaProcessor.getFileFromURL(urlAttributeValue, (LocationData) commandElement.getData());
        boolean result = file.setLastModified(System.currentTimeMillis());
        if (!result)
            throw new OXFException("Setting last modified date on file '" + file.toString() + "' failed.");
    }

    private void handleWaitCommand(ExecutionContext executionContext, Element commandElement) {
        String delay = commandElement.attributeValue("delay");
        try {
            Thread.sleep(Long.parseLong(delay));
        } catch (InterruptedException e) {
            throw new OXFException(e);
        }
    }

    private void handleReadCommand(ExecutionContext executionContext, Element commandElement) {
        throw new IllegalArgumentException("Not implemented yet.");
    }

    private void handleRunCommand(ExecutionContext executionContext, Element commandElement) {
        final PipelineContext pipelineContext = new PipelineContext();
        boolean success = false;
        try {
            if (executionContext.externalContext != null)
                pipelineContext.setAttribute(PipelineContext.EXTERNAL_CONTEXT, executionContext.externalContext);
            executionContext.mainProcessor.reset(pipelineContext);
            executionContext.mainProcessor.start(pipelineContext);
            success = true;
        } finally {
            pipelineContext.destroy(success);
        }
    }

    private void handleSetRequestCommand(ExecutionContext executionContext, Element commandElement, PipelineContext pipelineContext) {

        // Build request document
        final Document requestDocument = ProcessorUtils.createDocumentFromEmbeddedOrHref(commandElement, XPathUtils.selectStringValue(commandElement, "@href"));

        // Create external context
        executionContext.externalContext = new TestExternalContext(pipelineContext, requestDocument);
    }

    private static class OutputProcessor extends ProcessorImpl {

        @Override
        public void start(PipelineContext pipelineContext) {}

        public void readCacheInputWithValue(PipelineContext pipelineContext, String inputName, final String value) {

            final ProcessorInput input = getInputByName(inputName);

            // As for KeyValidity as does ProcessorImpl.readCacheInputAsObject()
            KeyValidity keyValidity = getInputKeyValidity(pipelineContext, input);
            // Read input in every case (we ignore the content)
            readInputAsSAX(pipelineContext, input, new XMLReceiverAdapter());

            // Cache result if possible, asking again for KeyValidity if needed
            if (keyValidity == null)
                keyValidity = getInputKeyValidity(pipelineContext, input);

            if (keyValidity == null)
                throw new OXFException("Cannot cache value '" + value + "' from output '" + inputName + "'.");

            ObjectCache.instance().add(keyValidity.key, keyValidity.validity, value);
        }

        @Override
        public boolean isInputInCache(PipelineContext pipelineContext, String inputName) {
            return super.isInputInCache(pipelineContext, inputName);
        }

        @Override
        public KeyValidity getInputKeyValidity(PipelineContext context, String inputName) {
            return super.getInputKeyValidity(context, inputName);
        }

        public Object getCachedValue(PipelineContext pipelineContext, String inputName) {

            ProcessorInput input = getInputByName(inputName);

            KeyValidity keyValidity = getInputKeyValidity(pipelineContext, input);
            if (keyValidity == null)
                return null;
            return ObjectCache.instance().findValid(keyValidity.key, keyValidity.validity);
        }

        @Override
        public Document readInputAsDOM4J(PipelineContext context, String inputName) {
            return super.readInputAsDOM4J(context, inputName);
        }
    }
}
