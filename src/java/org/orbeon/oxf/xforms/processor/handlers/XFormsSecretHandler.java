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

import org.orbeon.oxf.xforms.control.XFormsValueControl;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLUtils;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Handle xforms:secret.
 */
public class XFormsSecretHandler extends XFormsValueControlHandler {

    private static final String HIDDEN_PASSWORD = "********";

    private Attributes elementAttributes;

    public XFormsSecretHandler() {
        super(false);
    }

    public void start(String uri, String localname, String qName, Attributes attributes) throws SAXException {
        elementAttributes = new AttributesImpl(attributes);
        super.start(uri, localname, qName, attributes);
    }

    public void end(String uri, String localname, String qName) throws SAXException {

        final ContentHandler contentHandler = handlerContext.getController().getOutput();
        final String effectiveId = handlerContext.getEffectiveId(elementAttributes);
        final XFormsValueControl xformsControl = handlerContext.isGenerateTemplate()
                ? null : (XFormsValueControl) containingDocument.getObjectById(pipelineContext, effectiveId);

        // xforms:label
        handleLabelHintHelpAlert(effectiveId, "label", xformsControl);

        final AttributesImpl newAttributes;
        {
            final StringBuffer classes = getInitialClasses(localname, elementAttributes, xformsControl);
            if (!handlerContext.isGenerateTemplate()) {

                handleMIPClasses(classes, xformsControl);

                newAttributes = getAttributes(elementAttributes, classes.toString(), effectiveId);
                handleReadOnlyAttribute(newAttributes, containingDocument, xformsControl);
            } else {
                newAttributes = getAttributes(elementAttributes, classes.toString(), effectiveId);
            }
        }

        // Create xhtml:input
        {
            final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
            if (!isStaticReadonly(xformsControl)) {
                final String inputQName = XMLUtils.buildQName(xhtmlPrefix, "input");
                newAttributes.addAttribute("", "type", "type", ContentHandlerHelper.CDATA, "password");
                newAttributes.addAttribute("", "name", "name", ContentHandlerHelper.CDATA, effectiveId);
                newAttributes.addAttribute("", "value", "value", ContentHandlerHelper.CDATA,
                        handlerContext.isGenerateTemplate() ? "" : xformsControl.getValue());

                // Handle accessibility attributes
                handleAccessibilityAttributes(elementAttributes, newAttributes);

                contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "input", inputQName, newAttributes);
                contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "input", inputQName);
            } else {
                final String spanQName = XMLUtils.buildQName(xhtmlPrefix, "span");
                contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName, newAttributes);
                final String value = xformsControl.getValue();
                if (value != null && value.length() > 0)
                    contentHandler.characters(HIDDEN_PASSWORD.toCharArray(), 0, HIDDEN_PASSWORD.length());
                contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName);
            }
        }

        // xforms:help
        handleLabelHintHelpAlert(effectiveId, "help", xformsControl);

        // xforms:alert
        handleLabelHintHelpAlert(effectiveId, "alert", xformsControl);

        // xforms:hint
        handleLabelHintHelpAlert(effectiveId, "hint", xformsControl);
    }
}
