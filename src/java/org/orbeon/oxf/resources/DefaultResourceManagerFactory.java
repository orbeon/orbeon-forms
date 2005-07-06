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

import org.apache.log4j.Logger;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.util.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;

/**
 * This is the default resource manager factory, which creates a database-backed
 * resource manager initialized with a standard JDBC URL, username and password.
 */
public class DefaultResourceManagerFactory implements ResourceManagerFactoryFunctor {

    private static Logger logger = LoggerFactory.createLogger(DefaultResourceManagerFactory.class);

    public static final String JDBC_URL = "org.orbeon.oxf.resources.DBResourceManagerFactory.jdbcUrl";
    public static final String JDBC_DRIVER = "org.orbeon.oxf.resources.DBResourceManagerFactory.driver";
    public static final String JDBC_USERNAME = "org.orbeon.oxf.resources.DBResourceManagerFactory.username";
    public static final String JDBC_PASSWORD = "org.orbeon.oxf.resources.DBResourceManagerFactory.password";
    public static final String USE_CACHE = "org.orbeon.oxf.resources.FilesystemResourceManagerFactory.useCache";

    private String jdbcUrl = null;
    private Properties info = null;
    private String driver = null;
    private Connection connection = null;
    private Map props;

    public DefaultResourceManagerFactory(Map props) throws OXFException {
        this.props = props;

        String url = (String) props.get(JDBC_URL);
        String driver = (String) props.get(JDBC_DRIVER);
        String username = (String) props.get(JDBC_USERNAME);
        String password = (String) props.get(JDBC_PASSWORD);

        if (url == null || driver == null || username == null || password == null)
            throw new OXFException("Properties " + JDBC_URL + ", " + JDBC_DRIVER + ", " +
                    JDBC_USERNAME + ", or " + JDBC_PASSWORD + " are null");
        this.jdbcUrl = url;
        this.driver = driver;
        this.info = new Properties();
        this.info.setProperty("user", username);
        this.info.setProperty("password", password);
    }


    public DefaultResourceManagerFactory(String jdbcUrl, Properties info, String driver) {
        this.jdbcUrl = jdbcUrl;
        this.info = info;
        this.driver = driver;
    }


    public void setJdbcUrl(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    public void setInfo(Properties info) {
        this.info = info;
    }

    public void setDriver(String driver) {
        this.driver = driver;
    }

    public ResourceManager makeInstance() {
        try {
            if (connection == null || connection.isClosed()) {
                if (driver != null)
                    Class.forName(driver);
                this.connection = DriverManager.getConnection(jdbcUrl, info);
            }
        } catch (ClassNotFoundException e) {
            logger.fatal("Class Not Found: " + driver, e);
            throw new OXFException("Class Not Found: " + driver, e);
        } catch (SQLException e) {
            logger.fatal("Can't connect to DB", e);
            throw new OXFException("Can't connect to DB", e);
        }
        return new DBResourceManagerImpl(props, connection);
    }
}
