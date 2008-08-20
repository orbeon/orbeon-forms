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
package org.orbeon.oxf.processor.converter;

import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.processor.ProcessorInput;
import org.orbeon.oxf.processor.ProcessorOutput;
import org.orbeon.oxf.processor.ProcessorUtils;
import org.orbeon.oxf.util.ContentHandlerWriter;
import org.orbeon.oxf.xml.XMLConstants;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import java.io.Writer;

/**
 * Base class for text converters.
 */
public abstract class TextConverterBase extends ConverterBase {

    /**
     * This must be overridden by subclasses.
     *
     * @return true iif the method already
     */
    protected abstract boolean readInput(PipelineContext context, ContentHandler contentHandler, ProcessorInput input, Config config, Writer writer);

    /**
     * Return the namespace URI of the schema validating the config input. Can be overridden by
     * subclasses.
     */
    protected String getConfigSchemaNamespaceURI() {
        return STANDARD_TEXT_CONVERTER_CONFIG_NAMESPACE_URI;
    }

    /**
     * Perform the conversion.
     */
    public ProcessorOutput createOutput(String name) {
        if (!name.equals(OUTPUT_DATA))
            throw new OXFException("Invalid output created: " + name);
        final ProcessorOutput output = new ProcessorImpl.CacheableTransformerOutputImpl(getClass(), name) {
            public void readImpl(PipelineContext pipelineContext, ContentHandler contentHandler) {
                // Create OutputStream that converts to Base64
                final Writer writer = new ContentHandlerWriter(contentHandler);

                // Read configuration input
                final Config config = readConfig(pipelineContext);
                final String encoding = getEncoding(config, DEFAULT_ENCODING);
                final String contentType = getContentType(config, getDefaultContentType());

                try {
                    // Start document
                    final AttributesImpl attributes = new AttributesImpl();
                    attributes.addAttribute(XMLConstants.XSI_URI, "type", "xsi:type", "CDATA", XMLConstants.XS_STRING_QNAME.getQualifiedName());
                    if (contentType != null)
                        attributes.addAttribute("", "content-type", "content-type", "CDATA", contentType + "; charset=" + encoding);

                    // Start document
                    contentHandler.startDocument();
                    contentHandler.startPrefixMapping(XMLConstants.XSI_PREFIX, XMLConstants.XSI_URI);
                    contentHandler.startPrefixMapping(XMLConstants.XSD_PREFIX, XMLConstants.XSD_URI);
                    contentHandler.startElement("", ProcessorUtils.DEFAULT_TEXT_DOCUMENT_ELEMENT, ProcessorUtils.DEFAULT_TEXT_DOCUMENT_ELEMENT, attributes);

                    // Write content
                    final boolean didEndDocument = readInput(pipelineContext, contentHandler, getInputByName(INPUT_DATA), config, writer);

                    // End document if readInput() did not do it

                    // The reason we allow readInput() to do it directly is to help with streaming: upstream calls
                    // endDocument(), which in turn calls endDocument() downstream, instead of waiting until readInput()
                    // returns.
                    if (!didEndDocument)
                        sendEndDocument(contentHandler);

                } catch (SAXException e) {
                    throw new OXFException(e);
                }
            }
        };
        addOutput(name, output);
        return output;
    }

    protected void sendEndDocument(ContentHandler contentHandler) {
        try {
            contentHandler.endElement("", ProcessorUtils.DEFAULT_TEXT_DOCUMENT_ELEMENT, ProcessorUtils.DEFAULT_TEXT_DOCUMENT_ELEMENT);
            contentHandler.endDocument();
        } catch (SAXException e) {
            throw new OXFException(e);
        }
    }
}