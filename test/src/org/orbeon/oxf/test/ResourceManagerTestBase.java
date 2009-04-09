/**
 *  Copyright (C) 2009 Orbeon, Inc.
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
package org.orbeon.oxf.test;

import junit.framework.TestCase;
import org.orbeon.oxf.resources.ResourceManagerWrapper;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;


public abstract class ResourceManagerTestBase extends TestCase {

	// See also ProcessorTest
    static {
		// Setup resource manager
		final Map props = new HashMap();
		final java.util.Properties properties = System.getProperties();
		for (Enumeration e = properties.propertyNames(); e.hasMoreElements();) {
			final String name = (String) e.nextElement();
			if (name.startsWith("oxf.resources."))
				props.put(name, properties.getProperty(name));
		}
		ResourceManagerWrapper.init(props);
        // Initialize properties
		org.orbeon.oxf.properties.Properties.init("oxf:/ops/unit-tests/properties.xml");
	}
}
