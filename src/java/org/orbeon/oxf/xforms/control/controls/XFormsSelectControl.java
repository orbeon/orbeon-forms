/**
 *  Copyright (C) 2006 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.control.controls;

import org.dom4j.Element;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.saxon.om.FastStringBuffer;

import java.util.*;

/**
 * Represents an xforms:select control.
 *
 * xforms:select represents items as a list of space-separated tokens.
 */
public class XFormsSelectControl extends XFormsSelect1Control {

    public XFormsSelectControl(XFormsContainingDocument containingDocument, XFormsControl parent, Element element, String name, String id) {
        super(containingDocument, parent, element, name, id);
    }

    /**
     * Set an external value. This consists of a list of space-separated tokens.
     *
     * o Itemset values which are in the list of tokens are merged with the bound control's value.
     * o Itemset values which are not in the list of tokens are removed from the bound control's value.
     *
     * @param pipelineContext   current pipeline context
     * @param value             list of tokens from the UI
     * @param type              should probably be null
     */
    public void setExternalValue(PipelineContext pipelineContext, String value, String type) {

        // Current values in the instance
        final Map instanceValues = tokenize(getValue());

        // Values currently selected in the UI
        final Map uiValues = tokenize(value);

        // Values in the itemset
        final List items = getItemset(pipelineContext, true);

        // Actual new value to store
        final String newValue;
        {
            for (Iterator i = items.iterator(); i.hasNext();) {
                final Item currentItem = (Item) i.next();
                final String currentValue = currentItem.getValue();
                if (uiValues.get(currentValue) != null) {
                    // Value is currently selected in the UI
                    instanceValues.put(currentValue, "");
                } else {
                    // Value is currently NOT selected in the UI
                    instanceValues.remove(currentValue);
                }
            }
            // Create resulting string
            final FastStringBuffer sb = new FastStringBuffer(getValue().length() + value.length() * 2);
            int index = 0;
            for (Iterator i = instanceValues.keySet().iterator(); i.hasNext(); index++) {
                final String currentKey = (String) i.next();
                if (index > 0)
                    sb.append(' ');
                sb.append(currentKey);
            }
            newValue = sb.toString();
        }

        super.setExternalValue(pipelineContext, newValue, type);
    }

    /**
     * Produce an external value. This returns the intersection of values in the bound node and values in the control's
     * itemset.
     *
     * @param pipelineContext   current pipeline context
     * @return                  external value for the UI
     */
    protected String evaluateExternalValue(PipelineContext pipelineContext) {

        // Current values in the instance
        final Map instanceValues = tokenize(getValue());

        // Values in the itemset
        final List items = getItemset(pipelineContext, true);

        // Actual value to return is the intersection of values in the instance and values in the itemset
        final String newValue;
        {
            // Create resulting string
            final FastStringBuffer sb = new FastStringBuffer(getValue().length());
            int index = 0;
            for (Iterator i = items.iterator(); i.hasNext(); index++) {
                final Item currentItem = (Item) i.next();
                final String currentValue = currentItem.getValue();
                if (instanceValues.get(currentValue) != null) {
                    if (index > 0)
                        sb.append(' ');
                    sb.append(currentValue);
                }
            }
            newValue = sb.toString();
        }
        return newValue;
    }

    private static Map tokenize(String value) {
        final Map result = new HashMap();
        for (final StringTokenizer st = new StringTokenizer(value); st.hasMoreTokens();) {
            final String token = st.nextToken();
            result.put(token, "");
        }
        return result;
    }
}
