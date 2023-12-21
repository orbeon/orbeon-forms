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
package org.orbeon.oxf.processor;

import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.externalcontext.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.util.LoggerFactory;
import org.sqlite.SQLiteDataSource;
import scala.Option;

import javax.naming.InitialContext;
import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import javax.sql.DataSource;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represent a database context for processors using SQL connections.
 */
public class DatabaseContext {

    private static final org.slf4j.Logger logger = LoggerFactory.createLoggerJava(DatabaseContext.class);
    public static final String DATASOURCE_CONTEXT = "datasource-context"; // used by DatabaseContext

    private static final String SQLITE_WAR_PATH = "WEB-INF/orbeon-demo.sqlite";

    private static Boolean fileExists(final String nonNullPath, final String warningIfNotExists) {
        if (! new File(nonNullPath).exists()) {
            logger.warn(warningIfNotExists);
            return false;
        } else {
            return true;
        }
    }

    public static DataSource fallbackDataSource(ExternalContext nullableExternalContext, final String jndiName) throws NamingException {
        if (jndiName.equals("sqlite") || jndiName.equals("jdbc/sqlite")) {
            // TODO: We depend here on concepts we shouldn't know about (e.g. WebAppContext, WAR file, etc.). This should be cleaned up.
            String sqliteFilePathFromRealPath =
                Option
                    .apply    (nullableExternalContext)
                    .flatMap  (webAppContext   -> Option.apply(webAppContext.getWebAppContext().getRealPath(SQLITE_WAR_PATH)))
                    .filter   (path            -> ! path.trim().isEmpty())
                    .filter   (path            -> fileExists(path, "File '" + path + "' from WAR file does not exist"))
                    .getOrElse(()              -> null);

            if (sqliteFilePathFromRealPath != null) {
                logger.info("Using SQLite database from WAR: " + sqliteFilePathFromRealPath);
            } else {
                throw new NameNotFoundException("Data source not found for '" + jndiName + "'");
            }

            SQLiteDataSource dataSource = new SQLiteDataSource();
            dataSource.setUrl("jdbc:sqlite:" + sqliteFilePathFromRealPath);
            return dataSource;
        } else {
            throw new NameNotFoundException("Data source not found for '" + jndiName + "'");
        }
    }

    /**
     * Get a connection valid for this pipeline execution, given a JDBC JNDI name.
     * The returned connection must not be closed by the user.
     */
    public static Connection getConnection(PipelineContext pipelineContext, final String jndiName) {
        // Try to obtain connection from context
        // We used to synchronize on DatabaseContext.class here, but this should not be necessary since pipelineContext
        // is only used by one thread at a time.
        Connection connection = (Connection) getContext(pipelineContext).connections.get(jndiName);
        if (connection == null) {
            try {
                // Create connection from datasource
                javax.naming.Context initialContext = new InitialContext();

                // Workaround for WildFly (TODO: do we really need it?)
                DataSource ds = null;
                List<String> prefixesToTry = Arrays.asList("java:comp/env/", "java:/");
                for (String prefix : prefixesToTry) {
                    try {
                        ds = (DataSource) initialContext.lookup(prefix + jndiName);
                        if (ds != null) break;
                    } catch (NamingException e) {
                        // NOP: we'll try the next prefix
                    }
                }
                if (ds == null) {

                    ds = fallbackDataSource((ExternalContext) pipelineContext.getAttribute(PipelineContext.EXTERNAL_CONTEXT), jndiName);
                }

                Connection newConnection = ds.getConnection();
                setConnectionProperties(newConnection, pipelineContext, jndiName);
                getContext(pipelineContext).connections.put(jndiName, newConnection);

                connection = newConnection;
            } catch (OXFException e) {
                throw e;
            } catch (Exception e) {
                throw new OXFException(e);
            }
        }
        return connection;
    }

    /**
     * Get a connection valid for this pipeline execution, given a Datasource object.
     *
     * The returned connection must not be closed by the user.
     *
     * @param pipelineContext  current pipeline context
     * @param datasource       Datasource object
     * @return                 Connection object
     */
    public static Connection getConnection(PipelineContext pipelineContext, Datasource datasource) {
        // Try to obtain connection from context
        Connection connection = (Connection) getContext(pipelineContext).connections.get(datasource.toString());
        if (connection == null) {
            synchronized (DatabaseContext.class) {
                connection = (Connection) getContext(pipelineContext).connections.get(datasource.toString());
                if (connection == null) {
                    // Create connection
                    try {
                        Class.forName(datasource.getDriverClassName());
                    } catch (ClassNotFoundException e) {
                        throw new OXFException("Cannot load JDBC driver for class: " + datasource.getDriverClassName());
                    }
                    Connection newConnection;
                    try {
                        newConnection = DriverManager.getConnection(datasource.getUri(), datasource.getUsername(), datasource.getPassword());
                    } catch (SQLException e) {
                        throw new OXFException("Cannot get connection from JDBC DriverManager for datasource: " + datasource, e);
                    }

                    // Set connection properties
                    try {
                        setConnectionProperties(newConnection, pipelineContext, datasource.toString());
                    } catch (Exception e) {
                         throw new OXFException(e);
                    }
                    // Save connection into context
                    getContext(pipelineContext).connections.put(datasource.toString(), newConnection);

                    connection = newConnection;
                }
            }
        }

        return connection;
    }

    private static void setConnectionProperties(final Connection connection, PipelineContext pipelineContext, final String datasourceName) throws SQLException {
        // Set connection properties
        connection.setAutoCommit(false);
        // Commit or rollback when context is destroyed
        pipelineContext.addContextListener(new PipelineContext.ContextListenerAdapter() {
            public void contextDestroyed(boolean success) {
                try {
                    if (success) {
                        logger.info("Committing JDBC connection for datasource: " + datasourceName + ".");
                        connection.commit();
                        connection.close();
                    } else {
                        logger.info("Rolling back JDBC connection for datasource: " + datasourceName + ".");
                        connection.rollback();
                        connection.close();
                    }
                } catch (SQLException e) {
                    throw new OXFException(e);
                }
            }
        });
    }

    private static Context getContext(PipelineContext pipelineContext) {
        Context context = (Context) pipelineContext.getAttribute(DATASOURCE_CONTEXT);
        if (context == null) {
            context = new Context();
            pipelineContext.setAttribute(DATASOURCE_CONTEXT, context);
        }
        return context;
    }

    private static class Context {
        // Map datasource to connections
        public Map connections = new HashMap();
    }
}
