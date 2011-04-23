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

import org.dom4j.Node;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsModel;
import org.orbeon.oxf.xforms.submission.XFormsSubmissionUtils;
import org.orbeon.saxon.dom4j.NodeWrapper;
import org.orbeon.saxon.expr.*;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.om.NodeInfo;
import org.orbeon.saxon.trans.XPathException;
import org.orbeon.saxon.value.BooleanValue;

public class XXFormsValid extends XXFormsMIPFunction {

    @Override
    public Item evaluateItem(XPathContext xpathContext) throws XPathException {

        final Expression nodesetExpression = (argument.length > 0) ? argument[0] : null;
        // "If the argument is omitted, it defaults to a node-set with the context node as its only
        // member."
        final Item item;
        if (nodesetExpression == null)
            item = xpathContext.getContextItem();
        else
            item = nodesetExpression.evaluateItem(xpathContext);

        // Whether to recurse
        final Expression recurseExpression = (argument.length < 2) ? null : argument[1];
        final boolean recurse = (recurseExpression != null) && ExpressionTool.effectiveBooleanValue(recurseExpression.iterate(xpathContext));

        // "If the node-set is empty then the function returns false."
        if (item == null || !(item instanceof NodeInfo))
            return BooleanValue.FALSE;

        final boolean result;
        if (recurse && item instanceof NodeWrapper) {
            // Recurse starting with the current node
            // NOTE: Don't recurse if we don't have a NodeWrapper, as those don't support MIPs anyway yet
            final Node node = (Node) ((NodeWrapper) item).getUnderlyingNode();
            final XFormsContainingDocument containingDocument = getContainingDocument(xpathContext);
            // NOTE: Rationale for using XFormsModel.LOGGING_CATEGORY is that this regards instance validity.
            result = XFormsSubmissionUtils.isSatisfiesValidRequired(containingDocument.getIndentedLogger(XFormsModel.LOGGING_CATEGORY), node, true, true, true);
        } else {
            // Just return the value associated with this node
            result = XFormsSubmissionUtils.isSatisfiesValidRequired((NodeInfo) item, true, true);
        }
        return BooleanValue.get(result);
    }
}
