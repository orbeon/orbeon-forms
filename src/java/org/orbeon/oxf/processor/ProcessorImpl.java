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
import org.dom4j.QName;
import org.orbeon.oxf.cache.*;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.pipeline.api.TransformerXMLReceiver;
import org.orbeon.oxf.pipeline.api.XMLReceiver;
import org.orbeon.oxf.processor.impl.DelegatingProcessorInput;
import org.orbeon.oxf.processor.impl.ProcessorInputImpl;
import org.orbeon.oxf.processor.validation.MSVValidationProcessor;
import org.orbeon.oxf.properties.Properties;
import org.orbeon.oxf.properties.PropertySet;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.util.PipelineUtils;
import org.orbeon.oxf.xml.SchemaRepository;
import org.orbeon.oxf.xml.TransformerUtils;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.oxf.xml.dom4j.LocationSAXContentHandler;
import org.orbeon.saxon.Configuration;
import org.orbeon.saxon.om.DocumentInfo;
import org.orbeon.saxon.tinytree.TinyBuilder;
import org.w3c.dom.Document;

import javax.xml.transform.dom.DOMResult;
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

    public static int PROCESSOR_SEQUENCE_NUMBER = 0;

    private final int sequenceNumber;

    private String id;
    private QName name;

    private final Map<String, List<ProcessorInput>> inputMap = new LinkedHashMap<String, List<ProcessorInput>>();
    private final Map<String, ProcessorOutput> outputMap = new LinkedHashMap<String, ProcessorOutput>();
    private int outputCount = 0;

    private final List<ProcessorInputOutputInfo> inputsInfo = new ArrayList<ProcessorInputOutputInfo>(0);
    private final List<ProcessorInputOutputInfo> outputsInfo = new ArrayList<ProcessorInputOutputInfo>(0);

    private LocationData locationData;

    public static final String PROCESSOR_INPUT_SCHEME = "input:";
    public static final String PROCESSOR_OUTPUT_SCHEME = "output:";

    /**
     * This is for internal pipeline engine use.
     */
    protected static final String PARENT_PROCESSORS = "parent-processors";

    protected ProcessorImpl() {
        sequenceNumber = PROCESSOR_SEQUENCE_NUMBER++;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

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
        final List<ProcessorInput> inputs =  inputMap.get(name);
        if (inputs == null)
            throw new ValidationException("Cannot find input \"" + name + "\"", getLocationData());
        if (inputs.size() != 1)
            throw new ValidationException("Found more than one input \"" + name + "\"", getLocationData());
        return inputs.get(0);
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

            input = new DelegatingProcessorInput(this, name, inputValData, outputValData);
        } else {
            input = new ProcessorInputImpl(this, name);
        }

        addInput(name, input);
        return input;
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

    public int getOutputCount() {
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

    protected void addOutputInfo(ProcessorInputOutputInfo outputInfo) {
        outputsInfo.add(outputInfo);
    }

    public Set<String> getInputNames() {
        return inputMap.keySet();
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

    /**
     * The fundamental read method based on SAX.
     */
    public static void readInputAsSAX(PipelineContext context, final ProcessorInput input, XMLReceiver xmlReceiver) {
        input.getOutput().read(context, xmlReceiver);
    }

    public void readInputAsSAX(PipelineContext context, String inputName, XMLReceiver xmlReceiver) {
        readInputAsSAX(context, getInputByName(inputName), xmlReceiver);
    }

    public Document readInputAsDOM(PipelineContext context, ProcessorInput input) {
        final TransformerXMLReceiver identity = TransformerUtils.getIdentityTransformerHandler();
        final DOMResult domResult = new DOMResult(XMLUtils.createDocument());
        identity.setResult(domResult);
        readInputAsSAX(context, input, identity);
        return (Document) domResult.getNode();
    }

    public org.dom4j.Document readInputAsDOM4J(PipelineContext context, ProcessorInput input) {
        LocationSAXContentHandler ch = new LocationSAXContentHandler();
        readInputAsSAX(context, input, ch);
        return ch.getDocument();
    }

    public DocumentInfo readInputAsTinyTree(PipelineContext pipelineContext, Configuration configuration, ProcessorInput input) {
        final TinyBuilder treeBuilder = new TinyBuilder();

        final TransformerXMLReceiver identity = TransformerUtils.getIdentityTransformerHandler(configuration);
        identity.setResult(treeBuilder);
        readInputAsSAX(pipelineContext, input, identity);

        return (DocumentInfo) treeBuilder.getCurrentRoot();
    }

    public org.dom4j.Document readInputAsDOM4J(PipelineContext context, String inputName) {
        return readInputAsDOM4J(context, getInputByName(inputName));
    }


    public Document readCacheInputAsDOM(PipelineContext context, String inputName) {
        return (Document) readCacheInputAsObject(context, getInputByName(inputName), new CacheableInputReader() {
            public Object read(PipelineContext context, ProcessorInput input) {
                return readInputAsDOM(context, input);
            }
        });
    }

    public org.dom4j.Document readCacheInputAsDOM4J(PipelineContext context, String inputName) {
        return (org.dom4j.Document) readCacheInputAsObject(context, getInputByName(inputName), new CacheableInputReader() {
            public Object read(PipelineContext context, ProcessorInput input) {
                return readInputAsDOM4J(context, input);
            }
        });
    }

    public DocumentInfo readCacheInputAsTinyTree(PipelineContext pipelineContext, final Configuration configuration, String inputName) {
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
    public Object readCacheInputAsObject(PipelineContext pipelineContext, ProcessorInput input, CacheableInputReader reader) {
        return readCacheInputAsObject(pipelineContext, input, reader, false);
    }

    public Object readCacheInputAsObject(PipelineContext pipelineContext, ProcessorInput input, CacheableInputReader reader, boolean logCache) {

        // Get associated output
        final ProcessorOutput output = input.getOutput();

        final String debugInfo = logger.isDebugEnabled()
                ? "[" + output.getName() + ", " + output.getProcessorClass() + ", "
                + input.getName() + ", " + input.getProcessorClass() + "]"
                : null;

        // Get cache instance
        final Cache cache = ObjectCache.instance();

        // Check in cache first
        KeyValidity keyValidity = getInputKeyValidity(pipelineContext, input);

        if (keyValidity != null && keyValidity.key != null && keyValidity.validity != null) {
            // We got a key and a validity
            final Object inputObject = cache.findValid(keyValidity.key, keyValidity.validity);
            if (inputObject != null) {
                // Return cached object
                if (logger.isDebugEnabled())
                    logger.debug("Cache " + debugInfo + ": source cacheable and found for key '" + keyValidity.key + "'. FOUND object: " + inputObject);

                reader.foundInCache();
                return inputObject;
            }
        }

        if (logger.isDebugEnabled())
            logger.debug("Cache " + debugInfo + ": READING.");

        final Object result = reader.read(pipelineContext, input);

        // Cache new result if possible, asking again for KeyValidity if needed
        if (keyValidity == null || keyValidity.key == null || keyValidity.validity == null)
            keyValidity = getInputKeyValidity(pipelineContext, input);

        if (keyValidity != null && keyValidity.key != null && keyValidity.validity != null) {
            if (logger.isDebugEnabled())
                logger.debug("Cache " + debugInfo + ": source cacheable for key '" + keyValidity.key + "'. STORING object:" + result);

            cache.add(keyValidity.key, keyValidity.validity, result);

            reader.storedInCache();
        }

        return result;
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
    public void setState(PipelineContext context, Object state) {
        context.setAttribute(getProcessorKey(context), state);
    }

    public boolean hasState(PipelineContext context) {
        return context.getAttribute(getProcessorKey(context)) != null;
    }

    /**
     * Returns a key that should be used to store the state of the processor in the context.
     *
     * This method must be called in ProcessorOutput.readImpl() or start() of the processors before read/start is
     * called on other processors. (The key returned by getProcessorKey can be used after read/start is called.)
     */
    public ProcessorKey getProcessorKey(PipelineContext context) {
        final Stack<ProcessorImpl> parents = (Stack<ProcessorImpl>) context.getAttribute(PARENT_PROCESSORS);
        return new ProcessorKey(parents);
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
        return uri.startsWith(PROCESSOR_INPUT_SCHEME) && !uri.startsWith(PROCESSOR_INPUT_SCHEME + "/");
    }

    /**
     * Check if the given URI is referring to a processor output.
     */
    public static boolean isProcessorOutputScheme(String uri) {
        return uri.startsWith(PROCESSOR_OUTPUT_SCHEME) && !uri.startsWith(PROCESSOR_OUTPUT_SCHEME + "/");
    }

    /**
     * Return the input name if the URI is referring to a processor input, null otherwise.
     */
    public static String getProcessorInputSchemeInputName(String uri) {
        return isProcessorInputScheme(uri) ? uri.substring(PROCESSOR_INPUT_SCHEME.length()) : null;
    }

    /**
     * Return the output name if the URI is referring to a processor output, null otherwise.
     */
    public static String getProcessorOutputSchemeInputName(String uri) {
        return isProcessorOutputScheme(uri) ? uri.substring(PROCESSOR_OUTPUT_SCHEME.length()) : null;
    }

    public static OutputCacheKey getInputKey(PipelineContext context, ProcessorInput input) {
        return input.getOutput().getKey(context);
    }

    public static Object getInputValidity(PipelineContext context, ProcessorInput input) {
        return input.getOutput().getValidity(context);
    }

    /**
     * Subclasses can use this utility method when implementing the getKey
     * and getValidity methods to make sure that they don't read the whole
     * config (if we don't already have it) just to return a key/validity.
     */
    public boolean isInputInCache(PipelineContext context, ProcessorInput input) {
        final KeyValidity keyValidity = getInputKeyValidity(context, input);
        return keyValidity != null && ObjectCache.instance().findValid(keyValidity.key, keyValidity.validity) != null;
    }

    public boolean isInputInCache(PipelineContext context, String inputName) {
        return isInputInCache(context, getInputByName(inputName));
    }

    public boolean isInputInCache(PipelineContext context, KeyValidity keyValidity) {
        return ObjectCache.instance().findValid(keyValidity.key, keyValidity.validity) != null;
    }

    /**
     * Subclasses can use this utility method to obtain the key and validity associated with an
     * input when implementing the getKey and getValidity methods.
     *
     * @return  a KeyValidity object containing non-null key and validity, or null
     */
    public KeyValidity getInputKeyValidity(PipelineContext context, ProcessorInput input) {
        final OutputCacheKey outputCacheKey = getInputKey(context, input);
        if (outputCacheKey == null) return null;
        final InputCacheKey inputCacheKey = new InputCacheKey(input, outputCacheKey);
        final Object inputValidity = getInputValidity(context, input);
        if (inputValidity == null) return null;
        return new KeyValidity(inputCacheKey, inputValidity);
    }

    public KeyValidity getInputKeyValidity(PipelineContext context, String inputName) {
        return getInputKeyValidity(context, getInputByName(inputName));
    }

    /**
     * Find the last modified timestamp of a particular input.
     *
     * @param pipelineContext       pipeline context
     * @param input                 input to check
     * @param inputMustBeInCache    if true, also return 0 if the input is not currently in cache
     * @return                      timestamp, <= 0 if unknown
     */
    public long findInputLastModified(PipelineContext pipelineContext, ProcessorInput input, boolean inputMustBeInCache) {
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
    public static long findLastModified(Object validity) {
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
            if (!(other instanceof ProcessorKey))
                return false;

            final List<ProcessorImpl> otherProcessors = ((ProcessorKey) other).getProcessors();
            int processorsSize = processors.size();
            if (processorsSize != otherProcessors.size())
                return false;
            // NOTE: Use get() which appears to be faster (profiling) than using an iterator in such a bottleneck
            for (int i = 0; i < processorsSize; i++) {
                if (processors.get(i) != otherProcessors.get(i))
                    return false;
            }
            return true;
        }

        public String toString() {
            StringBuilder result = null;
            for (Processor processor: processors) {
                if (result == null) {
                    result = new StringBuilder(hash + ": [");
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

    public static class KeyValidity {
        public KeyValidity(CacheKey key, Object validity) {
            this.key = key;
            this.validity = validity;
        }
        public CacheKey key;
        public Object validity;
    }

    // For backward compatibility
    protected abstract class ProcessorOutputImpl extends org.orbeon.oxf.processor.impl.ProcessorOutputImpl {
        public ProcessorOutputImpl(Class processorClass, String name) {
            super(processorClass, name);
        }

        public ProcessorOutputImpl(Processor processor, String name) {
            super(processor, name);
        }
    }
}
