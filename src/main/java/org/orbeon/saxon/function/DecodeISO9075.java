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
package org.orbeon.saxon.function;

import org.orbeon.oxf.util.ISO9075;
import org.orbeon.oxf.xml.FunctionSupportJava;
import org.orbeon.saxon.expr.Expression;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.trans.XPathException;
import org.orbeon.saxon.value.StringValue;

public class DecodeISO9075 extends FunctionSupportJava {

    public Item evaluateItem(XPathContext xpathContext) throws XPathException {

        // Get property name
        final Expression valueExpression = argument[0];
        final String value = valueExpression.evaluateAsString(xpathContext).toString();

        // Get property value
        return new StringValue(ISO9075.decode(value));
    }
}
