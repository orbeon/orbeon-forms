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
package org.orbeon.oxf.processor;

import org.apache.log4j.Logger;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.processor.generator.TidyConfig;
import org.orbeon.oxf.processor.serializer.CachedSerializer;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.xml.ForwardingContentHandler;
import org.orbeon.oxf.xml.TransformerUtils;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.w3c.dom.Document;
import org.w3c.tidy.Tidy;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;

import javax.xml.transform.Transformer;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;
import java.io.*;

/**
 * Intercept either an OutputStream or a Writer.
 *
 * This implementation holds a buffer for either a Writer or an Output Stream. The buffer can then
 * be parsed.
 */
public class StreamInterceptor {

    private static Logger logger = LoggerFactory.createLogger(StreamInterceptor.class);

    private StringWriter writer;
    private ByteArrayOutputStream byteStream;
    private String encoding = CachedSerializer.DEFAULT_ENCODING;
    private String contentType = ProcessorUtils.DEFAULT_CONTENT_TYPE;

    public Writer getWriter() {
        if (byteStream != null)
            throw new IllegalStateException("getWriter is called after getOutputStream was already called.");
        if (writer == null)
            writer = new StringWriter();
        return writer;
    }

    public OutputStream getOutputStream() {
        if (writer != null)
            throw new IllegalStateException("getOutputStream is called after getWriter was already called.");
        if (byteStream == null)
            byteStream = new ByteArrayOutputStream();
        return byteStream;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public void parse(ContentHandler contentHandler) {
        parse(contentHandler, null, false);
    }

    public void parse(ContentHandler contentHandler, boolean fragment) {
        parse(contentHandler, null, fragment);
    }

    public void parse(ContentHandler contentHandler, TidyConfig tidyConfig, boolean fragment) {
        try {
            // Create InputSource
            InputSource inputSource = null;
            String stringContent = null;
            if (writer != null) {
                stringContent = writer.toString();
                if (stringContent.length() > 0) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Document to parse in filter: ");
                        logger.debug(stringContent);
                    }
                    inputSource = new InputSource(new StringReader(stringContent));
                }
            } else if (byteStream != null) {
                byte[] byteContent = byteStream.toByteArray();
                if (byteContent.length > 0) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Document to parse in filter: ");
                        logger.debug(new String(byteContent, encoding));
                    }
                    inputSource = new InputSource(new ByteArrayInputStream(byteContent));
                    if (encoding != null)
                        inputSource.setEncoding(encoding);
                }
            } else {
                throw new OXFException("Filtered resource did not call getWriter() or getOutputStream().");
            }
            // Parse the output only if text was generated
            if (inputSource != null) {
                if (ProcessorUtils.HTML_CONTENT_TYPE.equals(contentType)) {
                    // The document contains HTML. Parse it using Tidy.
                    Tidy tidy = new Tidy();
                    if (tidyConfig != null) {
                        tidy.setShowWarnings(tidyConfig.isShowWarnings());
                        tidy.setQuiet(tidyConfig.isQuiet());
                        if (tidyConfig.isQuiet())
                            tidy.setErrout(new PrintWriter(new StringWriter()));
                    }

                    InputStream inputStream;
                    if (writer == null) {
                        // Unfortunately, it doesn't look like tidy support
                        // detecting the encoding from the HTML document, so we
                        // are left to using a default or hope that the source
                        // set a known encoding.
                        inputStream = inputSource.getByteStream();
                        tidy.setInputEncoding(TidyConfig.getTidyEncoding(encoding));
                    } else {
                        // Here we go from characters to bytes to characters
                        // again, which is very suboptimal, but the version of
                        // tidy used does not support a Reader as input.

                        // Use utf-8 both ways and hope for the best
                        inputStream = new ByteArrayInputStream(stringContent.getBytes("utf-8"));
                        tidy.setInputEncoding("utf-8");
                    }

                    Document document = tidy.parseDOM(inputStream, null);
                    // Output the result
                    Transformer transformer = TransformerUtils.getIdentityTransformer();

                    if (fragment) {
                        // Do not generate start and end document events
                        transformer.transform(new DOMSource(document), new SAXResult(new ForwardingContentHandler(contentHandler) {
                            public void startDocument() {
                            }

                            public void endDocument() {
                            }
                        }));
                    } else {
                        // Generate a complete document
                        transformer.transform(new DOMSource(document), new SAXResult(contentHandler));
                    }
                } else {
                    // Assume it is XML and parse the output
                    final XMLReader reader = XMLUtils.newXMLReader(false, false);

                    if (fragment) {
                        // Do not generate start and end document events
                        reader.setContentHandler(new ForwardingContentHandler(contentHandler) {
                            public void startDocument() {
                            }

                            public void endDocument() {
                            }
                        });
                    } else {
                        // Generate a complete document
                        reader.setContentHandler(contentHandler);
                    }

                    //inputSource.setSystemId();
                    reader.parse(inputSource);
                }
            }
        } catch (SAXParseException e) {
            throw new ValidationException(e.getMessage(), new LocationData(e));
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }
}
