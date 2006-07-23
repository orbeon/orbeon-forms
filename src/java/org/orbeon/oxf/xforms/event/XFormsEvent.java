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

import org.orbeon.oxf.xforms.processor.XFormsServer;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.om.ListIterator;
import org.orbeon.saxon.om.SequenceIterator;

import java.util.Collections;


/**
 * XFormsEvent represents an XForms event passed to all events and actions.
 */
public abstract class XFormsEvent {

    private String eventName;
    private XFormsEventTarget targetObject;
    private boolean bubbles;
    private boolean cancelable;

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

    public SequenceIterator getAttribute(String name) {
        // "If the event context information does not contain the property indicated by the string argument, then an
        // empty node-set is returned."
        return new ListIterator(Collections.EMPTY_LIST);
    }
}
