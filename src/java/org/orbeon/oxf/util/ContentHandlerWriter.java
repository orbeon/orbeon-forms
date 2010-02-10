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
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.Writer;

/**
 * A Writer that converts the characters written into it into characters written to a ContentHandler.
 */
public class ContentHandlerWriter extends Writer {
    private ContentHandler contentHandler;
    private boolean supportFlush;

    private char[] singleChar = new char[1];

    public ContentHandlerWriter(ContentHandler contentHandler) {
        this.contentHandler = contentHandler;
    }

    public ContentHandlerWriter(ContentHandler contentHandler, boolean supportFlush) {
        this(contentHandler);
        this.supportFlush = supportFlush;
    }

    public void close() throws IOException {
    }

    public void flush() throws IOException {
        if (supportFlush) {
            try {
                contentHandler.processingInstruction("oxf-serializer", "flush");
            } catch (SAXException e) {
                throw new OXFException(e);
            }
        }
    }

    public void write(int c) throws IOException {
        singleChar[0] = (char) c;
        try {
            contentHandler.characters(singleChar, 0, 1);
        } catch (SAXException e) {
            throw new OXFException(e);
        }
    }

    public void write(char cbuf[]) throws IOException {
        try {
            contentHandler.characters(cbuf, 0, cbuf.length);
        } catch (SAXException e) {
            throw new OXFException(e);
        }
    }

    public void write(String str) throws IOException {
        try {
            int len = str.length();
            char[] c = new char[len];
            str.getChars(0, str.length(), c, 0);
            contentHandler.characters(c, 0, len);
        } catch (SAXException e) {
            throw new OXFException(e);
        }
    }

    public void write(String str, int off, int len) throws IOException {
        try {
            char[] c = new char[len];
            str.getChars(off, off + len, c, 0);
            contentHandler.characters(c, 0, len);
        } catch (SAXException e) {
            throw new OXFException(e);
        }
    }

    public void write(char cbuf[], int off, int len) throws IOException {
        try {
            contentHandler.characters(cbuf, off, len);
        } catch (SAXException e) {
            throw new OXFException(e);
        }
    }
}
