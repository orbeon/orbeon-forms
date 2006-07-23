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
import org.orbeon.oxf.xforms.XFormsControls;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.action.XFormsAction;
import org.orbeon.oxf.xforms.action.XFormsActionInterpreter;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.event.XFormsEventHandlerContainer;
import org.orbeon.oxf.xforms.event.XFormsEventTarget;
import org.orbeon.oxf.xforms.event.events.XFormsFocusEvent;

/**
 * 10.1.7 The setfocus Element
 */
public class XFormsSetfocusAction extends XFormsAction {
    public void execute(XFormsActionInterpreter actionInterpreter, PipelineContext pipelineContext, String targetId, XFormsEventHandlerContainer eventHandlerContainer, Element actionElement) {

        final XFormsControls xformsControls = actionInterpreter.getXFormsControls();
        final XFormsContainingDocument containingDocument = actionInterpreter.getContainingDocument();

        final String controlId = XFormsUtils.namespaceId(containingDocument, actionElement.attributeValue("control"));
        if (controlId == null)
            throw new OXFException("Missing mandatory 'control' attribute on xforms:control element.");
        final String effectiveControlId = xformsControls.getCurrentControlsState().findEffectiveControlId(controlId);
        if (effectiveControlId == null)
            throw new OXFException("Could not find actual control on xforms:setfocus element for control: " + controlId);

        final Object controlObject = containingDocument.getObjectById(pipelineContext, effectiveControlId);

        if (!(controlObject instanceof XFormsControl))
            throw new OXFException("xforms:setfocus attribute 'control' must refer to a control: " + controlId);

        containingDocument.dispatchEvent(pipelineContext, new XFormsFocusEvent((XFormsEventTarget) controlObject));
    }
}
