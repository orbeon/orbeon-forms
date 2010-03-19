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

import org.apache.axis.utils.StringUtils;
import org.orbeon.oxf.xforms.InstanceData;
import org.orbeon.oxf.xforms.function.XFormsFunction;
import org.orbeon.saxon.expr.Expression;
import org.orbeon.saxon.expr.StaticProperty;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.om.*;
import org.orbeon.saxon.trans.XPathException;
import org.orbeon.saxon.value.StringValue;

import java.util.ArrayList;
import java.util.List;

/**
 * xxforms:invalid-binds()
 */
public class XXFormsInvalidBinds extends XFormsFunction {

    @Override
    public SequenceIterator iterate(XPathContext xpathContext) throws XPathException {

        final Expression nodesetExpression = argument[0];
        // "If the argument is omitted, it defaults to a node-set with the context node as its only
        // member."
        final Item item;
        if (nodesetExpression == null)
            item = xpathContext.getContextItem();
        else
            item = nodesetExpression.iterate(xpathContext).next();

        // Return () if we can't access the node
        if (item == null || !(item instanceof NodeInfo))
            return EmptyIterator.getInstance();

        final String invalidBindIdsString = InstanceData.getInvalidBindIds((NodeInfo) item);
        if (invalidBindIdsString == null)
            return EmptyIterator.getInstance();

        final String[] invalidBindIds = StringUtils.split(invalidBindIdsString, ' ');
        final List<StringValue> result = new ArrayList<StringValue>(invalidBindIds.length);
        for (String invalidBindId: invalidBindIds)
            result.add(new StringValue(invalidBindId));
        return new ListIterator(result);
    }

    @Override
    public int getIntrinsicDependencies() {
	    return StaticProperty.DEPENDS_ON_CONTEXT_ITEM;
    }
}
