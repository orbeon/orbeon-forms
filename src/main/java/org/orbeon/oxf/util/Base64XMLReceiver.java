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
import org.orbeon.oxf.xml.XMLReceiverAdapter;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Implement a streamed Base64 decoder acting as a ContentHandler.
 *
 * The Base64 decoder handles data by chunks of 4 characters. With SAX, character events may be
 * broken up at any point. We have to put data back together so we can correctly feed the Base64
 * decoder.
 */
public class Base64XMLReceiver extends XMLReceiverAdapter {
    private OutputStream os;
    private char[] buffer = new char[76];
    private int bufferSize;

    private int inputCharCount;
    private int charCount;
    private int byteCount;

    public Base64XMLReceiver(OutputStream os) {
        this.os = os;
    }

    public void characters(char ch[], int start, int length) throws SAXException {
        inputCharCount += length;
        try {
            int newStart = start;
            int newLength = length;
            do {
                // Try to fill buffer
                int displacement = fillBufferNoWhiteSpace(ch, newStart, newLength);
                newStart += displacement;
                newLength -= displacement;
                int groups = bufferSize / 4;
                if (groups > 0) {
                    // There is data to treat
                    byte[] result = NetUtils.base64StringToByteArray(new String(buffer, 0, groups * 4)); // NOTE: String and byte[] creation could be both removed
                    charCount += groups * 4;
                    byteCount += result.length;
                    os.write(result);
                    // Update buffer
                    int remainder = bufferSize % 4;
                    if (remainder > 0)
                        System.arraycopy(buffer, groups * 4, buffer, 0, remainder);
                    bufferSize = remainder;
                }
            } while (newLength > 0);
        } catch (IOException e) {
            throw new OXFException(e);
        }
    }

    private int fillBufferNoWhiteSpace(char ch[], int start, int length) {
        int i;
        for (i = start; i < start + length; i++) {
            if (!Base64.isWhiteSpace(ch[i])) {
                buffer[bufferSize++] = ch[i];
                if (bufferSize == buffer.length)
                    break;
            }
        }
        return i - start + 1;
    }

    public int getByteCount() {
        return byteCount;
    }

    public int getCharCount() {
        return charCount;
    }

    public int getInputCharCount() {
        return inputCharCount;
    }
}
