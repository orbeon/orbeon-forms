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
package org.orbeon.oxf.processor.validation;

import org.orbeon.datatypes.LocationData;
import org.orbeon.dom.Document;
import org.orbeon.msv.iso_relax.verifier.*;
import org.orbeon.msv.verifier.jarv.Const;
import org.orbeon.msv.verifier.jarv.TheFactoryImpl;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.*;
import org.orbeon.oxf.processor.generator.DOMGenerator;
import org.orbeon.oxf.processor.impl.CacheableTransformerOutputImpl;
import org.orbeon.oxf.resources.URLFactory;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.xml.*;
import org.orbeon.oxf.xml.dom.IOSupport;
import org.orbeon.oxf.xml.dom.XmlLocationData;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.AttributesImpl;

import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.io.StringReader;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

public class MSVValidationProcessor extends ProcessorImpl {

    private static final org.slf4j.Logger logger = LoggerFactory.createLoggerJava(MSVValidationProcessor.class);

    public static final String ORBEON_ERROR_NS = "http://orbeon.org/oxf/xml/validation";
    public static final String ORBEON_ERROR_PREFIX = "v";
    public static final String ERROR_ELEMENT = "error";
    public static final String MESSAGE_ATTRIBUTE = "message";
    public static final String SYSTEMID_ATTRIBUTE = "system-id";
    public static final String LINE_ATTRIBUTE = "line";
    public static final String COLUMN_ATTRIBUTE = "column";
    public static final String INPUT_SCHEMA = "schema";

    private String schemaId;

    public static DOMGenerator NO_DECORATION_CONFIG;
    public static DOMGenerator DECORATION_CONFIG;
    private static final SAXParserFactory factory;

    static {

        {
            final Document configDoc = Document.apply("config");
            configDoc.getRootElement().addElement("decorate").setText("false");

            NO_DECORATION_CONFIG = new DOMGenerator(configDoc, "no decorate cfg", DOMGenerator.ZeroValidity, DOMGenerator.DefaultContext);
        }

        {
            final Document configDoc = Document.apply("config");
            configDoc.getRootElement().addElement("decorate").setText("true");

            DECORATION_CONFIG = new DOMGenerator(configDoc, "decorate cfg", DOMGenerator.ZeroValidity, DOMGenerator.DefaultContext);
        }
        // 02/06/2004 d : If we don't do anything VM would just convert unchecked exceptions thrown
        //                from here into ExceptionInInitializerError without setting the cause.
        //                This of course makes diagnosing reports from the field a major pain.
        try {
            factory = XMLParsing.createSAXParserFactory(ParserConfiguration.XIncludeOnly());
        } catch (final Error e) {
            throw new ExceptionInInitializerError(e);
        } catch (final RuntimeException e) {
            throw new ExceptionInInitializerError(e);
        }
    }


    public MSVValidationProcessor() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_DATA));
        // NOTE: Don't set a schema here, because that will trigger the insertion of a validation processor in the
        // pipeline engine recursively, causing a StackOverflowError.
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG));
        addInputInfo(new ProcessorInputOutputInfo(INPUT_SCHEMA));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    /**
     * Creates a validation processor that decorate the output tree with error attribute if there is a validation error.
     * The default behaviour is to throw a SchemaValidationException
     */
    public MSVValidationProcessor(String schemaId) {
        this();
        this.schemaId = schemaId;
    }

    @Override
    public ProcessorOutput createOutput(String name) {
        final ProcessorOutput output = new CacheableTransformerOutputImpl(MSVValidationProcessor.this, name) {
            protected void readImpl(PipelineContext context, final XMLReceiver xmlReceiver) {
                try {
                    // Read config input ot determine of we should decorate or not
                    // Would be nice to validate it, but we can't simply use the schema on the input as usual (recursion)
                    final Document configDoc = readCacheInputAsOrbeonDom(context, INPUT_CONFIG);
                    final boolean decorateOutput = Boolean.valueOf(XPathUtils.selectStringValueNormalize(configDoc, "/config/decorate")).booleanValue();

                    final Schema schema = readCacheInputAsObject(context,
                            getInputByName(INPUT_SCHEMA),
                            new CacheableInputReader<Schema>() {
                                public Schema read(org.orbeon.oxf.pipeline.api.PipelineContext context, ProcessorInput input) {
                                    try {
                                        long time = 0;
                                        if (logger.isDebugEnabled()) {
                                            logger.debug("Reading Schema: " + schemaId);
                                            time = System.currentTimeMillis();
                                        }
                                        final Document schemaDoc = readInputAsOrbeonDom(context, input);
                                        final LocationData locator = (LocationData) schemaDoc.getRootElement().getData();
                                        final String schemaSystemId = (locator != null && locator.file() != null) ? locator.file() : null;
                                        // Be sure to set our own XML parser factory
                                        final VerifierFactory verifierFactory = new TheFactoryImpl(factory);
                                        verifierFactory.setEntityResolver(new EntityResolver() {
                                            public InputSource resolveEntity(String publicId, String systemId) throws IOException {
                                                final URL url = URLFactory.createURL(schemaSystemId, systemId);
                                                final InputSource i = new InputSource(url.openStream());
                                                i.setSystemId(url.toString());
                                                return i;
                                            }
                                        });
                                        verifierFactory.setFeature(Const.PANIC_MODE_FEATURE, false);
                                        final InputSource is = new InputSource(new StringReader(IOSupport.domToStringJava(schemaDoc)));
                                        is.setSystemId(schemaSystemId);

                                        // Just a precaution, as the factory is not thread-safe. Does this makes sense?
                                        synchronized (MSVValidationProcessor.class) {
                                            final Schema schema = verifierFactory.compileSchema(is);

                                            if (logger.isDebugEnabled())
                                                logger.debug(schemaId + " : Schema compiled in " + (System.currentTimeMillis() - time));
                                            return schema;
                                        }
                                    } catch (VerifierConfigurationException vce) {
                                        throw new OXFException(vce.getCauseException());
                                    } catch (Exception e) {
                                        throw new OXFException(e);
                                    }
                                }
                            });

                    final Verifier verifier = schema.newVerifier();
                    verifier.setErrorHandler(new org.xml.sax.ErrorHandler() {


                        private void generateErrorElement(SchemaValidationException ve) throws SAXException {
                            if (decorateOutput && ve != null) {

                                final String systemId = ve.firstLocationData().file();
                                final AttributesImpl a = new AttributesImpl();
                                a.addAttribute("", MESSAGE_ATTRIBUTE,
                                        MESSAGE_ATTRIBUTE, "CDATA", ve.message());
                                a.addAttribute("", SYSTEMID_ATTRIBUTE,
                                        SYSTEMID_ATTRIBUTE, "CDATA", systemId == null ? "" : systemId);
                                a.addAttribute("", LINE_ATTRIBUTE,
                                        LINE_ATTRIBUTE, "CDATA", Integer.toString(ve.firstLocationData().line()));
                                a.addAttribute("", COLUMN_ATTRIBUTE,
                                        COLUMN_ATTRIBUTE, "CDATA", Integer.toString(ve.firstLocationData().col()));

                                xmlReceiver.startElement(ORBEON_ERROR_NS,
                                        ERROR_ELEMENT,
                                        ORBEON_ERROR_PREFIX + ":" + ERROR_ELEMENT,
                                        a);
                                xmlReceiver.endElement(ORBEON_ERROR_NS,
                                        ERROR_ELEMENT,
                                        ORBEON_ERROR_PREFIX + ":" + ERROR_ELEMENT);

                            } else {
                                throw ve;
                            }
                        }

                        public void error(SAXParseException exception) throws SAXException {
                            generateErrorElement(new SchemaValidationException("Error " + exception.getMessage() + "(schema: " + schemaId + ")", XmlLocationData.apply(exception)));
                        }

                        public void fatalError(SAXParseException exception) throws SAXException {
                            generateErrorElement(new SchemaValidationException("Fatal Error " + exception.getMessage() + "(schema: " + schemaId + ")", XmlLocationData.apply(exception)));
                        }

                        public void warning(SAXParseException exception) throws SAXException {
                            generateErrorElement(new SchemaValidationException("Warning " + exception.getMessage() + "(schema: " + schemaId + ")", XmlLocationData.apply(exception)));
                        }
                    });

                    // NOTE: VerifierHandler only supports ContentHandler, not LexicalHandler. Comments are never validated.
                    final VerifierHandler verifierHandler = verifier.getVerifierHandler();
                    final List<XMLReceiver> xmlReceivers = Arrays.asList(new ExceptionWrapperXMLReceiver(verifierHandler, "validating document"), xmlReceiver);

                    long time = 0;
                    if (logger.isDebugEnabled()) {
                        time = System.currentTimeMillis();
                    }
                    readInputAsSAX(context, getInputByName(INPUT_DATA), new TeeXMLReceiver(xmlReceivers));
                    if (logger.isDebugEnabled()) {
                        logger.debug(schemaId + " validation completed in " + (System.currentTimeMillis() - time));
                    }
                } catch (OXFException e) {
                    throw e;
                } catch (Exception e) {
                    throw new OXFException(e);
                }
            }
        };
        addOutput(name, output);
        return output;
    }
}
