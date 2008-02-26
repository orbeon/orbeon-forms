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

    private Element eventHandlerElement;
    private String containerId;

    private String eventName;
    private String observerId;
    private String targetId;
    //private String handler;
    private boolean phase;          // "true" means "default" (bubbling), "false" means "capture"
    private boolean propagate;      // "true" means "continue", "false" means "stop"
    private boolean defaultAction;  // "true" means "perform", "false" means "cancel"

    public XFormsEventHandlerImpl(Element eventHandlerElement, String containerId, String observerId) {
        this.eventHandlerElement = eventHandlerElement;
        this.containerId = containerId;

        this.observerId = observerId;
        this.eventName = eventHandlerElement.attributeValue(XFormsConstants.XML_EVENTS_EVENT_ATTRIBUTE_QNAME);
        this.targetId = eventHandlerElement.attributeValue(XFormsConstants.XML_EVENTS_TARGET_ATTRIBUTE_QNAME);

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
     * @param containingElement         element possibly containing event handlers
     * @param eventNames                Map<String, String> of event name to ""
     * @return                          Map<String, List<XFormsEventHandler>> of observer id to List of XFormsEventHandler
     */
    public static Map extractEventHandlers(Element containingElement, Map eventNames) {

        // Nothing to do if there are no children elements
        final List children = containingElement.elements();
        if (children == null)
            return null;

        Map eventHandlersMap = null;
        final String containerId = containingElement.attributeValue("id");
        for (Iterator i = children.iterator(); i.hasNext();) {
            final Element currentElement = (Element) i.next();

            if (XFormsActions.isActionName(currentElement.getNamespaceURI(), currentElement.getName())) {
                final String eventName = currentElement.attributeValue(XFormsConstants.XML_EVENTS_EVENT_ATTRIBUTE_QNAME);
                if (eventName != null) {

                    // Found an action with ev:event attribute
                    if (eventHandlersMap == null)
                        eventHandlersMap = new HashMap();

                    // Get observer
                    final String observerId;
                    {
                        final String observerIdAttribute = currentElement.attributeValue(XFormsConstants.XML_EVENTS_OBSERVER_ATTRIBUTE_QNAME);
                        observerId = (observerIdAttribute == null) ? containerId : observerIdAttribute;
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

                    // Add event handler
                    eventHandlersForObserver.add(new XFormsEventHandlerImpl(currentElement, containerId, observerId));
                    
                    // Remember that there is an event
                    eventNames.put(eventName, "");
                }
            }
        }
        return eventHandlersMap;
    }

    public void handleEvent(PipelineContext pipelineContext, XFormsContainingDocument containingDocument,
                            XFormsEventHandlerContainer eventHandlerContainer, XFormsEvent event) {
        // Create a new top-level action interpreter to handle this event
        new XFormsActionInterpreter(pipelineContext, containingDocument, eventHandlerContainer, eventHandlerElement, containerId)
                .runAction(pipelineContext, event.getTargetObject().getEffectiveId(), eventHandlerContainer, eventHandlerElement);
    }

    public String getEventName() {
        return eventName;
    }

    public String getObserverId() {
        return observerId;
    }

    public String getTargetId() {
        return targetId;
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
