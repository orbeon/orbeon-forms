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
package org.orbeon.oxf.xforms.function.exforms;

import org.orbeon.oxf.xforms.function.xxforms.XXFormsMIPFunction;
import org.orbeon.saxon.dom4j.NodeWrapper;
import org.orbeon.saxon.expr.Expression;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.om.NodeInfo;
import org.orbeon.saxon.trans.XPathException;
import org.orbeon.saxon.value.BooleanValue;

/**
 * Base class for eXForms MIP functions.
 */
public abstract class EXFormsMIP extends XXFormsMIPFunction {

    @Override
    public Item evaluateItem(XPathContext c) throws XPathException {

        final Expression nodesetExpression = (argument.length > 0) ? argument[0] : null;
        // "If the argument is omitted, it defaults to a node-set with the context node as its only
        // member."
        final Item item;
        if (nodesetExpression == null)
            item = c.getContextItem();
        else
            item = nodesetExpression.iterate(c).next();

        // "If the node-set is empty then the function returns false."
        if (item == null)
            return BooleanValue.FALSE;

        return BooleanValue.get(getResult((NodeInfo) item));
    }

    protected abstract boolean getResult(NodeInfo nodeInfo);
}
