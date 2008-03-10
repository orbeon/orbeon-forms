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
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.processor.XFormsServer;
import org.orbeon.oxf.xforms.action.XFormsAction;
import org.orbeon.oxf.xforms.action.XFormsActionInterpreter;
import org.orbeon.oxf.xforms.event.XFormsEventFactory;
import org.orbeon.oxf.xforms.event.XFormsEventHandlerContainer;
import org.orbeon.oxf.xforms.event.XFormsEventTarget;
import org.orbeon.oxf.xforms.event.XFormsEvent;
import org.orbeon.oxf.xforms.event.events.XFormsCustomEvent;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.value.SequenceExtent;

import java.util.Map;
import java.util.Iterator;

/**
 * 10.1.2 The dispatch Element
 */
public class XFormsDispatchAction extends XFormsAction {
    public void execute(XFormsActionInterpreter actionInterpreter, PipelineContext pipelineContext, String targetId,
                        XFormsEventHandlerContainer eventHandlerContainer, Element actionElement,
                        boolean hasOverriddenContext, Item overriddenContext) {

        final XFormsControls xformsControls = actionInterpreter.getXFormsControls();
        final XFormsContainingDocument containingDocument = actionInterpreter.getContainingDocument();

        // Mandatory attributes
        final String newEventNameAttributeValue = actionElement.attributeValue("name");
        if (newEventNameAttributeValue == null)
            throw new OXFException("Missing mandatory name attribute on xforms:dispatch element.");
        final String newEventTargetIdValue = actionElement.attributeValue("target");
        if (newEventTargetIdValue == null)
            throw new OXFException("Missing mandatory target attribute on xforms:dispatch element.");

        final XFormsContextStack.BindingContext bindingContext = actionInterpreter.getContextStack().getCurrentBindingContext();

        final Map prefixToURIMap = containingDocument.getStaticState().getNamespaceMappings(actionElement.attributeValue("id"));
        final LocationData locationData = (LocationData) actionElement.getData();
        final XFormsContextStack contextStack = actionInterpreter.getContextStack();

        final String resolvedNewEventName;
        {
            // NOP if there is an AVT but no context node
            if (bindingContext.getSingleNode() == null && newEventNameAttributeValue.indexOf('{') != -1)
                return;

            // Resolve AVT
            resolvedNewEventName = XFormsUtils.resolveAttributeValueTemplates(pipelineContext, bindingContext.getSingleNode(),
                    contextStack.getCurrentVariables(), XFormsContainingDocument.getFunctionLibrary(), actionInterpreter.getFunctionContext(), prefixToURIMap, locationData, newEventNameAttributeValue);
        }

        final String resolvedNewEventTargetId;
        {
            // NOP if there is an AVT but no context node
            if (bindingContext.getSingleNode() == null && newEventTargetIdValue.indexOf('{') != -1)
                return;

            // Resolve AVT
            resolvedNewEventTargetId = XFormsUtils.namespaceId(containingDocument,
                    XFormsUtils.resolveAttributeValueTemplates(pipelineContext, bindingContext.getSingleNode(),
                    contextStack.getCurrentVariables(), XFormsContainingDocument.getFunctionLibrary(), actionInterpreter.getFunctionContext(), prefixToURIMap, locationData, newEventTargetIdValue));
        }

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
            final Object tempXFormsEventTarget = (XFormsEventTarget) containingDocument.getObjectById(resolvedNewEventTargetId);
            if (tempXFormsEventTarget != null) {
                // Object with this id exists
                xformsEventTarget = tempXFormsEventTarget;
            } else {
                // Otherwise, try effective id
                final String newEventTargetEffectiveId = xformsControls.getCurrentControlsState().findEffectiveControlId(resolvedNewEventTargetId);
                if (newEventTargetEffectiveId != null) {
                    xformsEventTarget = (XFormsEventTarget) containingDocument.getObjectById(newEventTargetEffectiveId);
                } else {
                    xformsEventTarget = null;
                }
            }
        }

        if (xformsEventTarget instanceof XFormsEventTarget) {
            // Dispatch the event
            final XFormsEvent newEvent = XFormsEventFactory.createEvent(resolvedNewEventName, (XFormsEventTarget) xformsEventTarget, newEventBubbles, newEventCancelable);

            if (newEvent instanceof XFormsCustomEvent) {
                final XFormsCustomEvent customEvent = (XFormsCustomEvent) newEvent;
                // Check if there are parameters specified

                for (Iterator i = actionElement.elements(XFormsConstants.XXFORMS_CONTEXT_QNAME).iterator(); i.hasNext();) {
                    final Element currentContextInfo = (Element) i.next();

                    final String name = currentContextInfo.attributeValue("name");
                    if (name == null)
                        throw new OXFException(XFormsConstants.XXFORMS_CONTEXT_QNAME + " element must have a \"name\" attribute.");

                    final String select = currentContextInfo.attributeValue("select");
                    if (select == null)
                        throw new OXFException(XFormsConstants.XXFORMS_CONTEXT_QNAME + " element must have a \"select\" attribute.");

                    // Evaluate context parameter
                    final SequenceExtent value = XPathCache.evaluateAsExtent(pipelineContext,
                        actionInterpreter.getContextStack().getCurrentNodeset(), actionInterpreter.getContextStack().getCurrentPosition(),
                        select, containingDocument.getNamespaceMappings(actionElement),
                        contextStack.getCurrentVariables(), XFormsContainingDocument.getFunctionLibrary(),
                        contextStack.getFunctionContext(), null,
                        (LocationData) actionElement.getData());

                    customEvent.setAttribute(name, value);
                }
            }

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
