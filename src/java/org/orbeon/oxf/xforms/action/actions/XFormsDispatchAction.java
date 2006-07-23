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
import org.orbeon.oxf.xforms.event.XFormsEventFactory;
import org.orbeon.oxf.xforms.event.XFormsEventHandlerContainer;
import org.orbeon.oxf.xforms.event.XFormsEventTarget;

/**
 * 10.1.2 The dispatch Element
 */
public class XFormsDispatchAction extends XFormsAction {
    public void execute(XFormsActionInterpreter actionInterpreter, PipelineContext pipelineContext, String targetId, XFormsEventHandlerContainer eventHandlerContainer, Element actionElement) {

        final XFormsControls xformsControls = actionInterpreter.getXFormsControls();
        final XFormsContainingDocument containingDocument = actionInterpreter.getContainingDocument();

        // Mandatory attributes
        final String newEventName = actionElement.attributeValue("name");
        if (newEventName == null)
            throw new OXFException("Missing mandatory name attribute on xforms:dispatch element.");
        final String newEventTargetId = XFormsUtils.namespaceId(containingDocument, actionElement.attributeValue("target"));
        if (newEventTargetId == null)
            throw new OXFException("Missing mandatory target attribute on xforms:dispatch element.");

        // Optional attributes
        final boolean newEventBubbles;
        {
                        final String newEventBubblesString = actionElement.attributeValue("bubbles");
                        // "The default value depends on the definition of a custom event. For predefined events, this attribute has no effect."
                        // The event factory makes sure that those values are ignored for predefined events
                        newEventBubbles = Boolean.valueOf((newEventBubblesString == null) ? "true" : newEventBubblesString).booleanValue();
                    }
        final boolean newEventCancelable;
        {
                        // "The default value depends on the definition of a custom event. For predefined events, this attribute has no effect."
                        // The event factory makes sure that those values are ignored for predefined events
                        final String newEventCancelableString = actionElement.attributeValue("cancelable");
                        newEventCancelable = Boolean.valueOf((newEventCancelableString == null) ? "true" : newEventCancelableString).booleanValue();
                    }

        // Find actual target
        final Object xformsEventTarget;
        {
            final Object tempXFormsEventTarget = (XFormsEventTarget) containingDocument.getObjectById(pipelineContext, newEventTargetId);
            if (tempXFormsEventTarget != null) {
                // Object with this id exists
                xformsEventTarget = tempXFormsEventTarget;
            } else {
                // Otherwise, try effective id
                final String newEventTargetEffectiveId = xformsControls.getCurrentControlsState().findEffectiveControlId(newEventTargetId);
                xformsEventTarget = (XFormsEventTarget) containingDocument.getObjectById(pipelineContext, newEventTargetEffectiveId);
            }
        }

        if (xformsEventTarget == null)
            throw new OXFException("Could not find actual event target on xforms:dispatch element for id: " + newEventTargetId);

        if (xformsEventTarget instanceof XFormsEventTarget) {
            // This can be anything
            containingDocument.dispatchEvent(pipelineContext, XFormsEventFactory.createEvent(newEventName, (XFormsEventTarget) xformsEventTarget, newEventBubbles, newEventCancelable));
        } else {
            throw new OXFException("Invalid event target for id: " + newEventTargetId);
        }
    }
}
