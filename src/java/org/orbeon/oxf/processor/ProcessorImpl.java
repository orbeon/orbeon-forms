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
package org.orbeon.oxf.processor;

import orbeon.apache.xerces.xni.NamespaceContext;
import orbeon.apache.xml.utils.DOMBuilder;
import org.apache.log4j.Logger;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.orbeon.oxf.cache.*;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.debugger.api.BreakpointKey;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.generator.DOMGenerator;
import org.orbeon.oxf.processor.pipeline.PipelineProcessor;
import org.orbeon.oxf.processor.validation.MSVValidationProcessor;
import org.orbeon.oxf.resources.OXFProperties;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.util.PipelineUtils;
import org.orbeon.oxf.xml.SchemaRepository;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.InspectingContentHandler;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.oxf.xml.dom4j.LocationSAXContentHandler;
import org.w3c.dom.Document;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.util.*;

/**
 * Helper class that implements default method of the
 * Processor interface.
 */
public abstract class ProcessorImpl implements Processor {
    
    static private Logger logger = LoggerFactory.createLogger(ProcessorImpl.class);

    public static final String INPUT_DATA = "data";
    public static final String INPUT_CONFIG = "config";
    public static final String OUTPUT_DATA = "data";

    public static final String PROCESSOR_VALIDATION_FLAG = "oxf.validation.processor";
    public static final String USER_VALIDATION_FLAG = "oxf.validation.user";
    public static final String SAX_INSPECTION_FLAG = "oxf.sax.inspection";

    public static final char KEY_SEPARATOR = '?';

    private String id;
    private QName name;
    private Map inputMap = new HashMap();
    private Map outputMap = new HashMap();
    private List inputsInfo = new ArrayList();
    private List outputsInfo = new ArrayList();

    private String systemId;
    private LocationData locationData;
    public static final String PROCESSOR_INPUT_SCHEME = "oxf:";

    public String getSystemId() {
        return systemId;
    }

    public void setSystemId(String systemId) {
        this.systemId = systemId;
    }

    /**
     * Return a property set for this processor.
     */
    protected OXFProperties.PropertySet getPropertySet() {
        return OXFProperties.instance().getPropertySet(getName());
    }

    public LocationData getLocationData() {
        return locationData;
    }

    public void setLocationData(LocationData locationData) {
        this.locationData = locationData;
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

    public Map getInputs() {
        return inputMap;
    }

    public ProcessorInput getInputByName(String name) {
        List l = (List) inputMap.get(name);
        if (l == null)
            throw new ValidationException("Cannot find input \"" + name + "\"", getLocationData());
        if (l.size() != 1)
            throw new ValidationException("Found more than one input \"" + name + "\"", getLocationData());
        return (ProcessorInput) l.get(0);
    }

    public List getInputsByName(String name) {
        List result = (List) inputMap.get(name);
        return result == null ? Collections.EMPTY_LIST : result;
    }

    public ProcessorInput createInput(final String name) {
        ProcessorInputOutputInfo inputInfo = getInputInfo(name);

        // The PropertySet can be null during properties initialization. This should be one of the
        // rare places where this should be tested on. By default, enable validation so the
        // properties can be validated!
        OXFProperties.PropertySet propertySet = OXFProperties.instance().getPropertySet();
        Boolean valEnabled = (propertySet == null) ? new Boolean(true) : propertySet.getBoolean(PROCESSOR_VALIDATION_FLAG, true);
        if (valEnabled.booleanValue() && inputInfo != null && inputInfo.getSchemaURI() != null) {

            if (logger.isDebugEnabled())
                logger.debug("Creating validator for input name '" + name
                        + "' and schema-uri '" + inputInfo.getSchemaURI() + "'");

            // Create and hook-up input validation processor if needed
            final Processor inputValidator = new MSVValidationProcessor(inputInfo.getSchemaURI());

            // Connect schema to validator
            Processor schema = SchemaRepository.instance().getResourceGenerator(inputInfo.getSchemaURI());
            PipelineUtils.connect(schema, OUTPUT_DATA, inputValidator, MSVValidationProcessor.INPUT_SCHEMA);
            PipelineUtils.connect(MSVValidationProcessor.NO_DECORATION_CONFIG, OUTPUT_DATA, inputValidator, INPUT_CONFIG);

            // Create data input and output
            final ProcessorInput inputValData = inputValidator.createInput(INPUT_DATA);
            final ProcessorOutput outputValData = inputValidator.createOutput(OUTPUT_DATA);

            ProcessorInput fakeInput = new ProcessorInput() {
                public String getDebugMessage() {
                    return inputValData.getDebugMessage();
                }

                public LocationData getDebugLocationData() {
                    return inputValData.getDebugLocationData();
                }

                public String getName() {
                    return name;
                }

                public ProcessorOutput getOutput() {
                    return outputValData;
                }

                public Class getProcessorClass() {
                    return ProcessorImpl.this.getClass();
                }

                public String getSchema() {
                    return inputValData.getSchema();
                }

                public void setDebug(String debugMessage, LocationData locationData) {
                    inputValData.setDebug(debugMessage, locationData);
                }

                public void setBreakpointKey(BreakpointKey breakpointKey) {
                    inputValData.setBreakpointKey(breakpointKey);
                }

                public void setOutput(ProcessorOutput output) {
                    inputValData.setOutput(output);
                }

                public void setSchema(String schema) {
                    inputValData.setSchema(schema);
                }
            };

            addInput(name, fakeInput);
            return fakeInput;
        } else {
            ProcessorInput input = new ProcessorInputImpl(ProcessorImpl.this.getClass(), name);
            addInput(name, input);
            return input;
        }
    }

    public void addInput(String name, ProcessorInput input) {
        List inputs = (List) inputMap.get(name);
        if (inputs == null) {
            inputs = new ArrayList();
            inputMap.put(name, inputs);
        }
        inputs.add(input);
    }

    public void deleteInput(ProcessorInput input) {
        deleteFromListMap(inputMap, input);
    }

    public ProcessorOutput getOutputByName(String name) {
        List l = (List) outputMap.get(name);
        if (l == null || l.size() != 1)
            throw new ValidationException("Exactly one output " + name + " is required", getLocationData());
        return (ProcessorOutput) l.iterator().next();
    }

    public List getOutputsByName(String name) {
        List result = (List) outputMap.get(name);
        return result == null ? Collections.EMPTY_LIST : result;
    }

    public ProcessorOutput createOutput(String name) {
        throw new ValidationException("Outputs are not supported", getLocationData());
    }

    public void addOutput(String name, ProcessorOutput output) {
        List outputs = (List) outputMap.get(name);
        if (outputs == null) {
            outputs = new ArrayList();
            outputMap.put(name, outputs);
        }
        outputs.add(output);
    }

    public void deleteOutput(ProcessorOutput output) {
        deleteFromListMap(outputMap, output);
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

    public List getInputsInfo() {
        return inputsInfo;
    }

    public ProcessorInputOutputInfo getInputInfo(String name) {
        for (Iterator i = inputsInfo.iterator(); i.hasNext();) {
            ProcessorInputOutputInfo inputInfo = (ProcessorInputOutputInfo) i.next();
            if (inputInfo.getName().equals(name))
                return inputInfo;
        }
        return null;
    }

    public List getOutputsInfo() {
        return outputsInfo;
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
        /*
        HACK: The default DOMBuild ignores startPrefixMapping, and so does
        not create xmlns attributes. We need those with the DOM API (as opposed
        to the DOM4j API).

        TransformerHandler identity = TransformerUtils.getIdentityTransformerHandler();
        DOMResult domResult = new DOMResult(XMLUtils.createDocument());
        identity.setResult(domResult);
        readInputAsSAX(context, input, identity);
        return (Document) domResult.getNode();
        */
        Document result = XMLUtils.createDocument();
        DOMBuilder domBuilder = new DOMBuilder(result) {

            private List prefixes = new ArrayList();
            private List uris = new ArrayList();

            public void startPrefixMapping(String prefix, String uri) {
                prefixes.add(prefix);
                uris.add(uri);
            }

            public void startElement( String ns, String localName, String name, Attributes atts) throws SAXException {
                super.startElement(ns, localName, name, atts);
                org.w3c.dom.Element currentElement = (org.w3c.dom.Element) m_currentNode;
                for (Iterator i = prefixes.iterator(), j = uris.iterator(); i.hasNext();) {
                    String prefix = (String) i.next();
                    String uri = (String) j.next();
                    String currentURI = currentElement.getAttributeNS(NamespaceContext.XMLNS_URI, prefix);
                    if (currentURI == null || "".equals(currentURI)) {
                        currentElement.setAttributeNS(NamespaceContext.XMLNS_URI, "xmlns:" + prefix, uri);
                    } else if (!currentURI.equals(uri)) {
                        throw new OXFException("Different URI for same prefix '" + prefix + "'");
                    }
                }
                prefixes.clear();
                uris.clear();
            }
        };
        readInputAsSAX(context, input, domBuilder);
        return result;
    }

    protected org.dom4j.Document readInputAsDOM4J(PipelineContext context, ProcessorInput input) {
        LocationSAXContentHandler ch = new LocationSAXContentHandler();
//        readInputAsSAX(context, input, new SAXDebuggerProcessor.DebugContentHandler(ch));
        readInputAsSAX(context, input, ch);
        return ch.getDocument();
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

        if ((!(output instanceof Cacheable)))
            logger.info("Not Cacheable : " + output);

        if (output instanceof Cacheable) {
            Cacheable c = (Cacheable) output;

            // Check in cache first
            Cache cache = ObjectCache.instance();
            InputCacheKey inputKey = c.getKey(context) == null ? null :
                    new InputCacheKey(input, c.getKey(context));
            Object validity = (inputKey != null) ? c.getValidity(context) : null;
            if (inputKey != null && validity != null) {
                Object configObject = cache.findValid(context, inputKey, validity);
                if (configObject != null) {
                    // Return cached object
                    if (logger.isDebugEnabled())
                        logger.debug("Cache " + debugInfo + ": source cacheable and found for key '" + inputKey + "'. FOUND object: " + configObject);
                    return configObject;
                }
            }

            // Read result
            if (logger.isDebugEnabled())
                logger.debug("Cache " + debugInfo + ": READING.");
            Object result = reader.read(context, input);

            // Cache result if possible
            if (inputKey == null)
                inputKey = c.getKey(context) == null ? null :
                        new InputCacheKey(input, c.getKey(context));
            if (validity == null)
                validity = (inputKey != null) ? c.getValidity(context) : null;
            if (inputKey != null && validity != null) {
                if (logger.isDebugEnabled())
                    logger.debug("Cache " + debugInfo + ": source cacheable for key '" + inputKey + "'. STORING object:" + result);
                cache.add(context, inputKey, validity, result);
            }

            return result;
        } else {
            if (logger.isDebugEnabled())
                logger.debug("Cache " + debugInfo + ": source never cacheable. READING.");
            // Never read from cache
            return reader.read(context, input);
        }
    }

    private void addSelfAsParent(PipelineContext context) {
        Stack parents = (Stack) context.getAttribute(PipelineContext.PARENT_PROCESSORS);
        if (parents == null) {
            parents = new Stack();
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
        runnable.run();
        removeSelfAsParent(context);
    }

    /**
     * For use in processor that contain other processors.
     * Consider the current processor as a child or at the same level of the
     * processors on which we call read/start.
     */
    protected static void executeParents(PipelineContext context, Runnable runnable) {
        Stack parents = (Stack) context.getAttribute(PipelineContext.PARENT_PROCESSORS);
        PipelineProcessor thisPipelineProcessor = (PipelineProcessor) parents.peek();
        // We cast this here to go arround a javac bug.
        ProcessorImpl castedThisPipelineProcessor = thisPipelineProcessor;
        castedThisPipelineProcessor.removeSelfAsParent(context);
        runnable.run();
        castedThisPipelineProcessor.addSelfAsParent(context);
    }

    protected static Object getParentState(final PipelineContext context) {
        Stack parents = (Stack) context.getAttribute(PipelineContext.PARENT_PROCESSORS);
        final ProcessorImpl parent = (ProcessorImpl) parents.peek();
        final Object[] result = new Object[1];
        executeParents(context, new Runnable() {
            public void run() {
                result[0] = parent.getState(context);
            }
        });
        return result[0];
    }

    protected Object getState(PipelineContext context) {
        Object state = context.getAttribute(getProcessorKey(context));
        if (state == null) {
            throw new OXFException("No state in context");
        }
        return state;
    }

    protected void setState(PipelineContext context, Object state) {
        context.setAttribute(getProcessorKey(context), state);
    }

    /**
     * <p>Returns a key that should be used to store the state of the
     * processor in the context.
     *
     * <p>This method must be called in ProcessorOutput.readImpl()
     * or start() of the processors before read/start is called on other
     * processors. (The key returned by getProcessorKey can be used after
     * read/start is called.)
     */
    protected ProcessorKey getProcessorKey(PipelineContext context) {
        Stack parents = (Stack) context.getAttribute(PipelineContext.PARENT_PROCESSORS);
        return new ProcessorKey(parents);
    }

    public class ProcessorKey {

        private int hash = 0;
        private List processors;

        public ProcessorKey(Stack parents) {
            processors = (parents == null ? new ArrayList() : new ArrayList(parents));
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

        private List getProcessors() {
            return processors;
        }

        public boolean equals(Object other) {
            List otherProcessors = ((ProcessorKey) other).getProcessors();
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
            StringBuffer result = null;
            for (Iterator i = processors.iterator(); i.hasNext();) {
                if (result == null) {
                    result = new StringBuffer(hash + ": [");
                } else {
                    result.append(", ");
                }
                Processor processor = (Processor) i.next();
                result.append(processor.hashCode());
                result.append(": ");
                result.append(processor.getClass().getName());
            }
            result.append("]");
            return result.toString();
        }
    }

    public void start(PipelineContext context) {
        throw new ValidationException("Start not supported; processor implemented by '"
                + getClass().getName() + "'", locationData);
    }

    public void reset(PipelineContext context) {
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
        return uri.startsWith("#") || (uri.startsWith(PROCESSOR_INPUT_SCHEME) && !uri.startsWith(PROCESSOR_INPUT_SCHEME + "/"));
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
        } else {
            return null;
        }
    }

    /**
     * Basic implementation of ProcessorInput.
     */
    public static class ProcessorInputImpl implements ProcessorInput {

        private ProcessorOutput output;
        private String id;
        private Class clazz;
        private String name;
        private String schema;
        private String debugMessage;
        private LocationData debugLocationData;
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

        public LocationData getDebugLocationData() {
            return debugLocationData;
        }

        public void setDebug(String debugMessage, LocationData debugLocationData) {
            this.debugMessage = debugMessage;
            this.debugLocationData = debugLocationData;
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
    public abstract static class ProcessorOutputImpl implements ProcessorOutput, Cacheable {

        private ProcessorInput input;
        private String id;
        private Class clazz;
        private String name;
        private String schema;
        private String debugMessage;
        private LocationData debugLocationData;
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

        public LocationData getDebugLocationData() {
            return debugLocationData;
        }

        public void setDebug(String debugMessage, LocationData debugLocationData) {
            this.debugMessage = debugMessage;
            this.debugLocationData = debugLocationData;
        }

        public void setBreakpointKey(BreakpointKey breakpointKey) {
            this.breakpointKey = breakpointKey;
        }

        protected abstract void readImpl(PipelineContext context, ContentHandler contentHandler);

        protected OutputCacheKey getKeyImpl(PipelineContext context) {
            return null;
        }

        protected Object getValidityImpl(PipelineContext context) {
            return null;
        }

        /**
         * All the methods implemented here should never be called.
         */
        private abstract class ProcessorFilter implements ProcessorOutput, Cacheable {
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

            public void setDebug(String debugMessage, LocationData locationData) {
            }

            public String getDebugMessage() {
                return null;
            }

            public LocationData getDebugLocationData() {
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
                    return previousProcessorOutput instanceof Cacheable
                            ? ((Cacheable) previousProcessorOutput).getKey(context) : null;
                }

                public Object getValidity(PipelineContext context) {
                    return previousProcessorOutput instanceof Cacheable
                            ? ((Cacheable) previousProcessorOutput).getValidity(context) : null;
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
                return processorOutput instanceof Cacheable
                        ? ((Cacheable) processorOutput).getKey(context) : null;
            }

            public Object getValidity(PipelineContext context) {
                return processorOutput instanceof Cacheable
                        ? ((Cacheable) processorOutput).getValidity(context) : null;
            }
        }

        private ProcessorFilter createFilter(PipelineContext context) {

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
                ProcessorFactory debugProcessorFactory = ProcessorFactoryRegistry.lookup("oxf/processor/debug");
                if (debugProcessorFactory == null)
                    throw new OXFException("Cannot find debug processor factory at URI 'oxf/processor/debug'");

                for (int i = 0; i < 2; i++) {
                    String debugMessage = i == 0 ? getDebugMessage() :
                            getInput() == null ? null : getInput().getDebugMessage();
                    LocationData debugLocationData = i == 0 ? getDebugLocationData() :
                            getInput() == null ? null : getInput().getDebugLocationData();
                    if (debugMessage != null) {
                        Processor debugProcessor = debugProcessorFactory.createInstance(context);
                        debugProcessor.createInput(INPUT_DATA);
                        debugProcessor.createOutput(OUTPUT_DATA);

                        // Create config document for Debug processor
                        final org.dom4j.Document debugConfigDocument;
                        {
                            debugConfigDocument = DocumentHelper.createDocument();
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
                        PipelineUtils.connect(new DOMGenerator(debugConfigDocument), "data", debugProcessor, "config");
                        filter = new ConcreteProcessorFilter(debugProcessor.getInputByName(INPUT_DATA),
                                debugProcessor.getOutputByName(OUTPUT_DATA),
                                filter);
                    }
                }
            }

            // The PropertySet can be null during properties initialization. This should be one of the
            // rare places where this should be tested on.
            OXFProperties.PropertySet propertySet = OXFProperties.instance().getPropertySet();

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

            // Hookup inspector
//            if (useInspector) {
//                InspectorProcessor inspectorProcessor = new InspectorProcessor();
//                inspectorProcessor.createInput(INPUT_DATA);
//                inspectorProcessor.createOutput(OUTPUT_DATA);
//                inspectorProcessor.setJndiName("inspector");
//                inspectorProcessor.setProcessorInput(_input);
//                inspectorProcessor.setProcessorOutput(_output);
//                filter = new ConcreteProcessorFilter(inspectorProcessor.getInputByName(INPUT_DATA),
//                        inspectorProcessor.getOutputByName(OUTPUT_DATA),
//                        filter);
//            }

            // Hookup profiler
//            if (useProfiler) {
//                ProfilerProcessor profilerProcessor = new ProfilerProcessor();
//                profilerProcessor.createInput(INPUT_DATA);
//                profilerProcessor.createOutput(OUTPUT_DATA);
//                profilerProcessor.setJndiName("profiler");
//                profilerProcessor.setProcessorInput(_input);
//                profilerProcessor.setProcessorOutput(_output);
//                filter = new ConcreteProcessorFilter(profilerProcessor.getInputByName(INPUT_DATA),
//                        profilerProcessor.getOutputByName(OUTPUT_DATA),
//                        filter);
//            }

            // Handle breakpoints
//            if (get) {
//
//            }

            // Disable inspector for the filter chain. Its state will be restored at the end
            // of the chain before calling readImp().
//            if (useInspector) {
//                inspectorRun.setEnabled(false);
//            }

            return filter;
        }

        /**
         * NOTE: We should never use processor instance variables. Here, the creation may not be thread safe in that
         * the filter may be initialized several times. This should not be a real problem, and the execution should not
         * be problematic either. It may be safer to synchronize getFilter().
         */
        ProcessorFilter filter = null;

        private ProcessorFilter getFilter(PipelineContext context) {
            if (filter == null)
                filter = createFilter(context);
            return filter;
        }

        public final void read(PipelineContext context, ContentHandler contentHandler) {
            try {
                getFilter(context).read(context, contentHandler);
            } catch (AbstractMethodError e) {
                e.printStackTrace();
            }
        }

        public final OutputCacheKey getKey(PipelineContext context) {
            return getFilter(context).getKey(context);
        }

        public final Object getValidity(PipelineContext context) {
            return getFilter(context).getValidity(context);
        }
    }

    protected static OutputCacheKey getInputKey(PipelineContext context, ProcessorInput input) {
        ProcessorOutput output = input.getOutput();
        return (output instanceof Cacheable) ? ((Cacheable) output).getKey(context) : null;
    }

    protected static Object getInputValidity(PipelineContext context, ProcessorInput input) {
        ProcessorOutput output = input.getOutput();
        return (output instanceof Cacheable) ? ((Cacheable) output).getValidity(context) : null;
    }

    /**
     * Subclasses can use this utility method when inmplementing the getKey
     * and getValidity methods to make sure that they don't read the whole
     * config (if we don't already have it) just to return a key/validity.
     */
    protected boolean isInputInCache(PipelineContext context, String inputName) {
        OutputCacheKey outputCacheKey = getInputKey(context, getInputByName(inputName));
        if (outputCacheKey == null) return false;
        InputCacheKey inputCacheKey = new InputCacheKey(getInputByName(inputName), outputCacheKey);
        Object configValidity = getInputValidity(context, getInputByName(inputName));
        if (configValidity == null) return false;
        return ObjectCache.instance().findValid(context, inputCacheKey, configValidity) != null;
    }

    /**
     * Implementation of a caching transformer output that assumes that an
     * output simply depends on all the inputs plus optional local information.
     *
     * The key computed depends on the current processor's class name (that
     * identifies the processor functionality), the output name, and all the
     * input keys. A key will look like this (package names removed for
     * concision):
     *
     * XSLTTransformer#data?[ResourceGenerator#data?my/document|ResourceGenerator#config?my/other/document]
     *
     * It is possible to implement local key and validty information as well,
     * that represent data not coming from an XML input. If any input is
     * connected to an output that is not cacheable, a null key is returned.
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

        protected InternalCacheKey getLocalKey(PipelineContext context) {
            throw new UnsupportedOperationException();
        }

        protected Object getLocalValidity(PipelineContext context) {
            throw new UnsupportedOperationException();
        }

        public OutputCacheKey getKeyImpl(PipelineContext context) {

            // Create input information
            List keys = new ArrayList();
            Map inputsMap = getInputs();
            for (Iterator i = inputsMap.keySet().iterator(); i.hasNext();) {
                List currentInputs = (List) inputsMap.get(i.next());
                for (Iterator j = currentInputs.iterator(); j.hasNext();) {
                    OutputCacheKey outputKey = getInputKey(context, (ProcessorInput) j.next());
                    if (outputKey == null) return null;
                    keys.add(outputKey);
                }
            }

            // Add local key if needed
            if (supportsLocalKeyValidity()) {
                InternalCacheKey localKey = getLocalKey(context);
                if (localKey == null) return null;
                keys.add(localKey);
            }

            // Concatenate current processor info and input info
            return new OutputCacheKey(this, keys);
        }

        public Object getValidityImpl(PipelineContext context) {
            List validityObjects = new ArrayList();

            Map inputsMap = getInputs();
            for (Iterator i = inputsMap.keySet().iterator(); i.hasNext();) {
                List currentInputs = (List) inputsMap.get(i.next());
                for (Iterator j = currentInputs.iterator(); j.hasNext();) {
                    Object validity = getInputValidity(context, (ProcessorInput) j.next());
                    if (validity == null)
                        return null;
                    validityObjects.add(validity);
                }
            }

            // Add local validity if needed
            if (supportsLocalKeyValidity()) {
                Object localValidity = getLocalValidity(context);
                if (localValidity == null) return null;
                validityObjects.add(localValidity);
            }

            return validityObjects;
        }
    }
}
