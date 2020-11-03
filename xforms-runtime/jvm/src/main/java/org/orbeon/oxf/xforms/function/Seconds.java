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
package org.orbeon.oxf.xforms.function;

import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.functions.Component;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.trans.XPathException;
import org.orbeon.saxon.value.*;
import org.orbeon.saxon.value.StringValue;

public class Seconds extends XFormsFunction {

    private static final StringValue NAN = new StringValue("NaN");

    private static final int MINUTE_COEF = 60;
    private static final int HOUR_COEF = 60 * 60;
    private static final int DAY_COEF = 60 * 60 * 24;


    public Item evaluateItem(XPathContext context) throws XPathException {
        String arg = argument[0].evaluateAsString(context).toString();
        final DurationValue value;
        try {
            value = (DurationValue) DurationValue.makeDuration(arg).asAtomic();
        } catch (XPathException e) {
            return NAN;
        }
        // TODO: should be a decimal, like xf:seconds-from-dateTime
        return new DoubleValue(getLengthInSeconds(value));
    }

    private double getLengthInSeconds(DurationValue value) throws XPathException {
        double a = ((IntegerValue)value.getComponent(Component.DAY)).getDoubleValue() * DAY_COEF;
        a += ((IntegerValue)value.getComponent(Component.HOURS)).getDoubleValue() * HOUR_COEF;
        a += ((IntegerValue)value.getComponent(Component.MINUTES)).getDoubleValue() * MINUTE_COEF;
        a += ((DecimalValue)value.getComponent(Component.SECONDS)).getDoubleValue();

        return a;
    }
}
