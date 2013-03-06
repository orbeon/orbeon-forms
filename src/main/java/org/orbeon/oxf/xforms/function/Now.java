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
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.trans.XPathException;
import org.orbeon.saxon.value.DateTimeValue;
import org.orbeon.saxon.value.StringValue;

import java.util.GregorianCalendar;
import java.util.TimeZone;

public class Now extends XFormsFunction {

    public Item evaluateItem(XPathContext context) throws XPathException {
        DateTimeValue value;
        if(argument.length == 1 && "test".equals(argument[0].evaluateAsString(context))) {
            value = (DateTimeValue) DateTimeValue.makeDateTimeValue("2004-12-31T12:00:00.000Z").asAtomic();
        } else {
            value = new DateTimeValue(new GregorianCalendar(TimeZone.getTimeZone("UTC")), true);
        }
        return new StringValue(value.getStringValue());

    }
}
