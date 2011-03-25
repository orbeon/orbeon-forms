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

import org.orbeon.oxf.util.PropertyContext;
import org.orbeon.oxf.xforms.control.controls.XFormsSelect1Control;
import org.orbeon.oxf.xforms.control.controls.XFormsSelectControl;
import org.orbeon.oxf.xforms.function.XFormsFunction;
import org.orbeon.oxf.xforms.itemset.Itemset;
import org.orbeon.saxon.expr.Expression;
import org.orbeon.saxon.expr.ExpressionTool;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.trans.XPathException;
import org.orbeon.saxon.value.StringValue;

public class XXFormsItemset extends XFormsFunction {

    @Override
    public Item evaluateItem(XPathContext xpathContext) throws XPathException {
        // Get control
        final Expression controlStaticIdExpression = argument[0];
        final String controlStaticId = (controlStaticIdExpression == null) ? null : controlStaticIdExpression.evaluateAsString(xpathContext).toString();
        final Object object = getXBLContainer(xpathContext).resolveObjectByIdInScope(getSourceEffectiveId(xpathContext), controlStaticId, null);

        if (object instanceof XFormsSelect1Control && ((XFormsSelect1Control) object).isRelevant()) {// only try if the control is relevant
            final XFormsSelect1Control select1Control = (XFormsSelect1Control) object;

            // Get format
            final String format = argument[1].evaluateAsString(xpathContext).toString();

            // Whether to mark selected values
            final Expression selectedExpression = (argument.length < 3) ? null : argument[2];
            final boolean selected = (selectedExpression != null) && ExpressionTool.effectiveBooleanValue(selectedExpression.iterate(xpathContext));

            // Obtain itemset
            final PropertyContext pipelineContext = getOrCreatePipelineContext();
            final Itemset itemset = select1Control.getItemset(pipelineContext);

            final String controlValueForSelection = selected ? select1Control.getValue() : null;
            final boolean isMultiple = select1Control instanceof XFormsSelectControl;

            if ("json".equalsIgnoreCase(format)) {
                final String json = itemset.getJSONTreeInfo(pipelineContext, controlValueForSelection, isMultiple, select1Control.getLocationData());
                return StringValue.makeStringValue(json);
            } else {
                return itemset.getXMLTreeInfo(xpathContext.getConfiguration(), controlValueForSelection, isMultiple, select1Control.getLocationData());
            }
        } else {
            return null;
        }
    }
}
