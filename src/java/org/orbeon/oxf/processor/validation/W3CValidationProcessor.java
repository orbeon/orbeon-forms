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

import com.sun.msv.grammar.ExpressionPool;
import com.sun.msv.grammar.Grammar;
import com.sun.msv.reader.GrammarReader;
import com.sun.msv.reader.GrammarReaderController;
import com.sun.msv.reader.xmlschema.XMLSchemaReader;
import com.sun.msv.verifier.DocumentDeclaration;
import com.sun.msv.verifier.Verifier;
import com.sun.msv.verifier.regexp.REDocumentDeclaration;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.processor.*;
import org.orbeon.oxf.resources.URLFactory;
import org.orbeon.oxf.xml.ForwardingContentHandler;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.xml.sax.*;
import org.xml.sax.helpers.AttributesImpl;

import java.io.IOException;
import java.net.URL;

public class W3CValidationProcessor extends ProcessorImpl {


    private boolean decorateOutput = false;
    private String schemaId;

    public W3CValidationProcessor() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_DATA));
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    /**
     * Creates a validation processor that decorate the output tree with error attribute if there is a validation error.
     * The default behaviour is to throw a ValidationException
     **/
    public W3CValidationProcessor(boolean decorateOutput, String schemaId) {
        this();
        this.schemaId = schemaId;
        this.decorateOutput = decorateOutput;
    }

    public ProcessorOutput createOutput(String name) {
        ProcessorOutput output = new ProcessorImpl.CacheableTransformerOutputImpl(getClass(), name) {
            protected void readImpl(org.orbeon.oxf.pipeline.api.PipelineContext context, final ContentHandler contentHandler) {
                ProcessorInput i = getInputByName(INPUT_DATA);

                try {
                    Grammar grammar = (Grammar) readCacheInputAsObject(context,
                            getInputByName(INPUT_CONFIG),
                            new CacheableInputReader() {
                                public Object read(org.orbeon.oxf.pipeline.api.PipelineContext context, ProcessorInput input) {
                                    final Locator[] locator = new Locator[1];
                                    GrammarReader grammarReader = new XMLSchemaReader(new GrammarReaderController() {
                                        public void error(Locator[] locators, String s, Exception e) {
                                            throw new ValidationException(s, e, new LocationData(locators[0]));
                                        }

                                        public void warning(Locator[] locators, String s) {
                                            throw new ValidationException(s, new LocationData(locators[0]));
                                        }

                                        public InputSource resolveEntity(String publicId,
                                                                         String systemId)
                                                throws SAXException, IOException {
                                            URL url = URLFactory.createURL(
                                                    (locator[0] != null && locator[0].getSystemId() != null) ? locator[0].getSystemId() : null,
                                                    systemId);
                                            InputSource i = new InputSource(url.openStream());
//                                            i.setPublicId(publicId);
                                            i.setSystemId(url.toString());
                                            return i;
                                        }
                                    });
                                    readInputAsSAX(context, input, new ForwardingContentHandler(grammarReader) {
                                        public void setDocumentLocator(Locator loc) {
                                            super.setDocumentLocator(loc);
                                            locator[0] = loc;
                                        }
                                    });
                                    return grammarReader.getResultAsGrammar();
                                }
                            });
                    DocumentDeclaration vgm = new REDocumentDeclaration(grammar.getTopLevel(), new ExpressionPool());
                    Verifier verifier = new Verifier(vgm, new ErrorHandler()) {
                        boolean stopDecorating = false;

                        private void generateErrorElement(ValidationException ve) throws SAXException {
                            if (decorateOutput && ve != null) {
                                if (!stopDecorating) {
                                    AttributesImpl a = new AttributesImpl();
                                    a.addAttribute("", ValidationProcessor.MESSAGE_ATTRIBUTE,
                                            ValidationProcessor.MESSAGE_ATTRIBUTE, "CDATA", ve.getSimpleMessage());
                                    a.addAttribute("", ValidationProcessor.SYSTEMID_ATTRIBUTE,
                                            ValidationProcessor.SYSTEMID_ATTRIBUTE, "CDATA", ve.getLocationData().getSystemID());
                                    a.addAttribute("", ValidationProcessor.LINE_ATTRIBUTE,
                                            ValidationProcessor.LINE_ATTRIBUTE, "CDATA", Integer.toString(ve.getLocationData().getLine()));
                                    a.addAttribute("", ValidationProcessor.COLUMN_ATTRIBUTE,
                                            ValidationProcessor.COLUMN_ATTRIBUTE, "CDATA", Integer.toString(ve.getLocationData().getCol()));


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

                        public void characters(char[] chars, int i, int i1) throws SAXException {
                            try {
                                super.characters(chars, i, i1);
                            } catch (ValidationException e) {
                                generateErrorElement(e);
                            }
                            contentHandler.characters(chars, i, i1);
                        }

                        public void endDocument() throws SAXException {
                            try {
                                super.endDocument();
                            } catch (ValidationException e) {
                                generateErrorElement(e);
                            }
                            contentHandler.endDocument();
                        }

                        public void endElement(String s, String s1, String s2) throws SAXException {
                            try {
                                super.endElement(s, s1, s2);
                            } catch (ValidationException e) {
                                generateErrorElement(e);
                            }
                            contentHandler.endElement(s, s1, s2);
                        }

                        public void startDocument() throws SAXException {
                            try {
                                super.startDocument();
                            } catch (ValidationException e) {
                                generateErrorElement(e);
                            }
                            contentHandler.startDocument();
                        }

                        public void startElement(String s, String s1, String s2, Attributes attributes) throws SAXException {
                            ((ErrorHandler) getErrorHandler()).setElement(s, s1);
                            try {
                                super.startElement(s, s1, s2, attributes);
                            } catch (ValidationException e) {
                                generateErrorElement(e);
                            }

                            contentHandler.startElement(s, s1, s2, attributes);
                        }

                        public void endPrefixMapping(String s) {
                            try {
                                super.endPrefixMapping(s);
                            } catch (ValidationException e) {
                                try {
                                    generateErrorElement(e);
                                } catch (SAXException se) {
                                    throw new OXFException(se.getException());
                                }
                            }
                            try {
                                contentHandler.endPrefixMapping(s);
                            } catch (SAXException se) {
                                throw new OXFException(se.getException());
                            }

                        }

                        public void processingInstruction(String s, String s1) {
                            try {
                                super.processingInstruction(s, s1);
                            } catch (ValidationException e) {
                                try {
                                    generateErrorElement(e);
                                } catch (SAXException se) {
                                    throw new OXFException(se.getException());
                                }
                            }
                            try {
                                contentHandler.processingInstruction(s, s1);
                            } catch (SAXException e) {
                                throw new OXFException(e.getException());
                            }
                        }

                        public void setDocumentLocator(Locator locator) {
                            try {
                                super.setDocumentLocator(locator);
                            } catch (ValidationException e) {
                                try {
                                    generateErrorElement(e);
                                } catch (SAXException se) {
                                    throw new OXFException(se.getException());
                                }
                            }
                            contentHandler.setDocumentLocator(locator);
                        }

                        public void skippedEntity(String s) {
                            try {
                                super.skippedEntity(s);
                            } catch (ValidationException e) {
                                try {
                                    generateErrorElement(e);
                                } catch (SAXException se) {
                                    throw new OXFException(se.getMessage());
                                }
                            }
                            try {
                                contentHandler.skippedEntity(s);
                            } catch (SAXException e) {
                                throw new OXFException(e.getException());
                            }
                        }

                        public void startPrefixMapping(String s, String s1) {
                            try {
                                super.startPrefixMapping(s, s1);
                            } catch (ValidationException e) {
                                try {
                                    generateErrorElement(e);
                                } catch (SAXException se) {
                                    throw new OXFException(se.getException());
                                }
                            }
                            try {
                                contentHandler.startPrefixMapping(s, s1);
                            } catch (SAXException e) {
                                throw new OXFException(e.getException());
                            }
                        }
                    };

                    readInputAsSAX(context, getInputByName(INPUT_DATA), verifier);

                } catch (Exception e) {
                    throw new OXFException(e);
                }
            }
        };
        addOutput(name, output);
        return output;
    }


    private class ErrorHandler implements org.xml.sax.ErrorHandler {

        private String namespaceUri = "";
        private String localName = "";

        public void setElement(String namespaceUri, String localName) {
            this.namespaceUri = namespaceUri;
            this.localName = localName;
        }

        private String getElement() {
            String ns = " near ";
            if (!namespaceUri.equals(""))
                ns = "{" + namespaceUri + "}";
            return ns + localName + " (schema: " + schemaId + ")";
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

}

