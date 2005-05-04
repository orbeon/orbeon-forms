/*
 * Copyright 1998-2002, Orbeon, Inc.
 * All Rights Reserved
 *
 * This is unpublished proprietary source code of Orbeon, Inc. The
 * copyright notice above does not evidence any actual or intended
 * publication of such source code.
 */
package org.orbeon.oxf.xforms.function;

import org.orbeon.saxon.expr.Expression;
import org.orbeon.saxon.expr.StaticContext;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.value.DateTimeValue;
import org.orbeon.saxon.value.DateValue;
import org.orbeon.saxon.value.StringValue;
import org.orbeon.saxon.value.TimeValue;
import org.orbeon.saxon.xpath.XPathException;

import java.util.GregorianCalendar;
import java.util.TimeZone;

public class Now extends XFormsFunction {



    public Expression preEvaluate(StaticContext env) throws XPathException {
        return this;
    }

    public Item evaluateItem(XPathContext context) throws XPathException {
        DateTimeValue value;
        if(argument.length == 1 && "test".equals(argument[0].evaluateAsString(context))) {
            value = new DateTimeValue(new DateValue("2004-12-31Z"), new TimeValue("12:00:00.000Z"));
        } else {
            value = new DateTimeValue(new GregorianCalendar(TimeZone.getTimeZone("UTC")), true);
        }
        return new StringValue(value.getStringValue());

    }

}
