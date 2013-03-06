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
import java.io.Writer;

/**
 * This ContentHandler writes all character events to a Writer.
 */
public class TextXMLReceiver extends XMLReceiverAdapter {

    private Writer writer;

    public TextXMLReceiver(Writer writer) {
        this.writer = writer;
    }

    public void characters(char ch[], int start, int length) throws SAXException {
        try {
            writer.write(ch, start, length);
        } catch (IOException e) {
            throw new OXFException(e);
        }
    }

}
