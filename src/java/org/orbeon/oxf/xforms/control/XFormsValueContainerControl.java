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
package org.orbeon.oxf.xforms.control;

import org.dom4j.Element;
import org.orbeon.oxf.util.PropertyContext;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.xbl.XBLContainer;

/**
 * A container control which supports value change events. Currently:
 *
 * o xforms:group
 * o xforms:switch
 */
public abstract class XFormsValueContainerControl extends XFormsSingleNodeContainerControl {

    private String value;
    private String previousValue;

    public XFormsValueContainerControl(XBLContainer container, XFormsControl parent, Element element, String name, String effectiveId) {
        super(container, parent, element, name, effectiveId);
    }

    @Override
    protected void evaluate(PropertyContext propertyContext, boolean isRefresh) {
        super.evaluate(propertyContext, isRefresh);

        // Evaluate control values
        if (isRelevant()) {
            // Control is relevant
            value = XFormsUtils.getBoundItemValue(getBoundItem());
        } else {
            // Control is not relevant
            value = null;
        }

        if (!isRefresh) {
            // Sync value
            previousValue = value;
        }
    }

    @Override
    public void markDirty() {
        super.markDirty();

        // Keep previous value
        previousValue = value;
    }

    @Override
    public boolean isValueChanged() {
        // For special uses, we want to allow the group to detect value changes
        return !XFormsUtils.compareStrings(previousValue, value);
    }

    @Override
    public String getType() {
        return null;
    }
}
