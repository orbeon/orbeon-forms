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
package org.orbeon.oxf.xforms.function.xxforms;

import org.orbeon.oxf.xforms.function.Index;
import org.orbeon.saxon.expr.Expression;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.trans.XPathException;

/**
 * xxforms:index() function. Behaves like the standard XForms index() function, except the repeat id is optional. When
 * it is not specified, the function returns the id of the closest enclosing repeat.
 */
public class XXFormsIndex extends Index {

    public Item evaluateItem(XPathContext xpathContext) throws XPathException {

        final Expression repeatIdExpression = (argument == null || argument.length == 0) ? null : argument[0];
        final String repeatId = (repeatIdExpression == null) ? null : repeatIdExpression.evaluateAsString(xpathContext).toString();

        if (repeatId == null) {
            // Find closest enclosing id
            return findIndexForRepeatId(xpathContext, getContextStack(xpathContext).getEnclosingRepeatId());
        } else {
            return super.evaluateItem(xpathContext);
        }
    }
}
