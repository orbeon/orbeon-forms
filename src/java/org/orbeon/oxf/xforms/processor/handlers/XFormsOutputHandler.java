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
import org.orbeon.oxf.xforms.control.controls.XFormsOutputControl;
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
        final XFormsOutputControl xformsOutputControl = handlerContext.isGenerateTemplate()
                ? null : (XFormsOutputControl) containingDocument.getObjectById(pipelineContext, effectiveId);

        // The "control" is allowed to be null when xforms:output is in
        // xforms:label|xforms:hint|xforms:alert|xforms:help, because in that case currently we don't put the control in
        // the regular hierarchy of controls
        if (xformsOutputControl == null && !handlerContext.isGenerateTemplate())
            return;

        // xforms:label
        handleLabelHintHelpAlert(effectiveId, "label", xformsOutputControl);

        final AttributesImpl newAttributes;
        final boolean isDateOrTime;
        final StringBuffer classes = getInitialClasses(localname, xformsOutputControl);

        final String appearanceValue = elementAttributes.getValue("appearance");
        final String appearanceLocalname = (appearanceValue == null) ? null : XMLUtils.localNameFromQName(appearanceValue);
        final String appearanceURI = (appearanceValue == null) ? null : uriFromQName(appearanceValue);

        final String mediatypeValue = elementAttributes.getValue("mediatype");
        final boolean isImage = mediatypeValue != null && mediatypeValue.startsWith("image/");
        final boolean isHTML = (mediatypeValue != null && mediatypeValue.equals("text/html"))
                || (appearanceValue != null && XFormsConstants.XXFORMS_NAMESPACE_URI.equals(appearanceURI) && "html".equals(appearanceLocalname));

        if (isHTML) {
            classes.append(" xforms-initially-hidden");
            classes.append(" xforms-mediatype-text-html");
        } else if (isImage) {
            classes.append(" xforms-mediatype-image");
        }

        if (!handlerContext.isGenerateTemplate()) {

            // Find classes to add
            isDateOrTime = isDateOrTime(xformsOutputControl.getType());
            handleMIPClasses(classes, xformsOutputControl);

            newAttributes = getAttributes(elementAttributes, classes.toString(), effectiveId);
        } else {
            isDateOrTime = false;

            // Find classes to add
            newAttributes = getAttributes(elementAttributes, classes.toString(), effectiveId);
        }

        // Create xhtml:span or xhtml:div
        final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
        // For IE we need to generate a div here for IE, which doesn't support working with innterHTML on spans.
        final String enclosingElementLocalname = isHTML ? "div" : "span";
        final String enclosingElementQName = XMLUtils.buildQName(xhtmlPrefix, enclosingElementLocalname);

        contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, enclosingElementLocalname, enclosingElementQName, newAttributes);
        if (!handlerContext.isGenerateTemplate()) {
            if (isImage) {
                // Case of image media type with URI
                final String imgQName = XMLUtils.buildQName(xhtmlPrefix, "img");
                final AttributesImpl imgAttributes = new AttributesImpl();
                // @src="..."
                imgAttributes.addAttribute("", "src", "src", ContentHandlerHelper.CDATA, xformsOutputControl.getValue());
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
                final String displayValue = xformsOutputControl.getDisplayValueOrValue();
                if (displayValue != null)
                    contentHandler.characters(displayValue.toCharArray(), 0, displayValue.length());
            } else {
                // Regular text case
                final String displayValue = xformsOutputControl.getDisplayValueOrValue();
                if (displayValue != null)
                    contentHandler.characters(displayValue.toCharArray(), 0, displayValue.length());
            }
        }
        contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, enclosingElementLocalname, enclosingElementQName);

        // xforms:help
        handleLabelHintHelpAlert(effectiveId, "help", xformsOutputControl);

        // xforms:alert
        if (elementAttributes.getValue("value") == null)
            handleLabelHintHelpAlert(effectiveId, "alert", xformsOutputControl);

        // xforms:hint
        handleLabelHintHelpAlert(effectiveId, "hint", xformsOutputControl);
    }
}
