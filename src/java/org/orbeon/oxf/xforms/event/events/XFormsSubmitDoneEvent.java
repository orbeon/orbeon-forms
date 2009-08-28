/**
 * Copyright (C) 2009 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.event.events;

import org.orbeon.oxf.util.ConnectionResult;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.event.XFormsEventTarget;
import org.orbeon.oxf.xforms.event.XFormsEvents;
import org.orbeon.saxon.om.SequenceIterator;


/**
 * 4.4.18 The xforms-submit-done Event
 *
 * Target: submission / Bubbles: Yes / Cancelable: No / Context Info: None
 * The default action for this event results in the following: None; notification event only.
 */
public class XFormsSubmitDoneEvent extends XFormsSubmitResponseEvent {

    public XFormsSubmitDoneEvent(XFormsContainingDocument containingDocument, XFormsEventTarget targetObject) {
        this(containingDocument, targetObject, null);
    }

    public XFormsSubmitDoneEvent(XFormsContainingDocument containingDocument, XFormsEventTarget targetObject, ConnectionResult connectionResult) {
        super(containingDocument, XFormsEvents.XFORMS_SUBMIT_DONE, targetObject, connectionResult);
    }

    public XFormsSubmitDoneEvent(XFormsContainingDocument containingDocument, XFormsEventTarget targetObject, String resourceURI, int statusCode) {
        super(containingDocument, XFormsEvents.XFORMS_SUBMIT_DONE, targetObject, resourceURI, statusCode);
    }

    public SequenceIterator getAttribute(String name) {
        // All attributes are handled by the base class
        return super.getAttribute(name);
    }
}
