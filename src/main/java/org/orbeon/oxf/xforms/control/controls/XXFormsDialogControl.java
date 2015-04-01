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
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.control.Focus;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsNoSingleNodeContainerControl;
import org.orbeon.oxf.xforms.event.Dispatch;
import org.orbeon.oxf.xforms.event.XFormsEvent;
import org.orbeon.oxf.xforms.event.XFormsEvents;
import org.orbeon.oxf.xforms.event.events.XFormsFocusEvent;
import org.orbeon.oxf.xforms.event.events.XXFormsDialogOpenEvent;
import org.orbeon.oxf.xforms.state.ControlState;
import org.orbeon.oxf.xforms.xbl.XBLContainer;
import org.orbeon.oxf.xml.XMLReceiverHelper;
import org.xml.sax.helpers.AttributesImpl;
import scala.Option;
import scala.Tuple3;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents an extension xxf:dialog control.
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

    public XXFormsDialogControl(XBLContainer container, XFormsControl parent, Element element, String effectiveId) {
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
    }

    @Override
    public void onCreate(boolean restoreState, Option<ControlState> state) {
        super.onCreate(restoreState, state);

        // Restore state if needed
        if (state.isDefined()) {
            final ControlState controlState = state.get();
            final Map<String, String> keyValues = controlState.keyValuesJava();

            setLocal(
                new XXFormsDialogControlLocal(
                    "true".equals(keyValues.get("visible")),
                    "true".equals(keyValues.get("constrain")),
                    keyValues.get("neighbor")
                )
            );
        } else if (restoreState) {
            // This can happen with xxf:dynamic, which does not guarantee the stability of ids, therefore state for a
            // particular control might not be found.
            setLocal(
                new XXFormsDialogControlLocal(initiallyVisible)
            );
        }
    }

    @Override
    public Tuple3<String, String, String> getJavaScriptInitialization() {
        return getCommonJavaScriptInitialization();
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

    @Override
    public void performTargetAction(XFormsEvent event) {
        super.performTargetAction(event);

        if (XFormsEvents.XXFORMS_DIALOG_OPEN.equals(event.name())) {
            // Open the dialog

            final XXFormsDialogOpenEvent dialogOpenEvent = (XXFormsDialogOpenEvent) event;

            final XXFormsDialogControlLocal localForUpdate = (XXFormsDialogControlLocal) getLocalForUpdate();
            localForUpdate.visible = true;
            localForUpdate.neighborControlId = dialogOpenEvent.jNeighbor();
            localForUpdate.constrainToViewport = dialogOpenEvent.constrainToViewport();

            containingDocument().getControls().markDirtySinceLastRequest(true);
            containingDocument().getControls().doPartialRefresh(this);
        } else if (XFormsEvents.XXFORMS_DIALOG_CLOSE.equals(event.name())) {
            // Close the dialog

            final XXFormsDialogControlLocal localForUpdate = (XXFormsDialogControlLocal) getLocalForUpdate();
            localForUpdate.visible = false;
            containingDocument().getControls().markDirtySinceLastRequest(false);
            containingDocument().getControls().doPartialRefresh(this);
        }
    }

    @Override
    public void performDefaultAction(XFormsEvent event) {
        if (XFormsEvents.XXFORMS_DIALOG_CLOSE.equals(event.name())) {
            // If the dialog is closed and the focus is within the dialog, remove the focus
            // NOTE: Ideally, we should get back to the control that had focus before the dialog opened, if possible.
            if (! isVisible() && Focus.isFocusWithinContainer(this))
                Focus.removeFocus(containingDocument());
        } else if (XFormsEvents.XXFORMS_DIALOG_OPEN.equals(event.name())) {
            // If the dialog is open and the focus has not been set within the dialog, attempt to set the focus within
            if (isVisible() && ! Focus.isFocusWithinContainer(this))
                Dispatch.dispatchEvent(new XFormsFocusEvent(this, false));
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
    public void outputAjaxDiff(
        XMLReceiverHelper ch,
        XFormsControl other,
        AttributesImpl attributesImpl,
        boolean isNewlyVisibleSubtree
    ) {

        // If needed, output basic diffs such as changes in class or LHHA
        {
            final boolean doOutputElement = addAjaxAttributes(attributesImpl, isNewlyVisibleSubtree, other);
            if (doOutputElement) {
                ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "control", attributesImpl);
            }
        }

        {
            // NOTE: This uses visible/hidden. But we could also handle this with relevant="true|false".
            // 2015-04-01: Unsure if note above still makes sense.
            final XXFormsDialogControl otherDialog = (XXFormsDialogControl) other;
            boolean doOutputElement = false;

            final AttributesImpl atts = new AttributesImpl();
            atts.addAttribute("", "id", "id", XMLReceiverHelper.CDATA, XFormsUtils.namespaceId(containingDocument(), getEffectiveId()));

            final boolean visible = isVisible();
            if (visible != otherDialog.wasVisible()) {
                atts.addAttribute("", "visibility", "visibility", XMLReceiverHelper.CDATA, visible ? "visible" : "hidden");
                doOutputElement = true;
            }

            if (visible) {
                final String neighbor = getNeighborControlId();
                if (neighbor != null && ! neighbor.equals(otherDialog.getNeighborControlId())) {
                    atts.addAttribute("", "neighbor", "neighbor", XMLReceiverHelper.CDATA, XFormsUtils.namespaceId(containingDocument(), neighbor));
                    doOutputElement = true;
                }
                final boolean constrain = isConstrainToViewport();
                if (constrain != otherDialog.isConstrainToViewport()) {
                    atts.addAttribute("", "constrain", "constrain", XMLReceiverHelper.CDATA, Boolean.toString(constrain));
                    doOutputElement = true;
                }
            }

            if (doOutputElement) {
                ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "div", atts);
            }
        }
    }

    public boolean contentVisible() {
        return isVisible();
    }
}
