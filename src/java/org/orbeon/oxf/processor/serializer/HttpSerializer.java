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
package org.orbeon.oxf.processor.serializer;

import org.dom4j.Namespace;
import org.dom4j.QName;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.ProcessorInput;
import org.orbeon.oxf.processor.XMLConstants;
import org.orbeon.oxf.util.Base64ContentHandler;
import org.orbeon.oxf.util.TextContentHandler;
import org.orbeon.oxf.xml.ContentHandlerAdapter;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

/**
 * This serializer is a generic HTTP serializer able to serialize text as well as binary.
 */
public class HttpSerializer extends HttpSerializerBase {

    public static final String HTTP_SERIALIZER_CONFIG_NAMESPACE_URI = "http://www.orbeon.com/oxf/http-serializer";

    public static final String DEFAULT_BINARY_CONTENT_TYPE = "application/octet-stream";
    public static final String DEFAULT_TEXT_CONTENT_TYPE = "text/plain";

    protected String getDefaultContentType() {
        return DEFAULT_BINARY_CONTENT_TYPE;
    }

    protected String getConfigSchemaNamespaceURI() {
        return HTTP_SERIALIZER_CONFIG_NAMESPACE_URI;
    }

    protected void readInput(PipelineContext context, final ExternalContext.Response response, ProcessorInput input, Object _config, final OutputStream outputStream) {
        try {
            final Config config = (Config) _config;
            readInputAsSAX(context, input, new ContentHandlerAdapter() {
                private Writer writer;
                private ContentHandler outputContentHandler;
                private int elementLevel = 0;
                private Map prefixMappings;

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
                            String contentType = getContentType(config, contentTypeAttribute, DEFAULT_BINARY_CONTENT_TYPE);

                            response.setContentType(contentType);

                            outputContentHandler = new Base64ContentHandler(outputStream);
                        } else {
                            // Get content-type and encoding
                            String contentType = getContentType(config, contentTypeAttribute, DEFAULT_TEXT_CONTENT_TYPE);
                            String encoding = getEncoding(config, contentTypeAttribute, DEFAULT_ENCODING);

                            // Always set the content type with a charset attribute
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
            });

        } catch (Exception e) {
            throw new OXFException(e);
        }
    }
}
