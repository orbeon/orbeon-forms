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
 */
public class XFormsSelectControl extends XFormsSelect1Control {

    public XFormsSelectControl(XFormsContainingDocument containingDocument, XFormsControl parent, Element element, String name, String id) {
        super(containingDocument, parent, element, name, id);
    }

    public void setExternalValue(PipelineContext pipelineContext, String value, String type) {

        // Current values in the instance
        final Map instanceValues = new HashMap();
        for (final StringTokenizer st = new StringTokenizer(getValue()); st.hasMoreTokens();) {
            final String token = st.nextToken();
            instanceValues.put(token, "");
        }

        // Values currently selected in the UI
        final Map uiValues = new HashMap();
        for (final StringTokenizer st = new StringTokenizer(value); st.hasMoreTokens();) {
            final String token = st.nextToken();
            uiValues.put(token, "");
        }

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
            final FastStringBuffer sb = new FastStringBuffer(100);
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
}
