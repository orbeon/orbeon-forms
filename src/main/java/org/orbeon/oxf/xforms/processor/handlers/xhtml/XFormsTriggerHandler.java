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
package org.orbeon.oxf.xforms.processor.handlers.xhtml;

import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsSingleNodeControl;
import org.orbeon.oxf.xml.XMLReceiverHelper;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Handle xf:trigger and xf:submit.
 */
public abstract class XFormsTriggerHandler extends XFormsControlLifecyleHandler {

    public XFormsTriggerHandler(String uri, String localname, String qName, Attributes attributes, Object matched, Object handlerContext) {
        super(uri, localname, qName, attributes, matched, handlerContext, false, false);
    }

    protected String getTriggerLabel(XFormsSingleNodeControl xformsControl) {
        return ! xformsHandlerContext.isTemplate() && xformsControl != null && xformsControl.getLabel() != null
            ? xformsControl.getLabel()
            : "";
    }

    @Override
    public boolean isMustOutputControl(XFormsControl control) {
        // Don't output anything in static readonly mode
        return ! isStaticReadonly(control);
    }

    @Override
    public void handleLabel() throws SAXException {
        // Label is handled differently
    }

    @Override
    public void handleHint() throws SAXException {
        // Hint is handled differently
    }

    @Override
    public void handleAlert() throws SAXException {
        // Triggers don't need an alert (in theory, they could have one)
    }

    @Override
    public AttributesImpl getEmptyNestedControlAttributesMaybeWithId(String uri, String localname, Attributes attributes, String effectiveId, XFormsControl control, boolean addId) {
        // Get standard attributes
        final AttributesImpl containerAttributes = super.getEmptyNestedControlAttributesMaybeWithId(uri, localname, attributes, effectiveId, control, addId);

        // Add title attribute if not yet present and there is a hint
        if (containerAttributes.getValue("title") == null) {
            final String hintValue = control != null ? control.getHint() : null;
            if (hintValue != null)
                containerAttributes.addAttribute("", "title", "title", XMLReceiverHelper.CDATA, hintValue);
        }

        // Handle accessibility attributes on <a>, <input> or <button>
        handleAccessibilityAttributes(attributes, containerAttributes);

        return containerAttributes;
    }
}
