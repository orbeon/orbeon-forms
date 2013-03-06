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
import org.orbeon.saxon.value.BooleanValue;

public class BooleanFromString extends XFormsFunction {

    public Item evaluateItem(XPathContext c) throws XPathException {
        String value = argument[0].evaluateAsString(c).toString();
        if("1".equals(value) || "true".equalsIgnoreCase(value))
            return BooleanValue.TRUE;
        else if("0".equals(value) || "false".equalsIgnoreCase(value))
            return BooleanValue.FALSE;
        else
        	return BooleanValue.FALSE;
    }
}
