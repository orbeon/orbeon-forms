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
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsValueContainerControl;
import org.orbeon.oxf.xforms.event.events.XFormsDeselectEvent;
import org.orbeon.oxf.xforms.event.events.XFormsSelectEvent;
import org.orbeon.oxf.xforms.xbl.XBLContainer;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.xml.sax.helpers.AttributesImpl;

import java.util.*;

/**
 * Represents an xforms:switch container control.
 *
 * NOTE: This keep the "currently selected flag" for all children xforms:case.
 */
public class XFormsSwitchControl extends XFormsValueContainerControl {

    private transient boolean restoredState;

    public static class XFormsSwitchControlLocal extends XFormsControlLocal {
        private String selectedCaseControlId;
    }

    public XFormsSwitchControl(XBLContainer container, XFormsControl parent, Element element, String name, String effectiveId, Map<String, Element> state) {
        super(container, parent, element, name, effectiveId);

        // Initial local state
        setLocal(new XFormsSwitchControlLocal());

        // Restore state if needed
        if (state != null) {
            final Element stateElement = state.get(effectiveId);
            // NOTE: Don't use getLocalForUpdate() as we don't want to cause initialLocal != currentLocal
            final XFormsSwitchControlLocal local = (XFormsSwitchControlLocal) getCurrentLocal();
            if (stateElement != null)
                local.selectedCaseControlId = stateElement.attributeValue("case-id");
            else
                local.selectedCaseControlId = findDefaultSelectedCaseId();// special case of unit tests which don't actually include a value

            // Indicate that deserialized state must be used
            restoredState = true;
        }
    }

    @Override
    protected void onCreate() {
        super.onCreate();

        // Ensure that the initial state is set, either from default value, or for state deserialization.
        if (!restoredState) {
            final XFormsSwitchControlLocal local = (XFormsSwitchControlLocal) getLocalForUpdate();
            local.selectedCaseControlId = findDefaultSelectedCaseId();
        } else {
            // NOTE: state deserialized -> state previously serialized -> control was relevant -> onCreate() called
            restoredState = false;
        }
    }

    @Override
    public void childrenAdded() {

        if (getSize() == 0) {
            throw new OXFException("xforms:switch does not contain at least one xforms:case for switch id: " + getEffectiveId());
        }
    }

    private List<XFormsCaseControl> getChildrenCases() {
        final List<XFormsControl> children = getChildren();
        final List<XFormsCaseControl> childrenCases;
        if (children == null || children.size() == 0) {
            childrenCases = Collections.emptyList();
        } else {
            // Filter because XXFormsVariableControl can also be a child
            childrenCases = new ArrayList<XFormsCaseControl>(children.size());
            for (final XFormsControl child: children) {
                if (child instanceof XFormsCaseControl)
                    childrenCases.add((XFormsCaseControl) child);
            }
        }
        return childrenCases;
    }

    /**
     * Set the currently selected case.
     *
     * @param caseControlToSelect   case control to select
     */
    public void setSelectedCase(XFormsCaseControl caseControlToSelect) {

        if (caseControlToSelect.getParent() != this)
            throw new OXFException("xforms:case is not child of current xforms:switch.");

        final XFormsSwitchControlLocal localForUpdate = (XFormsSwitchControlLocal) getLocalForUpdate();

        final XFormsCaseControl previouslySelectedCaseControl = getSelectedCase();
        final boolean isChanging = previouslySelectedCaseControl.getId() != caseControlToSelect.getId();
        localForUpdate.selectedCaseControlId = caseControlToSelect.getId();

        if (isChanging) {
            // "This action adjusts all selected attributes on the affected cases to reflect the new state, and then
            // performs the following:"

            // "1. Dispatching an xforms-deselect event to the currently selected case."
            previouslySelectedCaseControl.getXBLContainer().dispatchEvent(
                    new XFormsDeselectEvent(containingDocument, previouslySelectedCaseControl));

            if (isXForms11Switch()) {
                // Partial refresh on the case that is being deselected
                // Do this after xforms-deselect is dispatched
                containingDocument.getControls().doPartialRefresh(previouslySelectedCaseControl);

                // Partial refresh on the case that is being selected
                // Do this before xforms-select is dispatched
                containingDocument.getControls().doPartialRefresh(caseControlToSelect);
            }

            // "2. Dispatching an xforms-select event to the case to be selected."
            caseControlToSelect.getXBLContainer().dispatchEvent(
                    new XFormsSelectEvent(containingDocument, caseControlToSelect));
        }
    }

    /**
     * Get the effective id of the currently selected case.
     *
     * @return effective id
     */
    public String getSelectedCaseEffectiveId() {
        if (isRelevant()) {
            final XFormsSwitchControlLocal local = (XFormsSwitchControlLocal) getCurrentLocal();
            if (local.selectedCaseControlId != null) {
                return XFormsUtils.getRelatedEffectiveId(getEffectiveId(), local.selectedCaseControlId);
            } else {
                throw new OXFException("Selected case was not set for xforms:switch: " + getEffectiveId());
            }
        } else {
            return null;
        }
    }

    public XFormsCaseControl getSelectedCase() {
        if (isRelevant()) {
            return (XFormsCaseControl) containingDocument.getControls().getObjectByEffectiveId(getSelectedCaseEffectiveId());
        } else {
            return null;
        }
    }

    private String findDefaultSelectedCaseId() {
        final List<Element> caseElements = Dom4jUtils.elements(getControlElement(), XFormsConstants.XFORMS_CASE_QNAME);
        for (final Element caseElement: caseElements) {
            if (XFormsCaseControl.isDefaultSelected(caseElement)) {
                // Found first case with selected="true"
                return caseElement.attributeValue(XFormsConstants.ID_QNAME);
            }
        }
        // Didn't find a case with selected="true" so return first case
        return caseElements.get(0).attributeValue(XFormsConstants.ID_QNAME);
    }

    @Override
    public Object getBackCopy() {

        final XFormsSwitchControl cloned;

        // We want the new one to point to the children of the cloned nodes, not the children

        // Get initial index as we copy "back" to an initial state
        final XFormsSwitchControlLocal initialLocal = (XFormsSwitchControlLocal) getInitialLocal();

        // Clone this and children
        cloned = (XFormsSwitchControl) super.getBackCopy();

        // Update clone's selected case control to point to one of the cloned children
        final XFormsSwitchControlLocal clonedLocal = (XFormsSwitchControlLocal) cloned.getInitialLocal();

        // NOTE: we don't call getLocalForUpdate() because we know that XFormsSwitchControlLocal is safe to write
        // to (super.getBackCopy() ensures that we have a new copy)

        clonedLocal.selectedCaseControlId = initialLocal.selectedCaseControlId;

        return cloned;
    }

    @Override
    public Map<String, String> serializeLocal() {
        // Serialize case id
        return Collections.singletonMap("case-id", XFormsUtils.getStaticIdFromId(getSelectedCaseEffectiveId()));
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
        return XFormsUtils.compareStrings(getSelectedCaseEffectiveId(), getOtherSelectedCaseEffectiveId(otherSwitchControl));
    }

    @Override
    public void outputAjaxDiff(PipelineContext pipelineContext, ContentHandlerHelper ch, XFormsControl other,
                               AttributesImpl attributesImpl, boolean isNewlyVisibleSubtree) {
        // Output regular diff
        super.outputAjaxDiff(pipelineContext, ch, other, attributesImpl, isNewlyVisibleSubtree);

        // Output switch-specific diff if needed only
        final XFormsSwitchControl otherSwitchControl = (XFormsSwitchControl) other;
        if (!compareSelectedCase(otherSwitchControl)) {

            if (isRelevant()) {

                // Output newly selected case id
                final String selectedCaseEffectiveId = getSelectedCaseEffectiveId();
                assert selectedCaseEffectiveId != null;
                ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "div", new String[]{
                        "id", XFormsUtils.namespaceId(containingDocument, selectedCaseEffectiveId),
                        "visibility", "visible"
                });

                if (otherSwitchControl != null && otherSwitchControl.isRelevant()) {
                    // Used to be relevant, simply output deselected case ids
                    final String previousSelectedCaseId = getOtherSelectedCaseEffectiveId(otherSwitchControl);
                    assert previousSelectedCaseId != null;
                    ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "div", new String[]{
                            "id", XFormsUtils.namespaceId(containingDocument, previousSelectedCaseId),
                            "visibility", "hidden"}
                    );
                } else {
                    // Control was not relevant, send all deselected to be sure
                    // TODO: This should not be needed because the repeat template should have a reasonable default.
                    final List<XFormsCaseControl> children = getChildrenCases();
                    if (children != null && children.size() > 0) {
                        for (final XFormsCaseControl caseControl: children) {
                            if (!caseControl.getEffectiveId().equals(selectedCaseEffectiveId)) {
                                ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "div", new String[]{
                                        "id", XFormsUtils.namespaceId(containingDocument, caseControl.getEffectiveId()),
                                        "visibility", "hidden"
                                });
                            }
                        }
                    }
                }
            }
        }
    }

    private String getOtherSelectedCaseEffectiveId(XFormsSwitchControl switchControl1) {
        if (switchControl1 != null && switchControl1.isRelevant()) {
            final String selectedCaseId = ((XFormsSwitchControlLocal) switchControl1.getInitialLocal()).selectedCaseControlId;
            return XFormsUtils.getRelatedEffectiveId(switchControl1.getEffectiveId(), selectedCaseId);
        } else {
            return null;
        }
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
