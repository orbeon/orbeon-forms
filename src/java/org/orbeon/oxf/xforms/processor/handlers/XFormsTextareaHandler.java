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
package org.orbeon.oxf.xforms.processor.handlers;

import org.orbeon.oxf.pipeline.api.XMLReceiver;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.controls.XFormsTextareaControl;
import org.orbeon.oxf.xml.*;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Handle xforms:textarea.
 * 
 * TODO: Subclasses per appearance.
 */
public class XFormsTextareaHandler extends XFormsControlLifecyleHandler {

    public XFormsTextareaHandler() {
        super(false);
    }

    protected void handleControlStart(String uri, String localname, String qName, Attributes attributes, String staticId, String effectiveId, XFormsControl control) throws SAXException {

        final XFormsTextareaControl textareaControl = (XFormsTextareaControl) control;
        final XMLReceiver xmlReceiver = handlerContext.getController().getOutput();
        final boolean isConcreteControl = textareaControl != null;

        final AttributesImpl containerAttributes = getContainerAttributes(uri, localname, attributes, effectiveId, control, true);

        // Create xhtml:textarea
        {
            final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
            if (!isStaticReadonly(textareaControl)) {
                final String textareaQName = XMLUtils.buildQName(xhtmlPrefix, "textarea");
                containerAttributes.addAttribute("", "name", "name", ContentHandlerHelper.CDATA, effectiveId);

                // Handle accessibility attributes
                handleAccessibilityAttributes(attributes, containerAttributes);

                // Output all extension attributes
                if (isConcreteControl) {
                    // Output xxforms:* extension attributes
                    textareaControl.addExtensionAttributes(reusableAttributes, XFormsConstants.XXFORMS_NAMESPACE_URI);
                }

                if (isHTMLDisabled(textareaControl))
                    outputDisabledAttribute(reusableAttributes);

                xmlReceiver.startElement(XMLConstants.XHTML_NAMESPACE_URI, "textarea", textareaQName, containerAttributes);
                if (isConcreteControl) {
                    final String value = textareaControl.getExternalValue();
                    if (value != null)
                        xmlReceiver.characters(value.toCharArray(), 0, value.length());
                }
                xmlReceiver.endElement(XMLConstants.XHTML_NAMESPACE_URI, "textarea", textareaQName);
            } else {
                // Static readonly

                // For now the mediatype is known statically
                final boolean isHTMLMediaType = "text/html".equals(attributes.getValue("mediatype"));

                // Use <pre> in text/plain so that spaces are kept by the serializer
                final String containerName = isHTMLMediaType ? "span" : "pre";
                final String containerQName = XMLUtils.buildQName(xhtmlPrefix, containerName);

                xmlReceiver.startElement(XMLConstants.XHTML_NAMESPACE_URI, containerName, containerQName, containerAttributes);
                if (isConcreteControl) {
                    final String value = textareaControl.getExternalValue();
                    if (value != null) {
                        if (!isHTMLMediaType) {
                            // NOTE: Don't replace spaces with &nbsp;, as this is not the right algorithm for all cases
                            xmlReceiver.characters(value.toCharArray(), 0, value.length());
                        } else {
                            XFormsUtils.streamHTMLFragment(xmlReceiver, value, textareaControl.getLocationData(), xhtmlPrefix);
                        }
                    }
                }
                xmlReceiver.endElement(XMLConstants.XHTML_NAMESPACE_URI, containerName, containerQName);
            }
        }
    }
}
