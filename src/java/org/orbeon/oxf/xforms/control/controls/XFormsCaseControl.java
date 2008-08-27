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
import org.orbeon.oxf.xforms.XFormsContainer;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsNoSingleNodeContainerControl;
import org.orbeon.oxf.xforms.control.XFormsPseudoControl;
import org.orbeon.oxf.pipeline.api.PipelineContext;

/**
 * Represents an xforms:case pseudo-control.
 *
 * NOTE: This doesn't keep the "currently selected flag". Instead, the parent xforms:switch holds this information.
 */
public class XFormsCaseControl extends XFormsNoSingleNodeContainerControl implements XFormsPseudoControl {

    private boolean defaultSelected;

    public XFormsCaseControl(XFormsContainer container, XFormsControl parent, Element element, String name, String id) {
        super(container, parent, element, name, id);

        // Just keep the value
        final String selectedAttribute = element.attributeValue("selected");
        this.defaultSelected = "true".equals(selectedAttribute);
    }

    /**
     * Return whether this case has selected="true".
     */
    public boolean isDefaultSelected() {
        return defaultSelected;
    }

    /**
     * Return whether this is the currently selected case within the current switch.
     */
    public boolean isSelected() {
        final XFormsSwitchControl switchControl = (XFormsSwitchControl) getParent();
        return switchControl.getSelectedCase() == this;
    }

    /**
     * Return whether to show this case.
     */
    public boolean isVisible() {
        final XFormsSwitchControl switchControl = (XFormsSwitchControl) getParent();
        return isSelected() || switchControl.isStaticReadonly();
    }

    /**
     * Toggle to this case and dispatch events if this causes a change in selected cases.
     */
    public void toggle(PipelineContext pipelineContext) {
        final XFormsSwitchControl switchControl = (XFormsSwitchControl) getParent();
        switchControl.setSelectedCase(pipelineContext, this);
    }
}
