/**
 *  Copyright (C) 2008 Orbeon, Inc.
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
import org.orbeon.oxf.properties.PropertySet;
import org.orbeon.oxf.properties.PropertyStore;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;


public class PropertiesTest extends TestCase {

    private PropertyStore propertyStore;

    private static final String PROPERTIES
            = "<properties xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n" +
            "    <property as=\"xs:string\"  name=\"test.orbeon.builder.form\"  value=\"value0\"/>\n" +
            "    <property as=\"xs:string\"  name=\"test.*.builder.form\"  value=\"value1\"/>\n" +
            "    <property as=\"xs:string\"  name=\"test.orbeon.*.form\"  value=\"value2\"/>\n" +
            "    <property as=\"xs:string\"  name=\"test.*.*.form\"  value=\"value3\"/>\n" +
            "    <property as=\"xs:string\"  name=\"test.orbeon.builder.*\"  value=\"value4\"/>\n" +
            "    <property as=\"xs:string\"  name=\"test.*.builder.*\"  value=\"value5\"/>\n" +
            "    <property as=\"xs:string\"  name=\"test.orbeon.*.*\"  value=\"value6\"/>\n" +
            "    <property as=\"xs:string\"  name=\"test.*.*.*\"  value=\"value7\"/>\n" +
            "</properties>";

    protected void setUp() throws Exception {
        propertyStore = new PropertyStore(Dom4jUtils.readDom4j(PROPERTIES));
    }

    public void testWildcardMatches() {
        final PropertySet propertySet = propertyStore.getGlobalPropertySet();

        // Try to find one match per property
        assertEquals(propertySet.getString("test.orbeon.builder.form"), "value0");
        assertEquals(propertySet.getString("test.foo.builder.form"), "value1");
        assertEquals(propertySet.getString("test.orbeon.foo.form"), "value2");
        assertEquals(propertySet.getString("test.foo.bar.form"), "value3");
        assertEquals(propertySet.getString("test.orbeon.builder.foo"), "value4");
        assertEquals(propertySet.getString("test.foo.builder.bar"), "value5");
        assertEquals(propertySet.getString("test.orbeon.foo.bar"), "value6");
        assertEquals(propertySet.getString("test.foo.bar.bat"), "value7");
    }

    public void testExactMatches() {
        final PropertySet propertySet = propertyStore.getGlobalPropertySet();

        // Ensure that exact matches are handled first (you would expect that wildcards would work here too, but for
        // now they don't work by design ;)
        assertEquals(propertySet.getString("test.orbeon.builder.form"), "value0");
        assertEquals(propertySet.getString("test.*.builder.form"), "value1");
        assertEquals(propertySet.getString("test.orbeon.*.form"), "value2");
        assertEquals(propertySet.getString("test.*.*.form"), "value3");
        assertEquals(propertySet.getString("test.orbeon.builder.*"), "value4");
        assertEquals(propertySet.getString("test.*.builder.*"), "value5");
        assertEquals(propertySet.getString("test.orbeon.*.*"), "value6");
        assertEquals(propertySet.getString("test.*.*.*"), "value7");
    }
}
