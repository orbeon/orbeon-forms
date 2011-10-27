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

import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.analysis.controls.TriggerAppearanceTrait;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsSingleNodeControl;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Handle xforms:trigger and xforms:submit.
 */
public abstract class XFormsTriggerHandler extends XFormsControlLifecyleHandler {

    public XFormsTriggerHandler() {
        super(false);
    }

    protected String getTriggerLabel(XFormsSingleNodeControl xformsControl) {
        final boolean hasLabel = XFormsControl.hasLabel(containingDocument, getPrefixedId());
        if (xformsControl != null && !hasLabel)// CHECK: really need to check on xformsControl != null?
            throw new ValidationException("Missing label on xforms:trigger element.", xformsControl.getLocationData());

        return !handlerContext.isTemplate() && xformsControl != null && xformsControl.getLabel() != null
                ? xformsControl.getLabel() : "";
    }

    @Override
    protected boolean isMustOutputControl(XFormsControl control) {
        // Don't output anything in static readonly mode
        return !isStaticReadonly(control);
    }

    @Override
    protected void handleLabel() throws SAXException {
        // Label is handled differently
    }

    @Override
    protected void handleHint() throws SAXException {
        // Hint is handled differently
    }

    @Override
    protected void handleAlert() throws SAXException {
        // Triggers don't need an alert (in theory, they could have one)
    }

    @Override
    protected void addCustomClasses(StringBuilder classes, XFormsControl control) {
        // Set modal class
        // TODO: xf:trigger/@xxforms:modal; do this in static state?
        if (((TriggerAppearanceTrait) elementAnalysis).isModal())
            classes.append(" xforms-trigger-appearance-modal");
    }

    @Override
    protected AttributesImpl getContainerAttributes(String uri, String localname, Attributes attributes, String effectiveId, XFormsControl control, boolean addId) {
        // Get standard attributes
        final AttributesImpl containerAttributes = super.getContainerAttributes(uri, localname, attributes, effectiveId, control, addId);

        // Add title attribute if not yet present and there is a hint
        if (containerAttributes.getValue("title") == null) {
            final String hintValue = control != null ? control.getHint() : null;
            if (hintValue != null)
                containerAttributes.addAttribute("", "title", "title", ContentHandlerHelper.CDATA, hintValue);
        }

        // Handle accessibility attributes on <a>, <input> or <button>
        handleAccessibilityAttributes(attributes, containerAttributes);

        return containerAttributes;
    }
}
