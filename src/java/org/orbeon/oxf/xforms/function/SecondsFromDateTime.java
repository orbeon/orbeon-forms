/**
 *  Copyright (C) 2005 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.function;

import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.value.DateTimeValue;
import org.orbeon.saxon.value.IntegerValue;
import org.orbeon.saxon.value.StringValue;
import org.orbeon.saxon.xpath.XPathException;

public class SecondsFromDateTime extends XFormsFunction {

    private static final StringValue NAN = new StringValue("NaN");

    
    public Item evaluateItem(XPathContext context) throws XPathException {
        String arg = argument[0].evaluateAsString(context);
        DateTimeValue value = null;
        try {
            value = new DateTimeValue(arg);
        } catch (XPathException e) {
            return NAN;
        }
        return new IntegerValue(value.getUTCDate().getTime()/1000);
    }

}
