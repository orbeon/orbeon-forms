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

import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.trans.XPathException;

/**
 * XForms 1.1 context() function.
 */
public class Context extends XFormsFunction {

    public Item evaluateItem(XPathContext xpathContext) throws XPathException {

        // "7.10.4 The context() Function [...] This function returns the in-scope evaluation context node of the
        // nearest ancestor element of the node containing the XPath expression that invokes this function. The nearest
        // ancestor element may have been created dynamically as part of the run-time expansion of repeated content as
        // described in Section 4.7 Resolving ID References in XForms."

        return getContextStack(xpathContext).getContextItem();
    }
}
