/**
 *  Copyright (C) 2008 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.function.xxforms;

import org.orbeon.oxf.xforms.XFormsContextStack;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.function.XFormsFunction;
import org.orbeon.saxon.expr.Expression;
import org.orbeon.saxon.expr.StaticContext;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.om.ListIterator;
import org.orbeon.saxon.om.SequenceIterator;
import org.orbeon.saxon.trans.XPathException;

import java.util.List;

/**
 * The xxforms:repeat-nodeset() function returns the current node-set for a given enclosing repeat.
 */
public class XXFormsRepeatNodeset extends XFormsFunction {
    /**
     * preEvaluate: this method suppresses compile-time evaluation by doing nothing
     * (because the value of the expression depends on the runtime context)
     */
    public Expression preEvaluate(StaticContext env) {
        return this;
    }

    public SequenceIterator iterate(XPathContext xpathContext) throws XPathException {

        // Get repeat id
        final Expression contextIdExpression = (argument == null || argument.length == 0) ? null : argument[0];
        final String repeatId = (contextIdExpression == null) ? null : XFormsUtils.namespaceId(getContainingDocument(xpathContext), contextIdExpression.evaluateAsString(xpathContext));

        // Get repeat node-set for given id
        final XFormsContextStack contextStack = getContextStack(xpathContext);
        final List repeatNodeset = contextStack.getRepeatNodeset(repeatId);
        return new ListIterator(repeatNodeset);
    }
}
