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
package org.orbeon.oxf.xforms.function.xxforms;

import org.orbeon.oxf.xforms.function.XFormsFunction;
import org.orbeon.saxon.expr.Expression;
import org.orbeon.saxon.expr.StaticContext;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.expr.XPathContextMajor;
import org.orbeon.saxon.om.SequenceIterator;
import org.orbeon.saxon.trans.XPathException;

public class XXFormsEvaluateAVT extends XFormsFunction {

    public SequenceIterator iterate(XPathContext xpathContext) throws XPathException {

        final Expression avtExpression;
        final XPathContextMajor newXPathContext;
        {
            // NOTE: It would be better if we could use XPathCache/PooledXPathExpression instead of rewriting custom
            // code below. This would provide caching of compiled expressions, abstraction and some simplicity.

            // Prepare expression and context
            final PreparedExpression preparedExpression = prepareExpression(xpathContext, argument[0], true);
            newXPathContext = prepareXPathContext(xpathContext, preparedExpression);
            // Return expression
            avtExpression = preparedExpression.expression;
        }

        return avtExpression.iterate(newXPathContext);
    }

    public void checkArguments(StaticContext env) throws XPathException {
        // Needed by prepareExpression()
        copyStaticContextIfNeeded(env);
    }
}
