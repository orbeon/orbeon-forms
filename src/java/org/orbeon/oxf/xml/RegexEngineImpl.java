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
package org.orbeon.oxf.xml;

import com.thaiopensource.datatype.xsd.regex.Regex;
import com.thaiopensource.datatype.xsd.regex.RegexEngine;
import com.thaiopensource.datatype.xsd.regex.RegexSyntaxException;
import orbeon.apache.xerces.impl.xpath.regex.ParseException;
import orbeon.apache.xerces.impl.xpath.regex.RegularExpression;

/**
 * An implementation of <code>RegexEngine</code> using the Xerces 2 regular
 * expression implementation.
 */
public class RegexEngineImpl implements RegexEngine {
    public RegexEngineImpl() {
        // Force a linkage error on instantiation if the Xerces classes
        // are not available.
        try {
            new RegularExpression("", "X");
        }
        catch (ParseException e) {
        }
    }
    public Regex compile(String expr) throws RegexSyntaxException {
        try {
            final RegularExpression re = new RegularExpression(expr, "X");
            return new Regex() {
                public boolean matches(String str) {
                    return re.matches(str);
                }
            };
        }
        catch (ParseException e) {
            throw new RegexSyntaxException(e.getMessage(), e.getLocation());
        }
    }
}
