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
package org.orbeon.oxf.xforms.control.controls;

import org.dom4j.Element;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsNoSingleNodeContainerControl;
import org.orbeon.oxf.xforms.control.XFormsPseudoControl;
import org.orbeon.oxf.xforms.xbl.XBLContainer;

import java.util.Map;

/**
 * Represents an xforms:case pseudo-control.
 *
 * NOTE: This doesn't keep the "currently selected flag". Instead, the parent xforms:switch holds this information.
 */
public class XFormsCaseControl extends XFormsNoSingleNodeContainerControl implements XFormsPseudoControl {

    public XFormsCaseControl(XBLContainer container, XFormsControl parent, Element element, String name, String id, Map<String, Element> state) {
        super(container, parent, element, name, id);
    }

    public static boolean isDefaultSelected(Element element) {
        final String selectedAttribute = element.attributeValue("selected");
        return "true".equals(selectedAttribute);
    }

    @Override
    protected boolean computeRelevant() {
        if (!super.computeRelevant()) {
            // If parent is not relevant then we are not relevant either
            return false;
        } else {
            // Otherwise we are relevant only if we are selected
            return !getSwitch().isXForms11Switch() || isSelected();
        }
    }

    /**
     * Return whether this is the currently selected case within the current switch.
     */
    public boolean isSelected() {
        return XFormsUtils.compareStrings(getSwitch().getSelectedCaseEffectiveId(), getEffectiveId());
    }

    /**
     * Return whether to show this case.
     */
    public boolean isVisible() {
        return isSelected() || getSwitch().isStaticReadonly();
    }

    /**
     * Toggle to this case and dispatch events if this causes a change in selected cases.
     *
     */
    public void toggle() {
        getSwitch().setSelectedCase(this);
    }

    private XFormsSwitchControl getSwitch() {
        return (XFormsSwitchControl) getParent();
    }
}
