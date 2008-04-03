/**
 *  Copyright (C) 2005 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.processor.handlers;

import org.orbeon.oxf.xforms.control.XFormsSingleNodeControl;
import org.orbeon.oxf.xforms.control.controls.XFormsRangeControl;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.saxon.om.FastStringBuffer;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Handle xforms:range.
 */
public class XFormsRangeHandler extends XFormsCoreControlHandler {

    public XFormsRangeHandler() {
        super(false);
    }

    protected void handleControl(String uri, String localname, String qName, Attributes attributes, String id, String effectiveId, XFormsSingleNodeControl xformsControl) throws SAXException {

        final XFormsRangeControl rangeControl = (XFormsRangeControl) xformsControl;
        final ContentHandler contentHandler = handlerContext.getController().getOutput();

        final AttributesImpl newAttributes;
        {
            final FastStringBuffer classes = getInitialClasses(localname, attributes, rangeControl);
            classes.append(" xforms-range-background");

            handleMIPClasses(classes, rangeControl);
            newAttributes = getAttributes(attributes, classes.toString(), effectiveId);
        }

        // Create xhtml:div
        final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
        final String divQName = XMLUtils.buildQName(xhtmlPrefix, "div");
        {
            contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "div", divQName, newAttributes);

            {
                reusableAttributes.clear();
                reusableAttributes.addAttribute("", "class", "class", ContentHandlerHelper.CDATA, "xforms-range-thumb");
                contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "div", divQName, reusableAttributes);
                contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "div", divQName);
            }

            contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "div", divQName);
        }
    }
}
