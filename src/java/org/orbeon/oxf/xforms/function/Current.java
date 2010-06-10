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
package org.orbeon.oxf.xforms.function;

import org.orbeon.saxon.expr.PathMap;
import org.orbeon.saxon.expr.StaticProperty;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.trans.XPathException;

/**
 * XForms 1.1 current() function.
 */
public class Current extends XFormsFunction {

    @Override
    public Item evaluateItem(XPathContext xpathContext) throws XPathException {

        // "7.10.2 Returns the context node used to initialize the evaluation of the containing XPath expression."

        // Go up the stack to find the top-level context
        XPathContext currentContext = xpathContext;
        while (currentContext.getCaller() != null)
            currentContext = currentContext.getCaller();

        return currentContext.getContextItem();
    }

    @Override
    public int getIntrinsicDependencies() {
	    return StaticProperty.DEPENDS_ON_CONTEXT_ITEM;
    }

    @Override
    public PathMap.PathMapNodeSet addToPathMap(PathMap pathMap, PathMap.PathMapNodeSet pathMapNodeSet) {
        // TODO: something smart
        return super.addToPathMap(pathMap, pathMapNodeSet);
    }
}
