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

import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.xforms.XFormsModel;
import org.orbeon.oxf.xforms.event.events.XFormsComputeExceptionEvent;
import org.orbeon.oxf.xforms.xbl.XBLContainer;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.trans.XPathException;
import org.orbeon.saxon.value.Int64Value;

/**
 * XForms index() function.
 *
 * 7.8.5 The index() Function
 */
public class Index extends XFormsFunction {

    public Item evaluateItem(XPathContext xpathContext) throws XPathException {

        final String repeatId = argument[0].evaluateAsString(xpathContext).toString();

        // New implementation
        return findIndexForRepeatId(xpathContext, repeatId);
    }

    protected Item findIndexForRepeatId(XPathContext xpathContext, String repeatStaticId) {

        final int index = getXBLContainer(xpathContext).getRepeatIndex(getSourceEffectiveId(xpathContext), repeatStaticId);

        if (index == -1) {
            // Dispatch exception event
            final String message = "Function index uses repeat id '" + repeatStaticId + "' which is not in scope";
            final RuntimeException exception = new ValidationException(message, null);

            final XFormsModel currentModel = getContextStack(xpathContext).getCurrentModel();
            final XBLContainer container = currentModel.getXBLContainer();
            container.dispatchEvent(
                    new XFormsComputeExceptionEvent(container.getContainingDocument(), currentModel, message, exception));

            // TODO: stop processing!
            // How do we do this: throw special exception? Or should throw exception with
            // XFormsComputeException() and then dispatch the event?
            throw exception;
        }

        // Return value found
        return new Int64Value(index);
    }
}
