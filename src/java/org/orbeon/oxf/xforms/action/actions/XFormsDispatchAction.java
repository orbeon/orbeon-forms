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
import org.orbeon.oxf.xforms.action.XFormsAction;
import org.orbeon.oxf.xforms.action.XFormsActionInterpreter;
import org.orbeon.oxf.xforms.event.XFormsEvent;
import org.orbeon.oxf.xforms.event.XFormsEventFactory;
import org.orbeon.oxf.xforms.event.XFormsEventHandlerContainer;
import org.orbeon.oxf.xforms.event.XFormsEventTarget;
import org.orbeon.oxf.xforms.processor.XFormsServer;
import org.orbeon.saxon.om.Item;

/**
 * 10.1.2 The dispatch Element
 */
public class XFormsDispatchAction extends XFormsAction {
    public void execute(XFormsActionInterpreter actionInterpreter, PipelineContext pipelineContext, String targetId,
                        XFormsEventHandlerContainer eventHandlerContainer, Element actionElement,
                        boolean hasOverriddenContext, Item overriddenContext) {

        final XFormsContainingDocument containingDocument = actionInterpreter.getContainingDocument();

        // Mandatory attributes
        final String newEventNameAttributeValue = actionElement.attributeValue("name");
        if (newEventNameAttributeValue == null)
            throw new OXFException("Missing mandatory name attribute on xforms:dispatch element.");
        final String newEventTargetIdValue = actionElement.attributeValue("target");
        if (newEventTargetIdValue == null)
            throw new OXFException("Missing mandatory target attribute on xforms:dispatch element.");

        final String resolvedNewEventName;
        {
            // Resolve AVT
            resolvedNewEventName = resolveAVTProvideValue(actionInterpreter, pipelineContext, actionElement, newEventNameAttributeValue, false);
            if (resolvedNewEventName == null)
                return;
        }
        final String resolvedNewEventTargetId;
        {
            // Resolve AVT
            resolvedNewEventTargetId = resolveAVTProvideValue(actionInterpreter, pipelineContext, actionElement, newEventTargetIdValue, true);
            if (resolvedNewEventTargetId == null)
                return;
        }

        // Optional attributes
        final boolean newEventBubbles;
        {
            // "The default value depends on the definition of a custom event. For predefined events, this attribute has no effect."
            // The event factory makes sure that those values are ignored for predefined events
            final String newEventBubblesString = resolveAVT(actionInterpreter, pipelineContext, actionElement, "bubbles", false);
            newEventBubbles = Boolean.valueOf((newEventBubblesString == null) ? "true" : newEventBubblesString).booleanValue();
        }
        final boolean newEventCancelable;
        {
            // "The default value depends on the definition of a custom event. For predefined events, this attribute has no effect."
            // The event factory makes sure that those values are ignored for predefined events
            final String newEventCancelableString = resolveAVT(actionInterpreter, pipelineContext, actionElement, "cancelable", false);
            newEventCancelable = Boolean.valueOf((newEventCancelableString == null) ? "true" : newEventCancelableString).booleanValue();
        }

        // Find actual target
        final Object xformsEventTarget;
        {
            final Object tempXFormsEventTarget = (XFormsEventTarget) containingDocument.getObjectById(resolvedNewEventTargetId);
            if (tempXFormsEventTarget != null) {
                // Object with this id exists
                xformsEventTarget = tempXFormsEventTarget;
            } else {
                // Otherwise, try effective id
                final String newEventTargetEffectiveId = findEffectiveControlId(actionInterpreter, pipelineContext, resolvedNewEventTargetId, actionElement);
                if (newEventTargetEffectiveId != null) {
                    xformsEventTarget = (XFormsEventTarget) containingDocument.getObjectById(newEventTargetEffectiveId);
                } else {
                    xformsEventTarget = null;
                }
            }
        }

        if (xformsEventTarget instanceof XFormsEventTarget) {
            // Create and dispatch the event
            final XFormsEvent newEvent = XFormsEventFactory.createEvent(resolvedNewEventName, (XFormsEventTarget) xformsEventTarget, newEventBubbles, newEventCancelable);
            addContextAttributes(actionInterpreter, pipelineContext, actionElement, newEvent);
            containingDocument.dispatchEvent(pipelineContext, newEvent);
        } else {
            // "If there is a null search result for the target object and the source object is an XForms action such as
            // dispatch, send, setfocus, setindex or toggle, then the action is terminated with no effect."

            if (XFormsServer.logger.isDebugEnabled())
                containingDocument.logDebug("xforms:dispatch", "cannot find target, ignoring action",
                        new String[] { "target id", resolvedNewEventTargetId } );
        }
    }
}
