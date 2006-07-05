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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Represents an XForms (or just plain XML Events) event handler implementation.
 */
public class XFormsEventHandlerImpl implements org.orbeon.oxf.xforms.event.XFormsEventHandler {

    private XFormsContainingDocument containingDocument;
    private Element eventHandlerElement;
    private XFormsEventHandlerContainer eventHandlerContainer;

    private String eventName;
//    private String observer;
//    private String target;
    //private String handler;
    private boolean phase; // "true" means "default" (bubbling), "false" means "capture"
    private boolean propagate; // "true" means "continue", "false" means "stop"
    private boolean defaultAction; // "true" means "perform", "false" means "cancel"

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

    public void handleEvent(PipelineContext pipelineContext, XFormsEvent event) {
        containingDocument.runAction(pipelineContext, event.getTargetObject().getId(), eventHandlerContainer, eventHandlerElement);
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
