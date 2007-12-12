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
package org.orbeon.oxf.processor.tamino;

import com.softwareag.tamino.db.api.accessor.TAccessLocation;
import com.softwareag.tamino.db.api.connection.*;
import org.apache.log4j.Logger;
import org.dom4j.Document;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.xml.XPathUtils;

import java.util.HashMap;
import java.util.Map;

public abstract class TaminoProcessor extends ProcessorImpl {

    protected static final Logger logger = LoggerFactory.createLogger(TaminoProcessor.class);
    
    private static final Map ISOLATION_DEGREE_VALUES = new HashMap();
    private static final Map LOCK_MODE_VALUES = new HashMap();
	private static final Map LOCK_WAIT_VALUES = new HashMap();
    private static final String ISOLATION_DEGREE_PROPERTY = "oxf.tamino.isolation-degree";
    private static final String LOCK_MODE_PROPERTY = "oxf.tamino.lock-mode";
	private static final String LOCK_WAIT_PROPERTY = "oxf.tamino.lock-wait";

    public static final String TAMINO_CONFIG_URI = "http://www.orbeon.org/oxf/tamino-config";
    public static final String TAMINO_QUERY_URI = "http://www.orbeon.org/oxf/tamino-query";

    public static final String URL_PROPERTY = "url";
    public static final String USERNAME_PROPERTY = "username";
    public static final String PASSWORD_PROPERTY = "password";

    protected static final String TAMINO_CONNECTION = TaminoProcessor.class.getName() + "_connection_";

    static {
        ISOLATION_DEGREE_VALUES.put("committedCommand", TIsolationDegree.COMMITTED_COMMAND);
        ISOLATION_DEGREE_VALUES.put("serializable", TIsolationDegree.SERIALIZABLE);
        ISOLATION_DEGREE_VALUES.put("stableCursor", TIsolationDegree.STABLE_CURSOR);
        ISOLATION_DEGREE_VALUES.put("stableDocument", TIsolationDegree.STABLE_DOCUMENT);
        ISOLATION_DEGREE_VALUES.put("uncommittedDocument", TIsolationDegree.UNCOMMITTED_DOCUMENT);
        LOCK_MODE_VALUES.put("protected", TLockMode.PROTECTED);
        LOCK_MODE_VALUES.put("shared", TLockMode.SHARED);
        LOCK_MODE_VALUES.put("unprotected", TLockMode.UNPROTECTED);
		LOCK_WAIT_VALUES.put("yes", TLockwaitMode.YES);
		LOCK_WAIT_VALUES.put("no", TLockwaitMode.NO);
    }

    protected Config readConfig(Document doc) {
        Config config = new Config();

        // Try local configuration first
        String url = XPathUtils.selectStringValueNormalize(doc, "/config/url");
        String username = XPathUtils.selectStringValueNormalize(doc, "/config/username");
        String password = XPathUtils.selectStringValueNormalize(doc, "/config/password");
		String isolationDegree = XPathUtils.selectStringValueNormalize(doc, "/config/isolation-degree");
		String lockMode = XPathUtils.selectStringValueNormalize(doc, "/config/lock-mode");
		String lockWait = XPathUtils.selectStringValueNormalize(doc, "/config/lock-wait");

        // Override with properties if needed
        config.setUrl(url != null ? url : getPropertySet().getString(URL_PROPERTY));
        config.setUsername(username != null ? username : getPropertySet().getString(USERNAME_PROPERTY));
        config.setPassword(password != null ? password : getPropertySet().getString(PASSWORD_PROPERTY));
		// Set isolation and locking properties, override from global if set
        config.setIsolationDegree(isolationDegree != null ? isolationDegree : getPropertySet().getString(ISOLATION_DEGREE_PROPERTY));
		config.setLockMode(lockMode != null ? lockMode : getPropertySet().getString(LOCK_MODE_PROPERTY));
		config.setLockWait(lockWait != null ? lockWait : getPropertySet().getString(LOCK_WAIT_PROPERTY));

        config.setCollection(TAccessLocation.newInstance(XPathUtils.selectStringValueNormalize(doc, "/config/collection")));

        return config;
    }

    protected TConnection getConnection(PipelineContext context, Config config) {
        String attributeName = TAMINO_CONNECTION + config.getUrl() + config.getUsername();
        TConnection connection = (TConnection) context.getAttribute(attributeName);
        if (connection != null && !connection.isClosed()) {
            return connection;
        } else {
            try {
                final TConnection newConnection = TConnectionFactory.getInstance().newConnection(config.getUrl(),
                        config.getUsername(), config.getPassword());

                // Initialize isolation degree and lock mode as set in properties
                {
                    //OXFProperties.PropertySet propertySet = OXFProperties.instance().getPropertySet();
                    TIsolationDegree isolationDegree = (TIsolationDegree) ISOLATION_DEGREE_VALUES.get(config.getIsolationDegree());
                            //ISOLATION_DEGREE_VALUES.get(propertySet.getString(ISOLATION_DEGREE_PROPERTY));
					TLockMode lockMode = (TLockMode) LOCK_MODE_VALUES.get(config.getLockMode());
                            //LOCK_MODE_VALUES.get(propertySet.getString(LOCK_MODE_PROPERTY));
					TLockwaitMode lockWait = (TLockwaitMode)LOCK_WAIT_VALUES.get(config.getLockWait());
					if (logger.isDebugEnabled()) {
						logger.debug("Isolation Degree: " + config.getIsolationDegree() +
									" Locking Mode: " + config.getLockMode() +
									" Lock Wait Mode: " + config.getLockWait());
					}
					/* TEST LOGGING VALUES */
                    if (isolationDegree != null)
                        newConnection.setIsolationDegree(isolationDegree);
                    if (lockMode != null)
                        newConnection.setLockMode(lockMode);
					if (lockWait != null)
						newConnection.setLockwaitMode(lockWait);
                }

                final TLocalTransaction transaction = newConnection.useLocalTransactionMode();

                context.setAttribute(attributeName, newConnection);
                context.addContextListener(new PipelineContext.ContextListenerAdapter() {
                    public void contextDestroyed(boolean success) {
                        try {
                            if (success) {
                                if(logger.isInfoEnabled())
                                    logger.info("Committing Tamino transaction");
                                transaction.commit();
                            } else {
                                if(logger.isInfoEnabled())
                                    logger.info("Rolling back Tamino transaction");
                                transaction.rollback();
                            }
                        } catch (TTransactionException e) {
                            throw new OXFException(e);
                        } finally {
                            if (!newConnection.isClosed())
                                try {
                                    newConnection.close();
                                } catch (TConnectionCloseException e) {
                                    throw new OXFException(e);
                                }
                        }
                    }
                });
                return newConnection;
            } catch (Exception e) {
                throw new OXFException(e);
            }
        }
    }


    protected static class Config {
        private TAccessLocation collection;
        private String url;
        private String username;
        private String password;
		private String isolationDegree;
		private String lockMode;
		private String lockWait;

        public TAccessLocation getCollection() {
            return collection;
        }

        public void setCollection(TAccessLocation collection) {
            this.collection = collection;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String toString() {
            return url + collection.getCollection() + username;
        }

		// New configuration controls for isolation and locking

		public String getIsolationDegree()
		{
			return isolationDegree;
		}

		public void setIsolationDegree(String isolationDegree)
		{
			this.isolationDegree = isolationDegree;
		}

		public String getLockMode()
		{
			return lockMode;
		}

		public void setLockMode(String lockMode)
		{
			this.lockMode = lockMode;
		}

		public String getLockWait()
		{
			return lockWait;
		}

		public void setLockWait(String lockWait)
		{
			this.lockWait = lockWait;
		}
    }
}

