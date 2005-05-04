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
