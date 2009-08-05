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
package org.orbeon.oxf.xforms.function;

import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.trans.XPathException;
import org.orbeon.saxon.value.CalendarValue;
import org.orbeon.saxon.value.DateTimeValue;
import org.orbeon.saxon.value.DateValue;
import org.orbeon.saxon.value.IntegerValue;

public class DaysFromDate extends XFormsFunction {

    private static final int SECONDS_PER_DAY = (60 * 60 * 24);

    public Item evaluateItem(XPathContext context) throws XPathException {

        // "If the string parameter represents a legal lexical xsd:date or xsd:dateTime"...
        final String arg = argument[0].evaluateAsString(context);
        CalendarValue value = null;
        try {
            value = new DateTimeValue(arg);
        } catch (XPathException e1) {// FIXME: handling this with exceptions may be slow
            try {
                value = new DateValue(arg);
            } catch (XPathException e2) {// FIXME: handling this with exceptions may be slow
            }
        }

        // "Any other input parameter causes a return value of NaN."
        if(value == null)
            return SecondsFromDateTime.NAN;

        else {

            // "the return value is equal to the number of days difference between the specified date or dateTime
            // (normalized to UTC) and 1970-01-01. Hour, minute, and second components are ignored after
            // normalization."

            final DateTimeValue dateTimeValue = (value instanceof DateTimeValue) ? (DateTimeValue) value : value.toDateTime();
            return new IntegerValue(dateTimeValue.normalize(context.getConfiguration()).toJulianInstant().subtract(SecondsFromDateTime.BASELINE).longValue() / SECONDS_PER_DAY);
        }
    }
}
