/**
 *  Copyright (C) 2007 Orbeon, Inc.
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

import org.orbeon.saxon.expr.Expression;
import org.orbeon.saxon.expr.StaticContext;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.trans.XPathException;
import org.orbeon.oxf.xforms.XFormsContextStack;

/**
 * XForms 1.1 context() function.
 */
public class Context extends XFormsFunction {

    /**
     * preEvaluate: this method suppresses compile-time evaluation by doing nothing
     * (because the value of the expression depends on the runtime context)
     */
    public Expression preEvaluate(StaticContext env) {
        return this;
    }

    public Item evaluateItem(XPathContext xpathContext) throws XPathException {

        // "7.10.4 The context() Function [...] This function returns the in-scope evaluation context node of the
        // nearest ancestor element of the node containing the XPath expression that invokes this function. The nearest
        // ancestor element may have been created dynamically as part of the run-time expansion of repeated content as
        // described in Section 4.7 Resolving ID References in XForms."

        // TODO: This is partially broken. It will work if context() is called from expressions after a binding node-set
        // has been evaluated. However, it will not work from within @ref, @nodeset, or @context.

        final XFormsContextStack.BindingContext parentBindingContext; {
            final XFormsContextStack contextStack = getContextStack(xpathContext);
            final XFormsContextStack.BindingContext currentBindingContext = contextStack.getCurrentBindingContext();
            parentBindingContext = currentBindingContext.getParent();
        }

        // Take position into account so that repeats and iterations are handled
        return (Item) parentBindingContext.getNodeset().get(parentBindingContext.getPosition() - 1);
    }
}
