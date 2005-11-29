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
import org.orbeon.oxf.xforms.XFormsControls;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLUtils;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Handle xforms:output.
 */
public class XFormsOutputHandler extends XFormsValueControlHandler {

    private Attributes elementAttributes;

    public XFormsOutputHandler() {
        super(false);
    }

    public void start(String uri, String localname, String qName, Attributes attributes) throws SAXException {
        elementAttributes = new AttributesImpl(attributes);
        super.start(uri, localname, qName, attributes);
    }

    public void end(String uri, String localname, String qName) throws SAXException {

        final ContentHandler contentHandler = handlerContext.getController().getOutput();
        final String effectiveId = handlerContext.getEffectiveId(elementAttributes);
        final XFormsControls.OutputControlInfo controlInfo = handlerContext.isGenerateTemplate()
                ? null : (XFormsControls.OutputControlInfo) containingDocument.getObjectById(pipelineContext, effectiveId);

        // xforms:label
        handleLabelHintHelpAlert(effectiveId, "label", controlInfo);

        final AttributesImpl newAttributes;
        final boolean isImage;
        final boolean isDateOrTime;
        final StringBuffer classes = new StringBuffer("xforms-control xforms-output");
        if (!handlerContext.isGenerateTemplate()) {

            final String appearanceValue = elementAttributes.getValue("appearance");
            final String appearanceLocalname = (appearanceValue == null) ? null : XMLUtils.localNameFromQName(appearanceValue);
            final String appearanceURI = (appearanceValue == null) ? null : uriFromQName(appearanceValue);

            final String mediaType = controlInfo.getMediaTypeAttribute();

            final boolean isHTML = appearanceValue != null && XFormsConstants.XXFORMS_NAMESPACE_URI.equals(appearanceURI) && "html".equals(appearanceLocalname);
            isImage = mediaType != null && mediaType.startsWith("image/");

            // Find classes to add

            if (isHTML) {
                classes.append(" xforms-output-html");
            } else if (isImage) {
                classes.append(" xforms-output-image");
            }
            isDateOrTime = isDateOrTime(controlInfo.getType());
            if (isDateOrTime) {
                classes.append(" xforms-date");
            }
            handleReadOnlyClass(classes, controlInfo);
            handleRelevantClass(classes, controlInfo);

            newAttributes = getAttributes(elementAttributes, classes.toString(), effectiveId);
            handleReadOnlyAttribute(newAttributes, controlInfo);
        } else {
            isImage = false;
            isDateOrTime = false;

            // Find classes to add
            newAttributes = getAttributes(elementAttributes, classes.toString(), effectiveId);
        }

        // Create xhtml:span
        final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
        final String spanQName = XMLUtils.buildQName(xhtmlPrefix, "span");

        contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName, newAttributes);
        if (!handlerContext.isGenerateTemplate()) {
            if (isImage) {
                // Case of image media type with URI
                final String imgQName = XMLUtils.buildQName(xhtmlPrefix, "img");
                final AttributesImpl imgAttributes = new AttributesImpl();
                // @src="..."
                imgAttributes.addAttribute("", "src", "src", ContentHandlerHelper.CDATA, controlInfo.getValue());
                // @f:url-norewrite="true"
                final String formattingPrefix;
                final boolean isNewPrefix;
                {
                    final String existingFormattingPrefix = handlerContext.findFormattingPrefix();
                    if (existingFormattingPrefix == null || "".equals(existingFormattingPrefix)) {
                        // No prefix is currently mapped
                        formattingPrefix = handlerContext.findNewPrefix();
                        isNewPrefix = true;
                    } else {
                        formattingPrefix = existingFormattingPrefix;
                        isNewPrefix = false;
                    }
                    imgAttributes.addAttribute(XMLConstants.OPS_FORMATTING_URI, "url-norewrite", XMLUtils.buildQName(formattingPrefix, "url-norewrite"), ContentHandlerHelper.CDATA, "true");
                }
                if (isNewPrefix)
                    contentHandler.startPrefixMapping(formattingPrefix, XMLConstants.OPS_FORMATTING_URI);
                contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "img", imgQName, imgAttributes);
                contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "img", imgQName);
                if (isNewPrefix)
                    contentHandler.endPrefixMapping(formattingPrefix);
            } else if (isDateOrTime) {
                // Display formatted value for dates
                final String displayValue = controlInfo.getDisplayValue();
                contentHandler.characters(displayValue.toCharArray(), 0, displayValue.length());
            } else {
                // Regular text case
                final String value = controlInfo.getValue();
                contentHandler.characters(value.toCharArray(), 0, value.length());
            }
        }
        contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName);

        // xforms:help
        handleLabelHintHelpAlert(effectiveId, "help", controlInfo);

        // xforms:hint
        handleLabelHintHelpAlert(effectiveId, "hint", controlInfo);
    }
}
