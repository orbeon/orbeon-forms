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

import org.orbeon.oxf.util.IndentedLogger;
import org.orbeon.oxf.xforms.XFormsControls;
import org.orbeon.oxf.xforms.function.XFormsFunction;
import org.orbeon.saxon.expr.Expression;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.trans.XPathException;

/**
 * Return the current node of one of the enclosing xforms:repeat iteration, either the closest
 * iteration if no argument is passed, or the iteration for the repeat id passed.
 *
 * This function must be called from within an xforms:repeat.
 */
public class XXFormsRepeatCurrent extends XFormsFunction {

    public Item evaluateItem(XPathContext xpathContext) throws XPathException {

        // Get repeat id
        final Expression repeatIdExpression = (argument == null || argument.length == 0) ? null : argument[0];
        final String repeatId = (repeatIdExpression == null) ? null : repeatIdExpression.evaluateAsString(xpathContext).toString();

        // Note that this is deprecated. Move to warning later?
        final IndentedLogger indentedLogger = getContainingDocument(xpathContext).getIndentedLogger(XFormsControls.LOGGING_CATEGORY);
        if (indentedLogger.isDebugEnabled())
            indentedLogger.logDebug("xxforms:repeat-curent()", "function is deprecated, use context() or xxforms:context() instead");

        // Get current single node
        return getContextStack(xpathContext).getRepeatCurrentSingleNode(repeatId);
    }
}
