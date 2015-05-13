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

import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.controls.XFormsSecretControl;
import org.orbeon.oxf.xml.*;
import org.xml.sax.*;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Handle xf:secret.
 */
public class XFormsSecretHandler extends XFormsControlLifecyleHandler {

    private static final String HIDDEN_PASSWORD = "********";

    public XFormsSecretHandler() {
        super(false);
    }

    protected void handleControlStart(String uri, String localname, String qName, Attributes attributes, String effectiveId, XFormsControl control) throws SAXException {

        final XFormsSecretControl secretControl = (XFormsSecretControl) control;
        final ContentHandler contentHandler = handlerContext.getController().getOutput();
        final boolean isConcreteControl = secretControl != null;

        final AttributesImpl containerAttributes = getEmptyNestedControlAttributesMaybeWithId(uri, localname, attributes, effectiveId, secretControl, true);

        // Create xhtml:input
        {
            final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
            if (!isStaticReadonly(secretControl)) {
                final String inputQName = XMLUtils.buildQName(xhtmlPrefix, "input");
                containerAttributes.addAttribute("", "type", "type", XMLReceiverHelper.CDATA, "password");
                containerAttributes.addAttribute("", "name", "name", XMLReceiverHelper.CDATA, effectiveId);
                containerAttributes.addAttribute("", "value", "value", XMLReceiverHelper.CDATA,
                        handlerContext.isTemplate() || secretControl == null || secretControl.getExternalValue() == null ? "" : secretControl.getExternalValue());

                // Handle accessibility attributes
                handleAccessibilityAttributes(attributes, containerAttributes, secretControl);

                // Output all extension attributes
                if (isConcreteControl) {
                    // Output xxf:* extension attributes
                    secretControl.addExtensionAttributesExceptClassAndAcceptForHandler(reusableAttributes, XFormsConstants.XXFORMS_NAMESPACE_URI);
                }

                if (isHTMLDisabled(secretControl))
                    outputDisabledAttribute(reusableAttributes);

                if (isConcreteControl)
                    handleAriaAttributes(secretControl.isRequired(), secretControl.isValid(), containerAttributes);

                // Output element
                contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "input", inputQName, containerAttributes);
                contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "input", inputQName);
            } else {
                // Output static read-only value
                final String spanQName = XMLUtils.buildQName(xhtmlPrefix, "span");
                containerAttributes.addAttribute("", "class", "class", "CDATA", "xforms-field");
                contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName, containerAttributes);

                final String value = secretControl.getValue();
                // TODO: Make sure that Ajax response doesn't send the value back
                if (value != null && value.length() > 0)
                    contentHandler.characters(HIDDEN_PASSWORD.toCharArray(), 0, HIDDEN_PASSWORD.length());
                contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName);
            }
        }
    }
}
