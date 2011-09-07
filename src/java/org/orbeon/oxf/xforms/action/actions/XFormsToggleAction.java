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
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.util.IndentedLogger;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsContextStack;
import org.orbeon.oxf.xforms.XFormsControls;
import org.orbeon.oxf.xforms.action.XFormsAction;
import org.orbeon.oxf.xforms.action.XFormsActionInterpreter;
import org.orbeon.oxf.xforms.control.controls.XFormsCaseControl;
import org.orbeon.oxf.xforms.event.XFormsEvent;
import org.orbeon.oxf.xforms.event.XFormsEventObserver;
import org.orbeon.oxf.xforms.xbl.XBLBindingsBase;
import org.orbeon.saxon.om.Item;

/**
 * 9.2.3 The toggle Element
 */
public class XFormsToggleAction extends XFormsAction {
    public void execute(XFormsActionInterpreter actionInterpreter, XFormsEvent event,
                        XFormsEventObserver eventObserver, Element actionElement,
                        XBLBindingsBase.Scope actionScope, boolean hasOverriddenContext, Item overriddenContext) {

        final XFormsContainingDocument containingDocument = actionInterpreter.getContainingDocument();
        final XFormsContextStack contextStack = actionInterpreter.getContextStack();
        final XFormsContextStack.BindingContext bindingContext = contextStack.getCurrentBindingContext();

        final String caseAttribute = actionElement.attributeValue("case");
        if (caseAttribute == null)
            throw new OXFException("Missing mandatory case attribute on xforms:toggle element.");

        final String caseStaticId;
        if (bindingContext.getSingleItem() != null) {
            caseStaticId = actionInterpreter.resolveAVTProvideValue(actionElement, caseAttribute);
        } else {
            // TODO: Presence of context is not the right way to decide whether to evaluate AVTs or not
            caseStaticId = caseAttribute;
        }

        // "This XForms Action begins by invoking the deferred update behavior."
        containingDocument.synchronizeAndRefresh();

        // Find case control
        final XFormsCaseControl caseControl = (XFormsCaseControl) actionInterpreter.resolveEffectiveControl(actionElement, caseStaticId);
        if (caseControl != null) { // can be null if the switch is not relevant
            // Found control
            if (caseControl.getParent().isRelevant() && !caseControl.isSelected()) {
                // This case is in a relevant switch and not currently selected

                // Actually toggle the xforms:case
                final XFormsControls controls = containingDocument.getControls();
                controls.markDirtySinceLastRequest(false);// NOTE: xxforms:case() function might still be impacted, so this is not quite right
                caseControl.toggle();// this will dispatch events
            }
        } else {
            // "If there is a null search result for the target object and the source object is an XForms action such as
            // dispatch, send, setfocus, setindex or toggle, then the action is terminated with no effect."
            final IndentedLogger indentedLogger = actionInterpreter.getIndentedLogger();
            if (indentedLogger.isDebugEnabled())
                indentedLogger.logDebug("xforms:toggle", "case does not refer to an existing xforms:case element, ignoring action",
                        "case id", caseStaticId);
        }
    }
}
