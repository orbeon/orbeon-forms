package org.orbeon.oxf.xforms.function;

import org.orbeon.saxon.expr.Expression;
import org.orbeon.saxon.expr.StaticContext;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.trans.XPathException;

/**
 *
 */
public class Current extends XFormsFunction {

    public Expression preEvaluate(StaticContext env) {
        return this;
    }

    public Item evaluateItem(XPathContext c) throws XPathException {
        return c.getContextItem();
    }
}
