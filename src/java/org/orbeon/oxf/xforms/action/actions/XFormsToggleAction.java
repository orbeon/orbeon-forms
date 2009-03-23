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
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsContextStack;
import org.orbeon.oxf.xforms.XFormsControls;
import org.orbeon.oxf.xforms.action.XFormsAction;
import org.orbeon.oxf.xforms.action.XFormsActionInterpreter;
import org.orbeon.oxf.xforms.control.controls.XFormsCaseControl;
import org.orbeon.oxf.xforms.event.XFormsEventObserver;
import org.orbeon.oxf.xforms.processor.XFormsServer;
import org.orbeon.saxon.om.Item;

/**
 * 9.2.3 The toggle Element
 */
public class XFormsToggleAction extends XFormsAction {
    public void execute(XFormsActionInterpreter actionInterpreter, PipelineContext pipelineContext, String targetId,
                        XFormsEventObserver eventObserver, Element actionElement,
                        boolean hasOverriddenContext, Item overriddenContext) {

        final XFormsContainingDocument containingDocument = actionInterpreter.getContainingDocument();
        final XFormsContextStack contextStack = actionInterpreter.getContextStack();
        final XFormsContextStack.BindingContext bindingContext = contextStack.getCurrentBindingContext();

        final String caseAttribute = actionElement.attributeValue("case");
        if (caseAttribute == null)
            throw new OXFException("Missing mandatory case attribute on xforms:toggle element.");

        final String caseStaticId;
        if (bindingContext.getSingleNode() != null) {
            caseStaticId = resolveAVTProvideValue(actionInterpreter, pipelineContext, actionElement, caseAttribute, true);
        } else {
            // TODO: Presence of context is not the right way to decide whether to evaluate AVTs or not
            caseStaticId = caseAttribute;
        }

        final XFormsCaseControl caseControl = (XFormsCaseControl) resolveEffectiveControl(actionInterpreter, pipelineContext, eventObserver.getEffectiveId(), caseStaticId, actionElement);
        if (caseControl != null) { // can be null if the switch is not relevant
            // Found control
            if (!caseControl.isSelected()) {
                // This case is not currently selected

                // Actually toogle the xforms:case
                final XFormsControls controls = containingDocument.getControls();
                controls.markDirtySinceLastRequest(false);
                caseControl.toggle(pipelineContext);// this will dispatch events
            }
        } else {
            // "If there is a null search result for the target object and the source object is an XForms action such as
            // dispatch, send, setfocus, setindex or toggle, then the action is terminated with no effect."
            if (XFormsServer.logger.isDebugEnabled())
                containingDocument.logDebug("xforms:toggle", "case does not refer to an existing xforms:case element, ignoring action",
                        new String[] { "case id", caseStaticId } );
        }
    }
}
