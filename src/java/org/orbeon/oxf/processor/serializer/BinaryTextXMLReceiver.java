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
package org.orbeon.oxf.processor.serializer;

import org.apache.commons.lang.StringUtils;
import org.dom4j.Namespace;
import org.dom4j.QName;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.util.Base64XMLReceiver;
import org.orbeon.oxf.util.ISODateUtils;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.util.TextXMLReceiver;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLReceiverAdapter;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

/**
 * ContentHandler able to serialize text or binary documents to an output stream.
 */
public class BinaryTextXMLReceiver extends XMLReceiverAdapter {

    public static final String DEFAULT_BINARY_CONTENT_TYPE = "application/octet-stream";
    public static final String DEFAULT_TEXT_CONTENT_TYPE = "text/plain";

    private ExternalContext.Response response;

    private OutputStream outputStream;
    private boolean isCloseStream;

    private boolean forceContentType;
    private String requestedContentType;
    private boolean ignoreDocumentContentType;

    private  boolean forceEncoding;
    private String requestedEncoding;
    private boolean ignoreDocumentEncoding;

    private Writer writer;
    private ContentHandler outputContentHandler;
    private int elementLevel = 0;
    private Map<String, String> prefixMappings;

    /**
     * Simple constructor to write to a stream and close it.
     *
     * @param outputStream  OutputStream to write to
     */
    public BinaryTextXMLReceiver(OutputStream outputStream) {
        this(null, outputStream, true, false, null, false, false, null, false);
    }

    /**
     * Constructor with all the options.
     *
     * @param response                      optional ExternalContext.Response used to set content-type
     * @param outputStream                  where the resulting data is written
     * @param isCloseStream                 whether to close the stream upon endDocument()
     * @param forceContentType
     * @param requestedContentType
     * @param ignoreDocumentContentType
     * @param forceEncoding
     * @param requestedEncoding
     * @param ignoreDocumentEncoding
     */
    public BinaryTextXMLReceiver(ExternalContext.Response response, OutputStream outputStream, boolean isCloseStream,
                                    boolean forceContentType, String requestedContentType, boolean ignoreDocumentContentType,
                                    boolean forceEncoding, String requestedEncoding, boolean ignoreDocumentEncoding) {
        this.outputStream = outputStream;
        this.isCloseStream = isCloseStream;
        this.response = response;
        this.forceContentType = forceContentType;
        this.requestedContentType = requestedContentType;
        this.ignoreDocumentContentType = ignoreDocumentContentType;
        this.forceEncoding = forceEncoding;
        this.requestedEncoding = requestedEncoding;
        this.ignoreDocumentEncoding = ignoreDocumentEncoding;
    }

    public void startPrefixMapping(String prefix, String uri) {
        if (elementLevel == 0) {
            // Record definitions only before root element arrives
            if (prefixMappings == null)
                prefixMappings = new HashMap<String, String>();
            prefixMappings.put(prefix, uri);
        }
    }

    public void startElement(String namespaceURI, String localName, String qName, Attributes attributes) {
        if (elementLevel++ == 0) {
            // This is the root element

            // Get xsi:type attribute and determine whether the input is binary or text
            String xsiType = attributes.getValue(XMLConstants.XSI_TYPE_QNAME.getNamespaceURI(), XMLConstants.XSI_TYPE_QNAME.getName());

            if (xsiType == null)
                throw new OXFException("Root element must contain an xsi:type attribute");

            int colonIndex = xsiType.indexOf(':');
            if (colonIndex == -1)
                throw new OXFException("Type xs:string or xs:base64Binary must be specified");

            String typePrefix = xsiType.substring(0, colonIndex);
            String typeLocalName = xsiType.substring(colonIndex + 1);

            if (prefixMappings == null)
                throw new OXFException("Undeclared prefix in xsi:type: " + typePrefix);

            String typeNamespaceURI = prefixMappings.get(typePrefix);
            if (typeNamespaceURI == null)
                throw new OXFException("Undeclared prefix in xsi:type: " + typePrefix);

            QName typeQName = new QName(typeLocalName, new Namespace(typePrefix, typeNamespaceURI));
            boolean isBinaryInput;
            if (typeQName.equals(XMLConstants.XS_BASE64BINARY_QNAME)) {
                isBinaryInput = true;
            } else if (typeQName.equals(XMLConstants.XS_STRING_QNAME)) {
                isBinaryInput = false;
            } else
                throw new OXFException("Type xs:string or xs:base64Binary must be specified");

            // Set last-modified if available
            final String validityAttribute = attributes.getValue("last-modified");
            if (StringUtils.isNotBlank(validityAttribute)) {
                // Override caching settings which may have taken place before
                if (response != null)
                    response.setCaching(ISODateUtils.parseRFC1123Date(validityAttribute), true, true);
            }

            // Set filename if available
            final String fileName = attributes.getValue("filename");
            if (StringUtils.isNotBlank(fileName)) {
                if (response != null)
                    response.setHeader("Content-Disposition", "attachment; filename=" + fileName );
            }

            // Set status code if available
            final String statusCode = attributes.getValue("status-code");
            if (StringUtils.isNotBlank(statusCode)) {
                if (response != null)
                     response.setStatus(Integer.parseInt(statusCode));
            }

            // Set ContentHandler and headers depending on input type
            final String contentTypeAttribute = attributes.getValue("content-type");
            if (isBinaryInput) {
                // Get content-type
                final String contentType = getContentType(contentTypeAttribute, DEFAULT_BINARY_CONTENT_TYPE);

                if (response != null)
                    response.setContentType(contentType);

                outputContentHandler = new Base64XMLReceiver(outputStream);
            } else {
                // Get content-type and encoding
                final String contentType = getContentType(contentTypeAttribute, DEFAULT_TEXT_CONTENT_TYPE);
                final String encoding = getEncoding(contentTypeAttribute, CachedSerializer.DEFAULT_ENCODING);

                // Always set the content type with a charset attribute
                if (response != null)
                    response.setContentType(contentType + "; charset=" + encoding);

                try {
                    writer = new OutputStreamWriter(outputStream, encoding);
                } catch (UnsupportedEncodingException e) {
                    throw new OXFException(e);
                }
                outputContentHandler = new TextXMLReceiver(writer);
            }
        }
    }

    public void endElement(String namespaceURI, String localName, String qName) {
        --elementLevel;
    }

    public void characters(char ch[], int start, int length) throws SAXException {
        outputContentHandler.characters(ch, start, length);
    }

    public void endDocument() {
        try {
            // Flush writer into output stream if needed
            if (writer != null)
                writer.flush();

            // Flush stream
            outputStream.flush();

            // Close stream if needed
            if (isCloseStream)
                outputStream.close();
        } catch (IOException e) {
            throw new OXFException(e);
        }
    }

    public void processingInstruction(String target, String data) throws SAXException {
        // STATUS CODE
        // Handle special Orbeon Forms processing instructions
        if ("oxf-serializer".equals(target)) {
            try {
                if (data != null && data.startsWith("status-code=\"")) {
                    // Output status code
                    final int endIndex = data.indexOf('"', 13);
                    if (endIndex != -1) {
                        final String codeString = data.substring(13, endIndex);
                        response.setStatus(Integer.parseInt(codeString));
                    }
                // NOTE: Code below is also in SerializerContentHandler
                } else if ("flush".equals(data)) {
                    if (writer != null)
                        writer.flush();
                    if (outputStream != null)
                        outputStream.flush();
                }
            } catch (IOException e) {
                throw new OXFException(e);
            }
        } else {
            super.processingInstruction(target, data);
        }
    }

    /**
     * Implement the content type determination algorithm.
     */
    private String getContentType(String contentTypeAttribute, String defaultContentType) {
        if (forceContentType)
            return requestedContentType;

        String documentContentType = NetUtils.getContentTypeMediaType(contentTypeAttribute);
        if (!ignoreDocumentContentType && documentContentType != null)
            return documentContentType;

        if (requestedContentType != null)
            return requestedContentType;

        return defaultContentType;
    }

    /**
     * Implement the encoding determination algorithm.
     */
    private String getEncoding(String contentTypeAttribute, String defaultEncoding) {
        if (forceEncoding)
            return requestedEncoding;

        final String documentEncoding = NetUtils.getContentTypeCharset(contentTypeAttribute);
        if (!ignoreDocumentEncoding && documentEncoding != null)
            return documentEncoding;

        if (requestedEncoding != null)
            return requestedEncoding;

        return defaultEncoding;
    }
}