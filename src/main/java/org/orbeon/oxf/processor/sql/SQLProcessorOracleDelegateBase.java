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
package org.orbeon.oxf.processor.sql;

import oracle.jdbc.*;
import oracle.sql.*;
import oracle.xdb.XMLType;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.xml.XMLUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import java.io.*;
import java.sql.*;

/**
 * Base class for Oracle delegates.
 *
 * NOTE: Check whether this works with WebLogic 8.1 and the thin driver, or if for example
 * weblogic.jdbc.vendor.oracle.OracleThinBlob must be used. Would this work with createTemporary()
 * anyway?
 */
public abstract class SQLProcessorOracleDelegateBase implements DatabaseDelegate {

//    public SQLProcessorOracleTomcat4Delegate() {
//      // Load the Oracle JDBC driver
//        try {
//            DriverManager.registerDriver
//                    (new oracle.jdbc.driver.OracleDriver());
//            Connection conn = DriverManager.getConnection("jdbc:oracle:oci:@rosaura","scott","tiger");
//
//            // Create Oracle DatabaseMetaData object
//            DatabaseMetaData meta = conn.getMetaData();
//
//            // gets driver info:
//            System.out.println("JDBC driver version is " + meta.getDriverVersion());
//            System.out.println("JDBC driver nameis     " + meta.getDriverName());
//        } catch (SQLException e) {
//            e.printStackTrace();
//        }
//    }


    public void setClob(PreparedStatement stmt, int index, String value) throws SQLException {

        // Get an OraclePreparedStatement
        final OraclePreparedStatement oracleStmt = getOraclePreparedStatement(stmt);

        // Create a temporary CLOB
        final CLOB clob = CLOB.createTemporary(oracleStmt.getConnection(), true, CLOB.DURATION_SESSION);

        // Write to the CLOB
        final Writer writer = clob.getCharacterOutputStream();
        try {
            NetUtils.copyStream(new StringReader(value), writer);
            writer.flush();
        } catch (IOException e) {
            throw new OXFException(e);
        }

        // Set the CLOB on the statement
        oracleStmt.setClob(index, clob);
    }

    public void setBlob(PreparedStatement stmt, int index, byte[] value) throws SQLException {
        final OutputStream os = getBlobOutputStream(stmt, index);
        try {
            NetUtils.copyStream(new ByteArrayInputStream(value), os);
            os.close();
        } catch (IOException e) {
            throw new OXFException(e);
        }
    }

    public OutputStream getBlobOutputStream(PreparedStatement stmt, final int index) throws SQLException {
        // Get an OraclePreparedStatement
        final OraclePreparedStatement oracleStmt = getOraclePreparedStatement(stmt);

        // Create a temporary BLOB
        final BLOB blob = BLOB.createTemporary(oracleStmt.getConnection(), true, BLOB.DURATION_SESSION);

        // Get the output stream
        final OutputStream os = blob.getBinaryOutputStream();

        // Return a special OutputStream that will *have* to be closed for anything to happen
        return new BufferedOutputStream(new OutputStream() {
            private boolean closed = false;
            public void close() throws IOException {
                if (!closed) {
                    os.flush();
                    os.close();
                    // Set the BLOB on the statement
                    try {
                        oracleStmt.setBlob(index, blob);
                    } catch (SQLException e) {
                        throw new OXFException(e);
                    }
                    closed = true;
                }
            }

            public void flush() throws IOException {
                os.flush();
            }

            public void write(byte b[]) throws IOException {
                os.write(b);
            }

            public void write(byte b[], int off, int len) throws IOException {
                os.write(b, off, len);
            }

            public void write(int b) throws IOException {
                os.write(b);
            }
        });
    }

    public boolean isXMLType(int columnType, String columnTypeName) throws SQLException {
        // This according to Oracle documentation
        return columnTypeName != null && OracleTypes.OPAQUE == columnType && columnTypeName.compareTo("SYS.XMLTYPE") == 0;
    }

    public Node getDOM(ResultSet resultSet, String columnName) throws SQLException {
        final OracleResultSet oracleResultSet = getOracleResultSet(resultSet);

//        XMLType xmlType = (XMLType) oracleResultSet.getObject(columnName);
        final OPAQUE opaque = oracleResultSet.getOPAQUE(columnName);
        final XMLType xmlType = XMLType.createXML(opaque);
        final Node doc;
        if(false && xmlType.isFragment()) {
            //  TODO: Handle XML fragment correctly
            //   doc = XMLUtils.parseDocumentFragment(xmlType.getStringVal());
            doc = null;
        } else {
            // FIXME: Xerces throws when walking the tree if we return the DOM directly!!!
//                doc = xmlType.getDOM();
            doc = XMLUtils.stringToDOM(xmlType.getStringVal());
        }

        return doc;
    }

    public void setDOM(PreparedStatement stmt, int index, String document) throws SQLException {
        // Get an OraclePreparedStatement
        final OraclePreparedStatement oracleStmt = getOraclePreparedStatement(stmt);

        final XMLType xmlType = XMLType.createXML(oracleStmt.getConnection(), document);
        oracleStmt.setObject(index, xmlType);
    }

    public void setDOM(PreparedStatement stmt, int index, Document document) throws SQLException {
        // Get an OraclePreparedStatement
        OraclePreparedStatement oracleStmt = getOraclePreparedStatement(stmt);

        // FIXME: XMLType.createXML(oracleStmt.getConnection(), doc) returns null!!!
        XMLType xmlType = XMLType.createXML(oracleStmt.getConnection(), document);
        oracleStmt.setObject(index, xmlType);
    }

    /**
     * Derived classes must implement this to return a native OraclePreparedStatement.
     */
    protected abstract OraclePreparedStatement getOraclePreparedStatement(PreparedStatement stmt);

    /**
     * Derived classes must implement this to return a native OracleResultSet.
     */
    protected abstract OracleResultSet getOracleResultSet(ResultSet resultSet);
}
