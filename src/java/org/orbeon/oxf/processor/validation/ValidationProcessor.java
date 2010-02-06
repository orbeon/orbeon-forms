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

import org.dom4j.Document;
import org.orbeon.oxf.cache.Cacheable;
import org.orbeon.oxf.cache.OutputCacheKey;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.processor.*;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.SAXStore;
import org.orbeon.oxf.xml.XPathUtils;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

public class ValidationProcessor extends ProcessorImpl {

    public static final String ORBEON_ERROR_NS = "http://orbeon.org/oxf/xml/validation";
    public static final String ORBEON_ERROR_PREFIX = "v";
    public static final String ERROR_ELEMENT = "error";
    public static final String MESSAGE_ATTRIBUTE = "message";
    public static final String SYSTEMID_ATTRIBUTE = "system-id";
    public static final String LINE_ATTRIBUTE = "line";
    public static final String COLUMN_ATTRIBUTE = "column";
    public static final String INPUT_SCHEMA = "schema";

    public static final int W3C_VALIDATOR = 0;
    public static final int RNG_VALIDATOR = 1;

    private String schemaId;

    public static SAXStore NO_DECORATION_CONFIG;
    public static SAXStore DECORATION_CONFIG;

    static {
        NO_DECORATION_CONFIG = new SAXStore();
        ContentHandlerHelper helper = new ContentHandlerHelper(NO_DECORATION_CONFIG);
        helper.startDocument();
        helper.startElement("config");
        helper.element("decorate", "false");
        helper.endElement();
        helper.endDocument();

        DECORATION_CONFIG = new SAXStore();
        helper = new ContentHandlerHelper(DECORATION_CONFIG);
        helper.startDocument();
        helper.startElement("config");
        helper.element("decorate", "true");
        helper.endElement();
        helper.endDocument();
    }

    public ValidationProcessor() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_DATA));
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG));
        addInputInfo(new ProcessorInputOutputInfo(INPUT_SCHEMA));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    /**
     * Creates a validation processor that decorate the output tree with error
     * attribute if there is a validation error. The default behaviour is to
     * throw a ValidationException
     **/
    public ValidationProcessor(String schemaId) {
        this();
        this.schemaId = schemaId;
    }

    @Override
    public ProcessorOutput createOutput(final String name) {
        ProcessorOutput output = new ProcessorImpl.CacheableTransformerOutputImpl(getClass(), name) {
            protected void readImpl(org.orbeon.oxf.pipeline.api.PipelineContext context, ContentHandler contentHandler) {

                long begin = System.currentTimeMillis();
                final ProcessorInput schemaInput = getInputByName(INPUT_SCHEMA);
                final ProcessorInput dataInput = getInputByName(INPUT_DATA);

                // Used to store the schema SAX store if we don't have the type in cache
                final SAXStore[] schemaSAXStore = new SAXStore[1];

                // Cache the type of validator to use, so we don't have to
                // read the schema over and over again.
                final Config chosenValidator = (Config) readCacheInputAsObject(context, schemaInput, new CacheableInputReader() {
                    public Object read(org.orbeon.oxf.pipeline.api.PipelineContext context, ProcessorInput input) {
                        final Config chosenValidator = new Config();

                        chosenValidator.validatorType = -1;
                        schemaSAXStore[0] = new SAXStore() {
                            @Override
                            public void startElement(String uri, String localname, String qName, Attributes attributes)
                                    throws SAXException {
                                if (chosenValidator.validatorType == -1)
                                    chosenValidator.validatorType = localname.equals("schema") ? W3C_VALIDATOR : RNG_VALIDATOR;
                                super.startElement(uri, localname, qName, attributes);
                            }
                        };
                        readInputAsSAX(context, input, schemaSAXStore[0]);
                        return chosenValidator;
                    }
                });

                // read config input ot determine of we should decorate or not
                Document configDoc = readCacheInputAsDOM4J(context, INPUT_CONFIG);
                boolean decorate = Boolean.valueOf(XPathUtils.selectStringValueNormalize(configDoc, "/config/decorate")).booleanValue();

                // Create and hookup validator
                Processor validator = chosenValidator.validatorType == W3C_VALIDATOR
                        ? (Processor) new W3CValidationProcessor(decorate, schemaId)
                        : (Processor) new RNGValidationProcessor(decorate, schemaId);
                validator.createInput(INPUT_DATA).setOutput(dataInput.getOutput());
                if (schemaSAXStore[0] == null) {
                    // Connect to config input
                    validator.createInput(INPUT_CONFIG).setOutput(schemaInput.getOutput());
                } else {
                    // Have read config already, connect to SAX store
                    validator.createInput(INPUT_CONFIG).setOutput(new ProcessorImpl.ProcessorOutputImpl(ValidationProcessor.this.getClass(), "dummy") {

                        // Data coming from SAX store
                        protected void readImpl(org.orbeon.oxf.pipeline.api.PipelineContext context, ContentHandler contentHandler) {
                            try {
                                schemaSAXStore[0].replay(contentHandler);
                            } catch (SAXException e) {
                                throw new OXFException(e);
                            }
                        }

                        // Forward to config input
                        @Override
                        protected OutputCacheKey getKeyImpl(org.orbeon.oxf.pipeline.api.PipelineContext context) {
                            return schemaInput instanceof Cacheable
                                    ? ((Cacheable) schemaInput).getKey(context) : null;
                        }

                        // Forward to config input
                        @Override
                        protected Object getValidityImpl(org.orbeon.oxf.pipeline.api.PipelineContext context) {
                            return schemaInput instanceof Cacheable
                                    ? ((Cacheable) schemaInput).getValidity(context) : null;
                        }
                    });
                }

                // Read from validator
                SAXStore result = new SAXStore();
                validator.createOutput(OUTPUT_DATA).read(context, result);
                Long current = (Long) context.getAttribute("validator-total");
                context.setAttribute("validator-total", new Long((current == null ? 0 : current.longValue())
                        + System.currentTimeMillis() - begin));
                try {
                    result.replay(contentHandler);
                } catch (SAXException e) {
                    throw new OXFException(e);
                }
            }

        };
        addOutput(name, output);
        return output;
    }

    private static class Config {
        int validatorType;
    }
}

