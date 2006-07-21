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
package org.orbeon.oxf.xforms.event.events;

import org.orbeon.oxf.xforms.event.XFormsEvent;
import org.orbeon.oxf.xforms.event.XFormsEvents;
import org.orbeon.oxf.xforms.controls.ControlInfo;
import org.orbeon.saxon.om.SequenceIterator;
import org.orbeon.saxon.om.ListIterator;
import org.orbeon.saxon.om.EmptyIterator;
import org.orbeon.saxon.value.StringValue;

import java.util.Collections;

/**
 * 4.4.7 The xforms-invalid Event
 *
 * Target: form control / Bubbles: Yes / Cancelable: No / Context Info: None
 */
public class XFormsInvalidEvent extends XFormsEvent {

    private ControlInfo targetControl;

    public XFormsInvalidEvent(ControlInfo targetObject) {
        super(XFormsEvents.XFORMS_INVALID, targetObject, true, false);
        this.targetControl = targetObject;
    }

    public SequenceIterator getAttribute(String name) {
        if ("target-ref".equals(name)) {
            // Return the node to which the control is bound
            return new ListIterator(Collections.singletonList(targetControl.getBindingContext().getSingleNode()));
        } else if ("target-id".equals(name)) {
            return new ListIterator(Collections.singletonList(new StringValue(targetControl.getOriginalId())));
        } else if ("alert".equals(name)) {
            final String alert = targetControl.getAlert();
            if (alert != null)
                return new ListIterator(Collections.singletonList(new StringValue(alert)));
            else
                return new EmptyIterator();
        } else if ("label".equals(name)) {
            final String label = targetControl.getLabel();
            if (label != null)
                return new ListIterator(Collections.singletonList(new StringValue(label)));
            else
                return new EmptyIterator();
        } else {
            return super.getAttribute(name);
        }
    }
}
