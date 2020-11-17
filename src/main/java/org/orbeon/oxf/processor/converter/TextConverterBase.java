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
package org.orbeon.oxf.processor.converter;

import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.http.Headers;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.pipeline.api.TransformerXMLReceiver;
import org.orbeon.oxf.processor.BinaryTextSupport;
import org.orbeon.oxf.xml.XMLReceiver;
import org.orbeon.oxf.processor.ProcessorInput;
import org.orbeon.oxf.processor.ProcessorOutput;
import org.orbeon.oxf.processor.impl.CacheableTransformerOutputImpl;
import org.orbeon.oxf.processor.serializer.BinaryTextXMLReceiver;
import org.orbeon.oxf.util.ContentHandlerWriter;
import org.orbeon.oxf.xml.SimpleForwardingXMLReceiver;
import org.orbeon.oxf.xml.XMLConstants;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import javax.xml.transform.stream.StreamResult;

/**
 * Base class for text converters.
 */
public abstract class TextConverterBase extends ConverterBase {

    public static final String DEFAULT_METHOD_PROPERTY_NAME = "default-method";

    /**
     * This must be overridden by subclasses.
     *
     * @return true iif the method already
     */
    protected abstract TransformerXMLReceiver createTransformer(Config config);

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
    @Override
    public ProcessorOutput createOutput(String name) {
        if (!name.equals(OUTPUT_DATA))
            throw new OXFException("Invalid output created: " + name);
        final ProcessorOutput output = new CacheableTransformerOutputImpl(TextConverterBase.this, name) {
            public void readImpl(PipelineContext pipelineContext, XMLReceiver xmlReceiver) {

                // Read configuration input
                final Config config = readConfig(pipelineContext);
                final String encoding = getEncoding(config, DEFAULT_ENCODING);
                final String contentType = getContentType(config, getDefaultContentType());

                try {
                    // Start document
                    final AttributesImpl attributes = new AttributesImpl();
                    attributes.addAttribute(XMLConstants.XSI_URI(), "type", "xsi:type", "CDATA", XMLConstants.XS_STRING_QNAME().qualifiedName());
                    if (contentType != null)
                        attributes.addAttribute("", Headers.ContentTypeLower(), Headers.ContentTypeLower(), "CDATA", contentType + "; charset=" + encoding);

                    // Start document
                    xmlReceiver.startDocument();
                    xmlReceiver.startPrefixMapping(XMLConstants.XSI_PREFIX(), XMLConstants.XSI_URI());
                    xmlReceiver.startPrefixMapping(XMLConstants.XSD_PREFIX(), XMLConstants.XSD_URI());
                    xmlReceiver.startElement("", BinaryTextSupport.TextDocumentElementName(), BinaryTextSupport.TextDocumentElementName(), attributes);

                    // Create OutputStream that converts to Base64
                    final TransformerXMLReceiver transformer = createTransformer(config);
                    transformer.setResult(new StreamResult(new ContentHandlerWriter(xmlReceiver, false)));

                    // Write content
                    final boolean didEndDocument = readInput(pipelineContext, xmlReceiver, getInputByName(INPUT_DATA), transformer);

                    // End document if readInput() did not do it

                    // The reason we allow readInput() to do it directly is to help with streaming: upstream calls
                    // endDocument(), which in turn calls endDocument() downstream, instead of waiting until readInput()
                    // returns.
                    if (!didEndDocument)
                        sendEndDocument(xmlReceiver);

                } catch (SAXException e) {
                    throw new OXFException(e);
                }
            }
        };
        addOutput(name, output);
        return output;
    }

    private boolean readInput(PipelineContext context, final XMLReceiver downstreamReceiver, ProcessorInput input, final XMLReceiver transformer) {

        final boolean[] didEndDocument = new boolean[1];
        readInputAsSAX(context, input, createFilterReceiver(downstreamReceiver, transformer, didEndDocument));
        return didEndDocument[0];
    }

    protected XMLReceiver createFilterReceiver(XMLReceiver downstreamReceiver, XMLReceiver transformer, boolean[] didEndDocument) {
        return new FilterReceiver(downstreamReceiver, transformer, didEndDocument);
    }

    protected static class FilterReceiver extends SimpleForwardingXMLReceiver {

        private final XMLReceiver downstreamReceiver;
        private final boolean[] didEndDocument;

        public FilterReceiver(XMLReceiver downstreamReceiver, XMLReceiver transformer, boolean[] didEndDocument) {
            super(transformer);
            this.downstreamReceiver = downstreamReceiver;
            this.didEndDocument = didEndDocument;
        }

        private boolean seenRootElement = false;

        @Override
        public void processingInstruction(String target, String data) throws SAXException {

            // Forward directly to the output any serializer processing instructions that took place before the root
            // element. Note that this is a rather arbitrary choice! we could do any of the following:
            //
            // 1. Just let the transformer serialize the PI
            // 2. Place serializer PIs as attributes the root element of the resulting <document> (e.g. <document status-code="404">)
            // 3. Forward those PIs as we do here
            //
            // This could even be configurable. For now, we choose option #3 for ease of implementation.
            if (seenRootElement || ! BinaryTextXMLReceiver.isSerializerPI(target, data))
                super.processingInstruction(target, data);
            else
                downstreamReceiver.processingInstruction(target, data);
        }

        @Override
        public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {
            seenRootElement = true;
            super.startElement(uri, localname, qName, attributes);
        }

        public void endDocument() throws SAXException {
            super.endDocument();
            sendEndDocument(downstreamReceiver);
            didEndDocument[0] = true;
        }
    }

    private static void sendEndDocument(ContentHandler contentHandler) {
        try {
            contentHandler.endElement("", BinaryTextSupport.TextDocumentElementName(), BinaryTextSupport.TextDocumentElementName());
            contentHandler.endDocument();
        } catch (SAXException e) {
            throw new OXFException(e);
        }
    }
}