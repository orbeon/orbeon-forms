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

import org.apache.commons.lang.StringUtils;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.event.XFormsEventHandler;
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
            final String keyModifiersParameter = parameters.get(XFormsConstants.XXFORMS_EVENTS_MODIFIERS_ATTRIBUTE_QNAME.getName());
            this.keyModifiers = StringUtils.isBlank(keyModifiersParameter) ? null : keyModifiersParameter.trim();
            final String keyTextParameter = parameters.get(XFormsConstants.XXFORMS_EVENTS_TEXT_ATTRIBUTE_QNAME.getName());
            this.keyText = StringUtils.isEmpty(keyTextParameter) ? null : keyTextParameter;// allow for e.g. " ";
        } else {
            this.keyModifiers = this.keyText = null;
        }
    }

    @Override
    public boolean matches(XFormsEventHandler handler) {

        final String handlerKeyModifiers = handler.getKeyModifiers();
        final String handlerKeyText = handler.getKeyText();

        // NOTE: We check on an exact match for modifiers, should be smarter
        return XFormsUtils.compareStrings(keyModifiers, handlerKeyModifiers)
                && XFormsUtils.compareStrings(keyText, handlerKeyText);
    }
}
