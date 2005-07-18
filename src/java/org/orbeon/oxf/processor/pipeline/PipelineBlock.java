/**
 *  Copyright (C) 2004 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.processor.pipeline;

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.Node;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.debugger.api.BreakpointKey;
import org.orbeon.oxf.processor.Processor;
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.processor.ProcessorInput;
import org.orbeon.oxf.processor.ProcessorOutput;
import org.orbeon.oxf.processor.generator.DOMGenerator;
import org.orbeon.oxf.processor.generator.URLGenerator;
import org.orbeon.oxf.processor.pipeline.ast.*;
import org.orbeon.oxf.processor.transformer.XPathProcessor;
import org.orbeon.oxf.resources.URLFactory;
import org.orbeon.oxf.util.PipelineUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.oxf.xml.dom4j.NonLazyUserDataDocument;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

/**
 * This class encapsulates the data structures visible from a pipeline.
 */
public class PipelineBlock {

    // Maps: (String outputId) -> (ProcessorOutput)
    private Map idToOutputMap = new HashMap();
    // Maps: (String paramOutputId) -> (ProcessorInput)
    private Map idToInputMap = new HashMap();
    // Maps: (String outputId) -> (TeeProcessor)
    private Map outputIdToTeeProcessor = new HashMap();
    // Set of (String paramOutputId)
    private Set inputIdAlreadyConnected = new HashSet();
    // Set of processors we create in the class for connection purposes
    private Set createdProcessors = new HashSet();

    public void declareOutput(Node node, String id, ProcessorOutput output) {
        if (idToOutputMap.containsKey(id)) {
            LocationData locationData = node == null ? null : (LocationData) ((Element) node).getData();
            throw new ValidationException("Output id \"" + id + "\" is already declared in pipeline", locationData);
        }
        idToOutputMap.put(id, output);
    }

    public ProcessorInput connectProcessorToHref(Node node, Processor processor, String inputName, ASTHref href) {

        LocationData locationData = node == null ? null : (LocationData) ((Element) node).getData();
        ProcessorInput processorInput = processor.createInput(inputName);

        if (href instanceof ASTHrefId) {

            String referencedId = ((ASTHrefId) href).getId();

            // Nice messsage for current()
            if (referencedId.equals(ForEachProcessor.CURRENT) && !idToOutputMap.containsKey(ForEachProcessor.CURRENT))
                throw new ValidationException("Function current() can only be used in a for-each block", locationData);

            // Reference to previously defined id
            if (!idToOutputMap.containsKey(referencedId))
                throw new ValidationException("Reference to undeclared output id \"" + referencedId + "\"", locationData);
            ProcessorOutput referencedOutput = (ProcessorOutput) idToOutputMap.get(referencedId);

            if (referencedOutput.getInput() == null) {
                // Output is virgin: just connect it to this processor
                processorInput.setOutput(referencedOutput);
                referencedOutput.setInput(processorInput);
            } else if (outputIdToTeeProcessor.containsKey(referencedId)) {
                // ASTInput already shared: just add ourselves to the tee
                Processor tee = (TeeProcessor) outputIdToTeeProcessor.get(referencedId);
                final LocationData locDat = Dom4jUtils.getLocationData();
                final BreakpointKey bptKey = new BreakpointKey( locDat );
                ProcessorOutput teeOutput = tee.createOutput(ProcessorImpl.OUTPUT_DATA);
                teeOutput.setBreakpointKey( bptKey );
                teeOutput.setInput(processorInput);
                processorInput.setOutput(teeOutput);
            } else {
                // Force menage a trois by introducing a tee
                Processor tee = new TeeProcessor(locationData);
                createdProcessors.add(tee);
                outputIdToTeeProcessor.put(referencedId, tee);

                // Reconnect the "other guy" to the tee output
                final LocationData frstLocDat = Dom4jUtils.getLocationData();
                final BreakpointKey frstBptKey = new BreakpointKey( frstLocDat );
                ProcessorOutput firstTeeOutput = tee.createOutput(ProcessorImpl.OUTPUT_DATA);
                firstTeeOutput.setBreakpointKey( frstBptKey );
                ProcessorInput otherGuyInput = referencedOutput.getInput();
                firstTeeOutput.setInput(otherGuyInput);
                otherGuyInput.setOutput(firstTeeOutput);

                // Connect tee to processor
                final LocationData scndLocDat = Dom4jUtils.getLocationData();
                final BreakpointKey scndBptKey = new BreakpointKey( scndLocDat );
                ProcessorOutput secondTeeOutput = tee.createOutput(ProcessorImpl.OUTPUT_DATA);
                secondTeeOutput.setBreakpointKey( scndBptKey );
                secondTeeOutput.setInput(processorInput);
                processorInput.setOutput(secondTeeOutput);

                // Connect tee input
                ProcessorInput teeInput = tee.createInput(ProcessorImpl.INPUT_DATA);
                teeInput.setOutput(referencedOutput);
                referencedOutput.setInput(teeInput);
            }

        } else if (href instanceof ASTHrefAggregate) {

            ASTHrefAggregate hrefAggregate = (ASTHrefAggregate) href;

            // Connect aggregator to config
            Processor aggregator = new AggregatorProcessor();
            Document aggregatorConfig = new NonLazyUserDataDocument();
            Element configElement = aggregatorConfig.addElement("config");
            configElement.addElement("root").addText(hrefAggregate.getRoot());
            addNamespaces(configElement, node);

            final String sid = locationData == null 
                             ? DOMGenerator.DefaultContext 
                             : locationData.getSystemID();
            final DOMGenerator configGenerator = new DOMGenerator
                ( aggregatorConfig, "aggregate config", DOMGenerator.ZeroValidity, sid );
            PipelineUtils.connect(configGenerator, ProcessorImpl.OUTPUT_DATA, aggregator, ProcessorImpl.INPUT_CONFIG);

            // Connect data input to aggregator
            for (Iterator i = hrefAggregate.getHrefs().iterator(); i.hasNext();) {
                ASTHref id = (ASTHref) i.next();
                connectProcessorToHref(node, aggregator, ProcessorImpl.INPUT_DATA, id);
            }

            // Connect aggregator output to current processor
            ProcessorOutput aggregatorOutput = aggregator.createOutput(ProcessorImpl.OUTPUT_DATA);
            aggregatorOutput.setInput(processorInput);
            processorInput.setOutput(aggregatorOutput);

        } else if (href instanceof ASTHrefURL) {
            try {
                // Get the docbase url from the location data if available
                // and concatenate it with the current href
                URL url = URLFactory.createURL(locationData != null && locationData.getSystemID() != null ?
                        locationData.getSystemID() : null,
                        ((ASTHrefURL) href).getURL());

                // This is interpreted as a URL, use URL Generator
                Processor urlGenerator = new URLGenerator(url.toExternalForm());
                ProcessorOutput referencedOutput = urlGenerator.createOutput(ProcessorImpl.OUTPUT_DATA);
                referencedOutput.setInput(processorInput);
                processorInput.setOutput(referencedOutput);
            } catch (MalformedURLException e) {
                throw new ValidationException(e, locationData);
            }

        } else if (href instanceof ASTHrefXPointer) {

            ASTHrefXPointer hrefXPointer = (ASTHrefXPointer) href;

            // Create config for XPath processor
            final Document xpthCfg = new NonLazyUserDataDocument();
            final Element configElement = xpthCfg.addElement("config");
            configElement.addElement("xpath").addText(hrefXPointer.getXpath());
            addNamespaces(configElement, node);

            // Connect XPath processor to config
            Processor xpathProcessor = new XPathProcessor();
            xpathProcessor.setLocationData(locationData);
            final String sid = locationData == null 
                             ? DOMGenerator.DefaultContext 
                             : locationData.getSystemID();
            final DOMGenerator configGenerator = new DOMGenerator
                ( xpthCfg, "xpath config", DOMGenerator.ZeroValidity, sid );
            PipelineUtils.connect(configGenerator, ProcessorImpl.OUTPUT_DATA, xpathProcessor, ProcessorImpl.INPUT_CONFIG);

            // Connect data input to XPath processor
            connectProcessorToHref(node, xpathProcessor, ProcessorImpl.INPUT_DATA, hrefXPointer.getHref());

            // Connect XPath processor output to current processor
            ProcessorOutput xpathOutput = xpathProcessor.createOutput(ProcessorImpl.OUTPUT_DATA);
            xpathOutput.setInput(processorInput);
            processorInput.setOutput(xpathOutput);
        } else {
            throw new ValidationException("Unsupported href type", locationData);
        }

        return processorInput;
    }

    private void addNamespaces(Element configElement, Node node) {
        if (node != null) {
            Map namespaces = Dom4jUtils.getNamespaceContext((Element) node);
            for (Iterator i = namespaces.keySet().iterator(); i.hasNext();) {
                String prefix = (String) i.next();
                String uri = (String) namespaces.get(prefix);
                Element namespaceElement = configElement.addElement("namespace");
                namespaceElement.addAttribute("prefix", prefix);
                namespaceElement.addAttribute("uri", uri);
            }
        }
    }

    public void declareBottomInput(Node node, String id, ProcessorInput input) {
        if (idToInputMap.containsKey(id)) {
            LocationData locationData = (LocationData) ((Element) node).getData();
            throw new ValidationException("There can be only one output parameter with id \"" + id + "\" in a pipeline", locationData);
        }
        idToInputMap.put(id, input);
    }

    public boolean isBottomInputConnected(String id) {
        ProcessorInput bottomInput = (ProcessorInput) idToInputMap.get(id);
        return bottomInput.getOutput() != null;
    }

    public ProcessorOutput connectProcessorToBottomInput(Node node, String outputName, String referencedId, ProcessorOutput processorOutput) {
        if (!idToInputMap.containsKey(referencedId)) {
            LocationData locationData = node == null ? null : (LocationData) ((Element) node).getData();
            throw new ValidationException("Reference to undeclared output parameter id \"" + referencedId + "\"", locationData);
        }
        if (inputIdAlreadyConnected.contains(referencedId)) {
            LocationData locationData = node == null ? null : (LocationData) ((Element) node).getData();
            throw new ValidationException("Other processor output is already connected to output parameter id \"" + referencedId + "\"", locationData);
        }
        ProcessorInput bottomInput = (ProcessorInput) idToInputMap.get(referencedId);
        bottomInput.setOutput(processorOutput);
        processorOutput.setInput(bottomInput);
        return processorOutput;
    }

    public Set getCreatedProcessors() {
        return createdProcessors;
    }
}
