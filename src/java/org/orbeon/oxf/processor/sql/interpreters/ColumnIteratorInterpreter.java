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
import org.orbeon.oxf.xml.SAXStore;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.sql.ResultSetMetaData;
import java.sql.ResultSet;
import java.util.Map;

/**
 *
 */
public class ColumnIteratorInterpreter extends SQLProcessor.InterpreterContentHandler {

    private Map elementHandlers;
    private SAXStore saxStore;
    private ContentHandler savedOutput;

    public ColumnIteratorInterpreter(SQLProcessorInterpreterContext interpreterContext, Map elementHandlers) {
        super(interpreterContext, false);
        this.elementHandlers = elementHandlers;
    }

    public void start(String uri, String localname, String qName, Attributes attributes) throws SAXException {
        // Everything will be stored in a SAXStore
        saxStore = new SAXStore();
        saxStore.setDocumentLocator(getDocumentLocator());
        savedOutput = getInterpreterContext().getOutput();
        getInterpreterContext().setOutput(saxStore);
        setForward(true);
    }

    public void end(String uri, String localname, String qName) throws SAXException {
        if (saxStore != null) {
            final SQLProcessorInterpreterContext interpreterContext = getInterpreterContext();
            // Restore the regular output
            interpreterContext.setOutput(savedOutput);

            try {
                // Create a new InterpreterContentHandler with the same handlers as our parent
                final SQLProcessor.InterpreterContentHandler contentHandler = new SQLProcessor.InterpreterContentHandler(interpreterContext, true);
                contentHandler.setElementHandlers(elementHandlers);

                final ResultSet resultSet = interpreterContext.getResultSet(0);
                final ResultSetMetaData metadata = resultSet.getMetaData();

                // Iterate through result set columns
                for (int i = 1; i <= metadata.getColumnCount(); i++) {
                    interpreterContext.pushContext();
                    interpreterContext.setColumnContext(i, metadata.getColumnName(i), metadata.getColumnTypeName(i), GetterInterpreter.getColumnStringValue(resultSet, i, metadata.getColumnType(i)));
                    saxStore.replay(contentHandler);
                    interpreterContext.popContext();
                }

            } catch (Exception e) {
                throw new ValidationException(e, new LocationData(getDocumentLocator()));
            }
        }
    }
}
