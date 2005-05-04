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
import org.orbeon.saxon.value.*;
import org.orbeon.saxon.xpath.XPathException;

public class Seconds extends XFormsFunction {

    private static final StringValue NAN = new StringValue("NaN");

    private static final int MINUTE_COEF = 60;
    private static final int HOUR_COEF = 60 * 60;
    private static final int DAY_COEF = 60 * 60 * 24;


    public Item evaluateItem(XPathContext context) throws XPathException {
        String arg = argument[0].evaluateAsString(context);
        DurationValue value = null;
        try {
            value = new DurationValue(arg);
        } catch (XPathException e) {
            return NAN;
        }
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
