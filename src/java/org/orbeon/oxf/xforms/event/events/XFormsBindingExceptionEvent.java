/**
 * Copyright (C) 2010 Orbeon, Inc.
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

import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.event.XFormsEventTarget;
import org.orbeon.oxf.xforms.event.XFormsEvents;
import org.orbeon.oxf.xforms.event.XFormsExceptionEvent;

/**
 * 4.5.1 The xforms-binding-exception Event
 *
 * Target: model / Bubbles: Yes / Cancelable: No / Context Info: None.
 * The default action for this event results in the following: Fatal error.
 */
public class XFormsBindingExceptionEvent extends XFormsExceptionEvent {

    public XFormsBindingExceptionEvent(XFormsContainingDocument containingDocument, XFormsEventTarget targetObject) {
        this(containingDocument, targetObject, null);
    }

    public XFormsBindingExceptionEvent(XFormsContainingDocument containingDocument, XFormsEventTarget targetObject, Throwable throwable) {
        super(containingDocument, XFormsEvents.XFORMS_BINDING_EXCEPTION, targetObject, throwable, true, false);
    }
}
