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
import org.orbeon.oxf.processor.sql.DatabaseDelegate;
import org.orbeon.oxf.processor.sql.SQLProcessorOracleDelegateBase;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Version for Oracle.
 *
 * NOTE: This will work if PreparedStatement and ResultSet can be directly cast to their Oracle counterparts. This is
 * the case for WebLogic 8.1, which has its own delegate.
 */
public class SQLProcessorOracleGenericDelegate extends SQLProcessorOracleDelegateBase implements DatabaseDelegate {
    protected OraclePreparedStatement getOraclePreparedStatement(PreparedStatement stmt) {
        // Simply cast
        return (OraclePreparedStatement) stmt;
    }

    protected OracleResultSet getOracleResultSet(ResultSet resultSet) {
        // Simply cast
        return (OracleResultSet) resultSet;
    }
}
