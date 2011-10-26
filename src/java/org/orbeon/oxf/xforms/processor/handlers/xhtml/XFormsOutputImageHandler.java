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
import org.orbeon.oxf.xforms.control.controls.XFormsOutputControl;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLUtils;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Handler for xforms:output[starts-with(@appearance, 'image/')].
 */
public class XFormsOutputImageHandler extends XFormsOutputHandler {
    @Override
    protected void handleControlStart(String uri, String localname, String qName, Attributes attributes, String staticId, String effectiveId, XFormsControl control) throws SAXException {
        // Case of image media type with URI

        final XFormsOutputControl outputControl = (XFormsOutputControl) control;
        final ContentHandler contentHandler = handlerContext.getController().getOutput();
        final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
        final String mediatypeValue = attributes.getValue("mediatype");

        final AttributesImpl containerAttributes = getContainerAttributes(uri, localname, attributes, effectiveId, outputControl);

        if (!handlerContext.isSpanHTMLLayout())
            contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, getContainingElementName(), getContainingElementQName(), containerAttributes);
        {
            final AttributesImpl imgAttributes = getImgAttributes(outputControl, mediatypeValue, containerAttributes);

            // Handle accessibility attributes on <img>
            handleAccessibilityAttributes(attributes, imgAttributes);

            final String imgQName = XMLUtils.buildQName(xhtmlPrefix, "img");
            contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "img", imgQName, imgAttributes);
            contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "img", imgQName);
        }
        if (!handlerContext.isSpanHTMLLayout())
            contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, getContainingElementName(), getContainingElementQName());
    }

    private AttributesImpl getImgAttributes(XFormsOutputControl outputControl, String mediatypeValue, AttributesImpl newAttributes) {
        final AttributesImpl imgAttributes = handlerContext.isSpanHTMLLayout() ? newAttributes : new AttributesImpl();
        // @src="..."
        // NOTE: If producing a template, or if the image URL is blank, we point to an existing dummy image
        final String srcValue = XFormsOutputControl.getExternalValue(outputControl, mediatypeValue);
        imgAttributes.addAttribute("", "src", "src", ContentHandlerHelper.CDATA, srcValue != null ? srcValue : XFormsConstants.DUMMY_IMAGE_URI);
        return imgAttributes;
    }
}
