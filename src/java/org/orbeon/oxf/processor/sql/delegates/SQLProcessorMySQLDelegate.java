package org.orbeon.oxf.processor.sql.delegates;

import org.orbeon.oxf.common.OXFException;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Custom Delegate for MySQL DB to be used with SQL Processor.
 *
 */
public class SQLProcessorMySQLDelegate extends SQLProcessorGenericDelegate {
    public OutputStream getBlobOutputStream(final PreparedStatement stmt, final int index) throws SQLException {
        return new ByteArrayOutputStream() {
            public void close() {
                try {
                    stmt.setBytes(index, toByteArray());
                } catch (SQLException e) {
                    throw new OXFException(e);
                }
            }
        };
    }

    public void setBlob(PreparedStatement stmt, int index, byte[] value) throws SQLException {
        stmt.setBytes(index,value);
    }
}
