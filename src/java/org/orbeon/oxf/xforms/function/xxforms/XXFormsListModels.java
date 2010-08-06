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

import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsModel;
import org.orbeon.oxf.xforms.function.XFormsFunction;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.om.*;
import org.orbeon.saxon.trans.XPathException;
import org.orbeon.saxon.value.StringValue;

import java.util.ArrayList;
import java.util.List;

public class XXFormsListModels extends XFormsFunction {
    
    public SequenceIterator iterate(XPathContext xpathContext) throws XPathException {

        final XFormsContainingDocument containingDocument = getContainingDocument(xpathContext);
        // Get all the models including local XML component models
        final List<XFormsModel> models = containingDocument.getAllModels();

        if (models != null && models.size() > 0) {
            final List<StringValue> modelIds = new ArrayList<StringValue>(models.size());

            for (final XFormsModel model: models) {
                modelIds.add(new StringValue(model.getEffectiveId()));
            }

            return new ListIterator(modelIds);
        } else {
            return EmptyIterator.getInstance();
        }
    }
}
