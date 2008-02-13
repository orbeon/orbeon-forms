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

import java.util.*;

/**
 * Represents an XForms (or just plain XML Events) event handler implementation.
 */
public class XFormsEventHandlerImpl implements XFormsEventHandler {

    private XFormsContainingDocument containingDocument;
    private Element eventHandlerElement;
    private XFormsEventHandlerContainer eventHandlerContainer;

    private String eventName;
//    private String observer;
//    private String target;
    //private String handler;
    private boolean phase;          // "true" means "default" (bubbling), "false" means "capture"
    private boolean propagate;      // "true" means "continue", "false" means "stop"
    private boolean defaultAction;  // "true" means "perform", "false" means "cancel"

    public XFormsEventHandlerImpl(XFormsContainingDocument containingDocument, XFormsEventHandlerContainer eventHandlerContainer, Element eventHandlerElement) {
        this.containingDocument = containingDocument;
        this.eventHandlerContainer = eventHandlerContainer;
        this.eventHandlerElement = eventHandlerElement;

        {
            this.eventName = eventHandlerElement.attributeValue(XFormsConstants.XML_EVENTS_EVENT_ATTRIBUTE_QNAME);
        }
        {
            final String captureString = eventHandlerElement.attributeValue(XFormsConstants.XML_EVENTS_PHASE_ATTRIBUTE_QNAME);
            this.phase = !"capture".equals(captureString);
        }
        {
            final String propagateString = eventHandlerElement.attributeValue(XFormsConstants.XML_EVENTS_PROPAGATE_ATTRIBUTE_QNAME);
            this.propagate = !"stop".equals(propagateString);
        }
        {
            final String defaultActionString = eventHandlerElement.attributeValue(XFormsConstants.XML_EVENTS_DEFAULT_ACTION_ATTRIBUTE_QNAME);
            this.defaultAction = !"cancel".equals(defaultActionString);
        }
    }

    /**
     * Utility method to extract event handlers.
     *
     * @param containingDocument        current XFormsContainingDocument
     * @param eventHandlerContainer     control, submission, etc. containing the event handlers
     * @param containingElement         element possibly containing event handlers
     * @return                          List of XFormsEventHandler
     */
    public static List extractEventHandlers(XFormsContainingDocument containingDocument, XFormsEventHandlerContainer eventHandlerContainer, Element containingElement) {
        final List children = containingElement.elements();
        if (children == null)
            return null;

        List eventHandlers = null;
        for (Iterator i = children.iterator(); i.hasNext();) {
            final Element currentElement = (Element) i.next();
            if (XFormsActions.isActionName(currentElement.getNamespaceURI(), currentElement.getName())
                    && currentElement.attributeValue(XFormsConstants.XML_EVENTS_EVENT_ATTRIBUTE_QNAME) != null) {
                // Found an action
                if (eventHandlers == null)
                    eventHandlers = new ArrayList();
                eventHandlers.add(new XFormsEventHandlerImpl(containingDocument, eventHandlerContainer, currentElement));
            }
        }
        return eventHandlers;
    }

    /**
     * Utility method to statically gather a event handlers' event names.
     *
     * @param eventNames            Map into which event names are stored
     * @param containingElement     element possibly containing event handlers
     */
    public static void gatherEventHandlerNames(Map eventNames, Element containingElement) {
        final List children = containingElement.elements();
        if (children == null)
            return;

        for (Iterator i = children.iterator(); i.hasNext();) {
            final Element currentElement = (Element) i.next();
            if (XFormsActions.isActionName(currentElement.getNamespaceURI(), currentElement.getName())) {
                final String eventName = currentElement.attributeValue(XFormsConstants.XML_EVENTS_EVENT_ATTRIBUTE_QNAME);
                if (eventName != null) {
                    // Found an action
                    eventNames.put(eventName, "");
                }
            }
        }
    }

    public static Map extractEventHandlersObserver(XFormsContainingDocument containingDocument, XFormsEventHandlerContainer eventHandlerContainer, Element containingElement) {
        final List children = containingElement.elements();
        if (children == null)
            return null;

        final Map eventHandlersMap = new HashMap();
        for (Iterator i = children.iterator(); i.hasNext();) {
            final Element currentElement = (Element) i.next();

            // Check if this is an action and a handler
            if (XFormsActions.isActionName(currentElement.getNamespaceURI(), currentElement.getName())
                    && currentElement.attributeValue(XFormsConstants.XML_EVENTS_EVENT_ATTRIBUTE_QNAME) != null) {

                // Get observer
                final String observerId;
                {
                    final String observerIdAttribute = currentElement.attributeValue(XFormsConstants.XML_EVENTS_OBSERVER_ATTRIBUTE_QNAME);
                    observerId = (observerIdAttribute == null) ? eventHandlerContainer.getEffectiveId() : observerIdAttribute;
                }

                // Get handlers for observer
                final List eventHandlersForObserver;
                {
                    final Object currentList = eventHandlersMap.get(observerId);
                    if (currentList == null) {
                        eventHandlersForObserver = new ArrayList();
                        eventHandlersMap.put(observerId, eventHandlersForObserver);
                    } else {
                        eventHandlersForObserver = (List) currentList;
                    }
                }

                // TODO: FIXME: eventHandlerContainer may not be the actual container when using @ev:observer
                eventHandlersForObserver.add(new XFormsEventHandlerImpl(containingDocument, eventHandlerContainer, currentElement));
            }
        }
        return eventHandlersMap;
    }

    public void handleEvent(PipelineContext pipelineContext, XFormsEvent event) {
        // Create a new top-level action interpreter to handle this event
        new XFormsActionInterpreter(pipelineContext, containingDocument, eventHandlerContainer, eventHandlerElement)
                .runAction(pipelineContext, event.getTargetObject().getEffectiveId(), eventHandlerContainer, eventHandlerElement);
    }

    public String getEventName() {
        return eventName;
    }

    public boolean isPhase() {
        return phase;
    }

    public boolean isPropagate() {
        return propagate;
    }

    public boolean isDefaultAction() {
        return defaultAction;
    }
}
