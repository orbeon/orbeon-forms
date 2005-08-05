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

import org.orbeon.oxf.processor.sql.DatabaseDelegate;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import java.io.OutputStream;
import java.io.StringReader;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class SQLProcessorGenericDelegate implements DatabaseDelegate {
    public void setClob(PreparedStatement stmt, int index, String value) throws SQLException {
        stmt.setCharacterStream(index, new StringReader(value), value.length());
    }

    public void setBlob(PreparedStatement stmt, int index, byte[] value) throws SQLException {
        throw new UnsupportedOperationException();
    }

    public OutputStream getBlobOutputStream(PreparedStatement stmt, int index) throws SQLException {
        throw new UnsupportedOperationException();
    }

    public boolean isXMLType(int columnType, String columnTypeName) throws SQLException {
        return false;
    }

    public Node getDOM(ResultSet resultSet, String columnName) throws SQLException {
        throw new UnsupportedOperationException();
    }

    public void setDOM(PreparedStatement stmt, int index, String document) throws SQLException {
        throw new UnsupportedOperationException();
    }

    public void setDOM(PreparedStatement stmt, int index, Document doc) throws SQLException {
        throw new UnsupportedOperationException();
    }
}
