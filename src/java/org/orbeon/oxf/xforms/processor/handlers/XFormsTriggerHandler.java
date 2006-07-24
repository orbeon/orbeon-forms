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

import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLUtils;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Handle xforms:trigger.
 */
public class XFormsTriggerHandler extends HandlerBase {

    private AttributesImpl xxformsImgAttributes;
    private AttributesImpl elementAttributes;

    public XFormsTriggerHandler() {
        super(false, false);
    }

    public void start(String uri, String localname, String qName, Attributes attributes) throws SAXException {
        elementAttributes = new AttributesImpl(attributes);

        // Reset state, as this handler is reused
        xxformsImgAttributes = null;
    }

    public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {

        if (XFormsConstants.XXFORMS_NAMESPACE_URI.equals(uri)) {
            if (localname.equals("img")) {
                // xxforms:img
                xxformsImgAttributes = new AttributesImpl(attributes);
            }
        }
        super.startElement(uri, localname, qName, attributes);
    }

    public void end(String uri, String localname, String qName) throws SAXException {

        final ContentHandler contentHandler = handlerContext.getController().getOutput();

        // xforms:trigger and xforms:submit

        final String effectiveId = handlerContext.getEffectiveId(elementAttributes);
        final XFormsControl XFormsControl = handlerContext.isGenerateTemplate() ? null : ((XFormsControl) containingDocument.getObjectById(pipelineContext, effectiveId));

        if (isStaticReadonly(XFormsControl))
            return;

        if (!handlerContext.isGenerateTemplate() && XFormsControl.getLabel() == null)
            throw new OXFException("Missing label on xforms:trigger element.");// TODO: location data

        final String labelValue = handlerContext.isGenerateTemplate() ? "$xforms-label-value$" : XFormsControl.getLabel();

        final String appearanceValue = elementAttributes.getValue("appearance");
        final String appearanceLocalname = (appearanceValue == null) ? null : XMLUtils.localNameFromQName(appearanceValue);
        final String appearanceURI = (appearanceValue == null) ? null : uriFromQName(appearanceValue);

        final StringBuffer classes = getInitialClasses(localname, XFormsControl);
        if (!handlerContext.isGenerateTemplate())
            handleMIPClasses(classes, XFormsControl);
        final AttributesImpl newAttributes = getAttributes(elementAttributes, classes.toString(), effectiveId);

        // Handle accessibility attributes
        handleAccessibilityAttributes(elementAttributes, newAttributes);

        // Add title attribute if not yet present and there is a hint
        if (newAttributes.getValue("title") == null) {
            final String hintValue = (XFormsControl != null) ? XFormsControl.getHint() : null;
            if (hintValue != null)
                newAttributes.addAttribute("", "title", "title", ContentHandlerHelper.CDATA, hintValue);
        }

        if (appearanceValue != null
                && XFormsConstants.XXFORMS_NAMESPACE_URI.equals(appearanceURI) && "link".equals(appearanceLocalname)) {
            // Link appearance (xxforms:link)

            // TODO: probably needs f:url-norewrite="true"
            newAttributes.addAttribute("", "href", "href", ContentHandlerHelper.CDATA, "");

            // xhtml:a
            final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
            final String spanQName = XMLUtils.buildQName(xhtmlPrefix, "a");
            contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "a", spanQName, newAttributes);
            contentHandler.characters(labelValue.toCharArray(), 0, labelValue.length());
            contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "a", spanQName);

        } else if (appearanceValue != null
                && XFormsConstants.XXFORMS_NAMESPACE_URI.equals(appearanceURI) && "image".equals(appearanceLocalname)) {
            // Image appearance

            newAttributes.addAttribute("", "type", "type", ContentHandlerHelper.CDATA, "image");
            newAttributes.addAttribute("", "alt", "alt", ContentHandlerHelper.CDATA, labelValue);

            // Handle nested xxforms:img
            if (xxformsImgAttributes != null) { // it should not be null
                // Add @src attribute
                newAttributes.addAttribute("", "src", "src", ContentHandlerHelper.CDATA, xxformsImgAttributes.getValue("src"));

                // Copy everything else except @src, @alt, and @id
                // NOTE: It is not 100% clear what attributes make sense for propagation.
                for (int i = 0; i < xxformsImgAttributes.getLength(); i++) {
                    final String attributeURI = xxformsImgAttributes.getURI(i);
                    final String attributeValue = xxformsImgAttributes.getValue(i);
                    final String attributeType = xxformsImgAttributes.getType(i);
                    final String attributeQName = xxformsImgAttributes.getQName(i);
                    final String attributeLocalname = xxformsImgAttributes.getLocalName(i);

                    if (!(attributeURI.equals("") && (attributeLocalname.equals("src") || attributeLocalname.equals("alt")) || attributeLocalname.equals("id")))
                        newAttributes.addAttribute(attributeURI, attributeLocalname, attributeQName, attributeType, attributeValue);
                }

            }

            // xhtml:input
            final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
            final String spanQName = XMLUtils.buildQName(xhtmlPrefix, "input");
            handleReadOnlyAttribute(newAttributes, XFormsControl);
            contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "input", spanQName, newAttributes);
            contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "input", spanQName);

        } else {
            // Default appearance (button)

            newAttributes.addAttribute("", "type", "type", ContentHandlerHelper.CDATA, "button");

            // xhtml:button
            final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
            final String spanQName = XMLUtils.buildQName(xhtmlPrefix, "button");
            handleReadOnlyAttribute(newAttributes, XFormsControl);
            contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "button", spanQName, newAttributes);
            contentHandler.characters(labelValue.toCharArray(), 0, labelValue.length());
            contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "button", spanQName);
        }

        // xforms:help
        handleLabelHintHelpAlert(effectiveId, "help", XFormsControl);
    }
}
