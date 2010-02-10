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

import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.control.XFormsSingleNodeControl;
import org.orbeon.oxf.xforms.control.controls.XFormsOutputControl;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLUtils;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Handler for xforms:output[@appearance = 'xxforms:download'].
 */
public class XFormsOutputDownloadHandler extends XFormsOutputHandler {

    @Override
    protected void handleLabel() {
        // NOP because the label is output as the text within <a>
    }

    @Override
    protected void handleControlStart(String uri, String localname, String qName, Attributes attributes, String staticId, String effectiveId, XFormsSingleNodeControl xformsControl) throws SAXException {

        final XFormsOutputControl outputControl = (XFormsOutputControl) xformsControl;
        final ContentHandler contentHandler = handlerContext.getController().getOutput();
        final String xhtmlPrefix = handlerContext.findXHTMLPrefix();

        final AttributesImpl containerAttributes = getContainerAttributes(uri, localname, attributes, effectiveId, outputControl);

        if (!handlerContext.isSpanHTMLLayout())
            contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, getContainingElementName(), getContainingElementQName(), containerAttributes);
        {
            final AttributesImpl aAttributes = getAnchorAttributes(outputControl, containerAttributes);

            // Handle accessibility attributes on <a>
            handleAccessibilityAttributes(attributes, aAttributes);

            final String aQName = XMLUtils.buildQName(xhtmlPrefix, "a");
            contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "a", aQName, aAttributes);
            {
                final String labelValue = (xformsControl != null) ? xformsControl.getLabel(pipelineContext) : null;
                final boolean mustOutputHTMLFragment = xformsControl != null && xformsControl.isHTMLLabel(pipelineContext);
                outputLabelText(contentHandler, xformsControl, labelValue, xhtmlPrefix, mustOutputHTMLFragment);
            }
            contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "a", aQName);
        }
        if (!handlerContext.isSpanHTMLLayout())
            contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, getContainingElementName(), getContainingElementQName());
    }

    private AttributesImpl getAnchorAttributes(XFormsOutputControl outputControl, AttributesImpl containerAttributes) {
        final AttributesImpl aAttributes = handlerContext.isSpanHTMLLayout() ? containerAttributes : new AttributesImpl();
        final String hrefValue = XFormsOutputControl.getExternalValue(pipelineContext, outputControl, null);

        if (hrefValue == null || hrefValue.trim().equals("")) {
            // No URL so make sure a click doesn't cause navigation, and add class
            aAttributes.addAttribute("", "href", "href", ContentHandlerHelper.CDATA, "#");
            XMLUtils.addOrAppendToAttribute(aAttributes, "class", "xforms-readonly");
        } else {
            // URL value
            aAttributes.addAttribute("", "href", "href", ContentHandlerHelper.CDATA, hrefValue);
        }

        // Add _blank target in order to prevent:
        // 1. The browser replacing the current page, and
        // 2. The browser displaying the "Are you sure you want to navigate away from this page?" warning dialog
        // This, as of 2009-05, seems to be how most sites handle this
        aAttributes.addAttribute("", "target", "target", ContentHandlerHelper.CDATA, "_blank");

        // Output xxforms:* extension attributes
        if (outputControl != null)
            outputControl.addExtensionAttributes(aAttributes, XFormsConstants.XXFORMS_NAMESPACE_URI);

        return aAttributes;
    }
}
