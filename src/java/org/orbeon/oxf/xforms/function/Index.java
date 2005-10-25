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

import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.StaticExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.xforms.XFormsElementContext;
import org.orbeon.oxf.xforms.XFormsModel;
import org.orbeon.saxon.expr.Expression;
import org.orbeon.saxon.expr.StaticContext;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.value.IntegerValue;
import org.orbeon.saxon.xpath.XPathException;

/**
 * XForms index() function.
 *
 * 7.8.5 The index() Function
 */
public class Index extends XFormsFunction {

    /**
    * preEvaluate: this method suppresses compile-time evaluation by doing nothing
    * (because the value of the expression depends on the runtime context)
    */
    public Expression preEvaluate(StaticContext env) {
        return this;
    }

    public Item evaluateItem(XPathContext xpathContext) throws XPathException {

        final String repeatId = argument[0].evaluateAsString(xpathContext);

        if (getXFormsControls() instanceof XFormsElementContext) {
            // Legacy implementation
            XFormsElementContext xFormsElementContext = (XFormsElementContext) getXFormsControls();

            return new IntegerValue(((Integer) xFormsElementContext.getRepeatIdToIndex().get(repeatId)).intValue());
        } else {
            // New implementation
            final int index = getXFormsControls().getRepeatIdIndex(repeatId);

            if (index == -1) {
                // Dispatch exception event
                final String message = "Function index uses repeat id '" + repeatId + "' which is not in scope";
                final RuntimeException exception = new ValidationException(message, null);

                // Obtain PipelineContext - this function is always called from controls so
                // PipelineContext should be present
                final StaticExternalContext.StaticContext staticContext = StaticExternalContext.getStaticContext();
                PipelineContext pipelineContext = (staticContext != null) ? staticContext.getPipelineContext() : null;

                final XFormsModel currentModel = getXFormsControls().getCurrentModel();
                currentModel.getContainingDocument().dispatchEvent(pipelineContext,
                        new org.orbeon.oxf.xforms.event.events.XFormsComputeExceptionEvent(currentModel, message, exception));

                // TODO: stop processing!
                // How do we do this: throw special exception? Or should throw exception with
                // XFormsComputeException() and then dispatch the event?
                throw exception;
            }

            // Return value found
            return new IntegerValue(index);
        }
    }
}
