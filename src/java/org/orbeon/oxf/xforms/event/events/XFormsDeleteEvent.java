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
import org.orbeon.oxf.xforms.event.XFormsEventTarget;
import org.orbeon.oxf.xforms.event.XFormsEvents;
import org.orbeon.saxon.om.EmptyIterator;
import org.orbeon.saxon.om.ListIterator;
import org.orbeon.saxon.om.SequenceIterator;
import org.orbeon.saxon.value.IntegerValue;

import java.util.Collections;
import java.util.List;


/**
 * 4.4.5 The xforms-insert and xforms-delete Events
 *
 * Target: instance / Bubbles: Yes / Cancelable: No / Context Info: Path expression used for insert/delete (xsd:string).
 * The default action for these events results in the following: None; notification event only.
 */
public class XFormsDeleteEvent extends XFormsEvent {

    private List deletedNodeInfos;
    private int deleteIndex;

    public XFormsDeleteEvent(XFormsEventTarget targetObject) {
        super(XFormsEvents.XFORMS_DELETE, targetObject, true, false);
    }

    public XFormsDeleteEvent(XFormsEventTarget targetObject,
                             List deletedNodeInfos,
                             int deleteIndex) {
        super(XFormsEvents.XFORMS_DELETE, targetObject, true, false);
        this.deletedNodeInfos = deletedNodeInfos;
        this.deleteIndex = deleteIndex;
    }

    public SequenceIterator getAttribute(String name) {
        if ("deleted-nodes".equals(name)) {
            // "The instance data node deleted. Note that these nodes are no longer referenced by their parents."
            return (deletedNodeInfos == null) ? new EmptyIterator() : (SequenceIterator) new ListIterator(deletedNodeInfos);
        } else if ("delete-location".equals(name)) {
            // "The delete location as defined by the delete action, or NaN if there is no delete location."
            return (deleteIndex < 1) ? new EmptyIterator() : (SequenceIterator) new ListIterator(Collections.singletonList(new IntegerValue(deleteIndex)));
        } else {
            return super.getAttribute(name);
        }
    }
}
