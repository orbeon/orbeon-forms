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

import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.event.XFormsEventTarget;
import org.orbeon.oxf.xforms.event.XFormsEvents;

import java.util.Map;

public class KeypressEvent extends XFormsUIEvent {

    private final String keyModifiers;
    private final String keyText;

    public KeypressEvent(XFormsContainingDocument containingDocument, XFormsEventTarget targetObject, Map<String, String> parameters) {
        // NOTE: Not sure if should be cancelable, this seems to indicate that "special keys" should not
        // be cancelable: http://www.quirksmode.org/dom/events/keys.html
        super(containingDocument, XFormsEvents.KEYPRESS, (XFormsControl) targetObject, true, false);

        if (parameters != null) {
            this.keyModifiers = parameters.get(XFormsConstants.XXFORMS_EVENTS_MODIFIERS_ATTRIBUTE_QNAME.getName());
            this.keyText = parameters.get(XFormsConstants.XXFORMS_EVENTS_TEXT_ATTRIBUTE_QNAME.getName());
        } else {
            this.keyModifiers = this.keyText = null;
        }
    }
}
