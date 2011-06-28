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
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.action.XFormsAction;
import org.orbeon.oxf.xforms.action.XFormsActionInterpreter;
import org.orbeon.oxf.xforms.event.XFormsEvent;
import org.orbeon.oxf.xforms.event.XFormsEventFactory;
import org.orbeon.oxf.xforms.event.XFormsEventObserver;
import org.orbeon.oxf.xforms.event.XFormsEventTarget;
import org.orbeon.oxf.xforms.xbl.XBLBindingsBase;
import org.orbeon.saxon.om.Item;

/**
 * 10.1.2 The dispatch Element
 */
public class XFormsDispatchAction extends XFormsAction {
    public void execute(XFormsActionInterpreter actionInterpreter, XFormsEvent event,
                        XFormsEventObserver eventObserver, Element actionElement,
                        XBLBindingsBase.Scope actionScope, boolean hasOverriddenContext, Item overriddenContext) {

        final XFormsContainingDocument containingDocument = actionInterpreter.getContainingDocument();

        // Mandatory attributes
        final String newEventNameAttributeValue = actionElement.attributeValue(XFormsConstants.NAME_QNAME);
        if (newEventNameAttributeValue == null)
            throw new OXFException("Missing mandatory name attribute on xforms:dispatch element.");

        // NOTE: As of 2009-05, XForms 1.1 gives @targetid priority over @target
        String newEventTargetIdValue = actionElement.attributeValue("targetid");
        if (newEventTargetIdValue == null)
            newEventTargetIdValue = actionElement.attributeValue(XFormsConstants.TARGET_QNAME);
        if (newEventTargetIdValue == null)
            throw new OXFException("Missing mandatory target attribute on xforms:dispatch element.");

        final String resolvedNewEventName;
        {
            // Resolve AVT
            resolvedNewEventName = actionInterpreter.resolveAVTProvideValue(actionElement, newEventNameAttributeValue);
            if (resolvedNewEventName == null)
                return;
        }
        final String resolvedNewEventTargetStaticId;
        {
            // Resolve AVT
            resolvedNewEventTargetStaticId = actionInterpreter.resolveAVTProvideValue(actionElement, newEventTargetIdValue);
            if (resolvedNewEventTargetStaticId == null)
                return;
        }

        // Optional attributes
        final boolean newEventBubbles;
        {
            // "The default value depends on the definition of a custom event. For predefined events, this attribute has no effect."
            // The event factory makes sure that those values are ignored for predefined events
            final String newEventBubblesString = actionInterpreter.resolveAVT(actionElement, "bubbles");
            newEventBubbles = Boolean.valueOf((newEventBubblesString == null) ? "true" : newEventBubblesString);
        }
        final boolean newEventCancelable;
        {
            // "The default value depends on the definition of a custom event. For predefined events, this attribute has no effect."
            // The event factory makes sure that those values are ignored for predefined events
            final String newEventCancelableString = actionInterpreter.resolveAVT(actionElement, "cancelable");
            newEventCancelable = Boolean.valueOf((newEventCancelableString == null) ? "true" : newEventCancelableString);
        }
        final int resolvedDelay;
        {
            // Resolve AVT
            final String delayString = actionInterpreter.resolveAVT(actionElement, "delay");
            resolvedDelay = (delayString == null || delayString.equals("")) ? 0 : Integer.parseInt(delayString);
        }

        if (resolvedDelay <= 0) {
            // Event is dispatched immediately

            // "10.8 The dispatch Element [...] If the delay is not specified or if the given value does not conform
            // to xsd:nonNegativeInteger, then the event is dispatched immediately as the result of the dispatch
            // action."

            // Find actual target
            final Object xformsEventTarget = actionInterpreter.resolveOrFindByEffectiveId(actionElement, resolvedNewEventTargetStaticId);
            if (xformsEventTarget instanceof XFormsEventTarget) {
                // Create and dispatch the event
                final XFormsEvent newEvent = XFormsEventFactory.createEvent(containingDocument, resolvedNewEventName, (XFormsEventTarget) xformsEventTarget, newEventBubbles, newEventCancelable);
                addContextAttributes(actionInterpreter, actionElement, newEvent);
                actionInterpreter.getXBLContainer().dispatchEvent(newEvent);
            } else {
                // "If there is a null search result for the target object and the source object is an XForms action such as
                // dispatch, send, setfocus, setindex or toggle, then the action is terminated with no effect."

                final IndentedLogger indentedLogger = actionInterpreter.getIndentedLogger();
                if (indentedLogger.isDebugEnabled())
                    indentedLogger.logDebug("xforms:dispatch", "cannot find target, ignoring action",
                            "target id", resolvedNewEventTargetStaticId);
            }
        } else {
            // Event is dispatched after a delay

            // "10.8 The dispatch Element [...] the specified event is added to the delayed event queue unless an event
            // with the same name and target element already exists on the delayed event queue. The dispatch action has
            // no effect if the event delay is a non-negative integer and the specified event is already in the delayed
            // event queue. [...] Since an element bearing a particular ID may be repeated, the delayed event queue may
            // contain more than one event with the same name and target IDREF. It is the name and the target run-time
            // element that must be unique."

            // Whether to tell the client to show a progress indicator when sending this event
            final boolean showProgress;
            {
                final String showProgressString = actionInterpreter.resolveAVT(actionElement, XFormsConstants.XXFORMS_SHOW_PROGRESS_QNAME);
                showProgress = !"false".equals(showProgressString);
            }
            final String progressMessage;
            if (showProgress) {
                progressMessage = actionInterpreter.resolveAVT(actionElement, XFormsConstants.XXFORMS_PROGRESS_MESSAGE_QNAME);
            } else {
                progressMessage = null;
            }

            containingDocument.addDelayedEvent(resolvedNewEventName, resolvedNewEventTargetStaticId, newEventBubbles, newEventCancelable, resolvedDelay, false, showProgress, progressMessage);
        }
    }
}
