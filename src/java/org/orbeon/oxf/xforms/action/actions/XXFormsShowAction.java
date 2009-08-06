/**
 * Copyright (C) 2009 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.action.actions;

import org.dom4j.Element;
import org.orbeon.oxf.util.IndentedLogger;
import org.orbeon.oxf.util.PropertyContext;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.action.XFormsAction;
import org.orbeon.oxf.xforms.action.XFormsActionInterpreter;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.controls.XXFormsDialogControl;
import org.orbeon.oxf.xforms.event.XFormsEvent;
import org.orbeon.oxf.xforms.event.XFormsEventObserver;
import org.orbeon.oxf.xforms.event.XFormsEventTarget;
import org.orbeon.oxf.xforms.event.events.XXFormsDialogOpenEvent;
import org.orbeon.saxon.om.Item;

/**
 * Extension xxforms:show action.
 */
public class XXFormsShowAction extends XFormsAction {

    public void execute(XFormsActionInterpreter actionInterpreter, PropertyContext propertyContext, String targetId,
                        XFormsEventObserver eventObserver, Element actionElement,
                        boolean hasOverriddenContext, Item overriddenContext) {

        final XFormsContainingDocument containingDocument = actionInterpreter.getContainingDocument();

        // Resolve all attributes as AVTs
        final String dialogStaticId = resolveAVT(actionInterpreter, propertyContext, actionElement, "dialog", true);
        final String effectiveNeighborId;
        {
            final String neighborStaticId = resolveAVT(actionInterpreter, propertyContext, actionElement, "neighbor", true);
            final XFormsControl effectiveNeighbor = (XFormsControl) ((neighborStaticId != null) ? resolveEffectiveControl(actionInterpreter, propertyContext, eventObserver.getEffectiveId(), neighborStaticId, actionElement) : null);
            effectiveNeighborId = (effectiveNeighbor != null) ? effectiveNeighbor.getEffectiveId() : null;
        }
        final boolean constrainToViewport;
        {
            final String constrain = resolveAVT(actionInterpreter, propertyContext, actionElement, "constrain", false);
            constrainToViewport = !"false".equals(constrain);
        }

        if (dialogStaticId != null) {
            // Dispatch xxforms-dialog-open event to dialog
            // TODO: use container.getObjectByEffectiveId() once XBLContainer is able to have local controls
            final Object controlObject = resolveEffectiveControl(actionInterpreter, propertyContext, eventObserver.getEffectiveId(), dialogStaticId, actionElement);
            if (controlObject instanceof XXFormsDialogControl) {
                final XFormsEventTarget eventTarget = (XFormsEventTarget) controlObject;
                final XFormsEvent newEvent = new XXFormsDialogOpenEvent(eventTarget, effectiveNeighborId, constrainToViewport);
                addContextAttributes(actionInterpreter, propertyContext, actionElement, newEvent);
                eventTarget.getXBLContainer(containingDocument).dispatchEvent(propertyContext, newEvent);
            } else {
                final IndentedLogger indentedLogger = actionInterpreter.getIndentedLogger();
                if (indentedLogger.logger.isDebugEnabled())
                    indentedLogger.logDebug("xxforms:show", "dialog does not refer to an existing xxforms:dialog element, ignoring action",
                            "dialog id", dialogStaticId);
            }
        }
    }
}
