/**
 *  Copyright (C) 2005 Orbeon, Inc.
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

import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.value.StringValue;
import org.orbeon.saxon.xpath.StaticError;
import org.orbeon.saxon.xpath.XPathException;

public class Property extends XFormsFunction {

    private static final StringValue VERSION = new StringValue("1.0");
    private static final StringValue CONFORMANCE = new StringValue("full");

    private static final String VERSTION_PROPERTY = "version";
    private static final String CONFORMANCE_PROPERTY = "conformance-level";

    public Item evaluateItem(XPathContext c) throws XPathException {
        String arg = argument[0].evaluateAsString(c);
        if(VERSTION_PROPERTY.equals(arg))
            return VERSION;
        else if(CONFORMANCE_PROPERTY.equals(arg))
            return CONFORMANCE;
        else
            throw new StaticError("property() function accepts only two property names: 'version' and 'conformance-level'");

    }
}
