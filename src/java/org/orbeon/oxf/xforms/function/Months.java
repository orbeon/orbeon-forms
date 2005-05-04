/*
 * Copyright 1998-2002, Orbeon, Inc.
 * All Rights Reserved
 *
 * This is unpublished proprietary source code of Orbeon, Inc. The
 * copyright notice above does not evidence any actual or intended
 * publication of such source code.
 */
package org.orbeon.oxf.xforms.function;

import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.functions.Component;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.value.DurationValue;
import org.orbeon.saxon.value.IntegerValue;
import org.orbeon.saxon.value.StringValue;
import org.orbeon.saxon.xpath.XPathException;

public class Months extends XFormsFunction {

    private static final StringValue NAN = new StringValue("NaN");

    private static final int YEAR_COEF = 12;


    public Item evaluateItem(XPathContext context) throws XPathException {
        String arg = argument[0].evaluateAsString(context);
        DurationValue value = null;
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
