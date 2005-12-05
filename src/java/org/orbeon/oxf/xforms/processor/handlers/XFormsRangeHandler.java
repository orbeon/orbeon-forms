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

import org.orbeon.oxf.xml.*;
import org.orbeon.oxf.xforms.XFormsControls;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.ContentHandler;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Handle xforms:range.
 */
public class XFormsRangeHandler extends XFormsValueControlHandler {

    private Attributes elementAttributes;

    public XFormsRangeHandler() {
        super(false);
    }

    public void start(String uri, String localname, String qName, Attributes attributes) throws SAXException {
        elementAttributes = new AttributesImpl(attributes);
        super.start(uri, localname, qName, attributes);
    }

    public void end(String uri, String localname, String qName) throws SAXException {

        final ContentHandler contentHandler = handlerContext.getController().getOutput();
        final String effectiveId = handlerContext.getEffectiveId(elementAttributes);
        final XFormsControls.ControlInfo controlInfo = handlerContext.isGenerateTemplate()
                ? null : (XFormsControls.ControlInfo) containingDocument.getObjectById(pipelineContext, effectiveId);

        // xforms:label
        handleLabelHintHelpAlert(effectiveId, "label", controlInfo);

        final AttributesImpl newAttributes;
        {
            final StringBuffer classes = new StringBuffer("xforms-control xforms-range xforms-range-casing");
            if (!handlerContext.isGenerateTemplate()) {

                handleMIPClasses(classes, controlInfo);

                newAttributes = getAttributes(elementAttributes, classes.toString(), effectiveId);
            } else {
                newAttributes = getAttributes(elementAttributes, classes.toString(), effectiveId);
            }
        }

        // Create xhtml:div
        final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
        final String divQName = XMLUtils.buildQName(xhtmlPrefix, "div");
        {
            contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "div", divQName, newAttributes);

            {
                reusableAttributes.clear();
                reusableAttributes.addAttribute("", "class", "class", ContentHandlerHelper.CDATA, "xforms-range-track");
                contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "div", divQName, reusableAttributes);
                contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "div", divQName);
            }

            {
                reusableAttributes.clear();
                reusableAttributes.addAttribute("", "class", "class", ContentHandlerHelper.CDATA, "xforms-range-slider");
                contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "div", divQName, reusableAttributes);
                contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "div", divQName);
            }

            contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "div", divQName);
        }

        // xforms:help
        handleLabelHintHelpAlert(effectiveId, "help", controlInfo);

        // xforms:alert
        handleLabelHintHelpAlert(effectiveId, "alert", controlInfo);

        // xforms:hint
        handleLabelHintHelpAlert(effectiveId, "hint", controlInfo);
    }
}
