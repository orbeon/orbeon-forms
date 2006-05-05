/**
 *  Copyright (C) 2005 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.function.exforms;

import org.dom4j.Node;
import org.orbeon.oxf.xforms.InstanceData;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.function.XFormsFunction;
import org.orbeon.saxon.dom4j.NodeWrapper;
import org.orbeon.saxon.expr.Expression;
import org.orbeon.saxon.expr.StaticContext;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.value.BooleanValue;
import org.orbeon.saxon.trans.XPathException;

/**
 * Base class for eXForms MIP functions.
 */
public abstract class EXFormsMIP extends XFormsFunction {

    /**
     * preEvaluate: this method suppresses compile-time evaluation by doing nothing
     * (because the value of the expression depends on the runtime context)
     */
    public Expression preEvaluate(StaticContext env) {
        return this;
    }

    public Item evaluateItem(XPathContext c) throws XPathException {

        final Expression nodesetExpression = argument[0];
        // "If the argument is omitted, it defaults to a node-set with the context node as its only
        // member."
        final Item item;
        if (nodesetExpression == null)
            item = c.getContextItem();
        else
            item = nodesetExpression.iterate(c).next();

        // "If the node-set is empty then the function returns false."
        if (item == null)
            return BooleanValue.get(false);

        final Node node = (Node) ((NodeWrapper) item).getUnderlyingNode();
        final InstanceData instanceData = XFormsUtils.getInstanceDataUpdateInherited(node);
        return BooleanValue.get(getResult(instanceData));
    }

    protected abstract boolean getResult(InstanceData instanceData);
}
