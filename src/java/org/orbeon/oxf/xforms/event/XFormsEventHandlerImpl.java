/**
 *  Copyright (C) 2005 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.event;

import org.dom4j.Element;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.action.XFormsActions;
import org.orbeon.oxf.xforms.action.XFormsActionInterpreter;
import org.apache.commons.lang.StringUtils;

import java.util.*;

/**
 * Represents an XForms (or just plain XML Events) event handler implementation.
 */
public class XFormsEventHandlerImpl implements XFormsEventHandler {

    private Element eventHandlerElement;
    private String containerId;

    private Map eventNames;
    private String[] observerIds;
    private Map targetIds;
    //private String handler;
    private boolean isBubblingPhase;        // "true" means "default" (bubbling), "false" means "capture"
    private boolean isPropagate;            // "true" means "continue", "false" means "stop"
    private boolean isPerformDefaultAction; // "true" means "perform", "false" means "cancel"

    public XFormsEventHandlerImpl(Element eventHandlerElement, String containerId) {

        this.eventHandlerElement = eventHandlerElement;
        this.containerId = containerId;

        // Gather observers
        // NOTE: Supporting space-separated handlers is an extension
        {
            final String observerAttribute = eventHandlerElement.attributeValue(XFormsConstants.XML_EVENTS_OBSERVER_ATTRIBUTE_QNAME);
            if (observerAttribute == null) {
                observerIds = new String[] { containerId };
            } else {
                observerIds = StringUtils.split(observerAttribute);
            }
        }

        // Gather event names
        // NOTE: Supporting space-separated event names is an extension
        {
            final String eventAttribute = eventHandlerElement.attributeValue(XFormsConstants.XML_EVENTS_EVENT_ATTRIBUTE_QNAME);
            eventNames = new HashMap();
            final String[] eventNamesArray = StringUtils.split(eventAttribute);
            for (int i = 0; i < eventNamesArray.length; i++) {
                eventNames.put(eventNamesArray[i], "");
            }
        }

        // Gather target ids
        // NOTE: Supporting space-separated target ids is an extension
        {
            final String targetAttribute = eventHandlerElement.attributeValue(XFormsConstants.XML_EVENTS_TARGET_ATTRIBUTE_QNAME);
            if (targetAttribute == null) {
                targetIds = null;
            } else {
                targetIds = new HashMap();
                final String[] targetIdsArray = StringUtils.split(targetAttribute);
                for (int i = 0; i < targetIdsArray.length; i++) {
                    targetIds.put(targetIdsArray[i], "");
                }
            }
        }

        {
            final String captureString = eventHandlerElement.attributeValue(XFormsConstants.XML_EVENTS_PHASE_ATTRIBUTE_QNAME);
            this.isBubblingPhase = !"capture".equals(captureString);
        }
        {
            final String propagateString = eventHandlerElement.attributeValue(XFormsConstants.XML_EVENTS_PROPAGATE_ATTRIBUTE_QNAME);
            this.isPropagate = !"stop".equals(propagateString);
        }
        {
            final String defaultActionString = eventHandlerElement.attributeValue(XFormsConstants.XML_EVENTS_DEFAULT_ACTION_ATTRIBUTE_QNAME);
            this.isPerformDefaultAction = !"cancel".equals(defaultActionString);
        }
    }

    /**
     * Utility method to extract event handlers.
     *
     * @param containingElement         element possibly containing event handlers
     * @param eventNamesMap             Map<String, String> of event name to ""
     * @return                          Map<String, List<XFormsEventHandler>> of observer id to List of XFormsEventHandler
     */
    public static Map extractEventHandlers(Element containingElement, Map eventNamesMap) {

        // TODO: we should check recursively for all event handlers, except within inline instances

        // Nothing to do if there are no children elements
        final List children = containingElement.elements();
        if (children == null)
            return null;

        Map eventHandlersMap = null;
        final String containerIdAttribute = containingElement.attributeValue("id");
        for (Iterator i = children.iterator(); i.hasNext();) {
            final Element currentElement = (Element) i.next();

            if (XFormsActions.isActionName(currentElement.getNamespaceURI(), currentElement.getName())) {
                final String eventAttribute = currentElement.attributeValue(XFormsConstants.XML_EVENTS_EVENT_ATTRIBUTE_QNAME);
                if (eventAttribute != null) {

                    // Found an action with ev:event attribute
                    if (eventHandlersMap == null)
                        eventHandlersMap = new HashMap();

                    addEventHandler(eventNamesMap, eventHandlersMap, containerIdAttribute, currentElement, eventAttribute);
                }

                // Handle nested actions with ev:observer or ev:target
                // TODO: should check recursively
                if (currentElement.getName().equals(XFormsActions.XFORMS_ACTION_ACTION)) {
                    for (Iterator j = currentElement.elements().iterator(); j.hasNext();) {
                        final Element currentActionElement = (Element) j.next();
                        final String actionEventAttribute = currentActionElement.attributeValue(XFormsConstants.XML_EVENTS_EVENT_ATTRIBUTE_QNAME);
                        if (actionEventAttribute != null &&
                                (currentActionElement.attributeValue(XFormsConstants.XML_EVENTS_OBSERVER_ATTRIBUTE_QNAME) != null
                                    || currentActionElement.attributeValue(XFormsConstants.XML_EVENTS_TARGET_ATTRIBUTE_QNAME) != null)) {

                            // Found a nested action
                            if (eventHandlersMap == null)
                                eventHandlersMap = new HashMap();

                            // Mmh, containerIdAttribute here doesn't make much sense, does it? See XFormsActionInterpreter`
                            addEventHandler(eventNamesMap, eventHandlersMap, containerIdAttribute, currentActionElement, actionEventAttribute);
                        }
                    }
                }
            }
        }
        return eventHandlersMap;
    }

    private static void addEventHandler(Map eventNamesMap, Map eventHandlersMap, String containerIdAttribute, Element currentElement, String eventAttribute) {
        final XFormsEventHandlerImpl newEventHandlerImpl = new XFormsEventHandlerImpl(currentElement, containerIdAttribute);
        final String[] observerIds = newEventHandlerImpl.getObserverIds();
        for (int j = 0; j < observerIds.length; j++) {
            final String currentObserverId = observerIds[j];
            // Get handlers for observer
            final List eventHandlersForObserver;
            {
                final Object currentList = eventHandlersMap.get(currentObserverId);
                if (currentList == null) {
                    eventHandlersForObserver = new ArrayList();
                    eventHandlersMap.put(currentObserverId, eventHandlersForObserver);
                } else {
                    eventHandlersForObserver = (List) currentList;
                }
            }

            // Add event handler
            eventHandlersForObserver.add(newEventHandlerImpl);
        }

        // Remember all event names
        final String[] eventNames = StringUtils.split(eventAttribute);
        for (int j = 0; j < eventNames.length; j++)
            eventNamesMap.put(eventNames[j], "");
    }

    public void handleEvent(PipelineContext pipelineContext, XFormsContainingDocument containingDocument,
                            XFormsEventHandlerContainer eventHandlerContainer, XFormsEvent event) {
        // Create a new top-level action interpreter to handle this event
        new XFormsActionInterpreter(pipelineContext, containingDocument, eventHandlerContainer, eventHandlerElement, containerId)
                .runAction(pipelineContext, event.getTargetObject().getEffectiveId(), eventHandlerContainer, eventHandlerElement);
    }

    public String[] getObserverIds() {
        return observerIds;
    }

    public boolean isBubblingPhase() {
        return isBubblingPhase;
    }

    public boolean isPropagate() {
        return isPropagate;
    }

    public boolean isPerformDefaultAction() {
        return isPerformDefaultAction;
    }

    public boolean isMatchEventName(String eventName) {
        return eventNames.get(eventName) != null;
    }

    public boolean isMatchTarget(String targetId) {
        // Match if no target id is specified, or if any specifed target matches
        return targetIds == null || targetIds.get(targetId) != null;
    }
}
