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
package org.orbeon.oxf.processor;

import org.apache.log4j.Logger;
import org.dom4j.Element;
import org.dom4j.QName;
import org.orbeon.oxf.cache.*;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.debugger.api.BreakpointKey;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.pipeline.api.PipelineContext.Trace;
import org.orbeon.oxf.pipeline.api.PipelineContext.TraceInfo;
import org.orbeon.oxf.processor.generator.DOMGenerator;
import org.orbeon.oxf.processor.validation.MSVValidationProcessor;
import org.orbeon.oxf.properties.Properties;
import org.orbeon.oxf.properties.PropertySet;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.util.PipelineUtils;
import org.orbeon.oxf.xml.*;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.oxf.xml.dom4j.LocationSAXContentHandler;
import org.orbeon.oxf.xml.dom4j.NonLazyUserDataDocument;
import org.orbeon.saxon.Configuration;
import org.orbeon.saxon.om.DocumentInfo;
import org.orbeon.saxon.om.FastStringBuffer;
import org.orbeon.saxon.tinytree.TinyBuilder;
import org.w3c.dom.Document;
import org.xml.sax.ContentHandler;

import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.sax.TransformerHandler;
import java.util.*;

/**
 * Helper class that implements default method of the Processor interface.
 */
public abstract class ProcessorImpl implements Processor {

    public static Logger logger = LoggerFactory.createLogger(ProcessorImpl.class);

    public static final String INPUT_DATA = "data";
    public static final String INPUT_CONFIG = "config";
    public static final String OUTPUT_DATA = "data";

    public static final String PROCESSOR_VALIDATION_FLAG = "oxf.validation.processor";
    public static final String USER_VALIDATION_FLAG = "oxf.validation.user";
    public static final String SAX_INSPECTION_FLAG = "oxf.sax.inspection";

    private static final List<ProcessorInput> EMPTY_INPUT_LIST = Collections.emptyList();

    private String id;
    private QName name;
    private Map<String, List<ProcessorInput>> inputMap = new LinkedHashMap<String, List<ProcessorInput>>();
    private Map<String, ProcessorOutput> outputMap = new LinkedHashMap<String, ProcessorOutput>();
    private int outputCount = 0;
    private List<ProcessorInputOutputInfo> inputsInfo = new ArrayList<ProcessorInputOutputInfo>(0);
    private List<ProcessorInputOutputInfo> outputsInfo = new ArrayList<ProcessorInputOutputInfo>(0);

    private LocationData locationData;
    public static final String PROCESSOR_INPUT_SCHEME_OLD = "oxf:";
    public static final String PROCESSOR_INPUT_SCHEME = "input:";

    /**
     * Return a property set for this processor.
     */
    protected PropertySet getPropertySet() {
        return Properties.instance().getPropertySet(getName());
    }

    public LocationData getLocationData() {
        return locationData;
    }

    public void setLocationData( final LocationData loc ) {
        locationData = loc;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public QName getName() {
        return name;
    }

    public void setName(QName name) {
        this.name = name;
    }

    public ProcessorInput getInputByName(String name) {
        List l = (List) inputMap.get(name);
        if (l == null)
            throw new ValidationException("Cannot find input \"" + name + "\"", getLocationData());
        if (l.size() != 1)
            throw new ValidationException("Found more than one input \"" + name + "\"", getLocationData());
        return (ProcessorInput) l.get(0);
    }

    public List<ProcessorInput> getInputsByName(String name) {
        final List<ProcessorInput> result = inputMap.get(name);
        return result == null ? EMPTY_INPUT_LIST : result;
    }

    public ProcessorInput createInput(final String name) {
        final ProcessorInputOutputInfo inputInfo = getInputInfo(name);

        // The PropertySet can be null during properties initialization. This should be one of the
        // rare places where this should be tested on. By default, enable validation so the
        // properties can be validated!
        final PropertySet propertySet = Properties.instance().getPropertySet();
        final boolean inputValidationEnabled = (propertySet == null) ? true : propertySet.getBoolean(PROCESSOR_VALIDATION_FLAG, true);
        final ProcessorInput input;
        if (inputValidationEnabled && inputInfo != null && inputInfo.getSchemaURI() != null) {

            if (logger.isDebugEnabled())
                logger.debug("Creating validator for input name '" + name
                        + "' and schema-uri '" + inputInfo.getSchemaURI() + "'");

            // Create and hook-up input validation processor if needed
            final Processor validatorProcessor = new MSVValidationProcessor(inputInfo.getSchemaURI());

            // Connect schema to validator
            final Processor schemaURLGenerator = PipelineUtils.createURLGenerator(SchemaRepository.instance().getSchemaLocation(inputInfo.getSchemaURI()));
            PipelineUtils.connect(schemaURLGenerator, OUTPUT_DATA, validatorProcessor, MSVValidationProcessor.INPUT_SCHEMA);
            PipelineUtils.connect(MSVValidationProcessor.NO_DECORATION_CONFIG, OUTPUT_DATA, validatorProcessor, INPUT_CONFIG);

            // Create data input and output
            final ProcessorInput inputValData = validatorProcessor.createInput(INPUT_DATA);
            final ProcessorOutput outputValData = validatorProcessor.createOutput(OUTPUT_DATA);

            input = new DelegatingProcessorInput(name, inputValData, outputValData);
        } else {
            input = new ProcessorInputImpl(ProcessorImpl.this.getClass(), name);
        }

        addInput(name, input);
        return input;
    }

    public class DelegatingProcessorInput implements ProcessorInput {

        private final String originalName;
        private ProcessorInput delegateInput;
        private ProcessorOutput delegateOutput;

        public DelegatingProcessorInput(String originalName, ProcessorInput delegateInput, ProcessorOutput delegateOutput) {
            this.originalName = originalName;
            this.delegateInput = delegateInput;
            this.delegateOutput = delegateOutput;
        }

        protected DelegatingProcessorInput(String originalName) {
            this.originalName = originalName;
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
            return ProcessorImpl.this.getClass();
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

        public void setBreakpointKey(BreakpointKey breakpointKey) {
            delegateInput.setBreakpointKey(breakpointKey);
        }
    };

    /**
     * This special input is able to handle dependencies on URLs.
     */
    protected abstract class DependenciesProcessorInput extends DelegatingProcessorInput {

        // Custom processor handling input dependencies
        private final ProcessorImpl dependencyProcessor = new ProcessorImpl() {
            @Override
            public ProcessorOutput createOutput(String outputName) {
                final ProcessorOutput output = new URIProcessorOutputImpl(this, outputName, INPUT_CONFIG) {
                    @Override
                    protected void readImpl(PipelineContext pipelineContext, final ContentHandler contentHandler) {
                        final boolean[] foundInCache = new boolean[] { false };
                        readCacheInputAsObject(pipelineContext, getInputByName(INPUT_CONFIG), new CacheableInputReader() {
                            @Override
                            public Object read(PipelineContext pipelineContext, ProcessorInput processorInput) {
                                // Read the input directly into the output
                                readInputAsSAX(pipelineContext, processorInput, contentHandler);

                                // Return dependencies object
                                return getURIReferences(pipelineContext);
                            }

                            @Override
                            public void foundInCache() {
                                foundInCache[0] = true;
                            }
                        });

                        // Finding the dependencies in cache doesn't mean we don't read to the output: after all,
                        // we were asked to.
                        if (foundInCache[0]) {
                            readInputAsSAX(pipelineContext, getInputByName(INPUT_CONFIG), contentHandler);
                        }
                    }
                };
                addOutput(outputName, output);
                return output;
            }
        };

        public DependenciesProcessorInput(String originalName, ProcessorInput originalInput) {
            super(originalName);

            // Create data input and output
            final ProcessorInput dependencyInput = dependencyProcessor.createInput(INPUT_CONFIG);
            final ProcessorOutput dependencyOutput = dependencyProcessor.createOutput(OUTPUT_DATA);

            setDelegateInput(dependencyInput);
            setDelegateOutput(dependencyOutput);

            // Connect output of dependency processor to original input
            {
                dependencyOutput.setInput(originalInput);
                originalInput.setOutput(dependencyOutput);
            }
        }

        /**
         * Get URI references on which this input depends. This is called right after the original input has been read.
         *
         * @param pipelineContext   current context
         * @return                  URI references
         */
        protected abstract URIProcessorOutputImpl.URIReferences getURIReferences(PipelineContext pipelineContext);
    }

    public void addInput(String inputName, ProcessorInput input) {
        List<ProcessorInput> inputs = inputMap.get(inputName);
        if (inputs == null) {
            inputs = new ArrayList<ProcessorInput>();
            inputMap.put(inputName, inputs);
        }
//        if (inputs.size() > 0)
//            logger.info("Processor " + getClass().getName() + " has more than 1 input called " + name);
        inputs.add(input);
    }

    public void deleteInput(ProcessorInput input) {
        deleteFromListMap(inputMap, input);
    }

    public ProcessorOutput getOutputByName(String outputName) {
        ProcessorOutput ret = (ProcessorOutput) outputMap.get(outputName);
        if (ret == null )
            throw new ValidationException("Exactly one output " + outputName + " is required", getLocationData());
        return ret;
    }

    public ProcessorOutput createOutput(String outputName) {
        throw new ValidationException("Outputs are not supported", getLocationData());
    }

    public void addOutput(String name, ProcessorOutput output) {
        // NOTE: One exception to the rule that we only have one output with a given name is the TeeProcessor, which
        // adds multiple outputs called "data".
        outputMap.put(name, output);
        outputCount++;
    }

    protected int getOutputCount() {
        return outputCount;
    }

    // NOTE: As of 2009-06-26, this is never called.
    public void deleteOutput(ProcessorOutput output) {
        final Collection outputs = outputMap.values();
        outputs.remove(output);
        // NOTE: This won't be correct with the TeeProcessor.
        outputCount--;
    }

    protected void addInputInfo(ProcessorInputOutputInfo inputInfo) {
        inputsInfo.add(inputInfo);
    }

    protected void removeInputInfo(ProcessorInputOutputInfo inputInfo) {
        inputsInfo.remove(inputInfo);
    }

    protected void addOutputInfo(ProcessorInputOutputInfo outputInfo) {
        outputsInfo.add(outputInfo);
    }

    protected void removeOutputInfo(ProcessorInputOutputInfo outputInfo) {
        outputsInfo.remove(outputInfo);
    }

    public List<ProcessorInputOutputInfo> getInputsInfo() {
        return inputsInfo;
    }

    public Map<String, List<ProcessorInput>> getConnectedInputs() {
        return Collections.unmodifiableMap(inputMap);
    }

    public ProcessorInputOutputInfo getInputInfo(String name) {
        for (Iterator i = inputsInfo.iterator(); i.hasNext();) {
            ProcessorInputOutputInfo inputInfo = (ProcessorInputOutputInfo) i.next();
            if (inputInfo.getName().equals(name))
                return inputInfo;
        }
        return null;
    }

    public List<ProcessorInputOutputInfo> getOutputsInfo() {
        return outputsInfo;
    }

    public Map<String, ProcessorOutput> getConnectedOutputs() {
        return Collections.unmodifiableMap(outputMap);
    }

    public ProcessorInputOutputInfo getOutputInfo(String name) {
        for (Iterator i = outputsInfo.iterator(); i.hasNext();) {
            ProcessorInputOutputInfo outputInfo = (ProcessorInputOutputInfo) i.next();
            if (outputInfo.getName().equals(name))
                return outputInfo;
        }
        return null;
    }


    public void checkSockets() {

        // FIXME: This method is never called and it cannot work correctly
        // right now as some processor (pipeline, aggregator) are not exposing their
        // interface correctly. This will be fixed when we implement the full
        // delegation model.

        throw new UnsupportedOperationException();

        // Check that each connection has a corresponding socket info
//        for (int io = 0; io < 2; io++) {
//            for (Iterator i = (io == 0 ? inputMap : outputMap).keySet().iterator(); i.hasNext();) {
//                String inputName = (String) i.next();
//                boolean found = false;
//                for (Iterator j = socketInfo.iterator(); j.hasNext();) {
//                    ProcessorInputOutputInfo socketInfo = (ProcessorInputOutputInfo) j.next();
//                    if (socketInfo.getType() ==
//                            (io == 0 ? ProcessorInputOutputInfo.INPUT_TYPE : ProcessorInputOutputInfo.OUTPUT_TYPE)
//                            && socketInfo.getName().equals(inputName)) {
//                        found = true;
//                        break;
//                    }
//                }
//                if (!found)
//                    throw new ValidationException("Processor does not support " + (io == 0 ? "input" : "output") +
//                            " with name " + inputName, getLocationData());
//            }
//        }
    }

    /**
     * The fundamental read method based on SAX.
     */
    protected static void readInputAsSAX(PipelineContext context, final ProcessorInput input, ContentHandler contentHandler) {
//        if (input instanceof ProcessorInputImpl) {
//            input.getOutput().read(context, new ForwardingContentHandler(contentHandler) {
//                private Locator locator;
//                public void setDocumentLocator(Locator locator) {
//                    this.locator = locator;
//                    super.setDocumentLocator(locator);
//                }
//
//                public void startDocument() throws SAXException {
//                    // Try to get the system id and set it on the input
//                    if (locator != null && locator.getSystemId() != null) {
////                        System.out.println("Got system id: " + locator.getSystemId());
//                        ((ProcessorInputImpl) input).setSystemId(locator.getSystemId());
//                    }
//                    super.startDocument();
//                }
//            });
//        } else {
            input.getOutput().read(context, contentHandler);
//        }
    }

    protected void readInputAsSAX(PipelineContext context, String inputName, ContentHandler contentHandler) {
        readInputAsSAX(context, getInputByName(inputName), contentHandler);
    }

    protected Document readInputAsDOM(PipelineContext context, ProcessorInput input) {
        final TransformerHandler identity = TransformerUtils.getIdentityTransformerHandler();
        final DOMResult domResult = new DOMResult(XMLUtils.createDocument());
        identity.setResult(domResult);
        readInputAsSAX(context, input, identity);
        return (Document) domResult.getNode();
    }

    protected org.dom4j.Document readInputAsDOM4J(PipelineContext context, ProcessorInput input) {
        LocationSAXContentHandler ch = new LocationSAXContentHandler();
        readInputAsSAX(context, input, ch);
        return ch.getDocument();
    }

    protected DocumentInfo readInputAsTinyTree(PipelineContext pipelineContext, Configuration configuration, ProcessorInput input) {
        final TinyBuilder treeBuilder = new TinyBuilder();

        final TransformerHandler identity = TransformerUtils.getIdentityTransformerHandler(configuration);
        identity.setResult(treeBuilder);
        readInputAsSAX(pipelineContext, input, identity);

        return (DocumentInfo) treeBuilder.getCurrentRoot();
    }

    protected Document readInputAsDOM(PipelineContext context, String inputName) {
        return readInputAsDOM(context, getInputByName(inputName));
    }

    protected org.dom4j.Document readInputAsDOM4J(PipelineContext context, String inputName) {
        return readInputAsDOM4J(context, getInputByName(inputName));
    }


    protected Document readCacheInputAsDOM(PipelineContext context, String inputName) {
        return (Document) readCacheInputAsObject(context, getInputByName(inputName), new CacheableInputReader() {
            public Object read(PipelineContext context, ProcessorInput input) {
                return readInputAsDOM(context, input);
            }
        });
    }

    protected org.dom4j.Document readCacheInputAsDOM4J(PipelineContext context, String inputName) {
        return (org.dom4j.Document) readCacheInputAsObject(context, getInputByName(inputName), new CacheableInputReader() {
            public Object read(PipelineContext context, ProcessorInput input) {
                return readInputAsDOM4J(context, input);
            }
        });
    }

    protected DocumentInfo readCacheInputAsTinyTree(PipelineContext pipelineContext, final Configuration configuration, String inputName) {
        return (DocumentInfo) readCacheInputAsObject(pipelineContext, getInputByName(inputName), new CacheableInputReader() {
            public Object read(PipelineContext context, ProcessorInput input) {
                return readInputAsTinyTree(context, configuration, input);
            }
        });
    }

    /**
     * To be used in the readImpl implementation of a processor when an object
     * is created based on an input (an the object only depends on the input).
     *
     * @param input   The input the object depends on
     * @param reader  The code constructing the object based on the input
     * @return        The object returned by the reader (either directly returned,
     *                or from the cache)
     */
    protected Object readCacheInputAsObject(PipelineContext context, ProcessorInput input, CacheableInputReader reader) {
        // Get associated output
        ProcessorOutput output = input.getOutput();

        String debugInfo = logger.isDebugEnabled()
                ? "[" + output.getName() + ", " + output.getProcessorClass() + ", "
                + input.getName() + ", " + input.getProcessorClass() + "]"
                : null;

        if (output instanceof CacheableInputOutput) {
            // Get cache instance
            final Cache cache = ObjectCache.instance();

            // Check in cache first
            KeyValidity keyValidity = getInputKeyValidity(context, input);
            if (keyValidity != null) {
                final Object inputObject = cache.findValid(context, keyValidity.key, keyValidity.validity);
                if (inputObject != null) {
                    // Return cached object
                    if (logger.isDebugEnabled())
                        logger.debug("Cache " + debugInfo + ": source cacheable and found for key '" + keyValidity.key + "'. FOUND object: " + inputObject);
                    reader.foundInCache();
                    return inputObject;
                }
            }

            // Result was not found in cache, read result

//            final long startTime = System.nanoTime();

            if (logger.isDebugEnabled())
                logger.debug("Cache " + debugInfo + ": READING.");
            final Object result = reader.read(context, input);

            // Cache new result if possible, asking again for KeyValidity if needed
            if (keyValidity == null)
                keyValidity = getInputKeyValidity(context, input);

            if (keyValidity != null) {
                if (logger.isDebugEnabled())
                    logger.debug("Cache " + debugInfo + ": source cacheable for key '" + keyValidity.key + "'. STORING object:" + result);
                cache.add(context, keyValidity.key, keyValidity.validity, result);

//                System.out.println("Cache cost: " + (System.nanoTime() - startTime));

                reader.storedInCache();
            }

            return result;
        } else {
            if (logger.isDebugEnabled())
                logger.debug("Cache " + debugInfo + ": source never cacheable. READING.");
            // Never read from cache
            return reader.read(context, input);
        }
    }

    protected Object getCachedInputAsObject(PipelineContext pipelineContext, ProcessorInput processorInput) {
        // Get associated output
        final ProcessorOutput output = processorInput.getOutput();

        if (output instanceof CacheableInputOutput) {
            // Get cache instance
            final Cache cache = ObjectCache.instance();

            // Check cache
            final KeyValidity keyValidity = getInputKeyValidity(pipelineContext, processorInput);
            if (keyValidity != null) {
                return cache.findValid(pipelineContext, keyValidity.key, keyValidity.validity);
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    private void addSelfAsParent(PipelineContext context) {
        Stack<ProcessorImpl> parents = (Stack<ProcessorImpl>) context.getAttribute(PipelineContext.PARENT_PROCESSORS);
        if (parents == null) {
            parents = new Stack<ProcessorImpl>();
            context.setAttribute(PipelineContext.PARENT_PROCESSORS, parents);
        }
        parents.push(this);
    }

    private void removeSelfAsParent(PipelineContext context) {
        Stack parents = (Stack) context.getAttribute(PipelineContext.PARENT_PROCESSORS);
        if (parents.peek() != this)
            throw new ValidationException("Current processor should be on top of the stack", getLocationData());
        parents.pop();
    }

    /**
     * For use in processor that contain other processors.
     * Consider the current processor as the parent of the processors on which
     * we call read/start.
     */
    protected void executeChildren(PipelineContext context, Runnable runnable) {
        addSelfAsParent(context);
        try {
            runnable.run();
        } finally {
            removeSelfAsParent(context);
        }
    }

    /**
     * For use in processor that contain other processors.
     * Consider the current processor as a child or at the same level of the
     * processors on which we call read/start.
     */
    protected static void executeParents(PipelineContext context, Runnable runnable) {
        final Stack<ProcessorImpl> parents = (Stack<ProcessorImpl>) context.getAttribute(PipelineContext.PARENT_PROCESSORS);
        final ProcessorImpl thisPipelineProcessor = parents.peek();
        thisPipelineProcessor.removeSelfAsParent(context);
        try {
            runnable.run();
        } finally {
            thisPipelineProcessor.addSelfAsParent(context);
        }
    }

    protected static Object getParentState(final PipelineContext context) {
        final Stack<ProcessorImpl> parents = (Stack<ProcessorImpl>) context.getAttribute(PipelineContext.PARENT_PROCESSORS);
        final ProcessorImpl parent = parents.peek();
        final Object[] result = new Object[1];
        executeParents(context, new Runnable() {
            public void run() {
                result[0] = parent.getState(context);
            }
        });
        return result[0];
    }

    /**
     * This method is used to retrieve the state information set with setState().
     *
     * This method may be called from start() and ProcessorOutput.readImpl().
     *
     * @param   pipelineContext current context
     * @return  state object set by the caller of setState()
     */
    public Object getState(PipelineContext pipelineContext) {
        final Object state = pipelineContext.getAttribute(getProcessorKey(pipelineContext));
        if (state == null) {
            throw new OXFException("No state in context");
        }
        return state;
    }

    /**
     * This method is used by processor implementations to store state information tied to the
     * current execution of the current processor, across processor initialization as well as reads
     * of all the processor's outputs.
     *
     * This method should be called from the reset() method.
     *
     * @param context current PipelineContext object
     * @param state   user-defined object containing state information
     */
    protected void setState(PipelineContext context, Object state) {
        context.setAttribute(getProcessorKey(context), state);
    }

    protected boolean hasState(PipelineContext context) {
        return context.getAttribute(getProcessorKey(context)) != null;
    }

    /**
     * Returns a key that should be used to store the state of the processor in the context.
     *
     * This method must be called in ProcessorOutput.readImpl() or start() of the processors before read/start is
     * called on other processors. (The key returned by getProcessorKey can be used after read/start is called.)
     */
    protected ProcessorKey getProcessorKey(PipelineContext context) {
        final Stack<ProcessorImpl> parents = (Stack<ProcessorImpl>) context.getAttribute(PipelineContext.PARENT_PROCESSORS);
        return new ProcessorKey(parents);
    }

    public class ProcessorKey {

        private int hash = 0;
        private List<ProcessorImpl> processors;

        public ProcessorKey(Stack<ProcessorImpl> parents) {
            processors = (parents == null ? new ArrayList<ProcessorImpl>() : new ArrayList<ProcessorImpl>(parents));
            processors.add(ProcessorImpl.this);
            // NOTE: Use get() which appears to be faster (profiling) than using an iterator in such a bottleneck
            for (int i = 0; i < processors.size(); i++) {
                Object processor = processors.get(i);
                hash += processor.hashCode() * 31;
            }
        }

        public int hashCode() {
            return hash;
        }

        private List<ProcessorImpl> getProcessors() {
            return processors;
        }

        public boolean equals(Object other) {
            final List<ProcessorImpl> otherProcessors = ((ProcessorKey) other).getProcessors();
            int processorsSize = processors.size();
            if (processorsSize != otherProcessors.size())
                return false;
            // NOTE: Use get() which appears to be faster (profiling) than using an iterator in such a bottleneck
            for (int i = 0; i < processorsSize; i++) {
                if (processors.get(i) != otherProcessors.get(i))
                    return false;
            }
//            Iterator j = ((ProcessorKey) other).getProcessors().iterator();
//            for (Iterator i = processors.iterator(); i.hasNext();) {
//                if (!j.hasNext())
//                    return false;
//                if (i.next() != j.next())
//                    return false;
//            }
            return true;
//            return other instanceof ProcessorKey
//                    ? CollectionUtils.isEqualCollection(getProcessors(), ((ProcessorKey) other).getProcessors())
//                    : false;
        }

        public String toString() {
            FastStringBuffer result = null;
            for (Processor processor: processors) {
                if (result == null) {
                    result = new FastStringBuffer(hash + ": [");
                } else {
                    result.append(", ");
                }
                result.append(Integer.toString(processor.hashCode()));
                result.append(": ");
                result.append(processor.getClass().getName());
            }
            result.append("]");
            return result.toString();
        }
    }

    public void start(PipelineContext pipelineContext) {
        throw new ValidationException("Start not supported; processor implemented by '"
                + getClass().getName() + "'", locationData);
    }

    public void reset(PipelineContext pipelineContext) {
        // nop
    }

    /**
     * Utility methods to remove an item from a map of lists.
     */
    private void deleteFromListMap(Map map, Object toRemove) {
        for (Iterator i = map.keySet().iterator(); i.hasNext();) {
            List list = (List) map.get(i.next());
            for (Iterator j = list.iterator(); j.hasNext();) {
                Object current = j.next();
                if (current == toRemove) {
                    j.remove();
                }
            }
            if (list.size() == 0) {
                i.remove();
            }
        }
    }

    /**
     * Check if the given URI is referring to a processor input.
     */
    public static boolean isProcessorInputScheme(String uri) {
        // NOTE: The check on the hash is for backward compatibility
        return uri.startsWith("#")
                || (uri.startsWith(PROCESSOR_INPUT_SCHEME) && !uri.startsWith(PROCESSOR_INPUT_SCHEME + "/"))
                || (uri.startsWith(PROCESSOR_INPUT_SCHEME_OLD) && !uri.startsWith(PROCESSOR_INPUT_SCHEME_OLD + "/"));
    }

    /**
     * Return the input name if the URI is referring to a processor input, null otherwise.
     */
    public static String getProcessorInputSchemeInputName(String uri) {
        if (uri.startsWith("#")) {
            // NOTE: The check on the hash is for backward compatibility
            return uri.substring(1);
        } else if (uri.startsWith(PROCESSOR_INPUT_SCHEME) && !uri.startsWith(PROCESSOR_INPUT_SCHEME + "/")) {
            return uri.substring(PROCESSOR_INPUT_SCHEME.length());
        } else if (uri.startsWith(PROCESSOR_INPUT_SCHEME_OLD) && !uri.startsWith(PROCESSOR_INPUT_SCHEME_OLD + "/")) {
            return uri.substring(PROCESSOR_INPUT_SCHEME_OLD.length());
        } else {
            return null;
        }
    }

    /**
     * Basic implementation of ProcessorInput.
     */
    public static class ProcessorInputImpl implements ProcessorInput {

        private final Class clazz;
        private final String name;

        private ProcessorOutput output;
        private String id;
        private String schema;
        private String debugMessage;
        private LocationData locationData;
        private String systemId;
        private BreakpointKey breakpointKey;

        public ProcessorInputImpl(Class clazz, String name) {
            this.clazz = clazz;
            this.name = name;
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

        public void setBreakpointKey(BreakpointKey breakpointKey) {
            this.breakpointKey = breakpointKey;
        }
    }

    /**
     * Basic implementation of ProcessorOutput.
     */
    public abstract static class ProcessorOutputImpl implements ProcessorOutput, CacheableInputOutput {

        private final Class clazz;
        private final String name;

        private ProcessorInput input;
        private String id;
        private String schema;
        private String debugMessage;
        private LocationData locationData;
        private BreakpointKey breakpointKey;

        public ProcessorOutputImpl(Class clazz, String name) {
            this.clazz = clazz;
            this.name = name;
        }

        public void setInput(ProcessorInput input) {
            this.input = input;
        }

        public ProcessorInput getInput() {
            return input;
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
            this.breakpointKey = breakpointKey;
        }

        protected abstract void readImpl(PipelineContext pipelineContext, ContentHandler contentHandler);

        protected OutputCacheKey getKeyImpl(PipelineContext pipelineContext) {
            return null;
        }

        protected Object getValidityImpl(PipelineContext pipelineContext) {
            return null;
        }

        /**
         * All the methods implemented here should never be called.
         */
        private abstract class ProcessorFilter implements ProcessorOutput, CacheableInputOutput {
            public void setInput(ProcessorInput processorInput) {
            }

            public ProcessorInput getInput() {
                return null;
            }

            public void setSchema(String schema) {
            }

            public String getSchema() {
                return null;
            }

            public Class getProcessorClass() {
                return null;
            }

            public String getId() {
                return null;
            }

            public String getName() {
                return null;
            }

            public void setDebug(String debugMessage) {
            }

            public String getDebugMessage() {
                return null;
            }

            public void setLocationData(LocationData locationData) {
            }

            public LocationData getLocationData() {
                return null;
            }

            public void setBreakpointKey(BreakpointKey breakpointKey) {
            }

            public void read(PipelineContext context, ContentHandler ch) {
                throw new OXFException("This method should never be called!!!");
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
        private class ConcreteProcessorFilter extends ProcessorFilter {

            private ProcessorOutput processorOutput;
            private ProcessorOutput previousProcessorOutput;

            private class ForwarderProcessorOutput extends ProcessorFilter {
                public void read(PipelineContext context, ContentHandler contentHandler) {
                    previousProcessorOutput.read(context, contentHandler);
                }

                public OutputCacheKey getKey(PipelineContext context) {
                    return previousProcessorOutput instanceof CacheableInputOutput
                            ? ((CacheableInputOutput) previousProcessorOutput).getKey(context) : null;
                }

                public Object getValidity(PipelineContext context) {
                    return previousProcessorOutput instanceof CacheableInputOutput
                            ? ((CacheableInputOutput) previousProcessorOutput).getValidity(context) : null;
                }

            }

            public ConcreteProcessorFilter(ProcessorInput processorInput,
                                           ProcessorOutput processorOutput,
                                           final ProcessorOutput previousOutput) {
                this.processorOutput = processorOutput;
                this.previousProcessorOutput = previousOutput;
                processorInput.setOutput(new ForwarderProcessorOutput());
            }

            public void read(PipelineContext context, ContentHandler contentHandler) {
                processorOutput.read(context, contentHandler);
            }

            public OutputCacheKey getKey(PipelineContext context) {
                return processorOutput instanceof CacheableInputOutput
                        ? ((CacheableInputOutput) processorOutput).getKey(context) : null;
            }

            public Object getValidity(PipelineContext context) {
                return processorOutput instanceof CacheableInputOutput
                        ? ((CacheableInputOutput) processorOutput).getValidity(context) : null;
            }
        }

        private ProcessorFilter createFilter() {

            // Get inspector instance

            // Final filter (i.e. at the top, executed last)
            ProcessorFilter filter = new ProcessorFilter() {
                public void read(PipelineContext context, ContentHandler contentHandler) {
                    // Read the current processor output
                    readImpl(context, contentHandler);
                }

                public OutputCacheKey getKey(PipelineContext context) {
                    return getKeyImpl(context);
                }

                public Object getValidity(PipelineContext context) {
                    return getValidityImpl(context);
                }
            };

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
                        debugProcessor.createInput(INPUT_DATA);
                        debugProcessor.createOutput(OUTPUT_DATA);

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
                            = debugProcessor.getOutputByName( OUTPUT_DATA );
                        final ProcessorInput dbgIn = debugProcessor.getInputByName( INPUT_DATA );
                        filter = new ConcreteProcessorFilter( dbgIn, dbgOut, filter );
                    }
                }
            }

            // The PropertySet can be null during properties initialization. This should be one of the
            // rare places where this should be tested on.
            PropertySet propertySet = Properties.instance().getPropertySet();

            // Create and hook-up output validation processor if needed
            Boolean isOutputValidation = (propertySet == null) ? null : propertySet.getBoolean(USER_VALIDATION_FLAG, true);
            if (isOutputValidation != null && isOutputValidation.booleanValue() && getSchema() != null) {
                Processor outputValidator = new MSVValidationProcessor(getSchema());
                // Create data input and output
                ProcessorInput input = outputValidator.createInput(INPUT_DATA);
                ProcessorOutput output = outputValidator.createOutput(OUTPUT_DATA);
                // Create and connect config input
                Processor resourceGenerator = PipelineUtils.createURLGenerator(getSchema());
                PipelineUtils.connect(resourceGenerator, OUTPUT_DATA, outputValidator, MSVValidationProcessor.INPUT_SCHEMA);
                PipelineUtils.connect(MSVValidationProcessor.NO_DECORATION_CONFIG, OUTPUT_DATA,
                        outputValidator, INPUT_CONFIG);
                filter = new ConcreteProcessorFilter(input, output, filter);
            }

            // Hook-up input validation processor if needed
            Boolean isInputValidation = isOutputValidation;
            if (isInputValidation != null && isInputValidation.booleanValue() &&
                    getInput() != null && getInput().getSchema() != null) {
                Processor inputValidator = new MSVValidationProcessor(getInput().getSchema());
                // Create data input and output
                ProcessorInput input = inputValidator.createInput(INPUT_DATA);
                ProcessorOutput output = inputValidator.createOutput(OUTPUT_DATA);
                // Create and connect config input

                Processor resourceGenerator = PipelineUtils.createURLGenerator(getInput().getSchema());
                PipelineUtils.connect(resourceGenerator, OUTPUT_DATA, inputValidator, MSVValidationProcessor.INPUT_SCHEMA);
                PipelineUtils.connect(MSVValidationProcessor.NO_DECORATION_CONFIG, OUTPUT_DATA,
                    inputValidator, INPUT_CONFIG);
                filter = new ConcreteProcessorFilter(input, output, filter);
            }

            // Perform basic inspection of SAX events
            Boolean isSAXInspection = (propertySet == null) ? null : propertySet.getBoolean(SAX_INSPECTION_FLAG, false);
            if (isSAXInspection != null && isSAXInspection.booleanValue()) {
                final ProcessorFilter previousFilter = filter;
                filter = new ProcessorFilter() {
                    public void read(PipelineContext context, ContentHandler contentHandler) {
                        InspectingContentHandler inspectingContentHandler = new InspectingContentHandler(contentHandler);
                        previousFilter.read(context, inspectingContentHandler);
                    }

                    public OutputCacheKey getKey(PipelineContext context) {
                        return previousFilter.getKey(context);
                    }

                    public Object getValidity(PipelineContext context) {
                        return previousFilter.getValidity(context);
                    }
                };
            }

            return filter;
        }

        /**
         * NOTE: We should never use processor instance variables. Here, the creation may not be thread safe in that
         * the filter may be initialized several times. This should not be a real problem, and the execution should not
         * be problematic either. It may be safer to synchronize getFilter().
         */
        ProcessorFilter filter = null;

        private ProcessorFilter getFilter() {
            if (filter == null)
                filter = createFilter();
            return filter;
        }

        public final void read(PipelineContext context, ContentHandler contentHandler) {
            final Trace trc = context.getTrace();
            final TraceInfo tinf;
            if (trc == null) {
                tinf = null;
            } else {
                final String sysID;
                final int line;
                if (breakpointKey == null) {
                    final Class cls = getClass();
                    sysID = cls.getName() + " " + this + " " + getName() + " " + getId();
                    line = -1;
                } else {
                    sysID = breakpointKey.getSystemId();
                    line = breakpointKey.getLine();
                }
                tinf = new TraceInfo(sysID, line);
                trc.add(tinf);
            }
            try {
                getFilter().read(context, contentHandler);
            } catch (AbstractMethodError e) {
                logger.error(e);
            } catch (Exception e) {
                throw ValidationException.wrapException(e, getLocationData());
            } finally {
                if (tinf != null) tinf.end = System.currentTimeMillis();
            }
        }

        public final OutputCacheKey getKey(PipelineContext context) {
            return getFilter().getKey(context);
        }

        public final Object getValidity(PipelineContext context) {
            return getFilter().getValidity(context);
        }

        public final KeyValidity getKeyValidityImpl(PipelineContext context) {
            final OutputCacheKey outputCacheKey = getKeyImpl(context);
            if (outputCacheKey == null) return null;
            final Object outputCacheValidity = getValidityImpl(context);
            if (outputCacheValidity == null) return null;
            return new KeyValidity(outputCacheKey, outputCacheValidity);
        }
    }

    protected static OutputCacheKey getInputKey(PipelineContext context, ProcessorInput input) {
        final ProcessorOutput output = input.getOutput();
        return (output instanceof CacheableInputOutput) ? ((CacheableInputOutput) output).getKey(context) : null;
    }

    protected static Object getInputValidity(PipelineContext context, ProcessorInput input) {
        final ProcessorOutput output = input.getOutput();
        return (output instanceof CacheableInputOutput) ? ((CacheableInputOutput) output).getValidity(context) : null;
    }

    /**
     * Subclasses can use this utility method when implementing the getKey
     * and getValidity methods to make sure that they don't read the whole
     * config (if we don't already have it) just to return a key/validity.
     */
    protected boolean isInputInCache(PipelineContext context, ProcessorInput input) {
        final KeyValidity keyValidity = getInputKeyValidity(context, input);
        if (keyValidity == null)
            return false;
        return ObjectCache.instance().findValid(context, keyValidity.key, keyValidity.validity) != null;
    }

    protected boolean isInputInCache(PipelineContext context, String inputName) {
        return isInputInCache(context, getInputByName(inputName));
    }

    protected boolean isInputInCache(PipelineContext context, KeyValidity keyValidity) {
        return ObjectCache.instance().findValid(context, keyValidity.key, keyValidity.validity) != null;
    }

    /**
     * Subclasses can use this utility method to obtain the key and validity associated with an
     * input when implementing the getKey and getValidity methods.
     *
     * @return  a KeyValidity object containing non-null key and validity, or null
     */
    protected KeyValidity getInputKeyValidity(PipelineContext context, ProcessorInput input) {
        final OutputCacheKey outputCacheKey = getInputKey(context, input);
        if (outputCacheKey == null) return null;
        final InputCacheKey inputCacheKey = new InputCacheKey(input, outputCacheKey);
        final Object inputValidity = getInputValidity(context, input);
        if (inputValidity == null) return null;
        return new KeyValidity(inputCacheKey, inputValidity);
    }

    protected KeyValidity getInputKeyValidity(PipelineContext context, String inputName) {
        return getInputKeyValidity(context, getInputByName(inputName));
    }

    /**
     * Implementation of a caching transformer output that assumes that an output simply depends on
     * all the inputs plus optional local information.
     *
     * It is possible to implement local key and validity information as well, that represent data
     * not coming from an XML input. If any input is connected to an output that is not cacheable,
     * a null key is returned.
     *
     * Use DigestTransformerOutputImpl whenever possible.
     */
    public abstract class CacheableTransformerOutputImpl extends ProcessorOutputImpl {
        public CacheableTransformerOutputImpl(Class clazz, String name) {
            super(clazz, name);
        }

        /**
         * Processor outputs that use the local key/validity feature must
         * override this method and return true.
         */
        protected boolean supportsLocalKeyValidity() {
            return false;
        }

        protected CacheKey getLocalKey(PipelineContext pipelineContext) {
            throw new UnsupportedOperationException();
        }

        protected Object getLocalValidity(PipelineContext pipelineContext) {
            throw new UnsupportedOperationException();
        }

        public OutputCacheKey getKeyImpl(PipelineContext pipelineContext) {

            // NOTE: This implementation assumes that there is only one input with a given name

            // Create input information
            final Set entrySet = getConnectedInputs().entrySet();
            final int keyCount = entrySet.size() + (supportsLocalKeyValidity() ? 1 : 0);
            final CacheKey[] outputKeys = new CacheKey[keyCount];

            int keyIndex = 0;
            for (Iterator i = entrySet.iterator(); i.hasNext();) {
                final Map.Entry currentEntry = (Map.Entry) i.next();
                final List currentInputs = (List) currentEntry.getValue();
//                int inputIndex = 0;
//                for (Iterator j = currentInputs.iterator(); j.hasNext(); inputIndex++) {
                for (Iterator j = currentInputs.iterator(); j.hasNext();) {
//                    if (inputIndex > 0)
//                        logger.info("CacheableTransformerOutputImpl: processor " + getProcessorClass().getName() + " has more than 1 input called " + getName());
                    final OutputCacheKey outputKey = getInputKey(pipelineContext, (ProcessorInput) j.next());
                    if (outputKey == null) return null;
                    outputKeys[keyIndex++] = outputKey;
                }
            }

            // Add local key if needed
            if (supportsLocalKeyValidity()) {
                CacheKey localKey = getLocalKey(pipelineContext);
                if (localKey == null) return null;
                outputKeys[keyIndex++] = localKey;
            }

            // Concatenate current processor info and input info
            final Class processorClass = getProcessorClass();
            final String outputName = getName();
            return new CompoundOutputCacheKey(processorClass, outputName, outputKeys);
        }

        public Object getValidityImpl(PipelineContext pipelineContext) {
            final List<Object> validityObjects = new ArrayList<Object>();

            final Map inputsMap = getConnectedInputs();
            for (Iterator i = inputsMap.keySet().iterator(); i.hasNext();) {
                final List currentInputs = (List) inputsMap.get(i.next());
                for (Iterator j = currentInputs.iterator(); j.hasNext();) {
                    final Object validity = getInputValidity(pipelineContext, (ProcessorInput) j.next());
                    if (validity == null)
                        return null;
                    validityObjects.add(validity);
                }
            }

            // Add local validity if needed
            if (supportsLocalKeyValidity()) {
                final Object localValidity = getLocalValidity(pipelineContext);
                if (localValidity == null) return null;
                validityObjects.add(localValidity);
            }

            return validityObjects;
        }
    }

    /**
     * Implementation of a caching transformer output that assumes that an output simply depends on
     * all the inputs plus optional local information that can be digested.
     */
    public abstract class DigestTransformerOutputImpl extends CacheableTransformerOutputImpl {

        private final Long DEFAULT_VALIDITY = new Long(0);

        public DigestTransformerOutputImpl(Class clazz, String name) {
            super(clazz, name);
        }

        protected final boolean supportsLocalKeyValidity() {
            return true;
        }

        protected CacheKey getLocalKey(PipelineContext pipelineContext) {
            for (Iterator i = inputMap.keySet().iterator(); i.hasNext();) {
                String key = (String) i.next();
                if (!isInputInCache(pipelineContext, key))// NOTE: We don't really support multiple inputs with the same name.
                    return null;
            }
            return getFilledOutState(pipelineContext).key;
        }

        protected final Object getLocalValidity(PipelineContext pipelineContext) {
            for (Iterator i = inputMap.keySet().iterator(); i.hasNext();) {
                String key = (String) i.next();
                if (!isInputInCache(pipelineContext, key))// NOTE: We don't really support multiple inputs with the same name.
                    return null;
            }
            return getFilledOutState(pipelineContext).validity;
        }

        /**
         * Fill-out user data into the state, if needed. Return caching information.
         *
         * @param pipelineContext the current PipelineContext
         * @param digestState state set during processor start() or reset()
         * @return false if private information is known that requires disabling caching, true otherwise
         */
        protected abstract boolean fillOutState(PipelineContext pipelineContext, DigestState digestState);

        /**
         * Compute a digest of the internal document on which the output depends.
         *
         * @param digestState state set during processor start() or reset()
         * @return the digest
         */
        protected abstract byte[] computeDigest(PipelineContext pipelineContext, DigestState digestState);

        protected final DigestState getFilledOutState(PipelineContext pipelineContext) {
            // This is called from both readImpl and getLocalValidity. Based on the assumption that
            // a getKeyImpl will be followed soon by a readImpl if it fails, we compute key,
            // validity, and user-defined data.

            DigestState state = (DigestState) getState(pipelineContext);

            // Create request document
            boolean allowCaching = fillOutState(pipelineContext, state);

            // Compute key and validity if possible
            if ((state.validity == null || state.key == null) && allowCaching) {
                // Compute digest
                if (state.digest == null) {
                    state.digest = computeDigest(pipelineContext, state);
                }
                // Compute local key
                if (state.key == null) {
                    state.key = new InternalCacheKey(ProcessorImpl.this, "requestHash", new String(state.digest));
                }
                // Compute local validity
                if (state.validity == null) {
                    state.validity = DEFAULT_VALIDITY; // HACK so we don't recurse at the next line
                    OutputCacheKey outputCacheKey = getKeyImpl(pipelineContext);
                    if (outputCacheKey != null) {
                        Cache cache = ObjectCache.instance();
                        DigestValidity digestValidity = (DigestValidity) cache.findValid(pipelineContext, outputCacheKey, DEFAULT_VALIDITY);
                        if (digestValidity != null && Arrays.equals(state.digest, digestValidity.digest)) {
                            state.validity = digestValidity.lastModified;
                        } else {
                            Long currentValidity = new Long(System.currentTimeMillis());
                            cache.add(pipelineContext, outputCacheKey, DEFAULT_VALIDITY, new DigestValidity(state.digest, currentValidity));
                            state.validity = currentValidity;
                        }
                    } else {
                        state.validity = null; // HACK restore
                    }
                }
            }

            return state;
        }
    }

    /**
     * Cache an object associated with a given processor output.
     *
     * @param pipelineContext   current PipelineContext
     * @param processorOutput   output to associate with
     * @param keyName           key for the object to cache
     * @param creator           creator for the object
     * @return                  created object if caching was possible, null otherwise
     */
    protected Object getCacheOutputObject(PipelineContext pipelineContext, ProcessorOutputImpl processorOutput, String keyName, OutputObjectCreator creator) {

        final KeyValidity outputKeyValidity = processorOutput.getKeyValidityImpl(pipelineContext);
        if (outputKeyValidity != null) {
            // Output is cacheable

            // Get cache instance
            final Cache cache = ObjectCache.instance();

            // Check in cache first
            final CacheKey internalCacheKey = new InternalCacheKey(this, "outputKey", keyName);
            final CacheKey compoundCacheKey = new CompoundOutputCacheKey(this.getClass(), processorOutput.getName(),
                    new CacheKey[] { outputKeyValidity.key, internalCacheKey } );

            final List<Object> compoundValidities = new ArrayList<Object>();
            compoundValidities.add(outputKeyValidity.validity);
            compoundValidities.add(new Long(0));

            final Object cachedObject = cache.findValid(pipelineContext, compoundCacheKey, compoundValidities);
            if (cachedObject != null) {
                // Found it
                creator.foundInCache();
                return cachedObject;
            } else {
                // Not found, call method to create object
                final Object readObject = creator.create(pipelineContext, processorOutput);
                cache.add(pipelineContext, compoundCacheKey, compoundValidities, readObject);
                return readObject;
            }
        } else {
            creator.unableToCache();
            return null;
        }
    }

    /**
     * Get a cached object associated with a given processor output.
     *
     * @param pipelineContext   current PipelineContext
     * @param processorOutput   output to associate with
     * @param keyName           key for the object to cache
     * @return                  cached object if object found, null otherwise
     */
    protected Object getOutputObject(PipelineContext pipelineContext, ProcessorOutputImpl processorOutput, String keyName) {
        final KeyValidity outputKeyValidityImpl = processorOutput.getKeyValidityImpl(pipelineContext);
        if (outputKeyValidityImpl != null) {
            // Output is cacheable

            // Get cache instance
            final Cache cache = ObjectCache.instance();

            // Check in cache first
            final CacheKey internalCacheKey = new InternalCacheKey(this, "outputKey", keyName);
            final CacheKey compoundCacheKey = new CompoundOutputCacheKey(this.getClass(), processorOutput.getName(),
                    new CacheKey[] { outputKeyValidityImpl.key, internalCacheKey } );

            final List<Object> compoundValidities = new ArrayList<Object>();
            compoundValidities.add(outputKeyValidityImpl.validity);
            compoundValidities.add(new Long(0));

            return cache.findValid(pipelineContext, compoundCacheKey, compoundValidities);
        } else {
            return null;
        }
    }

    protected Object getOutputObject(PipelineContext pipelineContext, ProcessorOutputImpl processorOutput, String keyName, KeyValidity outputKeyValidityImpl) {
        if (outputKeyValidityImpl != null) {
            // Output is cacheable

            // Get cache instance
            final Cache cache = ObjectCache.instance();

            // Check in cache first
            final CacheKey internalCacheKey = new InternalCacheKey(this, "outputKey", keyName);
            final CacheKey compoundCacheKey = new CompoundOutputCacheKey(this.getClass(), processorOutput.getName(),
                    new CacheKey[] { outputKeyValidityImpl.key, internalCacheKey } );

            final List<Object> compoundValidities = new ArrayList<Object>();
            compoundValidities.add(outputKeyValidityImpl.validity);
            compoundValidities.add(new Long(0));

            return cache.findValid(pipelineContext, compoundCacheKey, compoundValidities);
        } else {
            return null;
        }
    }

    /**
     * Find the last modified timestamp of a particular input.
     *
     * @param pipelineContext       pipeline context
     * @param input                 input to check
     * @param inputMustBeInCache    if true, also return 0 if the input is not currently in cache
     * @return                      timestamp, <= 0 if unknown
     */
    protected long findInputLastModified(PipelineContext pipelineContext, ProcessorInput input, boolean inputMustBeInCache) {
        final long lastModified;
        {
            final KeyValidity keyValidity = getInputKeyValidity(pipelineContext, input);
            if (keyValidity == null || inputMustBeInCache && !isInputInCache(pipelineContext, keyValidity)) {
                lastModified = 0;
            } else {
                lastModified = (keyValidity.validity != null) ? findLastModified(keyValidity.validity) : 0;
            }
        }

        if (logger.isDebugEnabled())
            logger.debug("Last modified: " + lastModified);

        return lastModified;
    }

    /**
     * Recursively find the last modified timestamp of a validity object. Supported types are Long and List<Long>. The
     * latest timestamp is returned.
     *
     * @param validity  validity object
     * @return          timestamp, <= 0 if unknown
     */
    protected static long findLastModified(Object validity) {
        if (validity instanceof Long) {
            return ((Long) validity).longValue();
        } else if (validity instanceof List) {
            final List list = (List) validity;
            long latest = 0;
            for (Iterator i = list.iterator(); i.hasNext();) {
                final Object o = i.next();
                latest = Math.max(latest, findLastModified(o));
            }
            return latest;
        } else {
            return 0;
        }
    }

    private static class DigestValidity {
        public DigestValidity(byte[] digest, Long lastModified) {
            this.digest = digest;
            this.lastModified = lastModified;
        }

        public byte[] digest;
        public Long lastModified;
    }

    protected static class DigestState {
        public byte[] digest;
        public CacheKey key;
        public Object validity;
    }

    public static class KeyValidity {
        public KeyValidity(CacheKey key, Object validity) {
            this.key = key;
            this.validity = validity;
        }
        public CacheKey key;
        public Object validity;
    }
}
