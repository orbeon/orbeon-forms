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
import org.orbeon.saxon.functions.Component;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.trans.XPathException;
import org.orbeon.saxon.value.DurationValue;
import org.orbeon.saxon.value.IntegerValue;
import org.orbeon.saxon.value.StringValue;

public class Months extends XFormsFunction {

    private static final StringValue NAN = new StringValue("NaN");

    private static final int YEAR_COEF = 12;

    public Item evaluateItem(XPathContext context) throws XPathException {
        String arg = argument[0].evaluateAsString(context);
        final DurationValue value;
        try {
            value = new DurationValue(arg);
        } catch (XPathException e) {
            return NAN;
        }
        return new IntegerValue(Math.round(getLengthInMonths(value)));
    }

    private double getLengthInMonths(DurationValue value) throws XPathException {
        double a = ((IntegerValue)value.getComponent(Component.YEAR)).getDoubleValue() * YEAR_COEF;
        a += ((IntegerValue)value.getComponent(Component.MONTH)).getDoubleValue();

        return a;
    }
}
