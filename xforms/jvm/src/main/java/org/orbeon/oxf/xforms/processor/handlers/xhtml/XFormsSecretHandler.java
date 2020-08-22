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

import org.orbeon.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.control.controls.XFormsSecretControl;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLReceiverHelper;
import org.orbeon.oxf.xml.XMLUtils;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import scala.Option;

/**
 * Handle xf:secret.
 */
public class XFormsSecretHandler extends XFormsControlLifecyleHandler {

    public XFormsSecretHandler(String uri, String localname, String qName, Attributes attributes, Object matched, Object handlerContext) {
        super(uri, localname, qName, attributes, matched, handlerContext, false, false);
    }

    @Override
    public void handleControlStart() throws SAXException {

        final XFormsSecretControl secretControl = (XFormsSecretControl) currentControl();
        final ContentHandler contentHandler = xformsHandlerContext.getController().getOutput();

        final AttributesImpl containerAttributes = getEmptyNestedControlAttributesMaybeWithId(getEffectiveId(), secretControl, true);

        // Create xhtml:input
        {
            final String xhtmlPrefix = xformsHandlerContext.findXHTMLPrefix();
            if (! isStaticReadonly(secretControl)) {
                final String inputQName = XMLUtils.buildQName(xhtmlPrefix, "input");
                containerAttributes.addAttribute("", "type", "type", XMLReceiverHelper.CDATA, "password");
                containerAttributes.addAttribute("", "name", "name", XMLReceiverHelper.CDATA, getEffectiveId());
                containerAttributes.addAttribute("", "value", "value", XMLReceiverHelper.CDATA,
                    secretControl == null || secretControl.getExternalValue() == null ? "" : secretControl.getExternalValue());

                // Handle accessibility attributes
                handleAccessibilityAttributes(attributes(), containerAttributes);
                handleAriaByAtts(containerAttributes);

                // Output all extension attributes
                // Output xxf:* extension attributes
                secretControl.addExtensionAttributesExceptClassAndAcceptForHandler(reusableAttributes, XFormsConstants.XXFORMS_NAMESPACE_URI());

                if (isXFormsReadonlyButNotStaticReadonly(secretControl))
                    outputReadonlyAttribute(reusableAttributes);

                handleAriaAttributes(secretControl.isRequired(), secretControl.isValid(), containerAttributes);

                // Output element
                contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI(), "input", inputQName, containerAttributes);
                contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI(), "input", inputQName);
            } else {
                // Output static read-only value
                final String spanQName = XMLUtils.buildQName(xhtmlPrefix, "span");
                containerAttributes.addAttribute("", "class", "class", "CDATA", "xforms-field");
                contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI(), "span", spanQName, containerAttributes);

                final Option<String> value = secretControl.getFormattedValue();
                if (value.isDefined())
                    contentHandler.characters(value.get().toCharArray(), 0, value.get().length());

                contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI(), "span", spanQName);
            }
        }
    }
}
