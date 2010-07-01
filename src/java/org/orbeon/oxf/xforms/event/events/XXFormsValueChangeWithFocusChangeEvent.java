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
import org.orbeon.oxf.xforms.event.*;


/**
 * Internal xxforms-value-change-with-focus-change event.
 */
public class XXFormsValueChangeWithFocusChangeEvent extends XFormsEvent {

    private String newValue;
    private XFormsEventTarget otherTargetObject;

    public XXFormsValueChangeWithFocusChangeEvent(XFormsContainingDocument containingDocument, XFormsEventTarget targetObject, XFormsEventTarget otherTargetObject, String newValue) {
        super(containingDocument, XFormsEvents.XXFORMS_VALUE_CHANGE_WITH_FOCUS_CHANGE, targetObject, false, false);
        this.otherTargetObject = otherTargetObject;
        this.newValue = newValue;
    }

    public XFormsEventTarget getOtherTargetObject() {
        return otherTargetObject;
    }

    public String getNewValue() {
        return newValue;
    }
}
