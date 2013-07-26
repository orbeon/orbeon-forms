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
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.processor.sql.DatabaseDelegate;
import org.orbeon.oxf.processor.sql.SQLProcessorOracleDelegateBase;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Works with JDBC4 or app servers that don't wrap, as is apparently the case with WebLogic 8.1 (and maybe newer
 * versions, hopefully all the way to WebLogic 12c, the first version that supports JDBC 4).
 */
public class SQLProcessorOracleJDBC4Delegate extends SQLProcessorOracleDelegateBase implements DatabaseDelegate {

    protected OraclePreparedStatement getOraclePreparedStatement(PreparedStatement statement) {
        try {
            return statement instanceof OraclePreparedStatement
                ? (OraclePreparedStatement) statement
                : statement.unwrap(OraclePreparedStatement.class);
        } catch (SQLException e) {
            throw new OXFException(e);
        }
    }

    protected OracleResultSet getOracleResultSet(ResultSet resultSet) {
        try {
            return resultSet instanceof OracleResultSet
                ? (OracleResultSet) resultSet
                : resultSet.unwrap(OracleResultSet.class);
        } catch (SQLException e) {
            throw new OXFException(e);
        }
    }
}
