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

import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.processor.sql.SQLProcessor;
import org.orbeon.oxf.processor.sql.SQLProcessorInterpreterContext;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 *
 */
public class ResultSetInterpreter extends SQLProcessor.InterpreterContentHandler {

    public ResultSetInterpreter(SQLProcessorInterpreterContext interpreterContext) {
        super(interpreterContext, false);
    }

    public void start(String uri, String localname, String qName, Attributes attributes) throws SAXException {
        if (!getInterpreterContext().isEmptyResultSet()) {
            setForward(true);
            final RowIteratorInterpreter rowIteratorInterpreter = new RowIteratorInterpreter(getInterpreterContext());
            addElementHandler(rowIteratorInterpreter, SQLProcessor.SQL_NAMESPACE_URI, "row-results");
            addElementHandler(rowIteratorInterpreter, SQLProcessor.SQL_NAMESPACE_URI, "row-iterator");
            final ValueOfCopyOfInterpreter valueOfCopyOfInterpreter = new ValueOfCopyOfInterpreter(getInterpreterContext());
            addElementHandler(valueOfCopyOfInterpreter, SQLProcessor.SQL_NAMESPACE_URI, "value-of");
            addElementHandler(valueOfCopyOfInterpreter, SQLProcessor.SQL_NAMESPACE_URI, "copy-of");
            addElementHandler(new TextInterpreter(getInterpreterContext()), SQLProcessor.SQL_NAMESPACE_URI, "text");
            // We must not be able to have a RowResultsInterpreter within the for-each
            // This must be checked in the schema
            addElementHandler(new ForEachInterpreter(getInterpreterContext(), getElementHandlers()), SQLProcessor.SQL_NAMESPACE_URI, "for-each");
        }
    }

    public void end(String uri, String localname, String qName) throws SAXException {
        SQLProcessorInterpreterContext interpreterContext = getInterpreterContext();
        PreparedStatement stmt = interpreterContext.getStatement(0);
        if (stmt != null) {
            try {
                stmt.close();
                interpreterContext.setStatement(null);
            } catch (SQLException e) {
                throw new ValidationException(e, new LocationData(getDocumentLocator()));
            }
        }
    }
}