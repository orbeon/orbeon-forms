/**
 *  Copyright (C) 2006 Orbeon, Inc.
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

import org.orbeon.oxf.xforms.XFormsControls;
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
 * Return the context node-set based on the enclosing xforms:repeat, xforms:group or xforms:switch,
 * either the closest one if no argument is passed, or context at the level of the element with the
 * given id passed.
 *
 * This function must be called from within an xforms:repeat, xforms:group or xforms:switch.
 */
public class XXFormsContext extends XFormsFunction {

    /**
     * preEvaluate: this method suppresses compile-time evaluation by doing nothing
     * (because the value of the expression depends on the runtime context)
     */
    public Expression preEvaluate(StaticContext env) {
        return this;
    }

    public SequenceIterator iterate(XPathContext xpathContext) throws XPathException {

        // Get instance id
        final Expression contextIdExpression = (argument == null || argument.length == 0) ? null : argument[0];
        final String contextId = (contextIdExpression == null) ? null : XFormsUtils.namespaceId(getXFormsContainingDocument(), contextIdExpression.evaluateAsString(xpathContext));

        // Get context for id
        final XFormsControls xformsControls = getXFormsControls();
        final List currentNodeset = xformsControls.getContextForId(contextId);

        return new ListIterator(currentNodeset);
    }
}
