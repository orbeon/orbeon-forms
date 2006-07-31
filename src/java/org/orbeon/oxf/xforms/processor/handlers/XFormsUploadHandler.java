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

import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLUtils;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Handle xforms:upload.
 */
public class XFormsUploadHandler extends XFormsValueControlHandler {

    private Attributes elementAttributes;

    public XFormsUploadHandler() {
        super(false);
    }

    public void start(String uri, String localname, String qName, Attributes attributes) throws SAXException {
        elementAttributes = new AttributesImpl(attributes);
        super.start(uri, localname, qName, attributes);
    }

    public void end(String uri, String localname, String qName) throws SAXException {

        final ContentHandler contentHandler = handlerContext.getController().getOutput();
        final String effectiveId = handlerContext.getEffectiveId(elementAttributes);
        final XFormsControl XFormsControl = handlerContext.isGenerateTemplate()
                ? null : (XFormsControl) containingDocument.getObjectById(pipelineContext, effectiveId);

        // xforms:label
        handleLabelHintHelpAlert(effectiveId, "label", XFormsControl);

        final AttributesImpl newAttributes;
        {
            final StringBuffer classes = getInitialClasses(localname, elementAttributes, XFormsControl);
            if (!handlerContext.isGenerateTemplate()) {
                handleMIPClasses(classes, XFormsControl);
                newAttributes = getAttributes(elementAttributes, classes.toString(), effectiveId);
            } else {
                newAttributes = getAttributes(elementAttributes, classes.toString(), effectiveId);
            }
        }

        // Handle accessibility attributes
        handleAccessibilityAttributes(elementAttributes, newAttributes);

        // Create xhtml:input
        final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
        final String inputQName = XMLUtils.buildQName(xhtmlPrefix, "input");
        {
            newAttributes.addAttribute("", "type", "type", ContentHandlerHelper.CDATA, "file");
            newAttributes.addAttribute("", "name", "name", ContentHandlerHelper.CDATA, effectiveId);
            newAttributes.addAttribute("", "value", "value", ContentHandlerHelper.CDATA,
                    handlerContext.isGenerateTemplate() ? "" : XFormsControl.getValue() != null ? XFormsControl.getValue() : "");

            handleReadOnlyAttribute(newAttributes, XFormsControl);
            contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "input", inputQName, newAttributes);
            contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "input", inputQName);
        }

        // xforms:help
        handleLabelHintHelpAlert(effectiveId, "help", XFormsControl);

        // xforms:alert
        handleLabelHintHelpAlert(effectiveId, "alert", XFormsControl);

        // xforms:hint
        handleLabelHintHelpAlert(effectiveId, "hint", XFormsControl);
    }
}
