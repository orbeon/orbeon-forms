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
import org.dom4j.QName;
import org.orbeon.oxf.xforms.XFormsContextStack;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.model.DataModel;
import org.orbeon.oxf.xforms.xbl.XBLContainer;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.om.NodeInfo;

/**
 * A container control which supports value change events. Currently:
 *
 * o xforms:group
 * o xforms:switch
 */
public abstract class XFormsValueContainerControl extends XFormsSingleNodeContainerControl {

    private boolean hasValue;
    private String value;
    private String previousValue;

    public XFormsValueContainerControl(XBLContainer container, XFormsControl parent, Element element, String name, String effectiveId) {
        super(container, parent, element, name, effectiveId);
    }

    @Override
    protected void onCreate() {
        super.onCreate();
        readBinding();
    }

    @Override
    protected void onBindingUpdate(XFormsContextStack.BindingContext oldBinding, XFormsContextStack.BindingContext newBinding) {
        super.onBindingUpdate(oldBinding, newBinding);
        readBinding();
    }

    private void readBinding() {
        final Item boundItem = getBoundItem();
        if (boundItem instanceof NodeInfo && !XFormsUtils.hasChildrenElements((NodeInfo) boundItem)) {
            hasValue = true;
        } else {
            hasValue = false;
        }
    }

    @Override
    protected void evaluateImpl() {
        super.evaluateImpl();

        // Evaluate control values
        if (hasValue && isRelevant()) {
            // Control has value and is relevant
            value = DataModel.getValue(getBoundItem());
        } else {
            // Control doesn't have value or is not relevant
            value = null;
        }
    }

    @Override
    public boolean isValueChanged() {
        // For special uses, we want to allow the group to detect value changes
        final boolean result = !XFormsUtils.compareStrings(previousValue, value);
        previousValue = value;
        return result;
    }

    @Override
    public QName getType() {
        return null;
    }
}
