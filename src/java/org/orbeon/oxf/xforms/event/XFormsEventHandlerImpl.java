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
package org.orbeon.oxf.xforms.event;

import org.apache.commons.lang.StringUtils;
import org.dom4j.Element;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.action.XFormsActionInterpreter;
import org.orbeon.oxf.xforms.control.XFormsComponentControl;
import org.orbeon.oxf.xforms.xbl.XBLContainer;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Represents an XForms (or just plain XML Events) event handler implementation.
 */
public class XFormsEventHandlerImpl implements XFormsEventHandler {

    // Special target id indicating that the target is the observer
    public static final String TARGET_IS_OBSERVER = "#observer";

    private final String prefix;
    private final Element eventHandlerElement;
    private final String ancestorObserverStaticId;

    private final Set<String> eventNames;
    private final boolean isAllEvents;
    private final String[] observerStaticIds;
    private final Set<String> targetStaticIds;

    // Phase filters
    private final boolean isCapturePhase;
    private final boolean isTargetPhase;
    private final boolean isBubblingPhase;

    private final boolean isPropagate;            // "true" means "continue", "false" means "stop"
    private final boolean isPerformDefaultAction; // "true" means "perform", "false" means "cancel"

    private final boolean isXBLHandler;

    private final String keyModifiers;
    private final String keyText;


    /**
     * Initialize an action handler based on an action element.
     *
     * @param prefix                    prefix of container in which the handler is located
     * @param eventHandlerElement       action element e.g. xforms:action
     * @param parentStaticId            static id of the parent element, or null
     * @param ancestorObserverStaticId  static id of the closest ancestor observer, or null
     */
    public XFormsEventHandlerImpl(String prefix, Element eventHandlerElement, String parentStaticId, String ancestorObserverStaticId) {
        this(prefix, eventHandlerElement, parentStaticId, ancestorObserverStaticId, false,
                eventHandlerElement.attributeValue(XFormsConstants.XML_EVENTS_EV_OBSERVER_ATTRIBUTE_QNAME),
                eventHandlerElement.attributeValue(XFormsConstants.XML_EVENTS_EV_EVENT_ATTRIBUTE_QNAME),
                eventHandlerElement.attributeValue(XFormsConstants.XML_EVENTS_EV_TARGET_ATTRIBUTE_QNAME),
                eventHandlerElement.attributeValue(XFormsConstants.XML_EVENTS_EV_PHASE_ATTRIBUTE_QNAME),
                eventHandlerElement.attributeValue(XFormsConstants.XML_EVENTS_EV_PROPAGATE_ATTRIBUTE_QNAME),
                eventHandlerElement.attributeValue(XFormsConstants.XML_EVENTS_EV_DEFAULT_ACTION_ATTRIBUTE_QNAME),
                eventHandlerElement.attributeValue(XFormsConstants.XXFORMS_EVENTS_MODIFIERS_ATTRIBUTE_QNAME),
                eventHandlerElement.attributeValue(XFormsConstants.XXFORMS_EVENTS_TEXT_ATTRIBUTE_QNAME));
    }

    /**
     * Initialize an action handler based on a handler element and individual parameters.
     *
     * @param prefix                    prefix of container in which the handler is located
     * @param eventHandlerElement       event handler element (e.g. xbl:handler or xforms:action)
     * @param parentStaticId            static id of the parent element, or null
     * @param ancestorObserverStaticId  static id of the closest ancestor observer, or null
     * @param isXBLHandler              whether the handler is an XBL handler (i.e. xbl:handler)
     * @param observersStaticIds        space-separated list of observers static ids, or null if parent element
     * @param eventNamesAttribute       space-separated list of event names
     * @param targets                   space-separated list of event targets
     * @param phase                     event phase ("capture" | "default", poss. more later as XBL has "capture" | "target" | "bubble" | "default-action")
     * @param propagate                 whether event must propagate ("stop" | "continue")
     * @param defaultAction             whether default action must run ("cancel" | "perform")
     * @param keyModifiers               key modifier for keypress event, or null
     * @param keyText                   key text for keypress event, or null
     */
    public XFormsEventHandlerImpl(String prefix, Element eventHandlerElement, String parentStaticId, String ancestorObserverStaticId,
                                  boolean isXBLHandler, String observersStaticIds, String eventNamesAttribute, String targets,
                                  String phase, String propagate, String defaultAction, String keyModifiers, String keyText) {

        this.prefix = prefix;
        this.eventHandlerElement = eventHandlerElement;
        this.ancestorObserverStaticId = ancestorObserverStaticId;
        this.isXBLHandler = isXBLHandler;

        // Normalize these
        this.keyModifiers = StringUtils.isBlank(keyModifiers) ? null : keyModifiers.trim();
        this.keyText = StringUtils.isEmpty(keyText) ? null : keyText;// allow for e.g. " "

        // Gather observers
        // NOTE: Supporting space-separated handlers is an extension, which may make it into XML Events 2
        if (observersStaticIds != null) {
            // ev:observer attribute specifies observers
            observerStaticIds = StringUtils.split(observersStaticIds);
        } else if (parentStaticId != null) {
            // Observer is parent
            observerStaticIds = new String[] { parentStaticId };
        } else {
            // No observer
            observerStaticIds = new String[0];
        }

        // Gather event names
        // NOTE: Supporting space-separated event names is an extension, which may make it into XML Events 2
        final Set<String> eventNames = new HashSet<String>();
        final String[] eventNamesArray = StringUtils.split(eventNamesAttribute);
        eventNames.addAll(Arrays.asList(eventNamesArray));

        // Special #all value catches all events
        if (eventNames.contains(XFormsConstants.XXFORMS_ALL_EVENTS)) {
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
            targetStaticIds = new HashSet<String>();
            final String[] targetIds = StringUtils.split(targets);
            for (String targetId: targetIds) {
                if (TARGET_IS_OBSERVER.equals(targetId)) {
                    // Add all observer ids as targets
                    targetStaticIds.addAll(Arrays.asList(observerStaticIds));
                } else {
                    // Add id as target
                    targetStaticIds.add(targetId);
                }
            }
        }

        this.isCapturePhase = "capture".equals(phase);
        this.isTargetPhase = "target".equals(phase) || "default".equals(phase) || phase == null;
        this.isBubblingPhase = "bubbling".equals(phase) || "default".equals(phase) || phase == null;

        this.isPropagate = !"stop".equals(propagate);
        this.isPerformDefaultAction = !"cancel".equals(defaultAction);
    }

    /**
     * Execute the given event on this event handler.
     *
     * @param containerForObserver  XBL container where observer is located
     * @param eventObserver         concrete event observer
     * @param event                 event
     */
    public void handleEvent(XBLContainer containerForObserver,
                            XFormsEventObserver eventObserver, XFormsEvent event) {

        final XBLContainer contextContainer;
        if (isXBLHandler) {
            // Run within context of nested container
            contextContainer = ((XFormsComponentControl) eventObserver).getNestedContainer();
        } else if (ancestorObserverStaticId == null || prefix.equals(XFormsUtils.getEffectiveIdPrefix(eventObserver.getEffectiveId()))) {
            // Run within provided container if:
            // * we don't know how to resolve otherwise because there is no ancestor observer
            // * or the handler's prefix is the same as the observer's prefix
            contextContainer = containerForObserver;
        } else {
            // The observer is in a different container from the handler
            // We need to find the corresponding XBL container to use as context for interpreting the action
            // To do so, we use the handler's ancestor static id and resolve it in the scope of the observer
            final XFormsContainingDocument containingDocument = containerForObserver.getContainingDocument();
            final Object result = eventObserver.getXBLContainer(containingDocument)
                    .findResolutionScope(XFormsUtils.getPrefixedId(eventObserver.getEffectiveId()))
                    .resolveObjectById(eventObserver.getEffectiveId(), ancestorObserverStaticId, null);
            if (result != null) {
                // Found the object corresponding to the ancestor object, just get its container
                contextContainer = ((XFormsEventTarget) result).getXBLContainer(containingDocument);
            } else {
                // Not found (should this happen?)
                contextContainer = containerForObserver;
            }
        }

        // Create a new top-level action interpreter to handle this event
            new XFormsActionInterpreter(contextContainer, eventObserver, eventHandlerElement, ancestorObserverStaticId, isXBLHandler)
                .runAction(event, eventObserver, eventHandlerElement);
        // NOTE: We would like here ideally to catch exceptions occurring within actions, and to dispatch an event that
        // can be recovered for example by the XForms inspector. However, this needs to be done properly: logging vs.
        // fatal, sending an error to the client, etc. Also, some code cannot recover at this time, e.g. a variable
        // within the control tree throwing a dynamic XPath error currently leaves the tree in a bad state. So either the
        // tree should be discarded, or XPath dynamic errors should leave the tree in a consistent state. Still, some errors
        // in the XForms engine will still be fatal.
//        try {
//        } catch (Exception e) {
//            // Something bad happened while running the action
//            // NOTE: Dispatch directly to the containing document. Ideally it would bubble and a default listener on the document would handle it.
//            contextContainer.dispatchEvent(new XXFormsActionErrorEvent(contextContainer.getContainingDocument(), contextContainer.getContainingDocument(), e));
//        }
    }

    public String[] getObserversStaticIds() {
        return observerStaticIds;
    }

    public boolean isCapturePhaseOnly() {
        return isCapturePhase;
    }

    public boolean isTargetPhase() {
        return isTargetPhase;
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

    private boolean isMatchEventName(String eventName) {
        return isAllEvents || eventNames.contains(eventName);
    }

    private boolean isMatchTarget(String targetStaticId) {
        // Match if no target id is specified, or if any specified target matches
        return targetStaticIds == null || targetStaticIds.contains(targetStaticId);
    }

    public boolean isMatch(XFormsEvent event) {
        return isMatchEventName(event.getName()) && isMatchTarget(event.getTargetObject().getId()) && event.matches(this);
    }

    public boolean isAllEvents() {
        return isAllEvents;
    }

    public Set<String> getEventNames() {
        return eventNames;
    }

    public Element getEventHandlerElement() {
        return eventHandlerElement;
    }

    public String getStaticId() {
        return eventHandlerElement.attributeValue(XFormsConstants.ID_QNAME);
    }

    public String getPrefix() {
        return prefix;
    }

    public String getAncestorObserverStaticId() {
        return ancestorObserverStaticId;
    }

    public String getKeyModifiers() {
        return keyModifiers;
    }

    public String getKeyText() {
        return keyText;
    }
}
