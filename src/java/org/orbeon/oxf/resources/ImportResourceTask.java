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
package org.orbeon.oxf.resources;

import oracle.jdbc.driver.OracleResultSet;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;

public class ImportResourceTask extends Task {

    public static final char[] INVALID_CHARACTERS = {' ', '\\', '/', ':', '*', '?', '"', '<', '>', '|'};
    public static final String SUFFIX = ".xml";

    /**
     * Schema:
     *   CREATE TABLE oxf_resources (
     *      key varchar2(1024),
     *      timestamp date,
     *      xml clob);
     */
    public static final String TABLE_NAME = "oxf_resources";
    public static final String KEY_NAME = "key";
    public static final String TIMESTAMP_NAME = "last_modified";
    public static final String XML_NAME = "xml";

    protected String url;
    protected String properties;
    protected String driver;
    protected String inDir;
    protected String table;
    protected Connection connection;

    public ImportResourceTask() {
        table = TABLE_NAME;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getProperties() {
        return properties;
    }

    public void setProperties(String properties) {
        this.properties = properties;
    }

    public String getDriver() {
        return driver;
    }

    public void setDriver(String driver) {
        this.driver = driver;
    }


    public String getInDir() {
        return inDir;
    }

    public void setInDir(String inputDir) {
        this.inDir = inputDir;
    }

    public String getTable() {
        return table;
    }

    public void setTable(String table) {
        this.table = table;
    }


    public void execute() throws BuildException {
        try {
            connect();

            Map keys = new HashMap();
            getKeysFromDirectory(new File(getInDir()), keys);
            importData(keys);
            connection.close();

        } catch (Exception e) {
            throw new BuildException(e);
        }

    }


    protected void connect() throws BuildException {
        try {
            Properties info = new Properties();
            StringTokenizer tokenizer = new StringTokenizer(properties, "=;");
            while (tokenizer.hasMoreElements()) {
                String key = tokenizer.nextToken();
                String value = tokenizer.nextToken();
                info.put(key, value);
            }

            if (driver != null)
                Class.forName(driver);
            log("Connecting to database...");
            connection = DriverManager.getConnection(url, info);
        } catch (Exception e) {
            throw new BuildException(e);
        }
    }

    /**
     * The map contains: String key -> File filename
     */
    protected void getKeysFromDirectory(File dir, Map keys) throws BuildException {
        try {
            File[] files = dir.listFiles();
            if (files == null) {
                //  dir is a file
                keys.put(pathToKey(dir.getCanonicalPath()), dir);
            } else {
                for (int i = 0; i < files.length; i++) {
                    if (files[i].isDirectory()) {
                        getKeysFromDirectory(files[i], keys);
                    } else {
                        keys.put(pathToKey(files[i].getCanonicalPath()), files[i]);
                    }
                }
            }
        } catch (IOException e) {
            throw new BuildException(e);
        }
    }

    protected String pathToKey(String path) throws IOException {
        StringBuffer buff = new StringBuffer(path);

        // remove parent dir
        buff.delete(0, new File(getInDir()).getCanonicalPath().toString().length());

        // convert \ to /
        int i = 0;
        while ((i = buff.toString().indexOf('\\', i)) != -1) {
            buff.replace(i, i + 1, "/");
            i++;
        }
        // remove suffix (.xml)
        //String key = buff.substring(0, buff.length() - SUFFIX.length());

        return buff.toString();
    }

    protected void importData(Map keys) throws BuildException {

        log("Importing Data...");
        try {
            try {

                Statement stmt = connection.createStatement();
                stmt.executeUpdate("TRUNCATE TABLE " + getTable());
                stmt.close();

                for (Iterator i = keys.keySet().iterator(); i.hasNext();) {
                    String key = (String) i.next();
                    File file = (File) keys.get(key);

                    stmt = connection.createStatement();
                    StringBuffer sqlInsert = new StringBuffer();
                    sqlInsert.append("INSERT INTO ").append(getTable());
                    sqlInsert.append(" (");
                    sqlInsert.append(KEY_NAME).append(',');
                    sqlInsert.append(TIMESTAMP_NAME).append(',').append(XML_NAME);
                    sqlInsert.append(") VALUES (");
                    sqlInsert.append("'").append(key).append("'");
                    sqlInsert.append(",sysdate, empty_clob())");

                    stmt.executeUpdate(sqlInsert.toString());

                    connection.setAutoCommit(false);
                    StringBuffer sqlSelect = new StringBuffer();
                    sqlSelect.append("SELECT ").append(XML_NAME);
                    sqlSelect.append(" FROM ").append(getTable());
                    sqlSelect.append(" WHERE ").append(KEY_NAME);
                    sqlSelect.append("='").append(key).append("'");
                    sqlSelect.append(" FOR UPDATE");

                    ResultSet rs = connection.createStatement().executeQuery(sqlSelect.toString());
                    if (rs.next()) {
                        FileInputStream in = new FileInputStream(file);

                        oracle.sql.CLOB clob = ((OracleResultSet) rs).getCLOB(1);
                        OutputStream out = clob.getAsciiOutputStream();
                        byte[] buff = new byte[clob.getBufferSize()];
                        int length = -1;
                        while ((length = in.read(buff)) != -1)
                            out.write(buff, 0, length);
                        in.close();
                        out.close();
                    }
                }
            } finally {
                if (connection != null) connection.commit();
            }
        } catch (Exception e) {
            throw new BuildException(e);
        }

    }
}
