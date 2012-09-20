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
package org.orbeon.oxf.xforms.action.actions;

import org.dom4j.Element;
import org.orbeon.oxf.util.IndentedLogger;
import org.orbeon.oxf.xforms.XFormsObject;
import org.orbeon.oxf.xforms.action.XFormsAction;
import org.orbeon.oxf.xforms.action.XFormsActionInterpreter;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.controls.XXFormsDialogControl;
import org.orbeon.oxf.xforms.event.Dispatch;
import org.orbeon.oxf.xforms.event.XFormsEvent;
import org.orbeon.oxf.xforms.event.events.XXFormsDialogOpenEvent;
import org.orbeon.oxf.xforms.xbl.Scope;
import org.orbeon.saxon.om.Item;

/**
 * Extension xxforms:show action.
 */
public class XXFormsShowAction extends XFormsAction {

    public void execute(XFormsActionInterpreter actionInterpreter, Element actionElement,
                        Scope actionScope, boolean hasOverriddenContext, Item overriddenContext) {

        // Resolve all attributes as AVTs
        final String dialogStaticOrAbsoluteId = actionInterpreter.resolveAVT(actionElement, "dialog");
        final String neighborEffectiveId;
        {
            final String neighborStaticOrAbsoluteId = actionInterpreter.resolveAVT(actionElement, "neighbor");
            final XFormsControl neighbor = (XFormsControl) ((neighborStaticOrAbsoluteId != null) ? actionInterpreter.resolveObject(actionElement, neighborStaticOrAbsoluteId) : null);
            neighborEffectiveId = (neighbor != null) ? neighbor.getEffectiveId() : null;
        }
        final boolean constrainToViewport;
        {
            final String constrain = actionInterpreter.resolveAVT(actionElement, "constrain");
            constrainToViewport = !"false".equals(constrain);
        }

        if (dialogStaticOrAbsoluteId != null) {
            // Dispatch xxforms-dialog-open event to dialog
            final XFormsObject controlObject = actionInterpreter.resolveObject(actionElement, dialogStaticOrAbsoluteId);
            if (controlObject instanceof XXFormsDialogControl) {
                final XXFormsDialogControl targetDialog = (XXFormsDialogControl) controlObject;

                final XFormsEvent newEvent = new XXFormsDialogOpenEvent(eventProperties(actionInterpreter, actionElement), targetDialog, neighborEffectiveId, constrainToViewport);
                Dispatch.dispatchEvent(newEvent);

            } else {
                final IndentedLogger indentedLogger = actionInterpreter.indentedLogger();
                if (indentedLogger.isDebugEnabled())
                    indentedLogger.logDebug("xxforms:show", "dialog does not refer to an existing xxforms:dialog element, ignoring action",
                            "dialog id", dialogStaticOrAbsoluteId);
            }
        }
    }
}
