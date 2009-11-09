/**
 * Copyright (C) 2009 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.function.exforms;

import org.orbeon.oxf.xforms.function.xxforms.XXFormsSort;
import org.orbeon.saxon.expr.Expression;
import org.orbeon.saxon.expr.StaticContext;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.expr.XPathContextMajor;
import org.orbeon.saxon.om.SequenceIterator;
import org.orbeon.saxon.trans.XPathException;

/**
 * exforms:sort() function
 */
public class EXFormsSort extends XXFormsSort {

    public SequenceIterator iterate(XPathContext xpathContext) throws XPathException {

        final Expression sequenceToSortExpression = argument[0];
        final Expression selectExpression = argument[1];

        final Expression sortKeyExpression;
        final XPathContextMajor newXPathContext;
        {
            // NOTE: It would be better if we could use XPathCache/PooledXPathExpression instead of rewriting custom
            // code below. This would provide caching of compiled expressions, abstraction and some simplicity.

            // Prepare expression and context
            final PreparedExpression preparedExpression = prepareExpression(xpathContext, selectExpression, false);
            newXPathContext = prepareXPathContext(xpathContext, preparedExpression);
            // Return expression
            sortKeyExpression = preparedExpression.expression;
        }

        return sort(newXPathContext, sequenceToSortExpression, sortKeyExpression);
    }

    public void checkArguments(StaticContext env) throws XPathException {
        // Needed by prepareExpression()
        copyStaticContextIfNeeded(env);
    }
}
