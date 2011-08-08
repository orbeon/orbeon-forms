/**
 * Copyright (C) 2009 Orbeon, Inc.
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
package org.orbeon.oxf.resources;

import org.apache.log4j.Logger;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.util.LoggerFactory;

import java.io.*;
import java.sql.*;
import java.util.Map;

/**
 * The database resource manager is able to load resource from a relational
 * database. It can be initialized frm either a JDBC URL or from a datasource.
 *
 * @see DataSourceResourceManagerImpl
 */
public class DBResourceManagerImpl extends ResourceManagerBase {

    static private Logger logger = LoggerFactory.createLogger(DBResourceManagerImpl.class);

    private Connection connection = null;

    public DBResourceManagerImpl(Map props, Connection connection) {
        super(props);
        this.connection = connection;
    }

    protected Connection getConnection() throws SQLException {
        return connection;
    }

    protected void setConnection(Connection connection) {
        this.connection = connection;
    }

    protected void closeConnection(Connection connection) throws SQLException {
        // Doesn't do anything here, since we have a static connection and don't want to close it
    }

    /**
     * Returns a character reader from the resource manager for the specified
     * key. The key could point to any text document.
     * @param key A Resource Manager key
     * @return a character reader
     */
    public Reader getContentAsReader(String key) {
        final Connection[] conn = new Connection[1];
        final PreparedStatement psmt[] = new PreparedStatement[1];
        final ResultSet rs[] = new ResultSet[1];
        try {
            conn[0] = getConnection();
            psmt[0] = conn[0].prepareStatement("SELECT last_modified, xml FROM oxf_config where key = ?");
            psmt[0].setString(1, key);
            rs[0] = psmt[0].executeQuery();
            if (rs[0].next()) {
                Clob clob = rs[0].getClob("xml");
                java.sql.Date lastMod = rs[0].getDate("last_modified");
                final Reader r = clob.getCharacterStream();
                return new Reader() {

                    public boolean markSupported() {
                        return r.markSupported();
                    }

                    public void mark(int readAheadLimit) throws IOException {
                        r.mark(readAheadLimit);
                    }

                    public void reset() throws IOException {
                        r.reset();
                    }

                    public boolean ready() throws IOException {
                        return r.ready();
                    }

                    public int read(char cbuf[], int off, int len) throws IOException {
                        return r.read(cbuf, off, len);
                    }

                    public int read() throws IOException {
                        return r.read();
                    }

                    public int read(char cbuf[]) throws IOException {
                        return r.read(cbuf);
                    }

                    public long skip(long n) throws IOException {
                        return r.skip(n);
                    }

                    public void close() throws IOException {
                        r.close();
                        try {
                            if (rs[0] != null) rs[0].close();
                            if (psmt[0] != null) psmt[0].close();
                            closeConnection(conn[0]);
                        } catch (SQLException e) {
                            throw new OXFException(e);
                        }
                    }
                };
            } else {
                throw new ResourceNotFoundException(key);
            }
        } catch (Exception e) {
            logger.fatal("Can't retrieve document for key " + key, e);
            try {
                closeConnection(conn[0]);
            } catch (SQLException sqle) {
            }
            throw new OXFException(e);
        }
    }

    /**
     * Returns a binary input stream for the specified key. The key could point
     * to any document type (text or binary).
     * @param key A Resource Manager key
     * @return a input stream
     */
    public InputStream getContentAsStream(String key) {
        final Connection[] conn = new Connection[1];
        final PreparedStatement[] psmt = new PreparedStatement[1];
        final ResultSet[] rs = new ResultSet[1];
        try {
            conn[0] = getConnection();
            psmt[0] = conn[0].prepareStatement("SELECT last_modified, xml FROM oxf_config where key = ?");
            psmt[0].setString(1, key);
            rs[0] = psmt[0].executeQuery();
            if (rs[0].next()) {
                Blob blob = rs[0].getBlob("xml");
                java.sql.Date lastMod = rs[0].getDate("last_modified");
                final InputStream stream = blob.getBinaryStream();
                return new InputStream() {
                    public int available() throws IOException {
                        return stream.available();
                    }

                    public synchronized void mark(int readlimit) {
                        stream.mark(readlimit);
                    }

                    public boolean markSupported() {
                        return stream.markSupported();
                    }

                    public int read(byte b[]) throws IOException {
                        return stream.read(b);
                    }

                    public int read(byte b[], int off, int len) throws IOException {
                        return stream.read(b, off, len);
                    }

                    public synchronized void reset() throws IOException {
                        stream.reset();
                    }

                    public long skip(long n) throws IOException {
                        return stream.skip(n);
                    }

                    public int read() throws IOException {
                        return stream.read();
                    }

                    public void close() throws IOException {
                        stream.close();
                        try {
                            if (rs[0] != null) rs[0].close();
                            if (psmt[0] != null) psmt[0].close();
                            closeConnection(conn[0]);
                        } catch (SQLException e) {
                            throw new OXFException(e);
                        }
                    }
                };

            } else {
                throw new ResourceNotFoundException(key);
            }
        } catch (Exception e) {
            logger.fatal("Can't retrieve document for key " + key, e);
            try {
                closeConnection(conn[0]);
            } catch (SQLException sql) {
            }
            throw new OXFException(e);
        } finally {

        }
    }

    /**
     * Gets the last modified timestamp for the specified resource
     * @param key A Resource Manager key
     * @param doNotThrowResourceNotFound
     * @return a timestamp
     */
    public long lastModifiedImpl(String key, boolean doNotThrowResourceNotFound) {
        return 0L;
    }

    /**
     * Returns the length of the file denoted by this abstract pathname.
     * @return The length, in bytes, of the file denoted by this abstract pathname, or 0L if the file does not exist
     */
    public int length(String key) {
        return 0;
    }

    /**
     * Indicates if the resource manager implementation suports write operations
     * @return true if write operations are allowed
     */
    public boolean canWrite(String key) {
        return false;
    }

    /**
     * Allows writing to the resource
     * @param key A Resource Manager key
     * @return an output stream
     */
    public OutputStream getOutputStream(String key) {
        throw new OXFException("Write Operation not supported");
    }

    /**
     * Allow writing to the resource
     * @param key A Resource Manager key
     * @return  a writer
     */
    public Writer getWriter(String key) {
        throw new OXFException("Write Operation not supported");
    }

    public String getRealPath(String key) {
        return null;
    }

}
