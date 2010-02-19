/**
 * Copyright (C) 2010 Orbeon, Inc.
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
package org.orbeon.oxf.test;

import junit.framework.TestCase;
import org.orbeon.oxf.xforms.control.controls.XFormsInputControl;


public class DateTimeTest extends TestCase {

    protected void setUp() throws Exception {
    }

    public void testTimeParsing() {

        // Test PM time parsing
        final String[] pmSuffixes = new String[] { "p.m.", "pm", "p" };
        for (int i = 0; i < pmSuffixes.length; i++) {
            final String pmSuffix = pmSuffixes[i];

            assertEquals("15:34:56", XFormsInputControl.testParseTime("3:34:56 " + pmSuffix));
            assertEquals("15:34:00", XFormsInputControl.testParseTime("3:34 " + pmSuffix));
            assertEquals("12:00:00", XFormsInputControl.testParseTime("12 " + pmSuffix));
            assertEquals("15:00:00", XFormsInputControl.testParseTime("3 " + pmSuffix));
            // 0 pm is rarely used
            assertEquals("12:00:00", XFormsInputControl.testParseTime("0 " + pmSuffix));
        }

        // Test AM time parsing
        final String[] amSuffixes = new String[] { "a.m.", "am", "a", "" };
        for (int i = 0; i < amSuffixes.length; i++) {
            final String amSuffix = amSuffixes[i];

            assertEquals("03:34:56", XFormsInputControl.testParseTime("3:34:56 " + amSuffix));
            assertEquals("03:34:00", XFormsInputControl.testParseTime("3:34 " + amSuffix));
            // 12 am is rarely used
            assertEquals("12:00:00", XFormsInputControl.testParseTime("12 " + amSuffix));
            assertEquals("03:00:00", XFormsInputControl.testParseTime("3 " + amSuffix));
            assertEquals("00:00:00", XFormsInputControl.testParseTime("0 " + amSuffix));
        }
    }

    public void testDateParsing() {
        assertEquals("2010-02-19", XFormsInputControl.testParseDate("2/19/2010"));
        assertEquals("2010-02-19", XFormsInputControl.testParseDate("02/19/2010"));
        assertEquals("2010-02-19", XFormsInputControl.testParseDate("19.2.2010"));
        assertEquals("2010-02-19", XFormsInputControl.testParseDate("19.02.2010"));

        assertEquals("2010-02-19", XFormsInputControl.testParseDate("2010-02-19-08:00"));
        assertEquals("2010-02-19", XFormsInputControl.testParseDate("2010-02-19+08:00"));
        assertEquals("2010-02-19", XFormsInputControl.testParseDate("2010-02-19Z"));
        assertEquals("2010-02-19", XFormsInputControl.testParseDate("2010-02-19"));
    }
}
