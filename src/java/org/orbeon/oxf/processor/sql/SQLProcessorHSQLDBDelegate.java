package org.orbeon.oxf.processor.sql;

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
