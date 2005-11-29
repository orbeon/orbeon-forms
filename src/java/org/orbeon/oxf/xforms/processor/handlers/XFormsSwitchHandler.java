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

import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLUtils;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import java.util.Map;

/**
 * Handle xhtml:group.
 */
public class XFormsSwitchHandler extends XFormsGroupHandler {

    public XFormsSwitchHandler() {
    }

    public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {

        if (XFormsConstants.XFORMS_NAMESPACE_URI.equals(uri) && localname.equals("case")) {
            // xforms:case

            final String effectiveId = handlerContext.getEffectiveId(attributes);

            // Find classes to add
            final StringBuffer classes = new StringBuffer("xforms-" + localname);

            final AttributesImpl newAttributes = getAttributes(attributes, classes.toString(), effectiveId);

            final Map switchIdToSelectedCaseIdMap = containingDocument.getXFormsControls().getCurrentControlsState().getSwitchIdToSelectedCaseIdMap();

            final String selectedCaseId = (String) switchIdToSelectedCaseIdMap.get(effectiveGroupId);
            final boolean isVisible = effectiveId.equals(selectedCaseId);
            newAttributes.addAttribute("", "style", "style", ContentHandlerHelper.CDATA, "display: " + (isVisible ? "block" : "none"));

            final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
            final String spanQName = XMLUtils.buildQName(xhtmlPrefix, "span");
            handlerContext.getController().getOutput().startElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName, newAttributes);
        } else {
            super.startElement(uri, localname, qName, attributes);
        }
    }

    public void endElement(String uri, String localname, String qName) throws SAXException {

        if (XFormsConstants.XFORMS_NAMESPACE_URI.equals(uri) && localname.equals("case")) {
            // xforms:case

            final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
            final String spanQName = XMLUtils.buildQName(xhtmlPrefix, "span");
            handlerContext.getController().getOutput().endElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName);
        } else {
            super.endElement(uri, localname, qName);
        }
    }
}
