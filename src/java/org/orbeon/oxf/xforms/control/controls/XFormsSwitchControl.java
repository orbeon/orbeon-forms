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
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.xforms.XFormsContainer;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsSingleNodeContainerControl;
import org.orbeon.oxf.xforms.event.events.XFormsDeselectEvent;
import org.orbeon.oxf.xforms.event.events.XFormsSelectEvent;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Represents an xforms:switch container control.
 *
 * NOTE: This keep the "currently selected flag" for all children xforms:case.
 *
 * TODO: Use inheritance to make this a single-node control that doesn't hold a value.
 */
public class XFormsSwitchControl extends XFormsSingleNodeContainerControl {

    public static class XFormsSwitchControlLocal extends XFormsControlLocal {
        private XFormsCaseControl selectedCaseControl;

        private XFormsSwitchControlLocal() {
        }

        private XFormsSwitchControlLocal(XFormsCaseControl selectedCaseControl) {
            this.selectedCaseControl = selectedCaseControl;
        }

        public XFormsCaseControl getSelectedCaseControl() {
            return selectedCaseControl;
        }
    }

    public XFormsSwitchControl(XFormsContainer container, XFormsControl parent, Element element, String name, String id) {
        super(container, parent, element, name, id);

        // Initial local state
        setLocal(new XFormsSwitchControlLocal());
    }

    public void childrenAdded() {

        if (getSize() == 0) {
            throw new OXFException("xforms:switch does not contain at least one xforms:case for switch id: " + getEffectiveId());
        }

        // Store initial selected case information
        final XFormsSwitchControlLocal local = (XFormsSwitchControlLocal) getCurrentLocal();
        local.selectedCaseControl = findSelectedCase();
    }

    public String getType() {
        return null;
    }

    /**
     * Set the currently selected case.
     */
    public void setSelectedCase(PipelineContext pipelineContext, XFormsCaseControl caseControl) {

        if (caseControl.getParent() != this)
            throw new OXFException("xforms:case is not child of current xforms:switch.");

        final XFormsSwitchControlLocal localForUpdate = (XFormsSwitchControlLocal) getLocalForUpdate();

        final XFormsCaseControl previouslySelectedCaseControl = localForUpdate.selectedCaseControl;
        final boolean isChanging = previouslySelectedCaseControl != caseControl;
        localForUpdate.selectedCaseControl = caseControl;

        if (isChanging && pipelineContext != null) {
            // "This action adjusts all selected attributes on the affected cases to reflect the new state, and then
            // performs the following:"

            // "1. Dispatching an xforms-deselect event to the currently selected case."
            previouslySelectedCaseControl.getContainer().dispatchEvent(pipelineContext, new XFormsDeselectEvent(previouslySelectedCaseControl));

            // "2. Dispatching an xform-select event to the case to be selected."
            caseControl.getContainer().dispatchEvent(pipelineContext, new XFormsSelectEvent(caseControl));
        }
    }

    /**
     * Get the currently selected case.
     *
     * @return currently selected case
     */
    public XFormsCaseControl getSelectedCase() {
        final XFormsSwitchControlLocal local = (XFormsSwitchControlLocal) getCurrentLocal();
        if (local.selectedCaseControl != null) {
            return local.selectedCaseControl;
        } else {
            throw new OXFException("Selected case was not set for xforms:switch: " + getEffectiveId());
        }
    }

    private XFormsCaseControl findSelectedCase() {
        final List children = getChildren();
        for (Iterator i = children.iterator(); i.hasNext();) {
            final XFormsCaseControl currentCaseControl = (XFormsCaseControl) i.next();
            if (currentCaseControl.isDefaultSelected()) {
                // Found first case with selected="true"
                return currentCaseControl;
            }
        }
        // Didn't find a case with selected="true" so return first case
        return (XFormsCaseControl) children.get(0);
    }

    public Object clone() {

        final XFormsSwitchControl cloned;

        // We want the new one to point to the children of the cloned nodes, not the children

        // Get initial index as we copy "back" to an initial state
        final XFormsSwitchControlLocal local = (XFormsSwitchControlLocal) getInitialLocal();
        final int selectedCaseIndex =  getChildren().indexOf(local.selectedCaseControl);

        // Clone this and children
        cloned = (XFormsSwitchControl) super.clone();

        // Update clone's selected case control to point to one of the cloned children
        final XFormsSwitchControlLocal clonedLocal = (XFormsSwitchControlLocal) cloned.getInitialLocal();

        // NOTE: we don't call getLocalForUpdate() because we know that XFormsSwitchControlLocal is safe to write
        // to (super.clone() ensures that we have a new copy)

        clonedLocal.selectedCaseControl = (XFormsCaseControl) cloned.getChildren().get(selectedCaseIndex);

        return cloned;
    }

    public Map serializeLocal() {
        // Serialize case id
        return Collections.singletonMap("case-id", getSelectedCase().getId());
    }

    public void deserializeLocal(Element element) {
        // Deserialize case id
        final String caseId = element.attributeValue("case-id");
        final List children = getChildren();
        for (Iterator i = children.iterator(); i.hasNext();) {
            final XFormsCaseControl currentCaseControl = (XFormsCaseControl) i.next();
            if (currentCaseControl.getId().equals(caseId)) {
                // NOTE: Don't use setSelectedCase() as we don't want to cause initialLocal != currentLocal
                setLocal(new XFormsSwitchControlLocal(currentCaseControl));
                break;
            }
        }
    }
}
