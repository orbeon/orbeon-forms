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
import org.orbeon.oxf.xforms.event.XFormsEvent;
import org.orbeon.oxf.xforms.event.XFormsEventHandler;
import org.orbeon.oxf.xforms.event.XFormsEventTarget;
import org.orbeon.oxf.xforms.event.XFormsEvents;

import java.util.Map;

public class KeypressEvent extends XFormsEvent {

    private static final String MODIFIERS_ATTRIBUTE = XFormsConstants.XXFORMS_EVENTS_MODIFIERS_ATTRIBUTE_QNAME.getName();
    private static final String TEXT_ATTRIBUTE = XFormsConstants.XXFORMS_EVENTS_TEXT_ATTRIBUTE_QNAME.getName();

    public KeypressEvent(XFormsContainingDocument containingDocument, XFormsEventTarget targetObject, Map<String, String> parameters) {
        // NOTE: Not sure if should be cancelable, this seems to indicate that "special keys" should not
        // be cancelable: http://www.quirksmode.org/dom/events/keys.html
        // NOTE: For now, not an XFormsUIEvent because can also be targeted at XFormsContainingDocument
        super(containingDocument, XFormsEvents.KEYPRESS, targetObject, true, false);

        if (parameters != null) {
            // NOTE: See also normalization logic in XFormsEventHandlerImpl
            final String keyModifiersParameter = parameters.get(MODIFIERS_ATTRIBUTE);
            if (StringUtils.isNotBlank(keyModifiersParameter))
                setAttributeAsString(MODIFIERS_ATTRIBUTE,  keyModifiersParameter.trim());

            final String keyTextParameter = parameters.get(TEXT_ATTRIBUTE);
            if (StringUtils.isNotEmpty(keyTextParameter))// allow for e.g. " "
                setAttributeAsString(TEXT_ATTRIBUTE, keyTextParameter);
        }
    }

    @Override
    public boolean matches(XFormsEventHandler handler) {

        final String handlerKeyModifiers = handler.getKeyModifiers();
        final String handlerKeyText = handler.getKeyText();

        // NOTE: We check on an exact match for modifiers, should be smarter
        return (handlerKeyModifiers == null || XFormsUtils.compareStrings(getKeyModifiers(), handlerKeyModifiers))
                && (handlerKeyText ==null || XFormsUtils.compareStrings(getKeyText(), handlerKeyText));
    }

    private String getKeyModifiers() {
        return getAttributeAsString(MODIFIERS_ATTRIBUTE);
    }

    private String getKeyText() {
        return getAttributeAsString(TEXT_ATTRIBUTE);
    }
}
