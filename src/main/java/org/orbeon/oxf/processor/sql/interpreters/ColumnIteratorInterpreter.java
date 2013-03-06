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

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;

/**
 *
 */
public class ColumnIteratorInterpreter extends SQLProcessor.InterpreterContentHandler {

    public ColumnIteratorInterpreter(SQLProcessorInterpreterContext interpreterContext) {
        // Repeating interpreter
        super(interpreterContext, true);
        setForward(true);
    }

    public void start(String uri, String localname, String qName, Attributes attributes) throws SAXException {

        addAllDefaultElementHandlers();

        final SQLProcessorInterpreterContext interpreterContext = getInterpreterContext();

        try {
            final ResultSet resultSet = interpreterContext.getResultSet();
            final ResultSetMetaData metadata = resultSet.getMetaData();

            // Iterate through result set columns
            for (int i = 1; i <= metadata.getColumnCount(); i++) {
                interpreterContext.pushContext();
                // NOTE: getColumnLabel() allows SQL "AS" to work
                interpreterContext.setColumnContext(i, metadata.getColumnLabel(i), metadata.getColumnTypeName(i), GetterInterpreter.getColumnStringValue(resultSet, i, metadata.getColumnType(i)));
                repeatBody();
                interpreterContext.popContext();
            }

        } catch (SQLException e) {
            throw new ValidationException(e, new LocationData(getDocumentLocator()));
        }
    }

    public void end(String uri, String localname, String qName) throws SAXException {
    }
}
