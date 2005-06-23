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



/**
 * XFormsEvent represents an XForms event passed to all events and actions.
 */
public abstract class XFormsEvent {

    private String eventName;
    private XFormsEventTarget targetObject;
    private boolean bubbles;
    private boolean cancelable;

    protected XFormsEvent(String eventName, XFormsEventTarget targetObject, boolean bubbles, boolean cancelable) {
        this.eventName = eventName;
        this.targetObject = targetObject;
        this.bubbles = bubbles;
        this.cancelable = cancelable;
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
}
