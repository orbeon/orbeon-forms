/**
 *  Copyright (C) 2009 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.function;

import org.orbeon.saxon.expr.Expression;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.trans.XPathException;
import org.orbeon.saxon.value.DoubleValue;
import org.orbeon.saxon.value.NumericValue;

/**
 * XForms power() function (XForms 1.1).
 */
public class Power extends XFormsFunction {

    public Item evaluateItem(XPathContext xpathContext) throws XPathException {

    	final Expression baseExpression = argument[0];
    	final Expression exponentExpression = argument[1];
    	try {
			final double base = ((NumericValue)baseExpression.evaluateItem(xpathContext)).getDoubleValue();
			final double exponent = ((NumericValue)exponentExpression.evaluateItem(xpathContext)).getDoubleValue();
    		
    		return new DoubleValue(Math.pow(base, exponent));
    	} catch(NumberFormatException e) {
    		return DoubleValue.NaN;
    	}
    }
}
