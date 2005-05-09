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
import org.orbeon.saxon.value.*;
import org.orbeon.saxon.xpath.XPathException;

public class DaysFromDate extends XFormsFunction {

    private static final StringValue NAN = new StringValue("NaN");
    private static final int coefficient = (1000 * 60 * 60 * 24);


    public Item evaluateItem(XPathContext context) throws XPathException {
        String arg = argument[0].evaluateAsString(context);
        CalendarValue value = null;
        try {
            value = new DateTimeValue(arg);
        } catch (XPathException e) {
            try {
                value = new DateValue(arg);
            } catch (XPathException ee) {
            }
        }
        if(value == null)
            return NAN;
        else {
            long l = 0;
            if(value instanceof DateTimeValue)
                l = ((DateTimeValue)value).getUTCDate().getTime();
            else
                l = ((DateValue)value).getUTCDate().getTime();
            return new IntegerValue(l/coefficient);
        }


    }

}
