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

import org.orbeon.oxf.util.SecureUtils;
import org.orbeon.saxon.expr.Expression;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.trans.XPathException;
import org.orbeon.saxon.value.StringValue;

/**
 * XForms digest() function (XForms 1.1).
 */
public class Digest extends XFormsFunction {

    public Item evaluateItem(XPathContext xpathContext) throws XPathException {

        final Expression dataExpression = argument[0];
        final Expression algorithmExpression = argument[1];
        final Expression encodingExpression = argument.length == 3 ? argument[2] : null;

        final String data = dataExpression.evaluateAsString(xpathContext);
        final String algorithm = algorithmExpression.evaluateAsString(xpathContext);
        final String encoding = encodingExpression != null ? encodingExpression.evaluateAsString(xpathContext) : "base64";

        // Create digest
        final String result = SecureUtils.digestString(data, algorithm, encoding);

        return new StringValue(result);
    }
}
