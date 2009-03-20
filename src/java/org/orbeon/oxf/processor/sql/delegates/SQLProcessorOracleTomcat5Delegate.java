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
package org.orbeon.oxf.processor.sql.delegates;

import oracle.jdbc.OraclePreparedStatement;
import oracle.jdbc.OracleResultSet;
import org.apache.commons.dbcp.DelegatingResultSet;
import org.apache.tomcat.dbcp.dbcp.DelegatingPreparedStatement;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.processor.sql.DatabaseDelegate;
import org.orbeon.oxf.processor.sql.SQLProcessorOracleDelegateBase;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Version for Tomcat 4.1 / Oracle.
 */

// This implementation is selected, but there is then a:
// java.lang.NoClassDefFoundError: org/apache/commons/dbcp/DelegatingPreparedStatement
//
// Issue with the selection method?

public class SQLProcessorOracleTomcat5Delegate extends SQLProcessorOracleDelegateBase implements DatabaseDelegate {

    protected OraclePreparedStatement getOraclePreparedStatement(PreparedStatement stmt) {
        // Use classes from Tomcat DBCP to get the delegate

        if (stmt instanceof OraclePreparedStatement)
            return (OraclePreparedStatement) stmt;
        else {

            // It seems that depending on the Tomcat version, there might be more levels of nesting. This works with
            // Tomcat 5.5.27.
            while (stmt instanceof DelegatingPreparedStatement) {
                final DelegatingPreparedStatement delegatingPreparedStatement = (DelegatingPreparedStatement) stmt;

                final PreparedStatement delegate = (PreparedStatement) delegatingPreparedStatement.getDelegate();

                if (delegate instanceof OraclePreparedStatement)
                    return (OraclePreparedStatement) delegate;

                stmt = delegate;
            }
            throw new OXFException("OraclePreparedStatement not found");
        }
    }

    protected OracleResultSet getOracleResultSet(ResultSet resultSet) {
        // Use classes from Tomcat DBCP to get the delegate
        if (resultSet instanceof OracleResultSet)
            return (OracleResultSet) resultSet;
        else
            return (OracleResultSet) ((DelegatingResultSet) resultSet).getDelegate();
    }
}