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
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsNoSingleNodeContainerControl;
import org.orbeon.oxf.xforms.xbl.XBLContainer;

/**
 * Represents an xf:case pseudo-control.
 *
 * NOTE: This doesn't keep the "currently selected flag". Instead, the parent xf:switch holds this information.
 */
public class XFormsCaseControl extends XFormsNoSingleNodeContainerControl {

    public XFormsCaseControl(XBLContainer container, XFormsControl parent, Element element, String effectiveId) {
        super(container, parent, element, effectiveId);
    }

    public static boolean isDefaultSelected(Element element) {
        final String selectedAttribute = element.attributeValue("selected");
        return "true".equals(selectedAttribute);
    }

    @Override
    public boolean computeRelevant() {
        // We are relevant only if we are selected
        return super.computeRelevant() && (! getSwitch().isXForms11Switch() || isSelected());
    }

    /**
     * Return whether this is the currently selected case within the current switch.
     */
    public boolean isSelected() {
        return getEffectiveId().equals(getSwitch().getSelectedCaseEffectiveId());
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
        // There are dependencies on toggled cases for:
        //
        // - xxf:case()
        // - case content relevance when XForms 1.1-behavior is enabled
        //
        // Ideally, XPath dependencies should make this smarter.
        //
        containingDocument().requireRefresh();

        getSwitch().setSelectedCase(this);
    }

    private XFormsSwitchControl getSwitch() {
        return (XFormsSwitchControl) parent();
    }
}
