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
 * 4.4.19 The xforms-submit-error Event
 *
 * Target: model / Bubbles: Yes / Cancelable: No / Context Info: The submit method URI that failed (xsd:anyURI)
 * The default action for this event results in the following: None; notification event only.
 */
public class XFormsSubmitErrorEvent extends XFormsEvent {
    private Throwable throwable;
    private String urlString;

    public XFormsSubmitErrorEvent(XFormsEventTarget targetObject, String urlString, Throwable throwable) {
        super(XFormsEvents.XFORMS_SUBMIT_ERROR, targetObject, true, false);
        this.urlString = urlString;
        this.throwable = throwable;
    }

    public Throwable getThrowable() {
        return throwable;
    }

    public String getUrlString() {
        return urlString;
    }
}
