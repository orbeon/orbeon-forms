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

import org.apache.commons.lang.StringUtils;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.controls.XFormsGroupControl;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.ElementHandlerController;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLUtils;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

public class XFormsGroupFieldsetHandler extends XFormsGroupHandler {

    private static final String ENCLOSING_ELEMENT_NAME = "fieldset";

    @Override
    protected String getContainingElementName() {
        return ENCLOSING_ELEMENT_NAME;
    }

    @Override
    public void handleControlStart(String uri, String localname, String qName, Attributes attributes, String staticId, final String effectiveId, XFormsControl control) throws SAXException {

        final XFormsGroupControl groupControl = (XFormsGroupControl) control;

        final String groupElementName = getContainingElementName();
        final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
        final String groupElementQName = XMLUtils.buildQName(xhtmlPrefix, groupElementName);

        final ElementHandlerController controller = handlerContext.getController();

        final ContentHandler contentHandler = controller.getOutput();

        // Start xhtml:fieldset element if needed
        if (!handlerContext.isSpanHTMLLayout())
            contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, groupElementName, groupElementQName,
                    getContainerAttributes(uri, localname, attributes, effectiveId, groupControl, false));

        // Output an xhtml:legend element if and only if there is an xforms:label element. This help with
        // styling in particular.
        final boolean hasLabel = XFormsControl.hasLabel(containingDocument, getPrefixedId());
        if (hasLabel) {

            // Handle label classes
            reusableAttributes.clear();
            reusableAttributes.addAttribute("", "class", "class", ContentHandlerHelper.CDATA, getLabelClasses(groupControl));
            reusableAttributes.addAttribute("", "id", "id", ContentHandlerHelper.CDATA, getLHHACId(containingDocument, effectiveId, LHHAC_CODES.get(LHHAC.LABEL)));

            // Output xhtml:legend with label content
            final String legendQName = XMLUtils.buildQName(xhtmlPrefix, "legend");
            contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "legend", legendQName, reusableAttributes);
            {
                final String labelValue = getLabelValue(groupControl);
                if (StringUtils.isNotEmpty(labelValue))
                    contentHandler.characters(labelValue.toCharArray(), 0, labelValue.length());
            }
            contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "legend", legendQName);
        }
    }

    @Override
    public void handleControlEnd(String uri, String localname, String qName, Attributes attributes, String staticId, String effectiveId, XFormsControl control) throws SAXException {

        final ElementHandlerController controller = handlerContext.getController();

        // Close xhtml:span
        final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
        final String groupElementName = getContainingElementName();
        final String groupElementQName = XMLUtils.buildQName(xhtmlPrefix, groupElementName);

        if (!handlerContext.isSpanHTMLLayout())
            controller.getOutput().endElement(XMLConstants.XHTML_NAMESPACE_URI, groupElementName, groupElementQName);
    }

    @Override
    protected void handleLabel() throws SAXException {
        // NOP because we handle the label in a custom way
    }
}
