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
import org.orbeon.oxf.xml.ContentHandlerAdapter;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.util.Map;

/**
 * Add an attribute to the last element output.
 */
public class AttributeInterpreter extends SQLProcessor.InterpreterContentHandler {

    private String attributeName;
    private StringBuffer content;

    public AttributeInterpreter(SQLProcessorInterpreterContext interpreterContext) {
        super(interpreterContext, false);
    }

    public void start(String uri, String localname, String qName, Attributes attributes) throws SAXException {
        // Get attributes
        this.attributeName = attributes.getValue("name");
    }

    public void characters(char ch[], int start, int length) {
        if (content == null)
            content = new StringBuffer();
        content.append(ch, start, length);
    }

    public void end(String uri, String localname, String qName) throws SAXException {

        // Output attribute
//        if (content == null)
//            throw new OXFException("sql:attribute content must not be empty");


        // TODO: figure out a way of outputting attribute (attributeName, content)
    }
}
