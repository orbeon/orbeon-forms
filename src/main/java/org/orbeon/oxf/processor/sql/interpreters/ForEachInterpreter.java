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

import org.dom4j.Node;
import org.orbeon.oxf.processor.sql.SQLFunctionLibrary;
import org.orbeon.oxf.processor.sql.SQLProcessor;
import org.orbeon.oxf.processor.sql.SQLProcessorInterpreterContext;
import org.orbeon.oxf.xml.XPathUtils;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import java.util.Iterator;

public class ForEachInterpreter extends SQLProcessor.InterpreterContentHandler {

    public ForEachInterpreter(SQLProcessorInterpreterContext interpreterContext) {
        // Repeating interpreter
        super(interpreterContext, true);
        setForward(true);
    }

    public void start(String uri, String localname, String qName, Attributes attributes) throws SAXException {

        addAllDefaultElementHandlers();

        // Get attributes
        final String select = attributes.getValue("select");

        final SQLProcessorInterpreterContext interpreterContext = getInterpreterContext();

        // Iterate through the result set
        int currentPosition = 1;

        final Iterator<Node> it =
            XPathUtils.selectNodeIterator(
                interpreterContext.getCurrentNode(),
                select,
                interpreterContext.getPrefixesMap(),
                SQLFunctionLibrary.instance(),
                interpreterContext.getFunctionContextOrNull()
            );

        for (; it.hasNext(); currentPosition++) {
            final Node currentNode  = it.next();

            final SQLFunctionLibrary.SQLFunctionContext functionContextOrNull = interpreterContext.getFunctionContextOrNull();

            interpreterContext.pushFunctionContext(
                new SQLFunctionLibrary.SQLFunctionContext(
                    currentNode,
                    currentPosition,
                    functionContextOrNull == null ? null : functionContextOrNull.getColumn()
                )
            );
            try {
                // Run one iteration
                interpreterContext.pushCurrentNode(currentNode);
                repeatBody();
                interpreterContext.popCurrentNode();
            } finally {
                interpreterContext.popFunctionContext();
            }
        }
    }

    public void end(String uri, String localname, String qName) throws SAXException {}
}
