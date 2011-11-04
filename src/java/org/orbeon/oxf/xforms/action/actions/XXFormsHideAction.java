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
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.action.XFormsAction;
import org.orbeon.oxf.xforms.action.XFormsActionInterpreter;
import org.orbeon.oxf.xforms.control.controls.XXFormsDialogControl;
import org.orbeon.oxf.xforms.event.XFormsEvent;
import org.orbeon.oxf.xforms.event.XFormsEventObserver;
import org.orbeon.oxf.xforms.event.events.XXFormsDialogCloseEvent;
import org.orbeon.oxf.xforms.xbl.XBLBindingsBase;
import org.orbeon.saxon.om.Item;

/**
 * Extension xxforms:hide action.
 */
public class XXFormsHideAction extends XFormsAction {

    public void execute(XFormsActionInterpreter actionInterpreter, XFormsEvent event,
                        XFormsEventObserver eventObserver, Element actionElement,
                        XBLBindingsBase.Scope actionScope, boolean hasOverriddenContext, Item overriddenContext) {

        final XFormsContainingDocument containingDocument = actionInterpreter.getContainingDocument();

        // Resolve attribute as AVTs
        final String dialogStaticId = actionInterpreter.resolveAVT(actionElement, "dialog");
        if (dialogStaticId == null) {
            // TODO: Should we try to find the dialog containing the action, of the dialog containing the observer or the target causing this event?
        }

        if (dialogStaticId != null) {
            // Dispatch xxforms-dialog-close event to dialog
            final Object controlObject = actionInterpreter.resolveEffectiveControl(actionElement, dialogStaticId);
            if (controlObject instanceof XXFormsDialogControl) {
                final XXFormsDialogControl targetDialog = (XXFormsDialogControl) controlObject;
                final XFormsEvent newEvent = new XXFormsDialogCloseEvent(containingDocument, targetDialog);
                addContextAttributes(actionInterpreter, actionElement, newEvent);
                targetDialog.getXBLContainer(containingDocument).dispatchEvent(newEvent);
            } else {
                final IndentedLogger indentedLogger = actionInterpreter.getIndentedLogger();
                if (indentedLogger.isDebugEnabled())
                    indentedLogger.logDebug("xxforms:hide", "dialog does not refer to an existing xxforms:dialog element, ignoring action",
                            "dialog id", dialogStaticId);
            }
        }
    }
}
