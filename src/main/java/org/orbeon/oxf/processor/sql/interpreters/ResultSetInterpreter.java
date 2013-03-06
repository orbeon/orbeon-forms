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
package org.orbeon.oxf.processor.sql.interpreters;

import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.processor.sql.SQLProcessor;
import org.orbeon.oxf.processor.sql.SQLProcessorInterpreterContext;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import java.sql.*;

/**
 *
 */
public class ResultSetInterpreter extends SQLProcessor.InterpreterContentHandler {

    private static final String UNBOUNDED = "unbounded";

    public ResultSetInterpreter(SQLProcessorInterpreterContext interpreterContext) {
        super(interpreterContext, true);
        setForward(true);
    }

    public void start(String uri, String localname, String qName, Attributes attributes) throws SAXException {

        addAllDefaultElementHandlers();

        // Optional result sets count
        final int allowedResultSetCount;
        {
            final String resultSetsAttribute = attributes.getValue("result-sets");
            allowedResultSetCount = (resultSetsAttribute == null) ? 1 : (resultSetsAttribute.equals(UNBOUNDED) ? -1 : Integer.parseInt(resultSetsAttribute));
        }

        final SQLProcessorInterpreterContext interpreterContext = getInterpreterContext();

        try {
            final PreparedStatement stmt = interpreterContext.getStatement(0);
            if (stmt != null) {
                int currentCount = 0;
                boolean hasMoreResultSets = interpreterContext.getResultSet() != null;
                do {

                    final boolean hasNext = !interpreterContext.isEmptyResultSet();

                    if (SQLProcessor.logger.isDebugEnabled()) {
                        SQLProcessor.logger.debug("Preparing to execute result set: hasMoreResultSets = " + hasMoreResultSets
                                + ", hasNext = " + hasNext
                                + ", statement = " + interpreterContext.getStatementString()
                                + ", currentCount = " + currentCount);
                    }

                    if (hasMoreResultSets) {

                        // Evaluate body

                        // NOTE: we do not evaluate the body if there is no row in the result set,
                        // for backward compatibility. This is questionable, as the sql:result-set
                        // element should intuitively evaluate its content in any case. Maybe an
                        // option should be provided on the sql:result-set element?
                        if (hasNext) {
                            repeatBody();
                        }

                        // One more result set has been processed
                        currentCount++;
                    }

                    // Try to go to next result set
                    hasMoreResultSets = setResultSetInfo(interpreterContext, stmt, stmt.getMoreResults());

                    // While we have not processed all the result sets we can process
                } while (hasMoreResultSets && (allowedResultSetCount == -1 || currentCount < allowedResultSetCount));
            }
        } catch (SQLException e) {
            throw new ValidationException(e, new LocationData(getDocumentLocator()));
        }
    }

    public void end(String uri, String localname, String qName) throws SAXException {

    }

    public static boolean setResultSetInfo(SQLProcessorInterpreterContext interpreterContext, PreparedStatement stmt, boolean hasResultSet) throws SQLException {
        if (!hasResultSet) {
            // There is no more result set, we can close everything
            final int updateCount = stmt.getUpdateCount();
            interpreterContext.setUpdateCount(updateCount);//FIXME: should add?
            closeStatement(interpreterContext, stmt);

            if (SQLProcessor.logger.isDebugEnabled())
                SQLProcessor.logger.debug("ResultSet info: no more result set, update count = " + updateCount);

            return false;
        } else {
            // There is one more result set
            final ResultSet resultSet = stmt.getResultSet();
            final boolean hasNext = resultSet.next();
            interpreterContext.setEmptyResultSet(!hasNext);
            interpreterContext.setResultSet(resultSet);
            interpreterContext.setGotResults(hasNext || interpreterContext.isGotResults());

            if (SQLProcessor.logger.isDebugEnabled())
                SQLProcessor.logger.debug("ResultSet info: more result set, hasNext = " + hasNext);

            return true;
        }
    }

    public static boolean setGeneratedKeysResultSetInfo(SQLProcessorInterpreterContext interpreterContext, PreparedStatement stmt) throws SQLException {
        // TODO: temporarily disabled as Oracle doesn't like it as is
        /*
            final ResultSet resultSet = stmt.getGeneratedKeys();
            
            final boolean hasNext = resultSet.next();
            interpreterContext.setEmptyResultSet(!hasNext);
            interpreterContext.setResultSet(resultSet);
            interpreterContext.setGotResults(hasNext || interpreterContext.isGotResults());

            if (SQLProcessor.logger.isDebugEnabled())
                SQLProcessor.logger.debug("GeneratedKeysResultSet info: more result set, hasNext = " + hasNext);
        */
		
		return true;
    }
    
    public static void closeStatement(SQLProcessorInterpreterContext interpreterContext, PreparedStatement stmt) throws SQLException {
        stmt.close();
        interpreterContext.setStatement(null);
        interpreterContext.setResultSet(null);
        interpreterContext.setEmptyResultSet(true);
    }
}
