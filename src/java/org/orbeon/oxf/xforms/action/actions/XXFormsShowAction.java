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
package org.orbeon.oxf.xforms.action.actions;

import org.dom4j.Element;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsControls;
import org.orbeon.oxf.xforms.action.XFormsAction;
import org.orbeon.oxf.xforms.action.XFormsActionInterpreter;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.controls.XXFormsDialogControl;
import org.orbeon.oxf.xforms.event.XFormsEvent;
import org.orbeon.oxf.xforms.event.XFormsEventHandlerContainer;
import org.orbeon.oxf.xforms.event.XFormsEventTarget;
import org.orbeon.oxf.xforms.event.events.XXFormsDialogOpenEvent;
import org.orbeon.oxf.xforms.processor.XFormsServer;
import org.orbeon.saxon.om.Item;

/**
 * Extension xxforms:show action.
 */
public class XXFormsShowAction extends XFormsAction {

    public void execute(XFormsActionInterpreter actionInterpreter, PipelineContext pipelineContext, String targetId,
                        XFormsEventHandlerContainer eventHandlerContainer, Element actionElement,
                        boolean hasOverriddenContext, Item overriddenContext) {

        final XFormsControls xformsControls = actionInterpreter.getXFormsControls();
        final XFormsContainingDocument containingDocument = actionInterpreter.getContainingDocument();

        // Resolve all attributes as AVTs
        final String dialogId = resolveAVT(actionInterpreter, pipelineContext, actionElement, "dialog", true);
        final String effectiveNeighborId;
        {
            final String neighborId = resolveAVT(actionInterpreter, pipelineContext, actionElement, "neighbor", true);
            final XFormsControl effectiveNeighbor = (XFormsControl) ((neighborId != null) ? resolveEffectiveControl(actionInterpreter, pipelineContext, eventHandlerContainer.getEffectiveId(), neighborId, actionElement) : null);
            effectiveNeighborId = (effectiveNeighbor != null) ? effectiveNeighbor.getEffectiveId() : null;
        }
        final boolean constrainToViewport;
        {
            final String constrain = resolveAVT(actionInterpreter, pipelineContext, actionElement, "constrain", false);
            constrainToViewport = !"false".equals(constrain);
        }

        if (dialogId != null) {
            // Dispatch xxforms-dialog-open event to dialog
            final Object controlObject = (dialogId != null) ? xformsControls.getObjectByEffectiveId(dialogId) : null;// xxx fix not effective
            if (controlObject instanceof XXFormsDialogControl) {

                final XFormsEvent newEvent = new XXFormsDialogOpenEvent((XFormsEventTarget) controlObject, effectiveNeighborId, constrainToViewport);
                addContextAttributes(actionInterpreter, pipelineContext, actionElement, newEvent);
                containingDocument.dispatchEvent(pipelineContext, newEvent);
            } else {
                if (XFormsServer.logger.isDebugEnabled())
                containingDocument.logDebug("xxforms:show", "dialog does not refer to an existing xxforms:dialog element, ignoring action",
                        new String[] { "dialog id", dialogId } );
            }
        }
    }
}
