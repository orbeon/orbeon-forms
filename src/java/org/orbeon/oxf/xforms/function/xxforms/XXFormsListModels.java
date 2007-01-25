/**
 *  Copyright (C) 20067 Orbeon, Inc.
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

import org.orbeon.oxf.xforms.XFormsModel;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.function.XFormsFunction;
import org.orbeon.saxon.expr.Expression;
import org.orbeon.saxon.expr.StaticContext;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.om.ListIterator;
import org.orbeon.saxon.om.SequenceIterator;
import org.orbeon.saxon.trans.XPathException;
import org.orbeon.saxon.value.StringValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class XXFormsListModels extends XFormsFunction {

    /**
    * preEvaluate: this method suppresses compile-time evaluation by doing nothing
    * (because the value of the expression depends on the runtime context)
    */
    public Expression preEvaluate(StaticContext env) {
        return this;
    }

    public SequenceIterator iterate(XPathContext xpathContext) throws XPathException {

        final XFormsContainingDocument containingDocument = getXFormsContainingDocument();
        final List models = containingDocument.getModels();

        if (models != null && models.size() > 0) {
            final List modelIds = new ArrayList(models.size());

            for (Iterator modelsIterator = models.iterator(); modelsIterator.hasNext();) {
                final XFormsModel currentModel = (XFormsModel) modelsIterator.next();
                // Tricky: we return a de-namespaced id, which seems to be the best thing to do
                modelIds.add(new StringValue(XFormsUtils.deNamespaceId(containingDocument , currentModel.getEffectiveId())));
            }

            return new ListIterator(modelIds);
        } else {
            return new ListIterator(Collections.EMPTY_LIST);
        }
    }
}
