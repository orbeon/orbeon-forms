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
package org.orbeon.oxf.processor.sql;

import java.io.OutputStream;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * This interface is implemented by all vendor-specific database delegates.
 */
public interface DatabaseDelegate {
    public void setClob(PreparedStatement stmt, int index, String value) throws SQLException;
    public void setBlob(PreparedStatement stmt, int index, byte[] value) throws SQLException;
    public OutputStream getBlobOutputStream(PreparedStatement stmt, int index) throws SQLException;
    public boolean isXMLType(int columnType, String columnTypeName) throws SQLException;
    public org.w3c.dom.Node getDOM(ResultSet resultSet, String columnName) throws SQLException;
//        public void setDOM(PreparedStatement stmt, int index, org.w3c.dom.Document node);
    public void setDOM(PreparedStatement stmt, int index, String document) throws SQLException;
}
