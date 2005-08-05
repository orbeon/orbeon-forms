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

import org.orbeon.oxf.processor.sql.DeferredContentHandler;
import org.orbeon.oxf.processor.sql.DeferredContentHandlerImpl;
import org.orbeon.oxf.processor.sql.SQLProcessor;
import org.orbeon.oxf.processor.sql.SQLProcessorInterpreterContext;
import org.orbeon.oxf.xml.ContentHandlerAdapter;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

/**
 * Add an attribute to the last element output.
 */
public class AttributeInterpreter extends SQLProcessor.InterpreterContentHandler {

    private DeferredContentHandler savedOutput;
    private String attributeName;
    private StringBuffer content;

    public AttributeInterpreter(SQLProcessorInterpreterContext interpreterContext) {
        super(interpreterContext, false);
        setForward(true);
    }

    public void start(String uri, String localname, String qName, Attributes attributes) throws SAXException {
        // Attribute name
        this.attributeName = attributes.getValue("name");

        // Remember output
        savedOutput = getInterpreterContext().getOutput();
        // New output just gather character data
        getInterpreterContext().setOutput(new DeferredContentHandlerImpl(new ContentHandlerAdapter() {
            public void characters(char ch[], int start, int length) {
                if (content == null)
                    content = new StringBuffer();
                content.append(ch, start, length);
            }
        }));

        addAllDefaultElementHandlers();
    }

    public void end(String uri, String localname, String qName) throws SAXException {

        // Restore output
        getInterpreterContext().setOutput(savedOutput);

        // Normalize
        final String contentString;
        if (content == null)
            contentString = "";
        else
            contentString = content.toString().trim();

        // Output attribute
        // TODO: handle namespaces
        getInterpreterContext().getOutput().addAttribute("", attributeName, attributeName, contentString);

        // Clear state
        savedOutput = null;
        attributeName = null;
        content = null;
    }
}
