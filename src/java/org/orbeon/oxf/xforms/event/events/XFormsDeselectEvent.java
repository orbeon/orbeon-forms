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
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.event.XFormsEventTarget;
import org.orbeon.oxf.xforms.event.XFormsEvents;
import org.orbeon.saxon.om.EmptyIterator;
import org.orbeon.saxon.om.SequenceIterator;
import org.orbeon.saxon.om.SingletonIterator;
import org.orbeon.saxon.value.StringValue;


/**
 * 4.4.3 The xforms-select and xforms-deselect Events
 *
 * Target: item or itemset or case / Bubbles: Yes / Cancelable: No / Context Info: None
 * The default action for this event results in the following: None; notification event only.
 */
public class XFormsDeselectEvent extends XFormsUIEvent {

    private String itemValue;

    public XFormsDeselectEvent(XFormsContainingDocument containingDocument, XFormsEventTarget targetObject) {
        super(containingDocument, XFormsEvents.XFORMS_DESELECT, (XFormsControl) targetObject, true, false);
    }

    public XFormsDeselectEvent(XFormsContainingDocument containingDocument, XFormsEventTarget targetObject, String itemValue) {
        this(containingDocument, targetObject);
        this.itemValue = itemValue;
    }

    public SequenceIterator getAttribute(String name) {
        if (XFormsSelectEvent.XXFORMS_ITEM_VALUE.equals(name)) {
            // Return the selected item value
            if (itemValue != null)
                return SingletonIterator.makeIterator(new StringValue(itemValue));
            else
                return EmptyIterator.getInstance();
        } else {
            return super.getAttribute(name);
        }
    }
}
