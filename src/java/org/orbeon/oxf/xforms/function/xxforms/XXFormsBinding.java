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
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.control.XFormsContainerControl;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsSingleNodeControl;
import org.orbeon.oxf.xforms.function.XFormsFunction;
import org.orbeon.saxon.expr.Expression;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.om.*;
import org.orbeon.saxon.trans.XPathException;

public class XXFormsBinding extends XFormsFunction {

    public SequenceIterator iterate(XPathContext xpathContext) throws XPathException {

        // Get control id
        final Expression controlIdExpression = (argument == null || argument.length == 0) ? null : argument[0];
        final String controlStaticId = (controlIdExpression == null) ? null : XFormsUtils.namespaceId(getContainingDocument(xpathContext), controlIdExpression.evaluateAsString(xpathContext));

        if (controlStaticId == null)
            return EmptyIterator.getInstance();

        final Object object = getXBLContainer(xpathContext).resolveObjectByIdInScope(getSourceEffectiveId(xpathContext), controlStaticId, null);

        // For now allow single-node controls only
        if (object instanceof XFormsSingleNodeControl) {
            // Get and return control binding
            final XFormsSingleNodeControl control = (XFormsSingleNodeControl) object;
            final Item boundItem = control.getBoundItem();

            if (boundItem == null)
                return EmptyIterator.getInstance();

            return SingletonIterator.makeIterator(boundItem);
        } else if (object instanceof XFormsContainerControl) {
            final XFormsControl control = (XFormsControl) object;
            final XFormsContextStack.BindingContext bindingContext = control.getBindingContext();
            if (bindingContext.isNewBind()) {
                return new ListIterator(bindingContext.getNodeset());
            } else {
                return EmptyIterator.getInstance();
            }
        } else {
            return EmptyIterator.getInstance();
        }
    }
}
