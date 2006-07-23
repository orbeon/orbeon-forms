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

/**
 * 4.1.1 The xforms-submit-serialize Event
 *
 * Target: submission / Bubbles: Yes / Cancelable: No / Context Info: A node
 * into which data to be submitted can be placed.
 *
 * The default action for this event is to perform the normal XForms submission
 * serialization if the event context node's content is empty. The content of
 * the event context node is the data sent by the XForms submission.
 */
public class XFormsSubmitSerializeEvent extends XFormsEvent {

    public XFormsSubmitSerializeEvent(final XFormsEventTarget targetObject) {
        super(XFormsEvents.XFORMS_SUBMIT_SERIALIZE, targetObject, true, false);
    }
}
