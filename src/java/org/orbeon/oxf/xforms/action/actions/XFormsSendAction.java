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
import org.orbeon.oxf.xforms.XFormsModelSubmission;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.XFormsContainer;
import org.orbeon.oxf.xforms.action.XFormsAction;
import org.orbeon.oxf.xforms.action.XFormsActionInterpreter;
import org.orbeon.oxf.xforms.event.XFormsEvent;
import org.orbeon.oxf.xforms.event.XFormsEventObserver;
import org.orbeon.oxf.xforms.event.XFormsEventTarget;
import org.orbeon.oxf.xforms.event.events.XFormsSubmitEvent;
import org.orbeon.oxf.xforms.processor.XFormsServer;
import org.orbeon.saxon.om.Item;

/**
 * 10.1.10 The send Element
 */
public class XFormsSendAction extends XFormsAction {
    public void execute(XFormsActionInterpreter actionInterpreter, PipelineContext pipelineContext, String targetId,
                        XFormsEventObserver eventObserver, Element actionElement,
                        boolean hasOverriddenContext, Item overriddenContextt) {

        final XFormsContainer container = actionInterpreter.getContainer();
        final XFormsContainingDocument containingDocument = actionInterpreter.getContainingDocument();

        // Find submission object
        final String submissionId = XFormsUtils.namespaceId(containingDocument, actionElement.attributeValue("submission"));
        if (submissionId == null)
            throw new OXFException("Missing mandatory submission attribute on xforms:send element.");

        final String resolvedSubmissionStaticId;
        {
            // Resolve AVT
            resolvedSubmissionStaticId = resolveAVTProvideValue(actionInterpreter, pipelineContext, actionElement, submissionId, true);
            if (resolvedSubmissionStaticId == null)
                return;
        }

        // Find actual target
        final Object submission = resolveEffectiveObject(actionInterpreter, pipelineContext, eventObserver, resolvedSubmissionStaticId, actionElement);
        if (submission instanceof XFormsModelSubmission) {
            // Dispatch event to submission object
            final XFormsEvent newEvent = new XFormsSubmitEvent((XFormsEventTarget) submission);
            addContextAttributes(actionInterpreter, pipelineContext, actionElement, newEvent);
            container.dispatchEvent(pipelineContext, newEvent);
        } else {
            // "If there is a null search result for the target object and the source object is an XForms action such as
            // dispatch, send, setfocus, setindex or toggle, then the action is terminated with no effect."

            if (XFormsServer.logger.isDebugEnabled())
                containingDocument.logDebug("xforms:send", "submission does not refer to an existing xforms:submission element, ignoring action",
                        new String[] { "submission id", submissionId } );
        }
    }
}
