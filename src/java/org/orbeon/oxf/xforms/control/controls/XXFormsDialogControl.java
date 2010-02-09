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
import org.orbeon.oxf.xforms.ControlTree;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsControls;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsNoSingleNodeContainerControl;
import org.orbeon.oxf.xforms.event.XFormsEvent;
import org.orbeon.oxf.xforms.event.XFormsEvents;
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

    public XXFormsDialogControl(XBLContainer container, XFormsControl parent, Element element, String name, String effectiveId) {
        super(container, parent, element, name, effectiveId);
        this.level = element.attributeValue("level");
        if (this.level == null) {
            // Default is "modeless" for "minimal" appearance, "modal" otherwise
            this.level = "minimal".equals(getAppearance()) ? "modeless" : "modal";
        }
        this.close = !"false".equals(element.attributeValue("close"));
        this.draggable = !"false".equals(element.attributeValue("draggable"));
        this.defaultNeighborControlId = element.attributeValue("neighbor");
        this.initiallyVisible = "true".equals(element.attributeValue("visible"));

        // Initial local state
        setLocal(new XXFormsDialogControlLocal(initiallyVisible));
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
    public void performDefaultAction(PropertyContext propertyContext, XFormsEvent event) {
        if (XFormsEvents.XXFORMS_DIALOG_CLOSE.equals(event.getName())) {
            // Close the dialog

            final XXFormsDialogControlLocal localForUpdate = (XXFormsDialogControlLocal) getLocalForUpdate();
            localForUpdate.visible = false;
            containingDocument.getControls().markDirtySinceLastRequest(false);
        } else if (XFormsEvents.XXFORMS_DIALOG_OPEN.equals(event.getName())) {
            // Open the dialog

            final XXFormsDialogOpenEvent dialogOpenEvent = (XXFormsDialogOpenEvent) event;

            final XXFormsDialogControlLocal localForUpdate = (XXFormsDialogControlLocal) getLocalForUpdate();
            localForUpdate.visible = true;
            localForUpdate.neighborControlId = dialogOpenEvent.getNeighbor();
            localForUpdate.constrainToViewport = dialogOpenEvent.isConstrainToViewport();

            containingDocument.getControls().markDirtySinceLastRequest(false);
        }
        super.performDefaultAction(propertyContext, event);
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

    @Override
    public void deserializeLocal(Element element) {
        // Deserialize

        // NOTE: Don't use setSelectedCase() as we don't want to cause initialLocal != currentLocal
        final String visibleString = element.attributeValue("visible");
        setLocal(new XXFormsDialogControlLocal("true".equals(visibleString),
                "true".equals(element.attributeValue("constrain")),
                element.attributeValue("neighbor")));
    }

    public void updateContent(PropertyContext propertyContext, boolean isVisible) {
        final XFormsControls controls = containingDocument.getControls();
        final ControlTree currentControlTree = controls.getCurrentControlTree();

        final List<XFormsControl> children = getChildren();

        if (isVisible) {
            // Became visible: create children
            if (children == null || children.size() == 0) {
                currentControlTree.createSubTree(propertyContext, this);
            }
        } else {
            // Became invisible: remove children
            if (children != null && children.size() > 0) {
                currentControlTree.deindexSubtree(this, false);
                this.setChildren(null);
            }
        }
    }

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

        if (this == other)
            return true;

        // Compare only what matters

        // NOTE: We only compare on isVisible as we don't support just changing other attributes for now
        final XXFormsDialogControl dialogControl1 = (XXFormsDialogControl) other;
        if (dialogControl1.wasVisible() != isVisible())
            return false;

        return super.equalsExternal(propertyContext, other);
    }

    @Override
    public void outputAjaxDiff(PipelineContext pipelineContext, ContentHandlerHelper ch, XFormsControl other,
                               AttributesImpl attributesImpl, boolean isNewlyVisibleSubtree
    ) {

        final String neighbor = getNeighborControlId();
        ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "div", new String[] {
                "id", getEffectiveId(),
                "visibility", isVisible() ? "visible" : "hidden",
                (neighbor != null && isVisible()) ? "neighbor" : null, neighbor,
                isVisible() ? "constrain" : null, Boolean.toString(isConstrainToViewport())
        });
    }
}
