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
