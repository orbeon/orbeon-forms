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

import org.orbeon.oxf.common.OXFException;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import java.util.Map;
import java.util.Properties;

/**
 * This class creates adatabase-backed resource manager, initialized with a JDBC
 * 2.0 datasource. This is the prefered way in a J2EE application server
 * environment.
 */
public class DataSourceResourceManagerFactory implements ResourceManagerFactoryFunctor {

    public static final String DATASOURCE_PROP = "org.orbeon.oxf.resources.DataSourceResourceManagerFactory.datasource";

    protected DataSource ds = null;

    public DataSourceResourceManagerFactory(Map props, Context jndiContext) {
        String dsName = (String) props.get(DATASOURCE_PROP);
        if (dsName == null) {
            throw new OXFException("Properties " + DATASOURCE_PROP +
                    " is missing or null");
        }
        try {
            this.ds = (DataSource) jndiContext.lookup(dsName);
        } catch (Exception e) {
            throw new OXFException(e);
        }
        if (ds == null) {
            throw new OXFException(DATASOURCE_PROP + " does not refer to a valid DataSource");
        }

    }

    public DataSourceResourceManagerFactory(Properties props) throws NamingException {
        this(props, new InitialContext());
    }

    public ResourceManager makeInstance() {
        return new DataSourceResourceManagerImpl(ds);
    }
}
