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
package org.orbeon.oxf.processor.pipeline.choose;

import org.apache.commons.collections.CollectionUtils;
import org.dom4j.Node;
import org.dom4j.XPath;
import org.jaxen.NamespaceContext;
import org.orbeon.oxf.cache.Cacheable;
import org.orbeon.oxf.cache.OutputCacheKey;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.*;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.oxf.xml.OXFFunctionContext;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.xml.sax.ContentHandler;

import java.util.*;

public class ConcreteChooseProcessor extends ProcessorImpl {

    // Created when constructed
    private LocationData locationData;
    private List branchConditions;
    private List branchNamespaces;
    private List branchProcessors;
    private Set outputsById;
    private Set outputsByParamRef;
    private List branchInputs = new ArrayList();  // List [Map: (String inputName) -> (ProcessorInput)]
    private List branchOutputs = new ArrayList(); // List [Map: (String outputName) -> (ProcessorOutput)]

    /**
     * @param branchConditions    List of Strings: XPath expression for each branch
     *                            (except the optinal last <otherwise>)
     * @param branchNamespaces    List of NamespaceContext objects: namespaces declared in
     *                            the context of the given XPath expression
     * @param branchProcessors    List of Processor objects: one for each branch
     * @param inputs              Set of Strings: all the ids possibly referenced by
     *                            a processor in any branch
     * @param outputsById         Set of Strings: outputs of the choose referenced
     *                            by and other processor
     * @param outputsByParamRef   Set of Strings: outputs of the choose referencing
     *                            pipeline outputs
     */
    public ConcreteChooseProcessor(String id, LocationData locationData,
                                   List branchConditions, List branchNamespaces, List branchProcessors,
                                   Set inputs, Set outputsById, Set outputsByParamRef) {
        setId(id);
        this.locationData = locationData;
        this.branchConditions = branchConditions;
        this.branchNamespaces = branchNamespaces;
        this.branchProcessors = branchProcessors;
        this.outputsById = outputsById;
        this.outputsByParamRef = outputsByParamRef;

        // Add inputs
        addInputInfo(new ProcessorInputOutputInfo(AbstractChooseProcessor.CHOOSE_DATA_INPUT));
        for (Iterator i = inputs.iterator(); i.hasNext();) {
            String name = (String) i.next();
            addInputInfo(new ProcessorInputOutputInfo(name));
        }

        // Add outputs
        for (Iterator i = CollectionUtils.union(outputsById, outputsByParamRef).iterator(); i.hasNext();) {
            String name = (String) i.next();
            addOutputInfo(new ProcessorInputOutputInfo(name));
        }

        for (Iterator i = branchProcessors.iterator(); i.hasNext();) {
            Processor processor = (Processor) i.next();

            // Create ProcessorInput for each branch
            Map currentBranchInputs = new HashMap();
            branchInputs.add(currentBranchInputs);
            for (Iterator j = inputs.iterator(); j.hasNext();) {
                String inputName = (String) j.next();
                currentBranchInputs.put(inputName, processor.createInput(inputName));
            }

            // Create ProcessorOutput for each branch
            Map currentBranchOuputs = new HashMap();
            branchOutputs.add(currentBranchOuputs);
            for (Iterator j = CollectionUtils.union(outputsById, outputsByParamRef).iterator(); j.hasNext();) {
                String outputName = (String) j.next();
                currentBranchOuputs.put(outputName, processor.createOutput(outputName));
            }
        }
    }

    /**
     * Those outputs that must be connected to an outer pipeline output
     */
    public Set getOutputsByParamRef() {
        return outputsByParamRef;
    }

    public Set getOutputsById() {
        return outputsById;
    }

    public ProcessorOutput createOutput(String name) {
        final String _name = name;
        ProcessorOutput output = new ProcessorImpl.ProcessorOutputImpl(getClass(), name) {
            public void readImpl(PipelineContext context, ContentHandler contentHandler) {
                State state = (State) getState(context);
                if (!state.started)
                    start(context);
                ProcessorOutput branchOutput = (ProcessorOutput) state.selectedBranchOutputs.get(_name);
                branchOutput.read(context, contentHandler);
            }

            protected OutputCacheKey getKeyImpl(PipelineContext context) {
                if (isInputInCache(context, AbstractChooseProcessor.CHOOSE_DATA_INPUT)) {
                    State state = (State) getState(context);
                    if (!state.started)
                        start(context);
                    ProcessorOutput branchOutput = (ProcessorOutput) state.selectedBranchOutputs.get(_name);
                    if (branchOutput instanceof Cacheable)
                        return ((Cacheable) branchOutput).getKey(context);
                    else
                        return null;
                } else
                    return null;
            }

            protected Object getValidityImpl(PipelineContext context) {
                if (isInputInCache(context, AbstractChooseProcessor.CHOOSE_DATA_INPUT)) {
                    State state = (State) getState(context);
                    if (!state.started)
                        start(context);
                    ProcessorOutput branchOutput = (ProcessorOutput) state.selectedBranchOutputs.get(_name);
                    if (branchOutput instanceof Cacheable)
                        return ((Cacheable) branchOutput).getValidity(context);
                    else
                        return null;
                } else
                    return null;
            }
        };
        addOutput(name, output);
        return output;
    }

    public void start(PipelineContext context) {
        final State state = (State) getState(context);
        if (state.started)
            throw new IllegalStateException("ASTChoose Processor already started");

        // Choose which branch we want to run (we cache the decision)
        Node refNode = readCacheInputAsDOM4J(context, AbstractChooseProcessor.CHOOSE_DATA_INPUT);
        int branchIndex = 0;
        int selectedBranch = -1;
        for (Iterator i = branchConditions.iterator(); i.hasNext();) {
            // Evaluate expression
            String condition = (String) i.next();
            if (condition == null) {
                selectedBranch = branchIndex;
                break;
            }
            // Try to cache the XPath expressions
            String xpathExpression = "boolean(" + condition + ")";
            XPath xpath = XPathCache.createCacheXPath(context, xpathExpression);

            xpath.setFunctionContext(new OXFFunctionContext());
            xpath.setNamespaceContext((NamespaceContext) branchNamespaces.get(branchIndex));
            if (((Boolean) xpath.evaluate(refNode)).booleanValue()) {
                selectedBranch = branchIndex;
                break;
            }
            branchIndex++;
        }

        if (selectedBranch == -1) {
            // No branch was selected: this is not acceptable if there are output to the choose
            if (!outputsById.isEmpty() || !outputsByParamRef.isEmpty())
                throw new ValidationException("Condition failed for every branch of choose: "
                        + branchConditions.toString(), locationData);
        } else {

            // Initialize variables depending on selected branch
            Processor selectedBranchProcessor = (Processor) branchProcessors.get(selectedBranch);
            Map selectedBranchInputs = (Map) branchInputs.get(selectedBranch);
            state.selectedBranchOutputs = (Map) branchOutputs.get(selectedBranch);

            // Connect branch inputs
            for (Iterator i = selectedBranchInputs.keySet().iterator(); i.hasNext();) {
                String branchInputName = (String) i.next();
                ProcessorInput branchInput = (ProcessorInput) selectedBranchInputs.get(branchInputName);
                ProcessorInput chooseInput = getInputByName(branchInputName);
                branchInput.setOutput(chooseInput.getOutput());
            }

            // Connect branch outputs, or start processor
            selectedBranchProcessor.reset(context);
            if (outputsById.size() == 0 && outputsByParamRef.size() == 0) {
                selectedBranchProcessor.start(context);
            }
            state.started = true;
        }
    }

    public void reset(PipelineContext context) {
        setState(context, new State());
    }

    private static class State {
        public boolean started = false;
        public Map selectedBranchOutputs;
    }
}
