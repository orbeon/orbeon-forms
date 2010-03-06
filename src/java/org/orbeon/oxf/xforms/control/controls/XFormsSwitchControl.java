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
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.util.PropertyContext;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsProperties;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsValueContainerControl;
import org.orbeon.oxf.xforms.event.events.XFormsDeselectEvent;
import org.orbeon.oxf.xforms.event.events.XFormsSelectEvent;
import org.orbeon.oxf.xforms.xbl.XBLContainer;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.xml.sax.helpers.AttributesImpl;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Represents an xforms:switch container control.
 *
 * NOTE: This keep the "currently selected flag" for all children xforms:case.
 */
public class XFormsSwitchControl extends XFormsValueContainerControl {

    private transient String restoredCaseId;    // used by deserializeLocal() and childrenAdded()

    public static class XFormsSwitchControlLocal extends XFormsControlLocal {
        private XFormsCaseControl selectedCaseControl;

        private XFormsSwitchControlLocal() {
        }

        public XFormsCaseControl getSelectedCaseControl() {
            return selectedCaseControl;
        }
    }

    public XFormsSwitchControl(XBLContainer container, XFormsControl parent, Element element, String name, String id) {
        super(container, parent, element, name, id);

        // Initial local state
        setLocal(new XFormsSwitchControlLocal());
    }

    @Override
    public void childrenAdded(PropertyContext propertyContext) {

        if (getSize() == 0) {
            throw new OXFException("xforms:switch does not contain at least one xforms:case for switch id: " + getEffectiveId());
        }

        if (restoredCaseId != null) {
            // Selected case id was restored from serialization
            // TODO: ideally, selected case id would be made available while sub-tree is created, so that
            // xxforms:case() function would work for nested bindings
            final List<XFormsControl> children = getChildren();
            for (XFormsControl child: children) {
                final XFormsCaseControl currentCaseControl = (XFormsCaseControl) child;
                if (currentCaseControl.getId().equals(restoredCaseId)) {
                    // NOTE: Don't use setSelectedCase() as we don't want to cause initialLocal != currentLocal
                    final XFormsSwitchControlLocal local = (XFormsSwitchControlLocal) getCurrentLocal();
                    local.selectedCaseControl = currentCaseControl;
                    break;
                }
            }
        } else {
            // Store initial selected case information
            final XFormsSwitchControlLocal local = (XFormsSwitchControlLocal) getCurrentLocal();
            local.selectedCaseControl = findSelectedCase();
        }
    }

    /**
     * Set the currently selected case.
     *
     * @param propertyContext       current context
     * @param caseControlToSelect   case control to select
     */
    public void setSelectedCase(PropertyContext propertyContext, XFormsCaseControl caseControlToSelect) {

        if (caseControlToSelect.getParent() != this)
            throw new OXFException("xforms:case is not child of current xforms:switch.");

        final XFormsSwitchControlLocal localForUpdate = (XFormsSwitchControlLocal) getLocalForUpdate();

        final XFormsCaseControl previouslySelectedCaseControl = localForUpdate.selectedCaseControl;
        final boolean isChanging = previouslySelectedCaseControl != caseControlToSelect;
        localForUpdate.selectedCaseControl = caseControlToSelect;

        if (isChanging && propertyContext != null) { // Not sure when propertyContext could be null! It shouldn't be!
            // "This action adjusts all selected attributes on the affected cases to reflect the new state, and then
            // performs the following:"

            // "1. Dispatching an xforms-deselect event to the currently selected case."
            previouslySelectedCaseControl.getXBLContainer().dispatchEvent(propertyContext,
                    new XFormsDeselectEvent(containingDocument, previouslySelectedCaseControl));

            if (isXForms11Switch()) {
                // Partial refresh on the case that is being deselected
                // Do this after xforms-deselect is dispatched
                containingDocument.getControls().doPartialRefresh(propertyContext, previouslySelectedCaseControl);

                // Partial refresh on the case that is being selected
                // Do this before xforms-select is dispatched
                containingDocument.getControls().doPartialRefresh(propertyContext, caseControlToSelect);
            }

            // "2. Dispatching an xforms-select event to the case to be selected."
            caseControlToSelect.getXBLContainer().dispatchEvent(propertyContext,
                    new XFormsSelectEvent(containingDocument, caseControlToSelect));
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
        final List<XFormsControl> children = getChildren();
        for (XFormsControl child: children) {
            final XFormsCaseControl currentCaseControl = (XFormsCaseControl) child;
            if (currentCaseControl.isDefaultSelected()) {
                // Found first case with selected="true"
                return currentCaseControl;
            }
        }
        // Didn't find a case with selected="true" so return first case
        return (XFormsCaseControl) children.get(0);
    }

    @Override
    public Object getBackCopy(PropertyContext propertyContext) {

        final XFormsSwitchControl cloned;

        // We want the new one to point to the children of the cloned nodes, not the children

        // Get initial index as we copy "back" to an initial state
        final XFormsSwitchControlLocal local = (XFormsSwitchControlLocal) getInitialLocal();
        final int selectedCaseIndex =  getChildren().indexOf(local.selectedCaseControl);

        // Clone this and children
        cloned = (XFormsSwitchControl) super.getBackCopy(propertyContext);

        // Update clone's selected case control to point to one of the cloned children
        final XFormsSwitchControlLocal clonedLocal = (XFormsSwitchControlLocal) cloned.getInitialLocal();

        // NOTE: we don't call getLocalForUpdate() because we know that XFormsSwitchControlLocal is safe to write
        // to (super.clone() ensures that we have a new copy)

        clonedLocal.selectedCaseControl = (XFormsCaseControl) cloned.getChildren().get(selectedCaseIndex);

        return cloned;
    }

    @Override
    public Map<String, String> serializeLocal() {
        // Serialize case id
        return Collections.singletonMap("case-id", getSelectedCase().getId());
    }

    @Override
    public void deserializeLocal(Element element) {
        // Deserialize case id
        this.restoredCaseId = element.attributeValue("case-id");
    }

    @Override
    public boolean setFocus() {
        return getSelectedCase().setFocus();
    }

    @Override
    public boolean equalsExternal(PropertyContext propertyContext, XFormsControl other) {

        if (other == null || !(other instanceof XFormsSwitchControl))
            return false;

        // NOTE: don't give up on "this == other" because there can be a difference just in XFormsControlLocal

        final XFormsSwitchControl otherSwitchControl = (XFormsSwitchControl) other;

        // Check whether selected case has changed
        if (!compareSelectedCase(otherSwitchControl))
            return false;

        return super.equalsExternal(propertyContext, other);
    }

    private boolean compareSelectedCase(XFormsSwitchControl otherSwitchControl) {
        return getSelectedCase().getEffectiveId().equals(getOtherSelectedCaseEffectiveId(otherSwitchControl));
    }

    @Override
    public void outputAjaxDiff(PipelineContext pipelineContext, ContentHandlerHelper ch, XFormsControl other,
                               AttributesImpl attributesImpl, boolean isNewlyVisibleSubtree) {
        // Output regular diff
        super.outputAjaxDiff(pipelineContext, ch, other, attributesImpl, isNewlyVisibleSubtree);

        // Output switch-specific diff if needed only
        final XFormsSwitchControl switchControl1 = (XFormsSwitchControl) other;
        if (!compareSelectedCase(switchControl1)) {

            // Output selected case id
            final String selectedCaseEffectiveId = getSelectedCase().getEffectiveId();
            ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "div", new String[]{
                    "id", selectedCaseEffectiveId,
                    "visibility", "visible"
            });

            final String previousSelectedCaseId = getOtherSelectedCaseEffectiveId(switchControl1);
            if (previousSelectedCaseId != null) {
                // Output deselected case ids
                ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "div", new String[]{
                        "id", previousSelectedCaseId,
                        "visibility", "hidden"}
                );
            } else {
                // This is a new switch (can happen with repeat), send all deselected to be sure
                // TODO: This should not be needed because the repeat template should have a reasonable default.
                final List<XFormsControl> children = getChildren();
                if (children != null && children.size() > 0) {
                    for (final XFormsControl caseControl: children) {
                        if (!caseControl.getEffectiveId().equals(selectedCaseEffectiveId)) {
                            ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "div", new String[]{
                                    "id", caseControl.getEffectiveId(),
                                    "visibility", "hidden"
                            });
                        }
                    }
                }
            }
        }
    }

    private String getOtherSelectedCaseEffectiveId(XFormsSwitchControl switchControl1) {
        return (switchControl1 != null) ? ((XFormsSwitchControlLocal) switchControl1.getInitialLocal()).getSelectedCaseControl().getEffectiveId() : null;
    }

    // NOTE: Duplicated in XXFormsDialogControl
    public boolean isXForms11Switch() {
        final String localXForms11Switch = getControlElement().attributeValue(XFormsConstants.XXFORMS_XFORMS11_SWITCH_QNAME);
        if (localXForms11Switch != null)
            return Boolean.parseBoolean(localXForms11Switch);
        else
            return XFormsProperties.isXForms11Switch(containingDocument);
    }
}
