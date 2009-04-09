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
import org.orbeon.oxf.xforms.control.XFormsSingleNodeControl;
import org.orbeon.oxf.xforms.control.controls.XFormsInputControl;
import org.orbeon.oxf.xforms.processor.OldControlsComparator;
import org.xml.sax.helpers.AttributesImpl;

import java.util.LinkedHashMap;
import java.util.Map;

public class XFormsControlsTest extends TestCase {

    public void testDiffCustomMIPsChanges() {

        final AttributesImpl atts = new AttributesImpl();
        final XFormsSingleNodeControl control1 = new XFormsInputControl(null, null, null, "input", "input-1") {

            private Map customMIPs = new LinkedHashMap();
            {
                customMIPs.put("name1", "value1");
                customMIPs.put("name2", "value2");
                customMIPs.put("name3", "value3");
                customMIPs.put("name4", "value4");
            }

            public Map getCustomMIPs() {
                return customMIPs;
            }
        };

        final XFormsSingleNodeControl control2 = new XFormsInputControl(null, null, null, "input", "input-1") {
            private Map customMIPs = new LinkedHashMap();
            {
                // leave as is
                customMIPs.put("name1", "value1");
                // remove name2
                // change value
                customMIPs.put("name3", "newvalue3");
                // leave as is
                customMIPs.put("name4", "value4");
            }

            public Map getCustomMIPs() {
                return customMIPs;
            }
        };

        OldControlsComparator.diffCustomMIPs(atts, control1, control2, false, false);
        assertEquals("-name2-value2 -name3-value3 +name3-newvalue3", atts.getValue("class"));
    }

    public void testDiffCustomMIPsNew() {

        final AttributesImpl atts = new AttributesImpl();

        final XFormsSingleNodeControl control2 = new XFormsInputControl(null, null, null, "input", "input-1") {
            private Map customMIPs = new LinkedHashMap();
            {
                customMIPs.put("name1", "value1");
                customMIPs.put("name2", "value2");
                customMIPs.put("name3", "value3");
                customMIPs.put("name4", "value4");
            }

            public Map getCustomMIPs() {
                return customMIPs;
            }
        };

        OldControlsComparator.diffCustomMIPs(atts, null, control2, false, false);
        assertEquals("name1-value1 name2-value2 name3-value3 name4-value4", atts.getValue("class"));
    }
}
