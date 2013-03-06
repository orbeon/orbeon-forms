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
import org.jaxen.Function;
import org.orbeon.oxf.processor.sql.SQLProcessor;
import org.orbeon.oxf.processor.sql.SQLProcessorInterpreterContext;
import org.orbeon.oxf.xml.XPathUtils;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 *
 */
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

        // Scope functions
        final Node[] currentNode = new Node[1];
        final int[] currentPosition = new int[1];
        Map functions = new HashMap();
        functions.put("current", new Function() {
            public Object call(org.jaxen.Context context, List args) {
                return currentNode[0];
            }
        });

        // FIXME: position() won't work because it will override
        // the default XPath position() function
        // We probably need to create a Jaxen Context directly to fix this

//                        functions.put("position", new Function() {
//                            public Object call(org.jaxen.Context context, List args) throws FunctionCallException {
//                                return new Integer(currentPosition[0]);
//                            }
//                        });

        final SQLProcessorInterpreterContext interpreterContext = getInterpreterContext();
        interpreterContext.pushFunctions(functions);
        try {
            // Iterate through the result set
            int nodeCount = 1;

            for (Iterator i = XPathUtils.selectIterator(interpreterContext.getCurrentNode(), select, interpreterContext.getPrefixesMap(), null, interpreterContext.getFunctionContext()); i.hasNext(); nodeCount++) {
                currentNode[0] = (Node) i.next();
                currentPosition[0] = nodeCount;

                // Run one iteration
                interpreterContext.pushCurrentNode(currentNode[0]);
                repeatBody();
                interpreterContext.popCurrentNode();
            }
        } finally {
            interpreterContext.popFunctions();
        }
    }

    public void end(String uri, String localname, String qName) throws SAXException {
    }
}
