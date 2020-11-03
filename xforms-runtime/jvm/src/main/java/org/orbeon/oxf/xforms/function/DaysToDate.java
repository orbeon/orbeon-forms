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
import org.orbeon.saxon.value.*;
import org.orbeon.saxon.value.StringValue;

import java.util.*;

public class DaysToDate extends XFormsFunction {

    private static final int SECONDS_PER_DAY = (60 * 60 * 24);

    public Item evaluateItem(XPathContext context) throws XPathException {

    	final NumericValue secondsAsNumericValue = ((NumericValue) argument[0].evaluateItem(context));

    	if (secondsAsNumericValue.isNaN()) {
    	    return StringValue.EMPTY_STRING;
    	}

    	GregorianCalendar cal = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
    	cal.setTime(new Date(Math.round(secondsAsNumericValue.getDoubleValue()) * 1000 * 60 * 60 * 24));

    	return new StringValue(new DateValue(cal, DateValue.NO_TIMEZONE).getStringValue());
    }
}
