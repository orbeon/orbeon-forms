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
import org.orbeon.oxf.xforms.control.XFormsValueControl;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLUtils;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Handle xforms:input.
 */
public class XFormsInputHandler extends XFormsValueControlHandler {

    private static final String[] XXFORMS_ATTRIBUTES_TO_COPY = { "size", "maxlength", "autocomplete" };
    private static final String NBSP = "\u00a0";
    private Attributes elementAttributes;

    public XFormsInputHandler() {
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
        final boolean isDate;
        final String typeClass;
        {
            final StringBuffer classes = getInitialClasses(localname, elementAttributes, xformsControl);
            if (!handlerContext.isGenerateTemplate()) {
                isDate = isDate(xformsControl.getType());
                typeClass = isDate ? "xforms-type-date" : "xforms-type-string";

                handleMIPClasses(classes, xformsControl);
            } else {
                isDate = false;
                typeClass = null;
            }
            newAttributes = getAttributes(elementAttributes, classes.toString(), effectiveId);
        }

        // Create xhtml:span
        final boolean isReadOnly = !handlerContext.isGenerateTemplate() && xformsControl.isReadonly();
        final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
        final String spanQName = XMLUtils.buildQName(xhtmlPrefix, "span");
        final String inputQName = XMLUtils.buildQName(xhtmlPrefix, "input");
        contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName, newAttributes);
        {
            // Create xhtml:span for date display
            if (!isStaticReadonly(xformsControl)) {
                final StringBuffer spanClasses = new StringBuffer("xforms-date-display");
                if (isReadOnly)
                    spanClasses.append(" xforms-readonly");
                reusableAttributes.clear();
                reusableAttributes.addAttribute("", "class", "class", ContentHandlerHelper.CDATA, spanClasses.toString());// TODO: check whether like in the XSTL version we need to copy other classes as well
                contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName, reusableAttributes);
                if (!handlerContext.isGenerateTemplate() && isDate) {
                    final String displayValueOrValue = xformsControl.getDisplayValueOrValue();
                    if (displayValueOrValue != null && !displayValueOrValue.equals("")) {
                        contentHandler.characters(displayValueOrValue.toCharArray(), 0, displayValueOrValue.length());
                    } else {
                        // Add an nbsp to facilitate styling
                        contentHandler.characters(NBSP.toCharArray(), 0, NBSP.length());
                    }
                }
                contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName);
            }

            // Create xhtml:input
            {
                reusableAttributes.clear();
                if (!isStaticReadonly(xformsControl)) {
                    // Regular mode

                    final StringBuffer inputClasses = new StringBuffer("xforms-input-input");
                    if (typeClass != null) {
                        inputClasses.append(' ');
                        inputClasses.append(typeClass);
                    }

                    reusableAttributes.addAttribute("", "id", "id", ContentHandlerHelper.CDATA, "input-" + effectiveId);
                    reusableAttributes.addAttribute("", "type", "type", ContentHandlerHelper.CDATA, "text");
                    reusableAttributes.addAttribute("", "name", "name", ContentHandlerHelper.CDATA, effectiveId);


                    if (!handlerContext.isGenerateTemplate()) {
                        final String value = xformsControl.getValue();
                        reusableAttributes.addAttribute("", "value", "value", ContentHandlerHelper.CDATA, (value == null) ? "" : value);
                    } else {
                        reusableAttributes.addAttribute("", "value", "value", ContentHandlerHelper.CDATA, "");
                    }

                    reusableAttributes.addAttribute("", "class", "class", ContentHandlerHelper.CDATA,
                            (inputClasses.length() > 0) ? inputClasses.toString() : "");// TODO: check whether like in the XSTL version we need to copy other classes as well
                    if (isReadOnly) {
                        reusableAttributes.addAttribute("", "disabled", "disabled", ContentHandlerHelper.CDATA, "disabled");
                    }

                    // Copy special attributes in xxforms namespace
                    copyAttributes(elementAttributes, XFormsConstants.XXFORMS_NAMESPACE_URI, XXFORMS_ATTRIBUTES_TO_COPY, reusableAttributes);

                    // Handle accessibility attributes
                    handleAccessibilityAttributes(elementAttributes, reusableAttributes);

                    contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "input", inputQName, reusableAttributes);
                    contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "input", inputQName);
                } else {
                    // Read-only mode
                    if (!handlerContext.isGenerateTemplate()) {
                        final String value = xformsControl.getDisplayValueOrValue();
                        if (value != null)
                            contentHandler.characters(value.toCharArray(), 0, value.length());
                    }
                }
            }
 
            // Create xhtml:span for date picker
            if (!isStaticReadonly(xformsControl)) {
                final StringBuffer spanClasses = new StringBuffer("xforms-showcalendar");
                if (isReadOnly)
                    spanClasses.append(" xforms-showcalendar-readonly");
                if (typeClass != null) {
                    spanClasses.append(' ');
                    spanClasses.append(typeClass);
                }

                reusableAttributes.clear();
                reusableAttributes.addAttribute("", "class", "class", ContentHandlerHelper.CDATA, spanClasses.toString());
                reusableAttributes.addAttribute("", "id", "id", ContentHandlerHelper.CDATA, "showcalendar-" + effectiveId);

                contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName, reusableAttributes);
                contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName);
            }
        }
        contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName);

        // xforms:help
        handleLabelHintHelpAlert(effectiveId, "help", xformsControl);

        // xforms:alert
        handleLabelHintHelpAlert(effectiveId, "alert", xformsControl);

        // xforms:hint
        handleLabelHintHelpAlert(effectiveId, "hint", xformsControl);
    }
}
