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
import org.orbeon.saxon.om.SequenceIterator;
import org.orbeon.saxon.om.ListIterator;
import org.orbeon.saxon.value.StringValue;

import java.util.Collections;


/**
 * 4.4.18 The xforms-submit-done Event
 *
 * Target: submission / Bubbles: Yes / Cancelable: No / Context Info: None
 * The default action for this event results in the following: None; notification event only.
 */
public class XFormsSubmitDoneEvent extends XFormsEvent {

    private String urlString;

    public XFormsSubmitDoneEvent(XFormsEventTarget targetObject, String urlString) {
        super(XFormsEvents.XFORMS_SUBMIT_DONE, targetObject, true, false);
        this.urlString = urlString;
    }

    public String getUrlString() {
        return urlString;
    }

    public SequenceIterator getAttribute(String name) {
        if ("resource-uri".equals(name)) {
            // "The submission resource URI that failed (xsd:anyURI)"
            return new ListIterator(Collections.singletonList(new StringValue(getUrlString())));

            // TODO: response-status-code, response-headers, response-reason-phrase

        } else {
            return super.getAttribute(name);
        }
    }
}
