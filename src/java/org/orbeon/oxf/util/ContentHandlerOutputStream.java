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
package org.orbeon.oxf.util;

import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.xml.XMLConstants;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import java.io.IOException;
import java.io.OutputStream;

/**
 * An OutputStream that converts the bytes written into it into Base64-encoded characters written to
 * a ContentHandler.
 */
public class ContentHandlerOutputStream extends OutputStream {

    private static final String DEFAULT_BINARY_DOCUMENT_ELEMENT = "document";

    private ContentHandler contentHandler;

    private byte[] byteBuffer = new byte[76 * 3 / 4]; // maximum bytes that, once decoded, can fit in a line of 76 characters
    private int currentBufferSize = 0;
    private char[] resultingLine = new char[76 + 1];

    private byte[] singleByte = new byte[1];

    private boolean documentStarted;
    private boolean closed;

    public ContentHandlerOutputStream(ContentHandler contentHandler) {
        this.contentHandler = contentHandler;
    }

    /**
     * Start a document, add a root element and output the content type attribute specified. Calling this method is
     * optional. If it is called, upon close() the corresponding element and document are closed as well.
     *
     * @param contentType   content type
     * @throws SAXException
     */
    public void startDocument(String contentType) throws SAXException {
        // Start document
        AttributesImpl attributes = new AttributesImpl();
        attributes.addAttribute(XMLConstants.XSI_URI, "type", "xsi:type", "CDATA", XMLConstants.XS_BASE64BINARY_QNAME.getQualifiedName());
        if (contentType != null)
            attributes.addAttribute("", "content-type", "content-type", "CDATA", contentType);

        contentHandler.startDocument();
        contentHandler.startPrefixMapping(XMLConstants.XSI_PREFIX, XMLConstants.XSI_URI);
        contentHandler.startPrefixMapping(XMLConstants.XSD_PREFIX, XMLConstants.XSD_URI);
        contentHandler.startElement("", DEFAULT_BINARY_DOCUMENT_ELEMENT, DEFAULT_BINARY_DOCUMENT_ELEMENT, attributes);

        documentStarted = true;
    }

    /**
     * Close this output stream. This must be called in the end if startDocument() was called, otherwise the document
     * won't be properly produced.
     *
     * @throws IOException
     */
    public void close() throws IOException {
        if (!closed) {
            // Always flush
            flushBuffer();

            // Only close element and document if startDocument was called
            if (documentStarted) {
                try {
                    // End document
                    contentHandler.endElement("", DEFAULT_BINARY_DOCUMENT_ELEMENT, DEFAULT_BINARY_DOCUMENT_ELEMENT);
                    contentHandler.endPrefixMapping(XMLConstants.XSI_PREFIX);
                    contentHandler.endPrefixMapping(XMLConstants.XSD_PREFIX);
                    contentHandler.endDocument();
                } catch (SAXException e) {
                    throw new OXFException(e);
                }
            }
            closed = true;
        }
    }

    public void flush() throws IOException {
        try {
            // NOTE: This will only flush on Base64 line boundaries. Is that what we want? Or
            // should we just ignore? We can't output an incomplete encoded line unless we have a
            // number of bytes in the buffer multiple of 3. Otherwise, we would have to output '='
            // characters, which do signal an end of transmission.
            contentHandler.processingInstruction("oxf-serializer", "flush");
        } catch (SAXException e) {
            throw new OXFException(e);
        }
    }

    public void write(byte b[]) throws IOException {
        addBytes(b, 0, b.length);
    }

    public void write(byte b[], int off, int len) throws IOException {
        addBytes(b, off, len);
    }

    public void write(int b) throws IOException {
        singleByte[0] = (byte) b;
        addBytes(singleByte, 0, 1);
    }

    private void addBytes(byte b[], int off, int len) {
        // Check bounds
        if ((off < 0) || (len < 0) || (off > b.length) || ((off + len) > b.length))
	        throw new IndexOutOfBoundsException();
	    else if (len == 0)
	        return;

        try {
            while (len > 0) {
                // Fill buffer as much as possible
                int lenToCopy = Math.min(len, byteBuffer.length - currentBufferSize);
                System.arraycopy(b, off, byteBuffer, currentBufferSize, lenToCopy);
                off += lenToCopy;
                len -= lenToCopy;
                currentBufferSize += lenToCopy;

                // If buffer is full, write it out
                if (currentBufferSize == byteBuffer.length) {
                    String encoded = Base64.encode(byteBuffer) + "\n";
                    // The terminating LF is already added by encode()
                    encoded.getChars(0, encoded.length(), resultingLine, 0);
                    // Output characters
                    contentHandler.characters(resultingLine, 0, encoded.length());
                    // Reset counter
                    currentBufferSize = 0;
                }
            }
        } catch (SAXException e) {
            throw new OXFException(e);
        }
    }

    private void flushBuffer() {
        if (currentBufferSize > 0) {
            byte[] tempBuf = new byte[currentBufferSize];
            System.arraycopy(byteBuffer, 0, tempBuf, 0, currentBufferSize);
            String encoded = Base64.encode(tempBuf);
            encoded.getChars(0, encoded.length(), resultingLine, 0);
            // Output characters
            try {
                contentHandler.characters(resultingLine, 0, encoded.length());
            } catch (SAXException e) {
                throw new OXFException(e);
            }
            // Reset counter
            currentBufferSize = 0;
        }
    }
}
