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
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 *
 */
public class ExecuteInterpreter extends SQLProcessor.InterpreterContentHandler {

    public ExecuteInterpreter(SQLProcessorInterpreterContext interpreterContext) {
        super(interpreterContext, true);
    }

    public void start(String uri, String localname, String qName, Attributes attributes) throws SAXException {
        final SQLProcessorInterpreterContext interpreterContext = getInterpreterContext();

        // Push context
        interpreterContext.pushContext();

        addElementHandler(new QueryInterpreter(interpreterContext, QueryInterpreter.QUERY), SQLProcessor.SQL_NAMESPACE_URI, "query");
        addElementHandler(new QueryInterpreter(interpreterContext, QueryInterpreter.UPDATE), SQLProcessor.SQL_NAMESPACE_URI, "update");

        final ResultSetInterpreter resultSetInterpreter = new ResultSetInterpreter(interpreterContext);
        addElementHandler(resultSetInterpreter, SQLProcessor.SQL_NAMESPACE_URI, "results");
        addElementHandler(resultSetInterpreter, SQLProcessor.SQL_NAMESPACE_URI, "result-set");

        addElementHandler(new NoResultsInterpreter(interpreterContext), SQLProcessor.SQL_NAMESPACE_URI, "no-results");

        final ValueOfCopyOfInterpreter valueOfCopyOfInterpreter = new ValueOfCopyOfInterpreter(interpreterContext);
        addElementHandler(valueOfCopyOfInterpreter, SQLProcessor.SQL_NAMESPACE_URI, "value-of");
        addElementHandler(valueOfCopyOfInterpreter, SQLProcessor.SQL_NAMESPACE_URI, "copy-of");

        addElementHandler(new TextInterpreter(interpreterContext), SQLProcessor.SQL_NAMESPACE_URI, "text");
        addElementHandler(new ForEachInterpreter(getInterpreterContext(), getElementHandlers()), SQLProcessor.SQL_NAMESPACE_URI, "for-each");

        addElementHandler(new RowIteratorInterpreter(getInterpreterContext()), SQLProcessor.SQL_NAMESPACE_URI, "column-iterator");
    }

    public void end(String uri, String localname, String qName) throws SAXException {
        final SQLProcessorInterpreterContext interpreterContext = getInterpreterContext();
        final PreparedStatement stmt = interpreterContext.getStatement(0);
        if (stmt != null) {
            try {
                stmt.close();
            } catch (SQLException e) {
                throw new ValidationException(e, new LocationData(getDocumentLocator()));
            }
        }
        // Pop context
        interpreterContext.popContext();
    }
}