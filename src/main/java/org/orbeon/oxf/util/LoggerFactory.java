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
package org.orbeon.oxf.util;

import org.apache.log4j.Logger;

public class LoggerFactory {

    public static final String LOG4J_DOM_CONFIG_PROPERTY = "oxf.log4j-config";

    static
    {
    	// 11-22-2004 d : Current log4j tries to load a default config. This is
    	//                why we are seeing a message about a log4j.properties being
    	//                loaded from the Axis jar.
    	//                Since this isn't a behaviour we want we hack around it by
    	//                specifying a file that doesn't exist.
        // 2008-05-05 a : It is clear if this solves a problem with an older version of
        //                Axis we were shipping with Orbeon Forms back in 2004, or a more
        //                complex interaction with a particular application server.
        //                We don't think this is relevant anymore and are commenting this out.
        //                Also see this thread on ops-users:
        //                http://www.nabble.com/Problem-with-log-in-orbeon-with-multiple-webapp-tt16932990.html

        // System.setProperty( "log4j.configuration", "-there-aint-no-such-file-" );
    }

    public static final Logger logger = LoggerFactory.createLogger(LoggerFactory.class);

    public static Logger createLogger(String name) {
        return Logger.getLogger(name);
    }

    public static Logger createLogger(Class clazz) {
        return Logger.getLogger(clazz.getName());
    }
}
