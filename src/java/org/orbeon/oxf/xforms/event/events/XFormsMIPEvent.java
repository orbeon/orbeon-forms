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
package org.orbeon.oxf.xforms.event.events;

import org.orbeon.oxf.xforms.event.XFormsEvent;
import org.orbeon.oxf.xforms.event.XFormsEvents;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.saxon.om.SequenceIterator;
import org.orbeon.saxon.om.ListIterator;
import org.orbeon.saxon.om.EmptyIterator;
import org.orbeon.saxon.value.StringValue;

import java.util.Collections;

/**
 * Base class for events related to MIP changes.
 */
public abstract class XFormsMIPEvent extends XFormsEvent {
    private XFormsControl targetXFormsControl;

    public XFormsMIPEvent(String eventName, XFormsControl targetObject) {
        super(eventName, targetObject, true, false);
        this.targetXFormsControl = targetObject;
    }

    public SequenceIterator getAttribute(String name) {
        if ("target-ref".equals(name)) {
            // Return the node to which the control is bound
            return new ListIterator(Collections.singletonList(targetXFormsControl.getBoundNode()));
        } else if ("target-id".equals(name)) {
            return new ListIterator(Collections.singletonList(new StringValue(targetXFormsControl.getOriginalId())));
        } else if ("alert".equals(name)) {
            final String alert = targetXFormsControl.getAlert();
            if (alert != null)
                return new ListIterator(Collections.singletonList(new StringValue(alert)));
            else
                return new EmptyIterator();
        } else if ("label".equals(name)) {
            final String label = targetXFormsControl.getLabel();
            if (label != null)
                return new ListIterator(Collections.singletonList(new StringValue(label)));
            else
                return new EmptyIterator();
        } else if ("target-effective-id".equals(name)) {
            return new ListIterator(Collections.singletonList(new StringValue(targetXFormsControl.getEffectiveId())));
        } else {
            return super.getAttribute(name);
        }
    }
}
