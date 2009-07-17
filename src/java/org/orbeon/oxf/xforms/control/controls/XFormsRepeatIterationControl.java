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

import org.orbeon.oxf.util.PropertyContext;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsPseudoControl;
import org.orbeon.oxf.xforms.control.XFormsSingleNodeContainerControl;
import org.orbeon.oxf.xforms.xbl.XBLContainer;

import java.util.List;

/**
 * Represents xforms:repeat iteration information.
 *
 * This is not really a control, but an abstraction for xforms:repeat branches.
 *
 * TODO: Use inheritance to make this a single-node control that doesn't hold a value.
 */
public class XFormsRepeatIterationControl extends XFormsSingleNodeContainerControl implements XFormsPseudoControl {
    private int iterationIndex;
    public XFormsRepeatIterationControl(XBLContainer container, XFormsRepeatControl parent, int iterationIndex) {
        // NOTE: Associate this control with the repeat element. This is so that even targets get a proper id
        // NOTE: Effective id of an iteration is parentRepeatId·iteration
        super(container, parent, parent.getControlElement(), "xxforms-repeat-iteration", XFormsUtils.getIterationEffectiveId(parent.getEffectiveId(), iterationIndex));
        this.iterationIndex = iterationIndex;
    }

    public int getIterationIndex() {
        return iterationIndex;
    }

    /**
     * Set a new iteration index. This will cause the nested effective ids to update.
     *
     * This is used to "shuffle" around repeat iterations when repeat nodesets change.
     *
     * @param iterationIndex    new iteration index
     */
    public void setIterationIndex(int iterationIndex) {
        if (this.iterationIndex != iterationIndex) {
            this.iterationIndex = iterationIndex;
            updateEffectiveId();
        }
    }

    @Override
    protected void evaluate(PropertyContext propertyContext) {
        // Just get the MIPs, don't call super.evaluate(), since that would also try to get label, etc.
        getMIPsIfNeeded();
    }

    @Override
    public boolean isStaticReadonly() {
        return false;
    }

    @Override
    public String getType() {
        return null;
    }

    /**
     * Update this control's effective id and its descendants based on the parent's effective id.
     */
    @Override
    public void updateEffectiveId() {

        // Update this iteration's effective id
        setEffectiveId(XFormsUtils.getIterationEffectiveId(getParent().getEffectiveId(), iterationIndex));

        // Update children
        final List<XFormsControl> children = getChildren();
        if (children != null && children.size() > 0) {
            for (XFormsControl currentControl: children) {
                currentControl.updateEffectiveId();
            }
        }
    }
}
