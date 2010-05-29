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
import org.orbeon.oxf.xforms.event.XFormsEvent;
import org.orbeon.oxf.xforms.event.XFormsEventTarget;
import org.orbeon.oxf.xforms.event.XFormsEvents;


/**
 * 4.3.6 The xforms-recalculate Event
 *
 * Target: model / Bubbles: Yes / Cancelable: Yes / Context Info: None
 */
public class XFormsRecalculateEvent extends XFormsEvent {

    private final boolean applyInitialValues;

    public XFormsRecalculateEvent(XFormsContainingDocument containingDocument, XFormsEventTarget targetObject) {
        this(containingDocument, targetObject, false);
    }

    public XFormsRecalculateEvent(XFormsContainingDocument containingDocument, XFormsEventTarget targetObject, boolean applyInitialValues) {
        super(containingDocument, XFormsEvents.XFORMS_RECALCULATE, targetObject, true, true);
        this.applyInitialValues = applyInitialValues;
    }

    public boolean isApplyInitialValues() {
        return applyInitialValues;
    }
}
