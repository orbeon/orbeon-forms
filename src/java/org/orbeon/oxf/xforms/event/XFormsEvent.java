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

import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.StaticExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.event.events.XFormsUIEvent;
import org.orbeon.oxf.xforms.processor.XFormsServer;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.om.EmptyIterator;
import org.orbeon.saxon.om.ListIterator;
import org.orbeon.saxon.om.SequenceIterator;
import org.orbeon.saxon.value.BooleanValue;
import org.orbeon.saxon.value.SequenceExtent;
import org.orbeon.saxon.value.StringValue;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;


/**
 * XFormsEvent represents an XForms event passed to all events and actions.
 */
public abstract class XFormsEvent implements Cloneable {

    private static final String XXFORMS_TYPE_ATTRIBUTE = XMLUtils.buildExplodedQName(XFormsConstants.XXFORMS_NAMESPACE_URI, "type");
    private static final String XXFORMS_TARGET_ATTRIBUTE = XMLUtils.buildExplodedQName(XFormsConstants.XXFORMS_NAMESPACE_URI, "target");
    private static final String XXFORMS_BUBBLES_ATTRIBUTE = XMLUtils.buildExplodedQName(XFormsConstants.XXFORMS_NAMESPACE_URI, "bubbles");
    private static final String XXFORMS_CANCELABLE_ATTRIBUTE = XMLUtils.buildExplodedQName(XFormsConstants.XXFORMS_NAMESPACE_URI, "cancelable");

    // Properties that change as the event propagates
    // TODO
//    private static final String XXFORMS_PHASE_ATTRIBUTE = XMLUtils.buildExplodedQName(XFormsConstants.XXFORMS_NAMESPACE_URI, "phase");
//    private static final String XXFORMS_DEFAULT_PREVENTED_ATTRIBUTE = XMLUtils.buildExplodedQName(XFormsConstants.XXFORMS_NAMESPACE_URI, "default-prevented");
//    private static final String XXFORMS_CURRENT_TARGET_ATTRIBUTE = XMLUtils.buildExplodedQName(XFormsConstants.XXFORMS_NAMESPACE_URI, "current-target");// same as observer?
//    private static final String XXFORMS_OBSERVER_ATTRIBUTE = XMLUtils.buildExplodedQName(XFormsConstants.XXFORMS_NAMESPACE_URI, "observer");
//    See DOM 3 events for phase:
//    const unsigned short      CAPTURING_PHASE                = 1;
//    const unsigned short      AT_TARGET                      = 2;
//    const unsigned short      BUBBLING_PHASE                 = 3;

    private String eventName;
    private XFormsEventTarget targetObject;
    private boolean bubbles;
    private boolean cancelable;

    private Map customAttributes;

    private LocationData locationData;

    protected XFormsEvent(String eventName, XFormsEventTarget targetObject, boolean bubbles, boolean cancelable) {
        this.eventName = eventName;
        this.targetObject = targetObject;
        this.bubbles = bubbles;
        this.cancelable = cancelable;

        // Get Java location information for debugging only (getting Java location data is very inefficient)
        if (XFormsServer.logger.isDebugEnabled())
            this.locationData = Dom4jUtils.getLocationData(2, true);
    }

    public String getEventName() {
        return eventName;
    }

    public XFormsEventTarget getTargetObject() {
        return targetObject;
    }

    public boolean isBubbles() {
        return bubbles;
    }

    public boolean isCancelable() {
        return cancelable;
    }

    public LocationData getLocationData() {
        return locationData;
    }

    public void setAttribute(String name, SequenceExtent value) {
        if (customAttributes == null)
            customAttributes = new HashMap();
        customAttributes.put(name, value);
    }

    public SequenceIterator getAttribute(String name) {
        if ("target".equals(name) || XXFORMS_TARGET_ATTRIBUTE.equals(name)) {// first is legacy name
            // Return the target static id

            if ("target".equals(name)) {
                XFormsServer.logger.warn("event('target') is deprecated. Use event('xxforms:target') instead.");
            }

            return new ListIterator(Collections.singletonList(new StringValue(targetObject.getId())));
        } else if ("event".equals(name) || XXFORMS_TYPE_ATTRIBUTE.equals(name)) {// first is legacy name
            // Return the event type

            if ("event".equals(name)) {
                XFormsServer.logger.warn("event('event') is deprecated. Use event('xxforms:type') instead.");
            }

            return new ListIterator(Collections.singletonList(new StringValue(eventName)));
        } else if (XXFORMS_BUBBLES_ATTRIBUTE.equals(name)) {
            // Return whether the event bubbles
            return new ListIterator(Collections.singletonList(BooleanValue.get(bubbles)));
        } else if (XXFORMS_CANCELABLE_ATTRIBUTE.equals(name)) {
            // Return whether the event is cancelable
            return new ListIterator(Collections.singletonList(BooleanValue.get(cancelable)));
        } else if (customAttributes != null && customAttributes.get(name) != null) {
            // Return custom attribute if found
            return ((SequenceExtent) customAttributes.get(name)).iterate(null); // NOTE: With Saxon 8, the param is not used, and Saxon 9 has value.iterate()
        } else {
            // "If the event context information does not contain the property indicated by the string argument, then an
            // empty node-set is returned."
            return EmptyIterator.getInstance();
        }
    }

    /**
     * Attempts to get the current pipeline context using the static context.
     *
     * @return  PipelineContext, null if not found
     */
    protected PipelineContext getPipelineContext() {
        return StaticExternalContext.getStaticContext().getPipelineContext();
    }

    public XFormsEvent retarget(XFormsEventTarget newTargetObject) {
        final XFormsEvent newEvent;
        try {
            newEvent = (XFormsUIEvent) this.clone();
            newEvent.targetObject = newTargetObject;
        } catch (CloneNotSupportedException e) {
            throw new OXFException(e);// should not happen because we are clonable
        }

        return newEvent;
    }

    public Object clone() throws CloneNotSupportedException {
        // Default implementation is good enough
        return super.clone();
    }
}
