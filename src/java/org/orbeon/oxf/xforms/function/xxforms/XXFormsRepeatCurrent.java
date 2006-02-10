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

import org.orbeon.oxf.xforms.function.XFormsFunction;
import org.orbeon.oxf.xforms.*;
import org.orbeon.saxon.expr.Expression;
import org.orbeon.saxon.expr.StaticContext;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.xpath.XPathException;
import org.orbeon.saxon.value.BooleanValue;
import org.orbeon.saxon.dom4j.NodeWrapper;
import org.dom4j.Node;
import org.dom4j.Document;

import java.util.Iterator;

/**
 * Return the current node of one of the enclosing xforms:repeat iteration, either the closest
 * iteration if no argument is passed, or the iteration for the repeat id passed.
 *
 * This function must be called from within an xforms:repeat.
 */
public class XXFormsRepeatCurrent extends XFormsFunction {

    /**
     * preEvaluate: this method suppresses compile-time evaluation by doing nothing
     * (because the value of the expression depends on the runtime context)
     */
    public Expression preEvaluate(StaticContext env) {
        return this;
    }

    public Item evaluateItem(XPathContext xpathContext) throws XPathException {

        // Get instance id
        final Expression repeatIdExpression = (argument == null || argument.length == 0) ? null : argument[0];
        final String repeatId = (repeatIdExpression == null) ? null : repeatIdExpression.evaluateAsString(xpathContext);

        final XFormsControls xformsControls = getXFormsControls();

        final Node currentNode = xformsControls.getRepeatCurrentSingleNode(repeatId);
        final Document currentDocument = currentNode.getDocument();

        for (Iterator i = xformsControls.getContainingDocument().getModels().iterator(); i.hasNext();) {
            final XFormsModel currentModel = (XFormsModel) i.next();
            for (Iterator j = currentModel.getInstances().iterator(); j.hasNext();) {
                final XFormsInstance currentInstance = (XFormsInstance) j.next();
                if (currentInstance.getDocument() == currentDocument) {
                    return currentInstance.wrapNode(currentNode);
                }
            }
        }

        // Should not happen
        throw new IllegalStateException("Node not found in any XForms intance.");
    }
}
