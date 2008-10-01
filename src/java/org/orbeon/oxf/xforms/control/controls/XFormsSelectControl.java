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
import org.orbeon.oxf.xforms.XFormsContainer;
import org.orbeon.oxf.xforms.XFormsItemUtils;
import org.orbeon.oxf.xforms.XFormsProperties;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.event.XFormsEvent;
import org.orbeon.oxf.xforms.event.events.XFormsDeselectEvent;
import org.orbeon.oxf.xforms.event.events.XFormsSelectEvent;
import org.orbeon.saxon.om.FastStringBuffer;

import java.util.*;

/**
 * Represents an xforms:select control.
 *
 * xforms:select represents items as a list of space-separated tokens.
 */
public class XFormsSelectControl extends XFormsSelect1Control {

    public XFormsSelectControl(XFormsContainer container, XFormsControl parent, Element element, String name, String id) {
        super(container, parent, element, name, id);
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
    public void storeExternalValue(PipelineContext pipelineContext, String value, String type) {

        final String controlValue = getValue(pipelineContext);

        // Actual new value to store
        final String newValue;
        {
            // All items
            final List items = getItemset(pipelineContext, true);

            // Current values in the instance
            final Map instanceValues = tokenize(pipelineContext, controlValue, false);

            // Values currently selected in the UI
            final boolean isEncryptItemValues = XFormsProperties.isEncryptItemValues(containingDocument);
            final Map uiValues = tokenize(pipelineContext, value, isEncryptItemValues);

            // Iterate over all the items
            final List selectEvents = new ArrayList();
            final List deselectEvents = new ArrayList();
            for (Iterator i = items.iterator(); i.hasNext();) {
                final XFormsItemUtils.Item currentItem = (XFormsItemUtils.Item) i.next();
                final String currentItemValue = currentItem.getValue();
                final boolean itemWasSelected = instanceValues.get(currentItemValue) != null;
                final boolean itemIsSelected;
                if (uiValues.get(currentItemValue) != null) {
                    // Value is currently selected in the UI
                    instanceValues.put(currentItemValue, "");
                    itemIsSelected = true;
                } else {
                    // Value is currently NOT selected in the UI
                    instanceValues.remove(currentItemValue);
                    itemIsSelected = false;
                }

                // Handle xforms-select / xforms-deselect
                // TODO: Dispatch to itemset or item once we support doing that
                if (!itemWasSelected && itemIsSelected) {
                    selectEvents.add(new XFormsSelectEvent(this, currentItemValue));
                } else if (itemWasSelected && !itemIsSelected) {
                    deselectEvents.add(new XFormsDeselectEvent(this, currentItemValue));
                }

            }
            // Dispatch xforms-deselect events
            if (deselectEvents.size() > 0) {
                for (Iterator i = deselectEvents.iterator(); i.hasNext();) {
                    final XFormsEvent currentEvent = (XFormsEvent) i.next();
                    currentEvent.getTargetObject().getContainer(containingDocument).dispatchEvent(pipelineContext, currentEvent);
                }
            }
            // Select events must be sent after all xforms-deselect events
            final boolean hasSelectedItem = selectEvents.size() > 0;
            if (hasSelectedItem) {
                for (Iterator i = selectEvents.iterator(); i.hasNext();) {
                    final XFormsEvent currentEvent = (XFormsEvent) i.next();
                    currentEvent.getTargetObject().getContainer(containingDocument).dispatchEvent(pipelineContext, currentEvent);
                }
            }

            // Create resulting string
            final FastStringBuffer sb = new FastStringBuffer(controlValue.length() + value.length() * 2);
            int index = 0;
            for (Iterator i = instanceValues.keySet().iterator(); i.hasNext(); index++) {
                final String currentKey = (String) i.next();
                if (index > 0)
                    sb.append(' ');
                sb.append(currentKey);
            }
            newValue = sb.toString();
        }

        // "newValue" is created so as to ensure that if a value is NOT in the itemset AND we are a closed selection
        // then we do NOT store the value in instance.
        // NOTE: At the moment we don't support open selection here anyway
        super.storeExternalValue(pipelineContext, newValue, type);
    }

    protected void evaluateExternalValue(PipelineContext pipelineContext) {

        final String internalValue = getValue(pipelineContext);
        final String updatedValue;
        if (internalValue == null) {
            updatedValue = null;
        } else {

            // Current values in the instance
            final Map instanceValues = tokenize(pipelineContext, internalValue, false);

            // Values in the itemset
            final List items = getItemset(pipelineContext, true);

            // Actual value to return is the intersection of values in the instance and values in the itemset
            final FastStringBuffer sb = new FastStringBuffer(internalValue.length());
            int index = 0;
            for (Iterator i = items.iterator(); i.hasNext(); index++) {
                final XFormsItemUtils.Item currentItem = (XFormsItemUtils.Item) i.next();
                final String currentValue = currentItem.getValue();
                if (instanceValues.get(currentValue) != null) {
                    if (index > 0)
                        sb.append(' ');

                    sb.append(currentItem.getExternalValue(pipelineContext));
                }
            }
            updatedValue = sb.toString();
        }
        setExternalValue(updatedValue);
    }

    private static Map tokenize(PipelineContext pipelineContext, String value, boolean decryptValues) {
        final Map result = new HashMap();
        if (value != null) {
            for (final StringTokenizer st = new StringTokenizer(value); st.hasMoreTokens();) {
                final String token = st.nextToken();
                // Keep value and decrypt if necessary
                result.put(decryptValues ? XFormsItemUtils.decryptValue(pipelineContext, token) : token, "");
            }
        }
        return result;
    }
}
