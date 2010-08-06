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

import org.orbeon.oxf.xforms.XFormsContextStack;
import org.orbeon.oxf.xforms.function.XFormsFunction;
import org.orbeon.saxon.expr.Expression;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.om.*;
import org.orbeon.saxon.trans.XPathException;

/**
 * The xxforms:context() function allows you to obtain the single-node binding for an enclosing xforms:group,
 * xforms:repeat, or xforms:switch. It takes one mandatory string parameter containing the id of an enclosing grouping
 * XForms control. For xforms:repeat, the context returned is the context of the current iteration.
 */
public class XXFormsContext extends XFormsFunction {

    public SequenceIterator iterate(XPathContext xpathContext) throws XPathException {

        // Get context id
        final Expression contextIdExpression = (argument == null || argument.length == 0) ? null : argument[0];
        final String contextId = (contextIdExpression == null) ? null : contextIdExpression.evaluateAsString(xpathContext).toString();

        // Get context item for id
        final XFormsContextStack contextStack = getContextStack(xpathContext);
        final Item contextItem = contextStack.getContextForId(contextId);
        if (contextItem != null)
            return SingletonIterator.makeIterator(contextItem);
        else
            return EmptyIterator.getInstance();
    }
}
