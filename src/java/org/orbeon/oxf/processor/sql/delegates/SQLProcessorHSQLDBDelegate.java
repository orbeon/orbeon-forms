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

import org.hsqldb.jdbc.jdbcBlob;
import org.orbeon.oxf.common.OXFException;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Delegate for HSQLDB.
 *
 * This is minimal at the moment.
 */
public class SQLProcessorHSQLDBDelegate extends SQLProcessorGenericDelegate {

    public OutputStream getBlobOutputStream(final PreparedStatement stmt, final int index) throws SQLException {
        // As of 1.7.2.8 HSQLDB only supports creating BLOB objects with byte[]
        return new ByteArrayOutputStream() {
            public void close() {
                try {
                    stmt.setBlob(index, new jdbcBlob(toByteArray()));
                } catch (SQLException e) {
                    throw new OXFException(e);
                }
            }
        };
    }

    public void setBlob(PreparedStatement stmt, int index, byte[] value) throws SQLException {
        stmt.setBlob(index, new jdbcBlob(value));
    }
}
