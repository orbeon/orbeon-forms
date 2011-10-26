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
import org.orbeon.oxf.xforms.control.controls.XFormsRangeControl;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.XMLConstants;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Handle xforms:range.
 */
public class XFormsRangeHandler extends XFormsControlLifecyleHandler {

    private static final String ENCLOSING_ELEMENT_NAME = "div";
    private static final String RANGE_BACKGROUND_CLASS = "xforms-range-background";
    private static final String RANGE_THUMB_CLASS = "xforms-range-thumb";

    public XFormsRangeHandler() {
        super(false);
    }

    @Override
    protected String getContainingElementName() {
        return ENCLOSING_ELEMENT_NAME;
    }

    @Override
    protected void addCustomClasses(StringBuilder classes, XFormsControl control) {
        if (!handlerContext.isSpanHTMLLayout()) {
            if (classes.length() > 0)
                classes.append(' ');
            classes.append(RANGE_BACKGROUND_CLASS);
        }
    }

    protected void handleControlStart(String uri, String localname, String qName, Attributes attributes, String staticId, String effectiveId, XFormsControl control) throws SAXException {

        final XFormsRangeControl rangeControl = (XFormsRangeControl) control;
        final ContentHandler contentHandler = handlerContext.getController().getOutput();

        // Create nested xhtml:div elements
        {
            final String divName = getContainingElementName();
            final String divQName = getContainingElementQName();

            final AttributesImpl backgroundAttributes = getBackgroundAttributes(uri, localname, attributes, effectiveId, rangeControl);
            contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, divName, divQName, backgroundAttributes);
            {
                contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, divName, divQName, getThumbAttributes());
                contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, divName, divQName);
            }
            contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, divName, divQName);
        }
    }

    private AttributesImpl getThumbAttributes() {
        // Just set class
        reusableAttributes.clear();
        reusableAttributes.addAttribute("", "class", "class", ContentHandlerHelper.CDATA, RANGE_THUMB_CLASS);
        return reusableAttributes;
    }

    protected AttributesImpl getBackgroundAttributes(String uri, String localname, Attributes attributes, String effectiveId, XFormsRangeControl xformsControl) {
        final AttributesImpl containerAttributes = getContainerAttributes(uri, localname, attributes, effectiveId, xformsControl, true);
        if (handlerContext.isSpanHTMLLayout()) {
            // Add custom class (added in addCustomClasses() for old layout)
            containerAttributes.addAttribute("", "class", "class", ContentHandlerHelper.CDATA, RANGE_BACKGROUND_CLASS);
        }
        return containerAttributes;
    }
}
