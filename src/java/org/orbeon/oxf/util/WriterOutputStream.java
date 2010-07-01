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

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.*;

/**
 * This class tries to implement a WriterOutputStream able to handle character encodings correctly.
 */
public class WriterOutputStream extends OutputStream {

    private Writer writer; // Writer to write to

    private CharsetDecoder decoder;

    private ByteBuffer inputBuffer;
    private CharBuffer outputBuffer;

    private char[] outputBufferArray;

    public WriterOutputStream(Writer writer) throws IOException, UnsupportedCharsetException {
        this.writer = writer;
        this.inputBuffer = ByteBuffer.allocateDirect(1024);
        this.outputBufferArray = new char[1024];
        this.outputBuffer = CharBuffer.wrap(outputBufferArray);
    }

    public void setCharset(String charset) {
        this.decoder = Charset.forName(charset).newDecoder().reset();
    }

    public void write(byte[] buf, int off, int len) throws IOException {
        int remaining = len;
        while (remaining > 0) {
            if (remaining > inputBuffer.remaining()) {
                remaining -= inputBuffer.remaining();
                inputBuffer.put(buf, off, inputBuffer.remaining());
            } else {
                inputBuffer.put(buf, off, remaining);
                remaining = 0;
            }
            decode();
        }
    }

    public void write(int b) throws IOException {
        writer.flush();
        inputBuffer.put((byte) b);

        if (!(inputBuffer.remaining() > 0))
            decode();
    }

    private void decode() throws IOException {
        CoderResult result;
        inputBuffer.flip();
        do {
            result = decoder.decode(inputBuffer, outputBuffer, false);
            writer.write(outputBufferArray, 0, outputBuffer.position());
            outputBuffer.clear();
        } while (!result.isUnderflow());
        inputBuffer.compact();
    }

    public void flush() throws IOException {
        decode();
        writer.flush();
    }

    public void close() throws IOException {
        flush();
        writer.close();
    }
}
