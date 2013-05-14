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
package org.orbeon.oxf.processor.bpel;

import org.apache.log4j.Logger;
import org.dom4j.Element;
import org.dom4j.QName;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.CacheableInputReader;
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.processor.ProcessorInput;
import org.orbeon.oxf.processor.ProcessorInputOutputInfo;
import org.orbeon.oxf.processor.bpel.activity.ActivityUtils;
import org.orbeon.oxf.processor.pipeline.PipelineProcessor;
import org.orbeon.oxf.processor.pipeline.ast.ASTDocumentHandler;
import org.orbeon.oxf.processor.pipeline.ast.ASTParam;
import org.orbeon.oxf.processor.pipeline.ast.ASTPipeline;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.xml.sax.ContentHandler;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class BPELProcessor extends ProcessorImpl {

    static private Logger logger = LoggerFactory.createLogger(BPELProcessor.class);

    public BPELProcessor() {
        // TODO: support schema
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG/*, BPELConstants.BPEL_NAMESPACE_URI*/));
    }

    public org.orbeon.oxf.processor.ProcessorOutput createOutput(final String name) {
        org.orbeon.oxf.processor.ProcessorOutput output = new org.orbeon.oxf.processor.ProcessorImpl.CacheableTransformerOutputImpl(getClass(), name) {
            public void readImpl(org.orbeon.oxf.pipeline.api.PipelineContext context, ContentHandler contentHandler) {
                State state = initState(context);
                state.pipelineProcessor.getOutputByName(name).read(context, contentHandler);
            }
        };
        addOutput(name, output);
        return output;
    }

    public State initState(PipelineContext context) {
        State state = (State) getState(context);
        if (state.pipelineProcessor == null) {
            state.pipelineProcessor = (PipelineProcessor) readCacheInputAsObject
                    (context, getInputByName(INPUT_CONFIG), new CacheableInputReader() {
                public Object read(final org.orbeon.oxf.pipeline.api.PipelineContext context, ProcessorInput input) {

                    // Variables that get modified as we read the BPEL program
                    Variables variables = new Variables();
                    ASTPipeline astPipeline = new ASTPipeline();
                    List statements = astPipeline.getStatements();

                    // Read BPEL program
                    Element processElement = readInputAsDOM4J(context, INPUT_CONFIG).getRootElement();
                    ActivityUtils.activitiesToXPL(variables, statements,
                            Arrays.asList(new Object[] {processElement.element(new QName("variables", BPELConstants.BPEL_NAMESPACE))}));
                    ActivityUtils.activitiesToXPL(variables, statements,
                            processElement.element(new QName("sequence", BPELConstants.BPEL_NAMESPACE)).elements());

                    // Add input and output parameters
                    for (Iterator i = variables.iterateVariablesParts(); i.hasNext();) {
                        Variables.VariablePart variablePart = (Variables.VariablePart) i.next();
                        if (variables.getInputVariable() != null
                                && variables.getInputVariable().equals(variablePart.getVariable()))
                            astPipeline.addParam(new ASTParam(ASTParam.INPUT, variablePart.getPart()));
                        if (variables.getOutputVariable() != null
                                && variables.getOutputVariable().equals(variablePart.getVariable())) {
                            astPipeline.addParam(new ASTParam(ASTParam.OUTPUT, variablePart.getPart()));
                        }
                    }

                    // Log pipeline
                    if (logger.isDebugEnabled()) {
                        ASTDocumentHandler astDocumentHandler = new ASTDocumentHandler();
                        astPipeline.walk(astDocumentHandler);
                        logger.debug("BPEL pipeline:\n"
                                + Dom4jUtils.domToString(astDocumentHandler.getDocument()));
                    }

                    // Connect processor
                    PipelineProcessor pipelineProcessor = new PipelineProcessor(astPipeline);
                    for (Iterator i = astPipeline.getParams().iterator(); i.hasNext();) {
                        ASTParam astParam = (ASTParam) i.next();
                        if (astParam.getType() == ASTParam.OUTPUT) {
                            pipelineProcessor.createOutput(astParam.getName());;
                        }
                        if (astParam.getType() == ASTParam.INPUT) {
                            pipelineProcessor.createInput(astParam.getName())
                                    .setOutput(new ForwardingProcessorOutput(astParam.getName()));
                        }
                    }
                    return pipelineProcessor;
                }
            });
            state.pipelineProcessor.reset(context);
        }
        return state;
    }

    public void start(PipelineContext context) {
        State state = initState(context);
        state.pipelineProcessor.start(context);
    }

    private class ForwardingProcessorOutput extends ProcessorOutputImpl {
        public ForwardingProcessorOutput(String name) {
            super(BPELProcessor.class, name);
        }

        protected void readImpl(PipelineContext context, ContentHandler contentHandler) {
            readInputAsSAX(context, getName(), contentHandler);
        }
    }

    public void reset(PipelineContext context) {
        setState(context, new State());
    }

    private static class State {
        PipelineProcessor pipelineProcessor;
    }
}
