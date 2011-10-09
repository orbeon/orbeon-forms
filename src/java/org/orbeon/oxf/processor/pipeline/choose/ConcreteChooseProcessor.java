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
package org.orbeon.oxf.processor.pipeline.choose;

import org.apache.commons.collections.CollectionUtils;
import org.apache.log4j.Logger;
import org.orbeon.oxf.cache.OutputCacheKey;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.pipeline.api.XMLReceiver;
import org.orbeon.oxf.processor.*;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.util.PooledXPathExpression;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.oxf.xml.NamespaceMapping;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.Configuration;
import org.orbeon.saxon.om.DocumentInfo;
import org.orbeon.saxon.trans.XPathException;

import java.util.*;

public class ConcreteChooseProcessor extends ProcessorImpl {

    public static Logger logger = LoggerFactory.createLogger(ConcreteChooseProcessor.class);

    // Created when constructed
    private LocationData locationData;
    private List branchConditions;
    private List<NamespaceMapping> branchNamespaces;
    private List branchProcessors;
    private Set outputsById;
    private Set outputsByParamRef;
    private List<Map<String, ProcessorInput>> branchInputs = new ArrayList<Map<String, ProcessorInput>>();  // List [Map: (String inputName) -> (ProcessorInput)]
    private List<Map<Object, ProcessorOutput>> branchOutputs = new ArrayList<Map<Object, ProcessorOutput>>(); // List [Map: (String outputName) -> (ProcessorOutput)]

    /**
     * @param branchConditions  List of Strings: XPath expression for each branch
     *                          (except the optimal last <otherwise>)
     * @param branchNamespaces  List of NamespaceContext objects: namespaces declared in
     *                          the context of the given XPath expression
     * @param branchProcessors  List of Processor objects: one for each branch
     * @param inputs            Set of Strings: all the ids possibly referenced by
     *                          a processor in any branch
     * @param outputsById       Set of Strings: outputs of the choose referenced
     *                          by and other processor
     * @param outputsByParamRef Set of Strings: outputs of the choose referencing
     *                          pipeline outputs
     */
    public ConcreteChooseProcessor(String id, LocationData locationData,
                                   List branchConditions, List<NamespaceMapping> branchNamespaces, List branchProcessors,
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
            Map<String, ProcessorInput> currentBranchInputs = new HashMap<String, ProcessorInput>();
            branchInputs.add(currentBranchInputs);
            for (Iterator j = inputs.iterator(); j.hasNext();) {
                String inputName = (String) j.next();
                currentBranchInputs.put(inputName, processor.createInput(inputName));
            }

            // Create ProcessorOutput for each branch
            Map<Object, ProcessorOutput> currentBranchOutputs = new HashMap<Object, ProcessorOutput>();
            branchOutputs.add(currentBranchOutputs);
            for (Iterator j = CollectionUtils.union(outputsById, outputsByParamRef).iterator(); j.hasNext();) {
                String outputName = (String) j.next();
                currentBranchOutputs.put(outputName, processor.createOutput(outputName));
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

    @Override
    public ProcessorOutput createOutput(String name) {
        final String _name = name;
        final ProcessorOutput output = new ProcessorOutputImpl(ConcreteChooseProcessor.this, name) {
            public void readImpl(PipelineContext context, XMLReceiver xmlReceiver) {
                final State state = (State) getState(context);
                if (!state.started)
                    start(context);
                final ProcessorOutput branchOutput = state.selectedBranchOutputs.get(_name);
                branchOutput.read(context, xmlReceiver);
            }

            @Override
            public OutputCacheKey getKeyImpl(PipelineContext pipelineContext) {
                if (isInputInCache(pipelineContext, AbstractChooseProcessor.CHOOSE_DATA_INPUT)) {
                    final State state = (State) getState(pipelineContext);
                    if (!state.started)
                        start(pipelineContext);
                    return state.selectedBranchOutputs.get(_name).getKey(pipelineContext);
                } else
                    return null;
            }

            @Override
            protected Object getValidityImpl(PipelineContext pipelineContext) {
                if (isInputInCache(pipelineContext, AbstractChooseProcessor.CHOOSE_DATA_INPUT)) {
                    final State state = (State) getState(pipelineContext);
                    if (!state.started)
                        start(pipelineContext);
                    return state.selectedBranchOutputs.get(_name).getValidity(pipelineContext);
                } else
                    return null;
            }
        };
        addOutput(name, output);
        return output;
    }

    @Override
    public void start(PipelineContext pipelineContext) {
        final State state = (State) getState(pipelineContext);
        if (state.started)
            throw new IllegalStateException("ASTChoose Processor already started");

        // Choose which branch we want to run (we cache the decision)
        DocumentInfo hrefDocumentInfo = null;
        int branchIndex = 0;
        int selectedBranch = -1;
        for (Iterator i = branchConditions.iterator(); i.hasNext(); branchIndex++) {
            // Evaluate expression
            final String condition = (String) i.next();
            if (condition == null) {
                selectedBranch = branchIndex;
                break;
            }
            // Try to cache the XPath expressions
//            String xpathExpression = "boolean(" + condition + ")";
//            XPath xpath = XPathCache.createCacheXPath(context, xpathExpression);
//
//            xpath.setFunctionContext(new OXFFunctionContext());
//            xpath.setNamespaceContext((NamespaceContext) branchNamespaces.get(branchIndex));
//            if (((Boolean) xpath.evaluate(refNode)).booleanValue()) {
//                selectedBranch = branchIndex;
//                break;
//            }
            // Lazily read input in case there is only a p:otherwise
            if (hrefDocumentInfo == null) {
                final Configuration configuration = XPathCache.getGlobalConfiguration();
                hrefDocumentInfo = readCacheInputAsTinyTree(pipelineContext, configuration, AbstractChooseProcessor.CHOOSE_DATA_INPUT);
            }
            PooledXPathExpression expression = null;
            final NamespaceMapping namespaces = branchNamespaces.get(branchIndex);


            try {
                expression = XPathCache.getXPathExpression(hrefDocumentInfo.getConfiguration(),
                        hrefDocumentInfo, "boolean(" + condition + ")", namespaces, null,
                        org.orbeon.oxf.pipeline.api.FunctionLibrary.instance(), null, locationData);// TODO: location should be that of branch

                if (((Boolean) expression.evaluateSingle()).booleanValue()) {
                    selectedBranch = branchIndex;
                    break;
                }
            } catch (XPathException e) {
                if (logger.isDebugEnabled())
                    logger.debug("Choose: condition evaluation failed for condition: " + condition + " at " + branchProcessors.get(branchIndex));// TODO: location should be that of branch
                throw new ValidationException("Choose: condition evaluation failed for condition: " + condition, e, locationData);// TODO: location should be that of branch
            } finally {
                try {
                    if (expression != null) expression.returnToPool();
                } catch (Exception e) {
                    throw new OXFException(e);
                }
            }
        }

        // In case the source document is large, and not cached, make it gc-able quicker (not sure if that makes a big difference!)
        hrefDocumentInfo = null;

        if (selectedBranch == -1) {
            // No branch was selected: this is not acceptable if there are output to the choose
            if (!outputsById.isEmpty() || !outputsByParamRef.isEmpty())
                throw new ValidationException("Condition failed for every branch of choose: "
                        + branchConditions.toString(), locationData);
        } else {

            // Initialize variables depending on selected branch
            final Processor selectedBranchProcessor = (Processor) branchProcessors.get(selectedBranch);
            final Map<String, ProcessorInput> selectedBranchInputs = branchInputs.get(selectedBranch);
            state.selectedBranchOutputs = branchOutputs.get(selectedBranch);

            // Connect branch inputs
            for (Iterator<String> i = selectedBranchInputs.keySet().iterator(); i.hasNext();) {
                final String branchInputName = i.next();
                final ProcessorInput branchInput = selectedBranchInputs.get(branchInputName);
                final ProcessorInput chooseInput = getInputByName(branchInputName);
                branchInput.setOutput(chooseInput.getOutput());
            }

            // Connect branch outputs, or start processor
            selectedBranchProcessor.reset(pipelineContext);
            if (outputsById.size() == 0 && outputsByParamRef.size() == 0) {
                if (logger.isDebugEnabled()) {
                    final String condition = (String) branchConditions.get(selectedBranch);
                    // TODO: location should be that of branch
                    if (condition != null)
                        logger.debug("Choose: taking when branch with test: " + condition + " at " + locationData);
                    else
                        logger.debug("Choose: taking otherwise branch at " + locationData);
                }
                selectedBranchProcessor.start(pipelineContext);
            }
            state.started = true;
        }
    }

    @Override
    public void reset(PipelineContext context) {
        setState(context, new State());
    }

    private static class State {
        public boolean started = false;
        public Map<Object, ProcessorOutput> selectedBranchOutputs;
    }
}
