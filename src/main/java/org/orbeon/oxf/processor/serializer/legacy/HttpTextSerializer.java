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
package org.orbeon.oxf.processor.serializer.legacy;

import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.http.Headers;
import org.orbeon.oxf.externalcontext.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.xml.XMLReceiver;
import org.orbeon.oxf.processor.*;
import org.orbeon.oxf.processor.impl.CacheableTransformerOutputImpl;
import org.orbeon.oxf.processor.serializer.HttpSerializerBase;
import org.orbeon.oxf.util.ContentHandlerWriter;
import org.orbeon.oxf.xml.XMLConstants;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import java.io.*;

/**
 * Legacy HTTP text serializer. This is deprecated by HttpSerializer.
 */
public abstract class HttpTextSerializer extends HttpSerializerBase {

    private static final String DEFAULT_TEXT_DOCUMENT_ELEMENT = "document";

    protected final void readInput(PipelineContext pipelineContext, ExternalContext.Response response, ProcessorInput input, Object _config) {
        Config config = (Config) _config;

        String encoding = config.encodingOrDefaultOrNull(DEFAULT_ENCODING);
        // Set content-type and encoding
        scala.Option<String> contentType = config.contentTypeOrDefault(getDefaultContentType());
        if (contentType.isDefined())
            response.setContentType(contentType.get() + "; charset=" + encoding);

        // Read input into a Writer
        try {
            Writer writer = new OutputStreamWriter(response.getOutputStream(), encoding);
            readInput(pipelineContext, input, config, writer);
        } catch (UnsupportedEncodingException e) {
            throw new OXFException(e);
        }
    }

    protected boolean isSerializeXML11() {
        return getPropertySet().getBoolean("serialize-xml-11", false);
    }

    /**
     * This must be overridden by subclasses.
     */
    protected abstract void readInput(PipelineContext context, ProcessorInput input, Config config, Writer writer);

    /**
     * This method is used when the legacy serializer is used in the new converter mode. In this
     * case, the converter exposes a "data" output, and the processor's start() method is not
     * called.
     */
    @Override
    public ProcessorOutput createOutput(String name) {
        if (!name.equals(OUTPUT_DATA))
            throw new OXFException("Invalid output created: " + name);
        final ProcessorOutput output = new CacheableTransformerOutputImpl(HttpTextSerializer.this, name) {
            public void readImpl(PipelineContext pipelineContext, XMLReceiver xmlReceiver) {
                // Create OutputStream that converts to Base64
                final Writer writer = new ContentHandlerWriter(xmlReceiver, false);

                // Read configuration input
                final Config config = readConfig(pipelineContext);
                final String encoding = config.encodingOrDefaultOrNull(DEFAULT_ENCODING);
                final scala.Option<String> contentType = config.contentTypeOrDefault(getDefaultContentType());

                try {
                    // Start document
                    final AttributesImpl attributes = new AttributesImpl();
                    attributes.addAttribute(XMLConstants.XSI_URI(), "type", "xsi:type", "CDATA", XMLConstants.XS_STRING_QNAME().qualifiedName());
                    if (contentType.isDefined())
                        attributes.addAttribute("", Headers.ContentTypeLower(), Headers.ContentTypeLower(), "CDATA", contentType.get() + "; charset=" + encoding);

                    // Start document
                    xmlReceiver.startDocument();
                    xmlReceiver.startPrefixMapping(XMLConstants.XSI_PREFIX(), XMLConstants.XSI_URI());
                    xmlReceiver.startPrefixMapping(XMLConstants.XSD_PREFIX(), XMLConstants.XSD_URI());
                    xmlReceiver.startElement("", DEFAULT_TEXT_DOCUMENT_ELEMENT, DEFAULT_TEXT_DOCUMENT_ELEMENT, attributes);

                    // Write content
                    readInput(pipelineContext, getInputByName(INPUT_DATA), config, writer);

                    // End document
                    xmlReceiver.endElement("", DEFAULT_TEXT_DOCUMENT_ELEMENT, DEFAULT_TEXT_DOCUMENT_ELEMENT);
                    xmlReceiver.endDocument();

                } catch (SAXException e) {
                    throw new OXFException(e);
                }
            }
        };
        addOutput(name, output);
        return output;
    }
}