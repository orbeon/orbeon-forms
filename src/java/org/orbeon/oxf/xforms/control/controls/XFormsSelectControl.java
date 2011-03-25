/**
 * Copyright (C) 2009 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.control.controls;

import org.apache.commons.lang.StringUtils;
import org.dom4j.Element;
import org.orbeon.oxf.util.PropertyContext;
import org.orbeon.oxf.xforms.analysis.XPathDependencies;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.event.XFormsEvent;
import org.orbeon.oxf.xforms.event.events.XFormsDeselectEvent;
import org.orbeon.oxf.xforms.event.events.XFormsSelectEvent;
import org.orbeon.oxf.xforms.itemset.Item;
import org.orbeon.oxf.xforms.itemset.Itemset;
import org.orbeon.oxf.xforms.itemset.XFormsItemUtils;
import org.orbeon.oxf.xforms.xbl.XBLContainer;

import java.util.*;

/**
 * Represents an xforms:select control.
 *
 * xforms:select represents items as a list of space-separated tokens.
 */
public class XFormsSelectControl extends XFormsSelect1Control {

    public XFormsSelectControl(XBLContainer container, XFormsControl parent, Element element, String name, String id) {
        super(container, parent, element, name, id);
    }

    /**
     * Set an external value. This consists of a list of space-separated tokens.
     *
     * o Itemset values which are in the list of tokens are merged with the bound control's value.
     * o Itemset values which are not in the list of tokens are removed from the bound control's value.
     *
     * @param propertyContext   current context
     * @param value             list of tokens from the UI
     * @param type              should probably be null
     */
    @Override
    public void storeExternalValue(PropertyContext propertyContext, String value, String type) {

        final String controlValue = getValue();

        // Actual new value to store
        final String newValue;
        {
            // All items
            final Itemset itemset = getItemset(propertyContext);
            // Current values in the instance
            final Set<String> instanceValues = tokenize(propertyContext, controlValue, false);

            // Values currently selected in the UI
            final Set<String> uiValues = tokenize(propertyContext, value, isEncryptItemValues());

            // Iterate over all the items
            final List<XFormsSelectEvent> selectEvents = new ArrayList<XFormsSelectEvent>();
            final List<XFormsDeselectEvent> deselectEvents = new ArrayList<XFormsDeselectEvent>();

            for (Item currentItem: itemset.toList()) {
                final String currentItemValue = currentItem.getValue();
                final boolean itemWasSelected = instanceValues.contains(currentItemValue);
                final boolean itemIsSelected;
                if (uiValues.contains(currentItemValue)) {
                    // Value is currently selected in the UI
                    instanceValues.add(currentItemValue);
                    itemIsSelected = true;
                } else {
                    // Value is currently NOT selected in the UI
                    instanceValues.remove(currentItemValue);
                    itemIsSelected = false;
                }

                // Handle xforms-select / xforms-deselect
                // TODO: Dispatch to itemset or item once we support doing that
                if (!itemWasSelected && itemIsSelected) {
                    selectEvents.add(new XFormsSelectEvent(containingDocument, this, currentItemValue));
                } else if (itemWasSelected && !itemIsSelected) {
                    deselectEvents.add(new XFormsDeselectEvent(containingDocument, this, currentItemValue));
                }
            }
            // Dispatch xforms-deselect events
            if (deselectEvents.size() > 0) {
                for (XFormsEvent currentEvent: deselectEvents) {
                    currentEvent.getTargetObject().getXBLContainer(containingDocument).dispatchEvent(propertyContext, currentEvent);
                }
            }
            // Select events must be sent after all xforms-deselect events
            final boolean hasSelectedItem = selectEvents.size() > 0;
            if (hasSelectedItem) {
                for (XFormsEvent currentEvent: selectEvents) {
                    currentEvent.getTargetObject().getXBLContainer(containingDocument).dispatchEvent(propertyContext, currentEvent);
                }
            }

            // Create resulting string
            final StringBuilder sb = new StringBuilder(controlValue.length() + value.length() * 2);
            int index = 0;
            for (Iterator<String> i = instanceValues.iterator(); i.hasNext(); index++) {
                final String currentKey = i.next();
                if (index > 0)
                    sb.append(' ');
                sb.append(currentKey);
            }
            newValue = sb.toString();
        }

        // "newValue" is created so as to ensure that if a value is NOT in the itemset AND we are a closed selection
        // then we do NOT store the value in instance.
        // NOTE: At the moment we don't support open selection here anyway
        super.storeExternalValue(propertyContext, newValue, type);
    }

    @Override
    protected void markDirtyImpl(XPathDependencies xpathDependencies) {

        // Default implementation
        super.markDirtyImpl(xpathDependencies);

        if (!isExternalValueDirty() && xpathDependencies.requireItemsetUpdate(getPrefixedId())) {
            // If the itemset has changed but the value has not changed, the external value might still need to be
            // re-evaluated.
            markExternalValueDirty();
        }
    }

    @Override
    protected void evaluateExternalValue(PropertyContext propertyContext) {

        final String internalValue = getValue();
        final String updatedValue;
        if (StringUtils.isEmpty(internalValue)) {
            // Keep null or ""
            updatedValue = internalValue;
        } else {
            // Values in the itemset
            final Itemset itemset = getItemset(propertyContext);
            if (itemset != null) {

                // Current values in the instance
                final Set<String> instanceValues = tokenize(propertyContext, internalValue, false);

                // Actual value to return is the intersection of values in the instance and values in the itemset
                final StringBuilder sb = new StringBuilder(internalValue.length());
                int index = 0;
                for (Item currentItem: itemset.toList()) {
                    final String currentValue = currentItem.getValue();
                    if (instanceValues.contains(currentValue)) {
                        if (index > 0)
                            sb.append(' ');

                        sb.append(currentItem.getExternalValue(propertyContext));

                        index++;
                    }
                }
                updatedValue = sb.toString();
            } else {
                // Null itemset probably means the control was non-relevant. This should be handled better: if the
                // control is not relevant, it should simply not be evaluated.
                updatedValue = null;
            }
        }
        setExternalValue(updatedValue);
    }

    private static Set<String> tokenize(PropertyContext propertyContext, String value, boolean decryptValues) {
        final Set<String> result;
        if (value != null) {
            result = new LinkedHashSet<String>();
            for (final StringTokenizer st = new StringTokenizer(value); st.hasMoreTokens();) {
                final String token = st.nextToken();
                // Keep value and decrypt if necessary
                result.add(decryptValues ? XFormsItemUtils.decryptValue(propertyContext, token) : token);
            }
        } else {
            result = Collections.emptySet();
        }
        return result;
    }
}
