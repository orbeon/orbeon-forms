/**
 * Copyright (C) 2009 Orbeon, Inc.
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

import org.orbeon.oxf.xforms.control.XFormsSingleNodeControl;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLUtils;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Handle xforms:range.
 */
public class XFormsRangeHandler extends XFormsControlLifecyleHandler {

    private static final String ENCLOSING_ELEMENT_NAME = "div";

    public XFormsRangeHandler() {
        super(false);
    }

    @Override
    protected void addCustomClasses(StringBuilder classes, XFormsSingleNodeControl xformsControl) {
        classes.append(" xforms-range-background");
    }

    protected void handleControlStart(String uri, String localname, String qName, Attributes attributes, String staticId, String effectiveId, XFormsSingleNodeControl xformsControl) throws SAXException {

        final ContentHandler contentHandler = handlerContext.getController().getOutput();

        final AttributesImpl containerAttributes = getContainerAttributes(uri, localname, attributes, effectiveId, xformsControl, true);

        // Create xhtml:div
        final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
        final String divQName = XMLUtils.buildQName(xhtmlPrefix, ENCLOSING_ELEMENT_NAME);
        {
            if (!handlerContext.isNewXHTMLLayout())
                contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, ENCLOSING_ELEMENT_NAME, divQName, containerAttributes);
            {
                final AttributesImpl sliderAttributes = getSliderAttributes(containerAttributes);
                contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, ENCLOSING_ELEMENT_NAME, divQName, sliderAttributes);
                contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, ENCLOSING_ELEMENT_NAME, divQName);
            }
            if (!handlerContext.isNewXHTMLLayout())
                contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, ENCLOSING_ELEMENT_NAME, divQName);
        }
    }

    private AttributesImpl getSliderAttributes(AttributesImpl containerAttributes) {
        final AttributesImpl sliderAttributes;
        if (handlerContext.isNewXHTMLLayout()) {
            sliderAttributes = containerAttributes;
        } else {
            reusableAttributes.clear();
            sliderAttributes = reusableAttributes;
        }
        sliderAttributes.addAttribute("", "class", "class", ContentHandlerHelper.CDATA, "xforms-range-thumb");
        return sliderAttributes;
    }
}
