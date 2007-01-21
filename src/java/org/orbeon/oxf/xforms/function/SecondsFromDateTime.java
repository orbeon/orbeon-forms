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

import org.orbeon.oxf.common.OXFException;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.trans.XPathException;
import org.orbeon.saxon.value.DateTimeValue;
import org.orbeon.saxon.value.IntegerValue;
import org.orbeon.saxon.value.StringValue;

import java.math.BigDecimal;

public class SecondsFromDateTime extends XFormsFunction {

    public static final StringValue NAN = new StringValue("NaN");
    public static final BigDecimal BASELINE;

    static {
        try {
            BASELINE = new DateTimeValue("1970-01-01T00:00:00Z").toJulianInstant();
        } catch (XPathException e) {
            throw new OXFException(e);
        }
    }

    public Item evaluateItem(XPathContext context) throws XPathException {
        final String arg = argument[0].evaluateAsString(context);
        try {
            final DateTimeValue value = new DateTimeValue(arg);

            // "the return value is equal to the number of seconds difference between the specified dateTime
            // (normalized to UTC) and 1970-01-01T00:00:00Z"
            return new IntegerValue(value.normalize(context.getConfiguration()).toJulianInstant().subtract(BASELINE).longValue());

        } catch (XPathException e) {
            return NAN;
        }
    }
}
