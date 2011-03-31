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

import org.dom4j.QName;
import org.orbeon.oxf.util.PropertyContext;
import org.orbeon.oxf.xforms.XFormsContextStack;
import org.orbeon.oxf.xforms.XFormsModelBinds;
import org.orbeon.oxf.xforms.function.XFormsFunction;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.trans.XPathException;

public class XXFormsEvaluateBindProperty extends XFormsFunction {
    public Item evaluateItem(XPathContext xpathContext) throws XPathException {

        // Get bind id
        final String bindId = argument[0].evaluateAsString(xpathContext).toString();

        // Get MIP QName
        final QName mipQName = getQNameFromExpression(xpathContext, argument[1]);

        // Get bind nodeset
        final XFormsContextStack contextStack = getContextStack(xpathContext);

        // TODO: We get the model and the current single item through different means. Is there potential for issues?
        final XFormsModelBinds binds = contextStack.getCurrentModel().getBinds();
        final XFormsModelBinds.Bind bind = binds.resolveBind(bindId, contextStack.getCurrentSingleItem());
        if (bind == null) {
            // Return empty sequence
            return null;
        } else {
            return binds.evaluateBindByType(bind, 1, mipQName);
        }
    }
}
