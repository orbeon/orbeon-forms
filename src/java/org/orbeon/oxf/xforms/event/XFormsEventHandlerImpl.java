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

import org.apache.commons.lang.StringUtils;
import org.dom4j.Element;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.control.XFormsComponentControl;
import org.orbeon.oxf.xforms.action.XFormsActionInterpreter;
import org.orbeon.oxf.xforms.xbl.XBLContainer;
import org.orbeon.oxf.util.PropertyContext;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents an XForms (or just plain XML Events) event handler implementation.
 */
public class XFormsEventHandlerImpl implements XFormsEventHandler {

    private final Element eventHandlerElement;
    private final String ancestorObserverStaticId;

    private final Map<String, String> eventNames;
    private final boolean isAllEvents;
    private final String[] observerStaticIds;
    private final Map<String, String> targetStaticIds;
    //private final String handler;
    private final boolean isBubblingPhase;        // "true" means "default" (bubbling), "false" means "capture"
    private final boolean isPropagate;            // "true" means "continue", "false" means "stop"
    private final boolean isPerformDefaultAction; // "true" means "perform", "false" means "cancel"

    private final boolean isXBLHandler;

    /**
     * Initialize an action handler based on an action element.
     *
     * @param eventHandlerElement       action element e.g. xforms:action
     * @param ancestorObserverStaticId  static id of the closest ancestor observer, or null
     */
    public XFormsEventHandlerImpl(Element eventHandlerElement, String ancestorObserverStaticId) {
        this(eventHandlerElement, ancestorObserverStaticId, false,
                eventHandlerElement.attributeValue(XFormsConstants.XML_EVENTS_EV_OBSERVER_ATTRIBUTE_QNAME),
                eventHandlerElement.attributeValue(XFormsConstants.XML_EVENTS_EV_EVENT_ATTRIBUTE_QNAME),
                eventHandlerElement.attributeValue(XFormsConstants.XML_EVENTS_EV_TARGET_ATTRIBUTE_QNAME),
                eventHandlerElement.attributeValue(XFormsConstants.XML_EVENTS_EV_PHASE_ATTRIBUTE_QNAME),
                eventHandlerElement.attributeValue(XFormsConstants.XML_EVENTS_EV_PROPAGATE_ATTRIBUTE_QNAME),
                eventHandlerElement.attributeValue(XFormsConstants.XML_EVENTS_EV_DEFAULT_ACTION_ATTRIBUTE_QNAME));
    }

    /**
     * Initialize an action handler based on a handler element and individual parameters.
     *
     * @param eventHandlerElement       event handler element (e.g. xbl:handler or xforms:action)
     * @param ancestorObserverStaticId  static id of the closest ancestor observer, or null
     * @param isXBLHandler              whether the handler is an XBL handler (i.e. xbl:handler)
     * @param observers                 space-separated list of observers, or null if parent element
     * @param eventNamesAttribute       space-separated list of event names
     * @param targets                   space-separated list of event targets
     * @param phase                     event phase ("capture" | "default", poss. more later as XBL has "capture" | "target" | "bubble" | "default-action")
     * @param propagate                 whether event must propagate ("stop" | "continue")
     * @param defaultAction             whether default action must run ("cancel" | "perform")
     */
    public XFormsEventHandlerImpl(Element eventHandlerElement, String ancestorObserverStaticId, boolean isXBLHandler,
                                  String observers, String eventNamesAttribute, String targets, String phase,
                                  String propagate, String defaultAction) {

        this.eventHandlerElement = eventHandlerElement;
        this.ancestorObserverStaticId = ancestorObserverStaticId;
        this.isXBLHandler = isXBLHandler;

        // Gather observers
        // NOTE: Supporting space-separated handlers is an extension, which may make it into XML Events 2
        final Element parentElement = eventHandlerElement.getParent();
        if (observers != null) {
            // ev:observer attribute specifies observers
            observerStaticIds = StringUtils.split(observers);
        } else if (parentElement != null && parentElement.attributeValue("id") != null) {
            // Observer is parent
            observerStaticIds = new String[] { parentElement.attributeValue("id")};
        } else {
            // No observer
            observerStaticIds = new String[0];
        }

        // Gather event names
        // NOTE: Supporting space-separated event names is an extension, which may make it into XML Events 2
        final Map<String, String> eventNames = new HashMap<String, String>();
        final String[] eventNamesArray = StringUtils.split(eventNamesAttribute);
        for (String anEventNamesArray: eventNamesArray) {
            eventNames.put(anEventNamesArray, "");
        }
        // Special #all value catches all events
        if (eventNames.get(XFormsConstants.XXFORMS_ALL_EVENTS) != null) {
            this.eventNames = null;
            isAllEvents = true;
        } else {
            this.eventNames = eventNames;
            isAllEvents = false;
        }

        // Gather target ids
        // NOTE: Supporting space-separated target ids is an extension, which may make it into XML Events 2
        if (targets == null) {
            targetStaticIds = null;
        } else {
            targetStaticIds = new HashMap<String, String>();
            final String[] targetIdsArray = StringUtils.split(targets);
            for (String aTargetIdsArray: targetIdsArray) {
                targetStaticIds.put(aTargetIdsArray, "");
            }
        }

        this.isBubblingPhase = !"capture".equals(phase);
        this.isPropagate = !"stop".equals(propagate);
        this.isPerformDefaultAction = !"cancel".equals(defaultAction);
    }

    /**
     * Execute the given event on this event handler.
     *
     * @param propertyContext
     * @param container             XBL container where observer is located
     * @param eventObserver         concrete event observer
     * @param event                 event
     */
    public void handleEvent(PropertyContext propertyContext, XBLContainer container,
                            XFormsEventObserver eventObserver, XFormsEvent event) {
        // Create a new top-level action interpreter to handle this event

        if (isXBLHandler) {
            // Run within context of nested container

            // Find nested container
            final XBLContainer nestedContainer = ((XFormsComponentControl) eventObserver).getNestedContainer();

            // Run action
            new XFormsActionInterpreter(propertyContext, nestedContainer, eventObserver, eventHandlerElement, ancestorObserverStaticId)
                    .runAction(propertyContext, event.getTargetObject().getEffectiveId(), eventObserver, eventHandlerElement);
        } else {
            // Run normally
            new XFormsActionInterpreter(propertyContext, container, eventObserver, eventHandlerElement, ancestorObserverStaticId)
                    .runAction(propertyContext, event.getTargetObject().getEffectiveId(), eventObserver, eventHandlerElement);
        }
    }

    public String[] getObserversStaticIds() {
        return observerStaticIds;
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
        return isAllEvents || eventNames.get(eventName) != null;
    }

    public boolean isMatchTarget(String targetStaticId) {
        // Match if no target id is specified, or if any specifed target matches
        return targetStaticIds == null || targetStaticIds.get(targetStaticId) != null;
    }

    public boolean isAllEvents() {
        return isAllEvents;
    }

    public Map<String, String> getEventNames() {
        return eventNames;
    }
}
