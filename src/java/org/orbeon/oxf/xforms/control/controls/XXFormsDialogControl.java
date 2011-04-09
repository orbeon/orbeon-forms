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
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.util.PropertyContext;
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsNoSingleNodeContainerControl;
import org.orbeon.oxf.xforms.event.XFormsEvent;
import org.orbeon.oxf.xforms.event.XFormsEvents;
import org.orbeon.oxf.xforms.event.events.XFormsFocusEvent;
import org.orbeon.oxf.xforms.event.events.XXFormsDialogOpenEvent;
import org.orbeon.oxf.xforms.xbl.XBLContainer;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.xml.sax.helpers.AttributesImpl;

import java.util.*;

/**
 * Represents an extension xxforms:dialog control.
 */
public class XXFormsDialogControl extends XFormsNoSingleNodeContainerControl {

    private String level;
    private boolean close;
    private boolean draggable;
    private String defaultNeighborControlId;
    private boolean initiallyVisible;

    public static class XXFormsDialogControlLocal extends XFormsControlLocal {
        private boolean visible;
        private boolean constrainToViewport;
        private String neighborControlId;

        private XXFormsDialogControlLocal(boolean visible) {
            this.visible = visible;
        }

        private XXFormsDialogControlLocal(boolean visible, boolean constrainToViewport, String neighborControlId) {
            this.visible = visible;
            this.constrainToViewport = constrainToViewport;
            this.neighborControlId = neighborControlId;
        }

        public boolean isVisible() {
            return visible;
        }
    }

    public XXFormsDialogControl(XBLContainer container, XFormsControl parent, Element element, String name, String effectiveId, Map<String, Element> state) {
        super(container, parent, element, name, effectiveId);

        // NOTE: attributes logic duplicated in XXFormsDialogHandler
        this.level = element.attributeValue("level");
        if (this.level == null) {
            // Default is "modeless" for "minimal" appearance, "modal" otherwise
            this.level = XFormsConstants.XFORMS_MINIMAL_APPEARANCE_QNAME.getName().equals(getAppearance()) ? "modeless" : "modal";
        }
        this.close = !"false".equals(element.attributeValue("close"));
        this.draggable = !"false".equals(element.attributeValue("draggable"));
        this.defaultNeighborControlId = element.attributeValue("neighbor");
        this.initiallyVisible = "true".equals(element.attributeValue("visible"));

        // Initial local state
        setLocal(new XXFormsDialogControlLocal(initiallyVisible));

        // Restore state if needed
        if (state != null) {
            final Element stateElement = state.get(effectiveId);
            // NOTE: Don't use getLocalForUpdate() as we don't want to cause initialLocal != currentLocal
            if (stateElement != null) {
                final String visibleString = stateElement.attributeValue("visible");
                setLocal(new XXFormsDialogControlLocal("true".equals(visibleString),
                        "true".equals(stateElement.attributeValue("constrain")),
                        stateElement.attributeValue("neighbor")));
            } else {
                // special case of unit tests which don't actually include a value
            }
        }
    }

    @Override
    protected boolean computeRelevant() {
        // If parent is not relevant then we are not relevant either
        if (!super.computeRelevant()) {
            return false;
        } else {
            // Otherwise we are relevant only if we are selected
            return !isXForms11Switch() || isVisible();
        }
    }

    @Override
    public boolean hasJavaScriptInitialization() {
        return true;
    }

    public String getLevel() {
        return level;
    }

    public boolean isClose() {
        return close;
    }

    public boolean isDraggable() {
        return draggable;
    }

    public boolean isVisible() {
        final XXFormsDialogControlLocal local = (XXFormsDialogControlLocal) getCurrentLocal();
        return local.visible;
    }

    public boolean wasVisible() {
        final XXFormsDialogControlLocal local = ((XXFormsDialogControl.XXFormsDialogControlLocal) getInitialLocal());
        return local.visible;
    }

    public String getNeighborControlId() {
        final XXFormsDialogControlLocal local = (XXFormsDialogControlLocal) getCurrentLocal();
        return (local.neighborControlId != null) ? local.neighborControlId : defaultNeighborControlId;
    }

    public boolean isConstrainToViewport() {
        final XXFormsDialogControlLocal local = (XXFormsDialogControlLocal) getCurrentLocal();
        return local.constrainToViewport;
    }

    public boolean isInitiallyVisible() {
        return initiallyVisible;
    }

    @Override
    public void performTargetAction(XBLContainer container, XFormsEvent event) {
        super.performTargetAction(container, event);

        if (XFormsEvents.XXFORMS_DIALOG_OPEN.equals(event.getName())) {
            // Open the dialog

            final XXFormsDialogOpenEvent dialogOpenEvent = (XXFormsDialogOpenEvent) event;

            final XXFormsDialogControlLocal localForUpdate = (XXFormsDialogControlLocal) getLocalForUpdate();
            localForUpdate.visible = true;
            localForUpdate.neighborControlId = dialogOpenEvent.getNeighbor();
            localForUpdate.constrainToViewport = dialogOpenEvent.isConstrainToViewport();

            containingDocument.getControls().markDirtySinceLastRequest(false);

            // TODO: Issue here: if the dialog is non-relevant, it can't receive xxforms-dialog-open!
            if (isXForms11Switch()) {
                // Partial refresh
                containingDocument.getControls().doPartialRefresh(this);
            }

            // NOTE: Focus handling now done in XXFormsShowAction, because upon xxforms-dialog-open the user can change
            // the visibility of controls, for example with a <toggle>, which means that the control to focus on must
            // be determined after xxforms-dialog-open has completed.
        }
    }

    @Override
    public void performDefaultAction(XFormsEvent event) {
        if (XFormsEvents.XXFORMS_DIALOG_CLOSE.equals(event.getName())) {
            // Close the dialog

            final XXFormsDialogControlLocal localForUpdate = (XXFormsDialogControlLocal) getLocalForUpdate();
            localForUpdate.visible = false;
            containingDocument.getControls().markDirtySinceLastRequest(false);

            if (isXForms11Switch()) {
                // Partial refresh
                containingDocument.getControls().doPartialRefresh(this);
            }

        }
        super.performDefaultAction(event);
    }

    @Override
    public Map<String, String> serializeLocal() {
        // Serialize
        final XXFormsDialogControlLocal local = (XXFormsDialogControlLocal) getCurrentLocal();
        final Map<String, String> result = new HashMap<String, String>(3);
        result.put("visible", Boolean.toString(local.visible));
        if (local.visible) {
            result.put("constrain", Boolean.toString(local.constrainToViewport));
            result.put("neighbor", local.neighborControlId);
        }

        return result;
    }

//    public void updateContent(PropertyContext propertyContext, boolean isVisible) {
//        final XFormsControls controls = containingDocument.getControls();
//        final ControlTree currentControlTree = controls.getCurrentControlTree();
//
//        final List<XFormsControl> children = getChildren();
//
//        if (isVisible) {
//            // Became visible: create children
//            if (children == null || children.size() == 0) {
//                currentControlTree.createSubTree(propertyContext, this);
//            }
//        } else {
//            // Became invisible: remove children
//            if (children != null && children.size() > 0) {
//                currentControlTree.deindexSubtree(this, false);
//                this.setChildren(null);
//            }
//        }
//    }

    // Only allow xxforms-dialog-close from client
    private static final Set<String> ALLOWED_EXTERNAL_EVENTS = new HashSet<String>();
    static {
        ALLOWED_EXTERNAL_EVENTS.add(XFormsEvents.XXFORMS_DIALOG_CLOSE);
    }

    @Override
    protected Set<String> getAllowedExternalEvents() {
        return ALLOWED_EXTERNAL_EVENTS;
    }

    @Override
    public boolean equalsExternal(PropertyContext propertyContext, XFormsControl other) {

        if (other == null)
            return false;

        // NOTE: don't give up on "this == other" because there can be a difference just in XFormsControlLocal

        // NOTE: We only compare on isVisible as we don't support just changing other attributes for now
        final XXFormsDialogControl otherDialog = (XXFormsDialogControl) other;
        if (otherDialog.wasVisible() != isVisible())
            return false;

        return super.equalsExternal(propertyContext, other);
    }

    @Override
    public void outputAjaxDiff(PipelineContext pipelineContext, ContentHandlerHelper ch, XFormsControl other,
                               AttributesImpl attributesImpl, boolean isNewlyVisibleSubtree) {

        // If needed, output basic diffs such as changes in class or LHHA
        final boolean doOutputElement = addAjaxAttributes(pipelineContext, attributesImpl, isNewlyVisibleSubtree, other);
        if (doOutputElement) {
            ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "control", attributesImpl);
        }

        // NOTE: At this point, this uses visible/hidden. But we could also handle this with relevant="true|false".
        final String neighbor = getNeighborControlId();
        ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "div", new String[] {
                "id", XFormsUtils.namespaceId(containingDocument, getEffectiveId()),
                "visibility", isVisible() ? "visible" : "hidden",
                (neighbor != null && isVisible()) ? "neighbor" : null, XFormsUtils.namespaceId(containingDocument, neighbor),
                isVisible() ? "constrain" : null, Boolean.toString(isConstrainToViewport())
        });
    }

    // NOTE: Duplicated in XFormsSwitchControl
    public boolean isXForms11Switch() {
        final String localXForms11Switch = getControlElement().attributeValue(XFormsConstants.XXFORMS_XFORMS11_SWITCH_QNAME);
        if (localXForms11Switch != null)
            return Boolean.parseBoolean(localXForms11Switch);
        else
            return XFormsProperties.isXForms11Switch(containingDocument);
    }
}
