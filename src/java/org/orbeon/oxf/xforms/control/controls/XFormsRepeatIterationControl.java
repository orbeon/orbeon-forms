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

import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.xforms.XFormsContainer;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsPseudoControl;
import org.orbeon.oxf.xforms.control.XFormsSingleNodeContainerControl;

import java.util.List;
import java.util.Iterator;

/**
 * Represents xforms:repeat iteration information.
 *
 * This is not really a control, but an abstraction for xforms:repeat branches.
 *
 * TODO: Use inheritance to make this a single-node control that doesn't hold a value.
 */
public class XFormsRepeatIterationControl extends XFormsSingleNodeContainerControl implements XFormsPseudoControl {
    private int iterationIndex;
    public XFormsRepeatIterationControl(XFormsContainer container, XFormsControl parent, int iterationIndex) {
        super(container, parent, null, "xxforms-repeat-iteration", XFormsUtils.getIterationEffectiveId(parent.getEffectiveId(), iterationIndex));
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

    protected void evaluate(PipelineContext pipelineContext) {
        // Just get the MIPs, don't call super.evaluate(), since that would also try to get label, etc.
        getMIPsIfNeeded();
    }

    public boolean isStaticReadonly() {
        return false;
    }

    public String getType() {
        return null;
    }

    /**
     * Update this control's effective id and its descendants based on the parent's effective id.
     */
    public void updateEffectiveId() {

        // Update this iteration's effective id
        setEffectiveId(XFormsUtils.getIterationEffectiveId(getParent().getEffectiveId(), iterationIndex));

        // Update children
        final List children = getChildren();
        if (children != null && children.size() > 0) {
            for (Iterator i = children.iterator(); i.hasNext();) {
                final XFormsControl currentControl = (XFormsControl) i.next();
                currentControl.updateEffectiveId();
            }
        }
    }
}
