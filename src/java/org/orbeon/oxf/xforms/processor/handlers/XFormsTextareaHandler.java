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
 * Handle xforms:textarea.
 */
public class XFormsTextareaHandler extends XFormsValueControlHandler {

    private Attributes elementAttributes;

    public XFormsTextareaHandler(HandlerContext context) {
        super(context, false);
    }

    public void start(String uri, String localname, String qName, Attributes attributes) throws SAXException {
        elementAttributes = new AttributesImpl(attributes);
    }

    public void end(String uri, String localname, String qName) throws SAXException {

        final ContentHandler contentHandler = handlerContext.getOutput();
        final String effectiveId = handlerContext.getEffectiveId(elementAttributes);
        final XFormsControls.ControlInfo controlInfo = handlerContext.isGenerateTemplate()
                ? null : (XFormsControls.ControlInfo) containingDocument.getObjectById(pipelineContext, effectiveId);

        // xforms:label
        handleLabelHintHelpAlert(effectiveId, "label", controlInfo);

        final AttributesImpl newAttributes;
        {
            final StringBuffer classes = new StringBuffer("xforms-control xforms-textarea");
            if (!handlerContext.isGenerateTemplate()) {

                handleReadOnlyClass(classes, controlInfo);
                handleRelevantClass(classes, controlInfo);

                newAttributes = getAttributes(elementAttributes, classes.toString(), effectiveId);
                handleReadOnlyAttribute(newAttributes, controlInfo);
            } else {
                newAttributes = getAttributes(elementAttributes, classes.toString(), effectiveId);
            }
        }

        // Create xhtml:textarea
        {
            final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
            final String textareaQName = XMLUtils.buildQName(xhtmlPrefix, "textarea");
            newAttributes.addAttribute("", "name", "name", ContentHandlerHelper.CDATA, effectiveId);

            contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "textarea", textareaQName, newAttributes);
            if (!handlerContext.isGenerateTemplate()) {
                final String value = controlInfo.getValue();
                contentHandler.characters(value.toCharArray(), 0, value.length());
            }
            contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "textarea", textareaQName);
        }

        // xforms:help
        handleLabelHintHelpAlert(effectiveId, "help", controlInfo);

        // xforms:alert
        handleLabelHintHelpAlert(effectiveId, "alert", controlInfo);

        // xforms:hint
        handleLabelHintHelpAlert(effectiveId, "hint", controlInfo);
    }
}
