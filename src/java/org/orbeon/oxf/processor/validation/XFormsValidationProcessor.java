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
package org.orbeon.oxf.processor.validation;

import com.sun.msv.verifier.jarv.Const;
import com.sun.msv.verifier.jarv.TheFactoryImpl;
import org.apache.log4j.Logger;
import org.dom4j.Document;
import org.iso_relax.verifier.Schema;
import org.iso_relax.verifier.Verifier;
import org.iso_relax.verifier.VerifierConfigurationException;
import org.iso_relax.verifier.VerifierFactory;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.*;
import org.orbeon.oxf.resources.URLFactory;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xml.ForwardingContentHandler;
import org.orbeon.oxf.xml.SAXStore;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.xml.sax.*;
import org.xml.sax.helpers.AttributesImpl;

import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.io.StringReader;
import java.net.URL;
import java.util.Iterator;
import java.util.Stack;

/**
 *
 */
public class XFormsValidationProcessor extends ProcessorImpl {

    private Logger logger = LoggerFactory.createLogger(MSVValidationProcessor.class);

    public static final String XXFORMS_ERROR_ATTRIBUTE_NAME = "error";
    public static final String INPUT_SCHEMA = "schema";


    private String schemaId;

    public XFormsValidationProcessor() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_DATA));
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG));
        addInputInfo(new ProcessorInputOutputInfo(INPUT_SCHEMA));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    // This factory is used by this class only. We synchronize around it later.
    private static SAXParserFactory factory = null;
    private static synchronized SAXParserFactory getFactory() {
        if (factory == null)
            factory = XMLUtils.createSAXParserFactory(false, true);
        return factory;
    }

    /**
     * Creates a validation processor that decorate the output tree with error attribute if there is a validation error.
     * The default behaviour is to throw a ValidationException
     **/
    public XFormsValidationProcessor(String schemaId) {
        this();
        this.schemaId = schemaId;
    }

    public ProcessorOutput createOutput(String name) {
        ProcessorOutput output = new ProcessorImpl.CacheableTransformerOutputImpl(getClass(), name) {
            protected void readImpl(PipelineContext context, ContentHandler contentHandler) {
                try {
                    Schema schema = (Schema) readCacheInputAsObject(context,
                            getInputByName(INPUT_SCHEMA),
                            new CacheableInputReader() {
                                public Object read(PipelineContext context, ProcessorInput input) {
                                    try {
                                        long time=0;
                                        if(logger.isDebugEnabled()) {
                                            logger.debug("Reading Schema: "+schemaId);
                                            time = System.currentTimeMillis();
                                        }
                                        Document schemaDoc = readInputAsDOM4J(context, input);
                                        LocationData locator = (LocationData) schemaDoc.getRootElement().getData();
                                        final String schemaSystemId = (locator != null && locator.getSystemID() != null) ? locator.getSystemID() : null;
                                        // Be sure to set our own XML parser factory
                                        VerifierFactory verifierFactory = new TheFactoryImpl(getFactory());
                                        verifierFactory.setEntityResolver(new EntityResolver() {
                                            public InputSource resolveEntity(String publicId, String systemId) throws IOException {
                                                URL url = URLFactory.createURL(schemaSystemId, systemId);
                                                InputSource i = new InputSource(url.openStream());
                                                i.setSystemId(url.toString());
                                                return i;
                                            }
                                        });
                                        verifierFactory.setFeature( Const.PANIC_MODE_FEATURE, false );
                                        InputSource is = new InputSource(new StringReader(Dom4jUtils.domToString(schemaDoc)));
                                        is.setSystemId(schemaSystemId);

                                        // Just a precaution, as the factory is not thread-safe. Does this makes sense?
                                        synchronized (MSVValidationProcessor.class) {
                                            Schema schema = verifierFactory.compileSchema(is);
                                            if(logger.isDebugEnabled())
                                                logger.debug(schemaId + " : Schema compiled in "+(System.currentTimeMillis()-time));
                                            return schema;
                                        }
                                    } catch (VerifierConfigurationException vce) {
                                        throw new OXFException(vce.getCauseException());
                                    } catch (Exception e) {
                                        throw new OXFException(e);
                                    }
                                }
                            });

                    // Create verifier
                    final Verifier verifier = schema.newVerifier();

                    // Create handler
                    abstract class Handler extends ForwardingContentHandler implements ErrorHandler {
                        protected Handler(ContentHandler contentHandler) {
                            super(contentHandler);
                        }
                    };

                    //final ContentHandler _contentHandler = new SAXLoggerProcessor.DebugContentHandler(contentHandler);
                    final ContentHandler _contentHandler = contentHandler;

                    Handler stateContentHandler = new Handler(verifier.getVerifierHandler()) {

                        public boolean recording;

                        private String uri;
                        private String localname;
                        private String qName;
                        private Attributes attributes;
                        private SAXStore saxStore = new SAXStore();
                        private Stack relevantState = new Stack();

                        private boolean inAnnotateState;

                        public void startDocument() throws SAXException {
                            super.startDocument();
                            _contentHandler.startDocument();
                        }

                        public void endDocument() throws SAXException {
                            super.endDocument();
                            _contentHandler.endDocument();
                        }

                        public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {
                            flushRecordIfNeeded();

                            // Record the event
                            this.uri = uri;
                            this.localname = localname;
                            this.qName = qName;
                            this.attributes = new AttributesImpl(attributes);
                            this.recording = true;

//                            {
//                                int index = attributes.getIndex("http://orbeon.org/oxf/xml/xforms", "error");
//                                if (index > -1) {
//                                    logger.info("xxforms:error value for : " + qName + ", " + attributes.getValue(index));
//                                }
//                            }
                            // Find xxforms:relevant attribute if any
                            {
                                // Store a null iif non-relevant
                                int index = attributes.getIndex(XFormsConstants.XXFORMS_NAMESPACE_URI, "relevant");
                                relevantState.push((index != -1 && "false".equals(attributes.getValue(index))) ? null : this);

//                                if (index > -1) {
//                                    logger.info("xxforms:relevant value for : " + qName + ", " + attributes.getValue(index));
//                                }
                            }

                            // Just send it to the parent, never directly to the output
                            super.startElement(uri, localname, qName, attributes);
                        }

                        public void endElement(String uri, String localname, String qName) throws SAXException {
                            inAnnotateState = true;
                            super.endElement(uri, localname, qName);
                            inAnnotateState = false;
                            flushRecordIfNeeded();
                            _contentHandler.endElement(uri, localname, qName);

                            relevantState.pop();
                        }

                        public void characters(char[] chars, int start, int length) throws SAXException {
                            inAnnotateState = true;
                            super.characters(chars, start, length);
                            inAnnotateState = false;
                            // Record the event if needed
                            if (recording) {
                                saxStore.characters(chars, start, length);
                            } else {
                                _contentHandler.characters(chars, start, length);
                            }
                        }

                        public void startPrefixMapping(String s, String s1) throws SAXException {
                            super.startPrefixMapping(s, s1);
                            // Record the event if needed
                            if (recording) {
                                saxStore.startPrefixMapping(s, s1);
                            } else {
                                _contentHandler.startPrefixMapping(s, s1);
                            }
                        }

                        public void endPrefixMapping(String s) throws SAXException {
                            super.endPrefixMapping(s);
                            // Record the event if needed
                            if (recording) {
                                saxStore.endPrefixMapping(s);
                            } else {
                                _contentHandler.endPrefixMapping(s);
                            }
                        }

                        public void processingInstruction(String s, String s1) throws SAXException {
                            super.processingInstruction(s, s1);
                            // Record the event if needed
                            if (recording) {
                                saxStore.processingInstruction(s, s1);
                            } else {
                                _contentHandler.processingInstruction(s, s1);
                            }
                        }

                        public void ignorableWhitespace(char[] chars, int start, int length) throws SAXException {
                            super.ignorableWhitespace(chars, start, length);
                            // Record the event if needed
                            if (recording) {
                                saxStore.ignorableWhitespace(chars, start, length);
                            } else {
                                _contentHandler.ignorableWhitespace(chars, start, length);
                            }
                        }

                        public void skippedEntity(String s) throws SAXException {
                            super.skippedEntity(s);
                            // Record the event if needed
                            if (recording) {
                                saxStore.skippedEntity(s);
                            } else {
                                _contentHandler.skippedEntity(s);
                            }
                        }

                        private void flushRecordIfNeeded() throws SAXException {
                            // Send out startElement and optional character content
                            if (recording) {
                                _contentHandler.startElement(uri, localname, qName, attributes);
                                saxStore.replay(_contentHandler);
                                saxStore.clear();
                                recording = false;
                            }
                        }

                        private void generateErrorAnnotation(ValidationException exception) {
                            if (recording && inAnnotateState && isCurrentElementRelevant()) {
                                // Annotate attributes on enclosing element
                                // We only annotate if we are in a relevant element
                                AttributesImpl newAttributes = new AttributesImpl(attributes);
                                newAttributes.addAttribute(XFormsConstants.XXFORMS_NAMESPACE_URI, XXFORMS_ERROR_ATTRIBUTE_NAME, XFormsConstants.XXFORMS_PREFIX + ":" + XXFORMS_ERROR_ATTRIBUTE_NAME, "CDATA", exception.getMessage());
                                newAttributes.addAttribute(XFormsConstants.XXFORMS_NAMESPACE_URI, XFormsConstants.VALID_ATTRIBUTE_NAME, XFormsConstants.XXFORMS_PREFIX + ":" + XFormsConstants.VALID_ATTRIBUTE_NAME, "CDATA", "false");
                                attributes = newAttributes;
                            }
                        }

                        private boolean isCurrentElementRelevant() {
                            // Everything under a non-relevant element is non-relevant
                            for (Iterator i = relevantState.iterator(); i.hasNext();) {
                                if (i.next() == null)
                                    return false;
                            }

                            return true;
                        }

                        public void error(SAXParseException exception) {
//                            logger.info("Validation error: " + exception.getMessage());
                            generateErrorAnnotation(new ValidationException("Warning " + exception.getMessage()+ "(schema: "+ schemaId + ")", new LocationData(exception)));
                        }

                        public void fatalError(SAXParseException exception) {
//                            logger.info("Validation fatalError: " + exception.getMessage());
                            generateErrorAnnotation(new ValidationException("Fatal Error " + exception.getMessage() + "(schema: "+ schemaId + ")", new LocationData(exception)));
                        }

                        public void warning(SAXParseException exception) {
//                            logger.info("Validation warning: " + exception.getMessage());
                            generateErrorAnnotation(new ValidationException("Warning " + exception.getMessage()+ "(schema: "+ schemaId + ")", new LocationData(exception)));
                        }
                    };

                    // Set error handler
                    verifier.setErrorHandler(stateContentHandler);

                    long time = 0;
                    if(logger.isDebugEnabled()) {
                        time = System.currentTimeMillis();
                    }
                    // Read input through the handler
                    readInputAsSAX(context, getInputByName(INPUT_DATA), stateContentHandler);
                    if(logger.isDebugEnabled()) {
                        logger.debug(schemaId+" validation completed in "+(System.currentTimeMillis()-time));
                    }

                } catch (Exception e) {
                    throw new OXFException(e);
                }
            }
        };
        addOutput(name, output);
        return output;
    }
}

