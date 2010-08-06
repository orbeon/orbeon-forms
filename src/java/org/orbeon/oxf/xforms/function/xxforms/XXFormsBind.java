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
import org.orbeon.oxf.xforms.XFormsModelBinds;
import org.orbeon.oxf.xforms.function.XFormsFunction;
import org.orbeon.saxon.expr.Expression;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.om.*;
import org.orbeon.saxon.trans.XPathException;

import java.util.Collections;
import java.util.List;

/**
 * The xxforms:bind() function.
 */
public class XXFormsBind extends XFormsFunction {

    public SequenceIterator iterate(XPathContext xpathContext) throws XPathException {

        // Get bind id
        final Expression bindIdExpression = (argument == null || argument.length == 0) ? null : argument[0];
        final String bindId = (bindIdExpression == null) ? null : bindIdExpression.evaluateAsString(xpathContext).toString();

        // Get bind nodeset
        final XFormsContextStack contextStack = getContextStack(xpathContext);

        // TODO: We get the model and the current single item through different means. Is there potential for issues?
        final XFormsModelBinds currentBinds = getModel(xpathContext).getBinds();
        final List<Item> bindNodeset = (currentBinds != null) ? currentBinds.getBindNodeset(bindId, contextStack.getCurrentSingleItem()) : Collections.<Item>emptyList();

        return new ListIterator(bindNodeset);
    }
}
