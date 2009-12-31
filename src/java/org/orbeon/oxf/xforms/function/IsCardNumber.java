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
import org.orbeon.saxon.value.BooleanValue;

/**
 * XForms is-card-number() function (XForms 1.1).
 */
public class IsCardNumber extends XFormsFunction {

    public Item evaluateItem(XPathContext xpathContext) throws XPathException {

    	final Expression luhnnumberExpression = argument[0];
    	final String luhnnumber = luhnnumberExpression.evaluateAsString(xpathContext).toString();
    	int result = 0;
    	boolean alternation = false;
    	try {
	    	for (int i = luhnnumber.length() - 1; i >= 0; i--) {
	    	    int n = Integer.parseInt(luhnnumber.substring(i, i + 1));
	    	    if (alternation) {
		    		n *= 2;
		    		if (n > 9) {
		    		    n = (n % 10) + 1;
		    		}
	    	    }
	    	    result += n;
	    	    alternation = !alternation;
	    	}
	    	return BooleanValue.get(result % 10 == 0);
    	} catch(NumberFormatException e) {
    		return BooleanValue.FALSE;
    	}
    }
}
