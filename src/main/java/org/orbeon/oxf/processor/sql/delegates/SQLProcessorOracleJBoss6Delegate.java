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
package org.orbeon.oxf.processor.sql.delegates;

import oracle.jdbc.OraclePreparedStatement;
import oracle.jdbc.OracleResultSet;
import org.jboss.resource.adapter.jdbc.WrappedPreparedStatement;
import org.jboss.resource.adapter.jdbc.WrappedResultSet;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.processor.sql.DatabaseDelegate;
import org.orbeon.oxf.processor.sql.SQLProcessorOracleDelegateBase;

import java.sql.*;

/**
 * Custom Delegate for Oracle / JBoss 6.
 */
public class SQLProcessorOracleJBoss6Delegate extends SQLProcessorOracleDelegateBase implements DatabaseDelegate {

    protected OraclePreparedStatement getOraclePreparedStatement(PreparedStatement stmt) {

        if (stmt instanceof OraclePreparedStatement)
            return (OraclePreparedStatement) stmt;
        else {
            try {
                return (OraclePreparedStatement) ((WrappedPreparedStatement) stmt).getUnderlyingStatement();
            } catch (SQLException e) {
                throw new OXFException(e);
            }
        }
    }

    protected OracleResultSet getOracleResultSet(ResultSet resultSet) {

        if (resultSet instanceof OracleResultSet)
            return (OracleResultSet) resultSet;
        else
            try {
                return (OracleResultSet) ((WrappedResultSet) resultSet).getUnderlyingResultSet();
            } catch (SQLException e) {
                throw new OXFException(e);
            }
    }
}
