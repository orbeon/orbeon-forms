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
package org.orbeon.oxf.processor.sql.interpreters;

import org.orbeon.oxf.processor.sql.SQLProcessor;
import org.orbeon.oxf.processor.sql.SQLProcessorInterpreterContext;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

/**
 *
 */
public class TextInterpreter extends SQLProcessor.InterpreterContentHandler {
    private StringBuffer text;

    public TextInterpreter(SQLProcessorInterpreterContext interpreterContext) {
        super(interpreterContext, false);
    }

    public void characters(char[] chars, int start, int length) throws SAXException {
        if (text == null)
            text = new StringBuffer();
        text.append(chars, start, length);
    }

    public void start(String uri, String localname, String qName, Attributes attributes) throws SAXException {
        text = null;
    }

    public void end(String uri, String localname, String qName) throws SAXException {
        char[] localText = text.toString().toCharArray();
        getInterpreterContext().getOutput().characters(localText, 0, localText.length);
    }
}