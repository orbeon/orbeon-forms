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

import org.orbeon.oxf.common.OXFException;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.om.EmptyIterator;
import org.orbeon.saxon.om.ListIterator;
import org.orbeon.saxon.om.SequenceIterator;
import org.orbeon.saxon.trans.XPathException;
import org.orbeon.saxon.value.AtomicValue;
import org.orbeon.saxon.value.DateTimeValue;
import org.orbeon.saxon.value.NumericValue;
import org.orbeon.saxon.value.SecondsDurationValue;

import java.util.Collections;


public class SecondsToDateTime extends XFormsFunction {

    private static final long SECONDS_PER_DAY = 60 * 60 * 24;

    public SequenceIterator iterate(XPathContext context) throws XPathException {
        final NumericValue atomicValue = (NumericValue) ((AtomicValue) argument[0].evaluateItem(context)).getPrimitiveValue();
        
        if (atomicValue.isNaN())
            return EmptyIterator.getInstance();
        
        final long totalSeconds = atomicValue.longValue();

        // "returns string containing a lexical xsd:dateTime that corresponds to the number of seconds passed as the
        // parameter according to the following rules: The number parameter is rounded to the nearest whole number, and
        // the result is interpreted as the difference between the desired dateTime and 1970-01-01T00:00:00Z. An input
        // parameter value of NaN results in output of the empty string."

        // NOTE: Here we assume the number is in fact an integer

        final long days = totalSeconds / SECONDS_PER_DAY;
        final int seconds = (int) (totalSeconds % SECONDS_PER_DAY);

        if (days > Integer.MAX_VALUE)
            throw new OXFException("Number of seconds exceeds implementation-defined limits: " + totalSeconds);

        return new ListIterator(Collections.singletonList(new DateTimeValue("1970-01-01T00:00:00Z").add(new SecondsDurationValue(1, (int) days, 0, 0, seconds, 0))));
    }
}
