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
package org.orbeon.oxf.xforms.function;

import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsControls;
import org.orbeon.oxf.xforms.XFormsModel;
import org.orbeon.oxf.util.PooledXPathExpression;
import org.orbeon.saxon.functions.SystemFunction;
import org.orbeon.saxon.expr.XPathContext;

/**
 * Base class for all XForms functions.
 */
abstract public class XFormsFunction extends SystemFunction {

    protected XFormsFunction() {
    }

    public XFormsModel getXFormsModel(XPathContext xpathContext) {
        final Object functionContext = PooledXPathExpression.getFunctionContext(xpathContext);
        return (XFormsModel) ((functionContext instanceof XFormsModel) ? functionContext : null);
    }

    public XFormsControls getXFormsControls(XPathContext xpathContext) {
        final Object functionContext = PooledXPathExpression.getFunctionContext(xpathContext);
        if (functionContext instanceof XFormsControls)
            return (XFormsControls) functionContext;
        if (functionContext instanceof XFormsModel) {
            final XFormsModel xformsModel = (XFormsModel) functionContext;
            return xformsModel.getContainingDocument().getXFormsControls();
        }
        return null;
    }

    public XFormsContainingDocument getXFormsContainingDocument(XPathContext xpathContext) {
        final XFormsModel xformsModel = getXFormsModel(xpathContext);
        if (xformsModel != null && xformsModel.getContainingDocument() != null)
            return xformsModel.getContainingDocument();
        final XFormsControls xformsControls = getXFormsControls(xpathContext);
        if (xformsControls != null)
            return xformsControls.getContainingDocument();
        return null;
    }
}
