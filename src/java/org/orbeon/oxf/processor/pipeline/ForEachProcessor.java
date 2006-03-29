/**
 *  Copyright (C) 2004 - 2005 Orbeon, Inc.
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

import org.apache.log4j.Logger;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.Node;
import org.orbeon.oxf.cache.*;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.*;
import org.orbeon.oxf.processor.generator.DOMGenerator;
import org.orbeon.oxf.processor.pipeline.ast.*;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.oxf.util.PooledXPathExpression;
import org.orbeon.oxf.xml.ForwardingContentHandler;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.saxon.dom4j.DocumentWrapper;
import org.orbeon.saxon.xpath.XPathException;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import java.util.*;

public class ForEachProcessor extends ProcessorImpl implements AbstractProcessor {

    static private Logger logger = LoggerFactory.createLogger(ForEachProcessor.class);
    public static final String CURRENT = "$current";
    public static final String FOR_EACH_DATA_INPUT = "$data";

    private ASTForEach forEachAST;
    private Object validity;

    public ForEachProcessor(ASTForEach forEachAST, Object validity) {
        this.forEachAST = forEachAST;
        this.validity = validity;
        setLocationData(forEachAST.getLocationData());
    }

    public Processor createInstance(PipelineContext context) {
        return new ConcreteForEachProcessor(forEachAST, validity);
    }

    public static class ConcreteForEachProcessor extends ProcessorImpl {

        Processor iterationProcessor;
        private ProcessorOutput iterationOutput;
        private String select;
        private Map namespaceContext;
        private String rootLocalName;
        private String rootQName;
        private String rootNamespaceURI;

        public ConcreteForEachProcessor(ASTForEach forEachAST, Object validity) {
            String[] refsWithNoId = getRefsWithNoId(forEachAST);
            String idOrRef = forEachAST.getId() != null ? forEachAST.getId() : forEachAST.getRef();

            // Create pipeline for content of for-each (the "iteration processor")
            {
                ASTPipeline astPipeline = new ASTPipeline();
                astPipeline.setValidity(validity);
                astPipeline.getStatements().addAll(forEachAST.getStatements());
                astPipeline.setNode(forEachAST.getNode());
                for (int i = 0; i < refsWithNoId.length; i++) {
                    astPipeline.addParam(new ASTParam(ASTParam.INPUT, refsWithNoId[i]));
                    if (! refsWithNoId[i].equals(CURRENT))
                        addInputInfo(new ProcessorInputOutputInfo(refsWithNoId[i]));
                }
                if (idOrRef != null) {
                    astPipeline.addParam(new ASTParam(ASTParam.OUTPUT, idOrRef));
                    addOutputInfo(new ProcessorInputOutputInfo(idOrRef));
                }
                if (logger.isDebugEnabled()) {
                    ASTDocumentHandler astDocumentHandler = new ASTDocumentHandler();
                    astPipeline.walk(astDocumentHandler);
                    logger.debug("Iteration pipeline:\n" + Dom4jUtils.domToString(astDocumentHandler.getDocument()));
                }
                iterationProcessor = new PipelineProcessor(astPipeline);
            }

            // Connect processor inputs to inputs of this processor
            for (int i = 0; i < refsWithNoId.length; i++) {
                if (! refsWithNoId[i].equals(CURRENT)) {
                    ProcessorInput pipelineInput = iterationProcessor.createInput(refsWithNoId[i]);
                    pipelineInput.setOutput(new ForwardingProcessorOutput(refsWithNoId[i]));
                }
            }

            // Connect processor to the IterationProcessorOutput
            ProcessorInput iterationInput = iterationProcessor.createInput(CURRENT);
            ProcessorOutput currentOutput = new IterationProcessorOutput(ForEachProcessor.class, CURRENT);
            currentOutput.setInput(iterationInput);
            iterationInput.setOutput(currentOutput);
            iterationOutput = iterationProcessor.createOutput(idOrRef);

            select = forEachAST.getSelect();
            namespaceContext = Dom4jUtils.getNamespaceContext((Element) forEachAST.getNode());
            if (forEachAST.getRoot() != null) {
                rootQName = forEachAST.getRoot();
                int columnPosition = rootQName.indexOf(':');
                if (columnPosition == -1) {
                    // No prefix
                    rootLocalName = rootQName;
                    rootNamespaceURI = "";
                } else {
                    // Extract prefix, find namespace URI
                    String prefix = rootQName.substring(0, columnPosition);
                    rootNamespaceURI = (String) namespaceContext.get(prefix);
                    if (rootNamespaceURI == null)
                        throw new ValidationException("Prefix '" + prefix + "' used in root attribute is undefined", forEachAST.getLocationData());
                    rootLocalName = rootQName.substring(columnPosition + 1);
                }
            }
        }

        public ProcessorOutput createOutput( final String name ) {
            ProcessorOutput output = new ProcessorImpl.ProcessorOutputImpl(getClass(), name) {
                public void readImpl(PipelineContext context, ContentHandler contentHandler) {
                    try {
                        State state = (State) getState(context);
                        initDOMGenerators(context, state);

                        // Read n times from iterationOutput
                        contentHandler.startDocument();
                        contentHandler.startElement(rootNamespaceURI, rootLocalName, rootQName, new AttributesImpl());
                        for (int i = 0; i < state.domGenerators.size(); i++) {
                            state.currentDOMGenerator = i;
                            iterationProcessor.reset(context);
                            iterationOutput.read(context, new ForwardingContentHandler(contentHandler) {
                                public void startDocument() {}
                                public void endDocument() {}
                            });
                        }
                        contentHandler.endElement(rootNamespaceURI, rootLocalName, rootQName);
                        contentHandler.endDocument();
                    } catch (SAXException e) {
                        throw new OXFException(e);
                    }
                }

                /**
                 * For each is not cachable. We used to have code here that combines the keys for
                 * the outputs of the different executions of the for-each. The problem is when we
                 * have a block in the for-each that has an output and a serializer. In that case,
                 * getting the output key runs the serializer.
                 *
                 * So if we get the key here, we'll run the serializer n times, which is
                 * unexpected. Maybe an an API that combines reading the output and reading the
                 * key/validity would solve this problem.
                 */
                protected OutputCacheKey getKeyImpl(PipelineContext context) {
                    return null;
                }

                /**
                 * For each is not cachable. See comment in getKeyImpl().
                 */
                protected Object getValidityImpl(PipelineContext context) {
                    return null;
                }

            };
            addOutput(name, output);
            return output;
        }

        /**
         * Create internal key based on $data and select expression.
         */
        private InternalCacheKey createInternalKey(PipelineContext context) {
            OutputCacheKey outputCacheKey = getInputKey(context, getInputByName(FOR_EACH_DATA_INPUT));
            if (outputCacheKey == null) return null;
            InputCacheKey inputCacheKey = new InputCacheKey(getInputByName(FOR_EACH_DATA_INPUT), outputCacheKey);
            InternalCacheKey selectKey = new InternalCacheKey(ConcreteForEachProcessor.this, "select", select);
            return new InternalCacheKey(ConcreteForEachProcessor.this,
                    Arrays.asList(new CacheKey[] {inputCacheKey, selectKey}));
        }

        private Object createInternalValidity(PipelineContext context) {
            return getInputValidity(context, getInputByName(FOR_EACH_DATA_INPUT));
        }

        /**
         * Try to find domGenerators in cache.
         */
        private void updateStateWithDOMGeneratorsFromCache(PipelineContext context, State state,
                                                           InternalCacheKey internalKey,
                                                           Object internalValidity) {
            if (state.domGenerators != null) {
                Cache cache = ObjectCache.instance();
                if (internalKey != null && internalValidity != null)
                    state.domGenerators = (List) cache.findValid(context, internalKey, internalValidity);
            }
        }

        private void initDOMGenerators(PipelineContext context, State state) {
            if (state.domGenerators == null) {

                // Try to find domGenerators in cache
                InternalCacheKey internalKey = createInternalKey(context);
                Object internalValidity = createInternalValidity(context);
                updateStateWithDOMGeneratorsFromCache(context, state, internalKey, internalValidity);

                // If we can't find domGenerators in cache, create them
                if (state.domGenerators == null) {
                    Document dataInput = readInputAsDOM4J(context, getInputByName(FOR_EACH_DATA_INPUT));
                    state.domGenerators = new ArrayList();
                    PooledXPathExpression expr = XPathCache.getXPathExpression(context,
                            new DocumentWrapper(dataInput, null),
                            select, namespaceContext);
                    try {
                        for (Iterator i = expr.evaluate().iterator(); i.hasNext();) {
                            Node node = (Node) i.next();
                            if ( node.getNodeType() != org.dom4j.Node.ELEMENT_NODE )
                                throw new OXFException("Select expression '" + select
                                        + "' did not return a sequence of elements. One node was a '"
                                        + node.getNodeTypeName() + "'");
                            final org.dom4j.Element elt = ( org.dom4j.Element )node;
                            final String sid = Dom4jUtils.makeSystemId( elt );
                            final DOMGenerator domGenerator = new DOMGenerator
                                ( elt, "for each input", DOMGenerator.ZeroValidity, sid );
                            domGenerator.createOutput(OUTPUT_DATA);
                            state.domGenerators.add(domGenerator);
                        }
                        if (internalKey != null && internalValidity != null)
                            ObjectCache.instance().add(context,
                                    internalKey, internalValidity, state.domGenerators);
                    } catch (XPathException e) {
                        throw new OXFException(e);
                    }
                }
            }
        }
        
        public void start(PipelineContext context) {
            State state = (State) getState(context);
            initDOMGenerators(context, state);

            // Run n times from iterationOutput
            for (int i = 0; i < state.domGenerators.size(); i++) {
                state.currentDOMGenerator = i;
                iterationProcessor.reset(context);
                iterationProcessor.start(context);
            }
        }

        public void reset(PipelineContext context) {
            setState(context, new State());
        }

        /**
         * Determine all &lt;p:input ref="..."> with no &lt;p:output id="...">.
         * Those are the inputs of this processor.
         */
        private String[] getRefsWithNoId(ASTForEach forEachAST) {
            // All the ids references from <p:input href="...">
            final Set inputRefs = new HashSet();
            // All the ids in <p:output id="...">
            final Set outputIds = new HashSet();

            // Init the 2 sets above
            for (Iterator i = forEachAST.getStatements().iterator(); i.hasNext();) {
                ASTStatement astStatement = (ASTStatement) i.next();
                IdInfo idInfo = astStatement.getIdInfo();
                inputRefs.addAll(idInfo.getInputRefs());
                outputIds.addAll(idInfo.getOutputIds());
            }

            Set refsWithNoId = new HashSet(inputRefs);
            refsWithNoId.removeAll(outputIds);
            return (String[]) refsWithNoId.toArray(new String[refsWithNoId.size()]);
        }

        private class ForwardingProcessorOutput extends ProcessorOutputImpl {
            public ForwardingProcessorOutput(String name) {
                super(ConcreteForEachProcessor.class, name);
            }

            protected void readImpl(PipelineContext context, ContentHandler contentHandler) {
                readInputAsSAX(context, getName(), contentHandler);
            }
        }

        /**
         * Reads from the DOM generators stored in state
         */
        private class IterationProcessorOutput extends ProcessorOutputImpl {

            public IterationProcessorOutput(Class clazz, String name) {
                super(clazz, name);
            }

            protected void readImpl(PipelineContext context, ContentHandler contentHandler) {
                State state = (State) getState(context);
                DOMGenerator domGenerator = (DOMGenerator) state.domGenerators.get(state.currentDOMGenerator);
                domGenerator.getOutputByName(OUTPUT_DATA).read(context, contentHandler);
            }

            protected OutputCacheKey getKeyImpl(PipelineContext context) {
                State state = (State) getState(context);
                DOMGenerator domGenerator = (DOMGenerator) state.domGenerators.get(state.currentDOMGenerator);
                return ((Cacheable) domGenerator.getOutputByName(OUTPUT_DATA)).getKey(context);
            }

            protected Object getValidityImpl(PipelineContext context) {
                State state = (State) getState(context);
                DOMGenerator domGenerator = (DOMGenerator) state.domGenerators.get(state.currentDOMGenerator);
                return ((Cacheable) domGenerator.getOutputByName(OUTPUT_DATA)).getValidity(context);
            }
        }

        private static class State {
            List domGenerators = null;
            int currentDOMGenerator;
        }
    }
}
