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

import com.thaiopensource.datatype.xsd.DatatypeLibraryFactoryImpl;
import com.thaiopensource.relaxng.Schema;
import com.thaiopensource.relaxng.SchemaFactory;
import com.thaiopensource.relaxng.ValidatorHandler;
import com.thaiopensource.relaxng.XMLReaderCreator;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.processor.*;
import org.orbeon.oxf.processor.generator.URLGenerator;
import org.orbeon.oxf.resources.URLFactory;
import org.orbeon.oxf.xml.ForwardingContentHandler;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.xml.sax.*;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.XMLFilterImpl;

import java.io.IOException;
import java.net.URL;

public class RNGValidationProcessor extends ProcessorImpl {

    public static final String NAMESPACE_URI = "http://relaxng.org/ns/structure/1.0";

    private boolean decorateOutput = false;
    private String schemaId;


    public RNGValidationProcessor() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_DATA));
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    /**
     * Creates a validation processor that decorate the output tree with error
     * attribute if there is a validation error. The default behaviour is to
     * throw a ValidationException
     */
    public RNGValidationProcessor(boolean decorateOutput, String schemaId) {
        this();
        this.schemaId = schemaId;
        this.decorateOutput = decorateOutput;
    }

    /** Jing  **/
    public ProcessorOutput createOutput(String name) {
        ProcessorOutput output = new ProcessorImpl.CacheableTransformerOutputImpl(getClass(), name) {
            protected void readImpl(org.orbeon.oxf.pipeline.api.PipelineContext context, final ContentHandler contentHandler) {
                try {
                    final ErrorHandler errorHandler = new ErrorHandler(schemaId);
                    Schema schema = (Schema) readCacheInputAsObject(context, getInputByName(INPUT_CONFIG), new CacheableInputReader() {
                        public Object read(final org.orbeon.oxf.pipeline.api.PipelineContext context, final ProcessorInput input) {
                            ProcessorXMLReaderCreator xmlReaderCreator = new ProcessorXMLReaderCreator(context, input);
                            SchemaFactory factory = new SchemaFactory();
                            factory.setDatatypeLibraryFactory(new DatatypeLibraryFactoryImpl());
                            factory.setErrorHandler(errorHandler);
                            factory.setXMLReaderCreator(xmlReaderCreator);

                            try {
                                Schema s = factory.createSchema(new InputSource());
                                xmlReaderCreator.close();
                                return s;
                            } catch (Exception e) {
                                throw new OXFException(e);
                            }
                        }
                    });
                    final ValidatorHandler validator = schema.createValidator(errorHandler);
                    readInputAsSAX(context, getInputByName(INPUT_DATA), new ContentHandler() {
                        boolean stopDecorating = false;
                        Locator locator;

                        private void generateErrorElement(ValidationException ve) throws SAXException {
                            if (decorateOutput && ve != null) {
                                if (!stopDecorating) {
                                    AttributesImpl a = new AttributesImpl();
                                    a.addAttribute("", ValidationProcessor.MESSAGE_ATTRIBUTE,
                                            ValidationProcessor.MESSAGE_ATTRIBUTE,
                                            "CDATA", ve.getSimpleMessage());
                                    a.addAttribute("", ValidationProcessor.SYSTEMID_ATTRIBUTE,
                                            ValidationProcessor.SYSTEMID_ATTRIBUTE,
                                            "CDATA", ve.getLocationData().getSystemID());
                                    a.addAttribute("", ValidationProcessor.LINE_ATTRIBUTE,
                                            ValidationProcessor.LINE_ATTRIBUTE,
                                            "CDATA", Integer.toString(ve.getLocationData().getLine()));
                                    a.addAttribute("", ValidationProcessor.COLUMN_ATTRIBUTE,
                                            ValidationProcessor.COLUMN_ATTRIBUTE,
                                            "CDATA", Integer.toString(ve.getLocationData().getCol()));


                                    contentHandler.startElement(ValidationProcessor.ORBEON_ERROR_NS,
                                            ValidationProcessor.ERROR_ELEMENT,
                                            ValidationProcessor.ORBEON_ERROR_PREFIX + ":" + ValidationProcessor.ERROR_ELEMENT,
                                            a);
                                    contentHandler.endElement(ValidationProcessor.ORBEON_ERROR_NS,
                                            ValidationProcessor.ERROR_ELEMENT,
                                            ValidationProcessor.ORBEON_ERROR_PREFIX + ":" + ValidationProcessor.ERROR_ELEMENT);

                                    stopDecorating = true;
                                }
                            } else {
                                throw ve;
                            }
                        }

                        public void characters(char ch[], int start, int length)
                                throws SAXException {
                            try {
                                validator.characters(ch, start, length);
                            } catch (ValidationException e) {
                                generateErrorElement(e);
                            }
                            contentHandler.characters(ch, start, length);
                        }

                        public void endDocument()
                                throws SAXException {
                            try {
                                validator.endDocument();
                            } catch (ValidationException e) {
                                generateErrorElement(e);
                            }
                            try {
                                contentHandler.endDocument();
                            } catch (Exception e) {
//                                System.out.println(e);
                                e.printStackTrace();

                            }
                            stopDecorating = false;
                        }

                        public void endElement(String namespaceURI, String localName,
                                               String qName)
                                throws SAXException {
                            errorHandler.setElement(namespaceURI, localName);
                            try {
                                validator.endElement(namespaceURI, localName, qName);
                            } catch (ValidationException e) {
                                generateErrorElement(e);
                            } catch (Exception e) {
                                generateErrorElement(new ValidationException
                                        (e.getMessage(), new LocationData(locator)));
                            }
                            contentHandler.endElement(namespaceURI, localName, qName);
                        }

                        public void endPrefixMapping(String prefix)
                                throws SAXException {
                            try {
                                validator.endPrefixMapping(prefix);
                            } catch (ValidationException e) {
                                generateErrorElement(e);
                            }
                            contentHandler.endPrefixMapping(prefix);
                        }

                        public void ignorableWhitespace(char ch[], int start, int length)
                                throws SAXException {
                            try {
                                validator.ignorableWhitespace(ch, start, length);
                            } catch (ValidationException e) {
                                generateErrorElement(e);
                            }
                            contentHandler.ignorableWhitespace(ch, start, length);
                        }

                        public void processingInstruction(String target, String data)
                                throws SAXException {
                            try {
                                validator.processingInstruction(target, data);
                            } catch (ValidationException e) {
                                generateErrorElement(e);
                            }
                            contentHandler.processingInstruction(target, data);
                        }

                        public void setDocumentLocator(Locator locator) {
                            try {
                                this.locator = locator;
                                validator.setDocumentLocator(locator);
                            } catch (ValidationException e) {
                                try {
                                    generateErrorElement(e);
                                } catch (SAXException se) {
                                    throw new OXFException(se.getException());
                                }
                            }
                            contentHandler.setDocumentLocator(locator);
                        }

                        public void skippedEntity(String name)
                                throws SAXException {
                            try {
                                validator.skippedEntity(name);
                            } catch (ValidationException e) {
                                generateErrorElement(e);
                            }
                            contentHandler.skippedEntity(name);
                        }

                        public void startDocument()
                                throws SAXException {
                            try {
                                validator.startDocument();
                            } catch (ValidationException e) {
                                generateErrorElement(e);
                            }
                            contentHandler.startDocument();
                        }

                        public void startElement(String namespaceURI, String localName,
                                                 String qName, Attributes atts)
                                throws SAXException {
                            // Jing doesn't like xmlns attribute. Strip them out !
                            atts = XMLUtils.stripNamespaceAttributes(atts);
                            if (namespaceURI == null) {
                                namespaceURI = "";
                            }
                            errorHandler.setElement(namespaceURI, localName);
                            try {
                                validator.startElement(namespaceURI, localName, qName, atts);
                            } catch (ValidationException e) {
                                generateErrorElement(e);
                            }
                            contentHandler.startElement(namespaceURI, localName, qName, atts);
                        }

                        public void startPrefixMapping(String prefix, String uri)
                                throws SAXException {
                            validator.startPrefixMapping(prefix, uri);
                            contentHandler.startPrefixMapping(prefix, uri);
                        }
                    });
                } catch (Exception e) {
                    throw new OXFException(e);
                }
            }
        };
        addOutput(name, output);
        return output;
    }

    private static class ErrorHandler implements org.xml.sax.ErrorHandler {

        private String id;
        private String namespaceUri = "";
        private String localName = "";

        public ErrorHandler(String id) {
            this.id = id;
        }

        public void setElement(String namespaceUri, String localName) {
            this.namespaceUri = namespaceUri;
            this.localName = localName;
        }

        private String getElement() {
            return " near " + (namespaceUri.equals("") ? "" : "{" + namespaceUri + "}")
                    + localName + " (schema: " + id + ")";
        }

        public void warning(SAXParseException exception)
                throws SAXException {
            throw new ValidationException("Warning " + exception.getMessage() + getElement(), new LocationData(exception));
        }

        public void fatalError(SAXParseException exception)
                throws SAXException {
            throw new ValidationException("Fatal Error " + exception.getMessage() + getElement(), new LocationData(exception));
        }

        public void error(SAXParseException exception)
                throws SAXException {
            throw new ValidationException("Error " + exception.getMessage() + getElement(), new LocationData(exception));
        }
    }

    private static class ProcessorXMLReaderCreator implements XMLReaderCreator {

        private org.orbeon.oxf.pipeline.api.PipelineContext context;
        private ProcessorInput input;
        private final Locator[] loc = new Locator[1];

        public ProcessorXMLReaderCreator(org.orbeon.oxf.pipeline.api.PipelineContext context, ProcessorInput input) {
            this.context = context;
            this.input = input;
        }

        public void close() {
            context = null;
            input = null;
            loc[0] = null;
        }

        public XMLReader createXMLReader() throws SAXException {
            XMLFilter filter = new XMLFilterImpl() {
                ForwardingContentHandler forwardingContentHandler = new ForwardingContentHandler(null, true) {
                    public void startElement(String namespaceURI, String localName,
                                             String qName, Attributes atts)
                            throws SAXException {
                        // Jing doesn't like xmlns attribute. Strip them out !
                        atts = XMLUtils.stripNamespaceAttributes(atts);
                        super.startElement(namespaceURI, localName, qName, atts);
                    }

                    public void setDocumentLocator(Locator locator) {
                        super.setDocumentLocator(locator);
                        loc[0] = locator;
                    }
                };

                public void parse(InputSource _input)
                        throws IOException {
                    if(_input.getSystemId() == null)
                        input.getOutput().read(context, forwardingContentHandler);
                    else {
                        URL url = URLFactory.createURL(
                                (loc[0] != null && loc[0].getSystemId() != null) ? loc[0].getSystemId() : null,
                                _input.getSystemId());
                        Processor urlgen = new URLGenerator(url);
                        urlgen.createOutput(ProcessorImpl.OUTPUT_DATA).read(context, forwardingContentHandler);
                    }
                }

                public void setContentHandler(ContentHandler handler) {
                    forwardingContentHandler.setContentHandler(handler);
                    super.setContentHandler(handler);
                }
            };
            return filter;
        }
    }
}
