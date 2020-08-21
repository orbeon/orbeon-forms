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

import org.orbeon.oxf.xforms.control.controls.XFormsRangeControl;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLReceiverHelper;
import org.orbeon.oxf.xml.XMLUtils;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Handle xf:range.
 */
public class XFormsRangeHandler extends XFormsControlLifecyleHandler {

    public XFormsRangeHandler(String uri, String localname, String qName, Attributes attributes, Object matched, Object handlerContext) {
        super(uri, localname, qName, attributes, matched, handlerContext, false, false);
    }

    private static final String RANGE_BACKGROUND_CLASS = "xforms-range-background";
    private static final String RANGE_THUMB_CLASS      = "xforms-range-thumb";

    @Override
    public String getContainingElementName() {
        return "div";
    }

    @Override
    public void handleControlStart() throws SAXException {

        final XFormsRangeControl rangeControl = (XFormsRangeControl) currentControl();
        final ContentHandler contentHandler = xformsHandlerContext.getController().getOutput();

        // Create nested xhtml:div elements
        {
            final String xhtmlPrefix = xformsHandlerContext.findXHTMLPrefix();
            final String divName     = "div";
            final String divQName    = XMLUtils.buildQName(xhtmlPrefix, divName);

            final AttributesImpl backgroundAttributes = getBackgroundAttributes(getEffectiveId(), rangeControl);
            contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI(), divName, divQName, backgroundAttributes);
            {
                contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI(), divName, divQName, getThumbAttributes());
                contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI(), divName, divQName);
            }
            contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI(), divName, divQName);
        }
    }

    private AttributesImpl getThumbAttributes() {
        // Just set class
        reusableAttributes.clear();
        reusableAttributes.addAttribute("", "class", "class", XMLReceiverHelper.CDATA, RANGE_THUMB_CLASS);
        return reusableAttributes;
    }

    private AttributesImpl getBackgroundAttributes(String effectiveId, XFormsRangeControl xformsControl) {
        // Add custom class
        final AttributesImpl containerAttributes = getEmptyNestedControlAttributesMaybeWithId(effectiveId, xformsControl, true);
        containerAttributes.addAttribute("", "class", "class", XMLReceiverHelper.CDATA, RANGE_BACKGROUND_CLASS);
        return containerAttributes;
    }
}
