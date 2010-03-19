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

import org.orbeon.oxf.util.PooledXPathExpression;
import org.orbeon.oxf.xforms.function.XFormsFunction;
import org.orbeon.saxon.expr.*;
import org.orbeon.saxon.om.SequenceIterator;
import org.orbeon.saxon.trans.XPathException;

public class XXFormsEvaluateAVT extends XFormsFunction {

    @Override
    public SequenceIterator iterate(XPathContext xpathContext) throws XPathException {

        final XPathContextMajor newXPathContext = xpathContext.newCleanContext();

        final Expression avtExpression;
        {
            // Prepare expression
            final PooledXPathExpression xpathExpression = prepareExpression(xpathContext, argument[0], true);
            avtExpression = xpathExpression.prepareExpression(newXPathContext, PooledXPathExpression.getFunctionContext(xpathContext));
        }

        return avtExpression.iterate(newXPathContext);
    }

    @Override
    public void checkArguments(ExpressionVisitor visitor) throws XPathException {
        // Needed by prepareExpression()
        copyStaticContextIfNeeded(visitor);
    }

    @Override
    public int getIntrinsicDependencies() {
	    return StaticProperty.DEPENDS_ON_FOCUS;
    }
}
