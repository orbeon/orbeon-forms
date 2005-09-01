/**
 *  Copyright (C) 2005 Orbeon, Inc.
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
package org.orbeon.oxf.processor.serializer;

import org.dom4j.Namespace;
import org.dom4j.QName;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.util.Base64ContentHandler;
import org.orbeon.oxf.util.TextContentHandler;
import org.orbeon.oxf.xml.ContentHandlerAdapter;
import org.orbeon.oxf.xml.XMLConstants;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

/**
 * ContentHandler able to serialize text or binary documents to an output stream.
 */
public class BinaryTextContentHandler extends ContentHandlerAdapter {

    public static final String DEFAULT_BINARY_CONTENT_TYPE = "application/octet-stream";
    public static final String DEFAULT_TEXT_CONTENT_TYPE = "text/plain";

    private ExternalContext.Response response;
    private OutputStream outputStream;

    private boolean forceContentType;
    private String requestedContentType;
    private boolean ignoreDocumentContentType;

    private  boolean forceEncoding;
    private String requestedEncoding;
    private boolean ignoreDocumentEncoding;

    private Writer writer;
    private ContentHandler outputContentHandler;
    private int elementLevel = 0;
    private Map prefixMappings;

    /**
     * Contructor.
     *
     * @param response                      optional ExternalContext.Response used to set content-type
     * @param outputStream                  where the resulting data is written
     * @param forceContentType
     * @param requestedContentType
     * @param ignoreDocumentContentType
     * @param forceEncoding
     * @param requestedEncoding
     * @param ignoreDocumentEncoding
     */
    public BinaryTextContentHandler(ExternalContext.Response response, OutputStream outputStream,
                                    boolean forceContentType, String requestedContentType, boolean ignoreDocumentContentType,
                                    boolean forceEncoding, String requestedEncoding, boolean ignoreDocumentEncoding) {
        this.outputStream = outputStream;
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
                prefixMappings = new HashMap();
            prefixMappings.put(prefix, uri);
        }
    }

    public void startElement(String namespaceURI, String localName, String qName, Attributes atts) {
        if (elementLevel++ == 0) {
            // This is the root element

            // Get xsi:type attribute and determine whether the input is binary or text
            String xsiType = atts.getValue(XMLConstants.XSI_TYPE_QNAME.getNamespaceURI(), XMLConstants.XSI_TYPE_QNAME.getName());

            if (xsiType == null)
                throw new OXFException("Root element must contain an xsi:type attribute");

            int colonIndex = xsiType.indexOf(':');
            if (colonIndex == -1)
                throw new OXFException("Type xs:string or xs:base64Binary must be specified");

            String typePrefix = xsiType.substring(0, colonIndex);
            String typeLocalName = xsiType.substring(colonIndex + 1);

            if (prefixMappings == null)
                throw new OXFException("Undeclared prefix in xsi:type: " + typePrefix);

            String typeNamespaceURI = (String) prefixMappings.get(typePrefix);
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

            // Set ContentHandler and headers depending on input type
            String contentTypeAttribute = atts.getValue("content-type");
            if (isBinaryInput) {
                // Get content-type
                String contentType = getContentType(contentTypeAttribute, DEFAULT_BINARY_CONTENT_TYPE);

                if (response != null)
                    response.setContentType(contentType);

                outputContentHandler = new Base64ContentHandler(outputStream);
            } else {
                // Get content-type and encoding
                String contentType = getContentType(contentTypeAttribute, DEFAULT_TEXT_CONTENT_TYPE);
                String encoding = getEncoding(contentTypeAttribute, CachedSerializer.DEFAULT_ENCODING);

                // Always set the content type with a charset attribute
                if (response != null)
                    response.setContentType(contentType + "; charset=" + encoding);

                try {
                    writer = new OutputStreamWriter(outputStream, encoding);
                } catch (UnsupportedEncodingException e) {
                    throw new OXFException(e);
                }
                outputContentHandler = new TextContentHandler(writer);
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
            if (writer != null)
                writer.flush();
            outputStream.flush();
        } catch (IOException e) {
            throw new OXFException(e);
        }
    }

    /**
     * Implement the content type determination algorithm.
     */
    private String getContentType(String contentTypeAttribute, String defaultContentType) {
        if (forceContentType)
            return requestedContentType;

        String documentContentType = getContentTypeMediaType(contentTypeAttribute);
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

        final String documentEncoding = getContentTypeCharset(contentTypeAttribute);
        if (!ignoreDocumentEncoding && documentEncoding != null)
            return documentEncoding;

        if (requestedEncoding != null)
            return requestedEncoding;

        return defaultEncoding;
    }

    // TODO: Use NetUtils once 2.8 compatibility is no longer required.
    public static String getContentTypeCharset(String contentType) {
        if (contentType == null)
            return null;
        int semicolumnIndex = contentType.indexOf(";");
        if (semicolumnIndex == -1)
            return null;
        int charsetIndex = contentType.indexOf("charset=", semicolumnIndex);
        if (charsetIndex == -1)
            return null;
        // FIXME: There may be other attributes after charset, right?
        String afterCharset = contentType.substring(charsetIndex + 8);
        afterCharset = afterCharset.replace('"', ' ');
        return afterCharset.trim();
    }

    // TODO: Use NetUtils once 2.8 compatibility is no longer required.
    public static String getContentTypeMediaType(String contentType) {
        if (contentType == null || contentType.equalsIgnoreCase("content/unknown"))
            return null;
        int semicolumnIndex = contentType.indexOf(";");
        if (semicolumnIndex == -1)
            return contentType;
        return contentType.substring(0, semicolumnIndex).trim();
    }
}