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
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsProperties;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.control.Focus;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsNoSingleNodeContainerControl;
import org.orbeon.oxf.xforms.event.Dispatch;
import org.orbeon.oxf.xforms.event.XFormsEvent;
import org.orbeon.oxf.xforms.event.XFormsEvents;
import org.orbeon.oxf.xforms.event.events.XFormsFocusEvent;
import org.orbeon.oxf.xforms.event.events.XXFormsDialogOpenEvent;
import org.orbeon.oxf.xforms.xbl.XBLContainer;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.xml.sax.helpers.AttributesImpl;
import scala.Tuple3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Represents an extension xxforms:dialog control.
 */
public class XXFormsDialogControl extends XFormsNoSingleNodeContainerControl {

    private String level;
    private boolean close;
    private boolean draggable;
    private String defaultNeighborControlId;
    private boolean initiallyVisible;

    public static class XXFormsDialogControlLocal extends XFormsControl.XFormsControlLocal {
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

    public XXFormsDialogControl(XBLContainer container, XFormsControl parent, Element element, String effectiveId, Map<String, String> state) {
        super(container, parent, element, effectiveId);

        // NOTE: attributes logic duplicated in XXFormsDialogHandler
        this.level = element.attributeValue("level");
        if (this.level == null) {
            // Default is "modeless" for "minimal" appearance, "modal" otherwise
            this.level = getAppearances().contains(XFormsConstants.XFORMS_MINIMAL_APPEARANCE_QNAME) ? "modeless" : "modal";
        }
        this.close = !"false".equals(element.attributeValue("close"));
        this.draggable = !"false".equals(element.attributeValue("draggable"));
        this.defaultNeighborControlId = element.attributeValue("neighbor");
        this.initiallyVisible = "true".equals(element.attributeValue("visible"));

        // Initial local state
        setLocal(new XXFormsDialogControlLocal(initiallyVisible));

        // Restore state if needed
        if (state != null) {
            // NOTE: Don't use getLocalForUpdate() as we don't want to cause initialLocal != currentLocal
            final String visibleString = state.get("visible");
            setLocal(new XXFormsDialogControlLocal("true".equals(visibleString),
                     "true".equals(state.get("constrain")),
                     state.get("neighbor")));
        }
    }

    @Override
    public boolean computeRelevant() {
        // If parent is not relevant then we are not relevant either
        if (!super.computeRelevant()) {
            return false;
        } else {
            // Otherwise we are relevant only if we are selected
            return !isXForms11Switch() || isVisible();
        }
    }

    @Override
    public Tuple3<String, String, String> getJavaScriptInitialization() {
        return getCommonJavaScriptInitialization();
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

            containingDocument().getControls().markDirtySinceLastRequest(false);

            // TODO: Issue here: if the dialog is non-relevant, it can't receive xxforms-dialog-open!
            // SOLUTION: Make dialog itself relevant (if it can be), but content non-relevant
            if (isXForms11Switch()) {
                // Partial refresh
                containingDocument().getControls().doPartialRefresh(this);
            }
        } else if (XFormsEvents.XXFORMS_DIALOG_CLOSE.equals(event.getName())) {
            // Close the dialog

            final XXFormsDialogControlLocal localForUpdate = (XXFormsDialogControlLocal) getLocalForUpdate();
            localForUpdate.visible = false;
            containingDocument().getControls().markDirtySinceLastRequest(false);

            if (isXForms11Switch()) {
                // Partial refresh
                containingDocument().getControls().doPartialRefresh(this);
            }
        }

    }

    @Override
    public void performDefaultAction(XFormsEvent event) {
        if (XFormsEvents.XXFORMS_DIALOG_CLOSE.equals(event.getName())) {
            // If the dialog is closed and the focus is within the dialog, remove the focus
            // NOTE: Ideally, we should get back to the control that had focus before the dialog opened, if possible.
            if (! isVisible() && Focus.isFocusWithinContainer(this))
                Focus.removeFocus(containingDocument());
        } else if (XFormsEvents.XXFORMS_DIALOG_OPEN.equals(event.getName())) {
            // If the dialog is open and the focus has not been set within the dialog, attempt to set the focus within
            if (isVisible() && ! Focus.isFocusWithinContainer(this))
                Dispatch.dispatchEvent(new XFormsFocusEvent(containingDocument(), this));
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
            if (local.neighborControlId != null)
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
    public Set<String> getAllowedExternalEvents() {
        return ALLOWED_EXTERNAL_EVENTS;
    }

    @Override
    public boolean equalsExternal(XFormsControl other) {

        if (other == null)
            return false;

        // NOTE: don't give up on "this == other" because there can be a difference just in XFormsControlLocal

        // NOTE: We only compare on isVisible as we don't support just changing other attributes for now
        final XXFormsDialogControl otherDialog = (XXFormsDialogControl) other;
        if (otherDialog.wasVisible() != isVisible())
            return false;

        return super.equalsExternal(other);
    }

    @Override
    public void outputAjaxDiff(ContentHandlerHelper ch, XFormsControl other,
                               AttributesImpl attributesImpl, boolean isNewlyVisibleSubtree) {

        // If needed, output basic diffs such as changes in class or LHHA
        final boolean doOutputElement = addAjaxAttributes(attributesImpl, isNewlyVisibleSubtree, other);
        if (doOutputElement) {
            ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "control", attributesImpl);
        }

        // NOTE: At this point, this uses visible/hidden. But we could also handle this with relevant="true|false".
        final String neighbor = getNeighborControlId();
        ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "div", new String[] {
                "id", XFormsUtils.namespaceId(containingDocument(), getEffectiveId()),
                "visibility", isVisible() ? "visible" : "hidden",
                (neighbor != null && isVisible()) ? "neighbor" : null, XFormsUtils.namespaceId(containingDocument(), neighbor),
                isVisible() ? "constrain" : null, Boolean.toString(isConstrainToViewport())
        });
    }

    // NOTE: Duplicated in XFormsSwitchControl
    public boolean isXForms11Switch() {
        final String localXForms11Switch = getControlElement().attributeValue(XFormsConstants.XXFORMS_XFORMS11_SWITCH_QNAME);
        if (localXForms11Switch != null)
            return Boolean.parseBoolean(localXForms11Switch);
        else
            return XFormsProperties.isXForms11Switch(containingDocument());
    }
}
