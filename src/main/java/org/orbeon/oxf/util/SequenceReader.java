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
package org.orbeon.oxf.util;

import org.orbeon.oxf.common.OXFException;

import java.io.IOException;
import java.io.Reader;
import java.util.Iterator;

public class SequenceReader extends Reader {
    private Iterator iterator;
    private Reader reader;

    public SequenceReader(Iterator iterator) {
        this.iterator = iterator;
        try {
            nextReader();
        } catch (IOException ex) {
            throw new OXFException("Invalid state");
        }
    }

    public int read() throws IOException {
        if (reader == null)
            return -1;

        int c = reader.read();
        if (c == -1) {
            nextReader();
            return read();
        }
        return c;
    }

    public int read(char b[], int off, int len) throws IOException {
        if (reader == null) {
            return -1;
        } else if (b == null) {
            throw new NullPointerException();
        } else if ((off < 0) || (off > b.length) || (len < 0) ||
                ((off + len) > b.length) || ((off + len) < 0)) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return 0;
        }

        int n = reader.read(b, off, len);
        if (n <= 0) {
            nextReader();
            return read(b, off, len);
        }
        return n;
    }

    public void close() throws IOException {
        do {
            nextReader();
        } while (reader != null);
    }

    void nextReader() throws IOException {
        if (reader != null)
            reader.close();

        if (iterator.hasNext()) {
            reader = (Reader) iterator.next();
            if (reader == null)
                throw new OXFException("Null reader passed to " + getClass().getName());
        } else
            reader = null;
    }
}
