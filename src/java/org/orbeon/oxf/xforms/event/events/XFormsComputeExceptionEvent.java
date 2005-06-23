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

import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.xforms.event.XFormsEvent;
import org.orbeon.oxf.xforms.event.XFormsEventTarget;
import org.orbeon.oxf.xforms.event.XFormsEvents;

/**
 * 4.5.4 The xforms-compute-exception Event
 *
 * Target: model / Bubbles: Yes / Cancelable: No / Context Info: Implementation-specific error string.
 * The default action for this event results in the following: Fatal error.
 */
public class XFormsComputeExceptionEvent extends XFormsEvent {
    private String errorString;
    private Throwable throwable;

    public XFormsComputeExceptionEvent(XFormsEventTarget targetObject, String errorString, Throwable throwable) {
        super(XFormsEvents.XFORMS_COMPUTE_EXCEPTION, targetObject, true, false);
        this.errorString = errorString;
        this.throwable = throwable;
    }

    public Throwable getThrowable() {
        return throwable;
    }

    public String getErrorString() {
        return errorString;
    }

    public RuntimeException createException() {
        String message = getEventName() + ": " + getErrorString();

        if (getThrowable() != null)
            return new OXFException(message, getThrowable());
        else
            return new OXFException(message);
    }
}
