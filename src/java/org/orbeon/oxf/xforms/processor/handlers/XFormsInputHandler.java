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

import org.dom4j.QName;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsItemUtils;
import org.orbeon.oxf.xforms.control.XFormsSingleNodeControl;
import org.orbeon.oxf.xforms.control.controls.XFormsInputControl;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLUtils;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Handle xforms:input.
 */
public class XFormsInputHandler extends XFormsCoreControlHandler {

    private static final String[] XXFORMS_ATTRIBUTES_TO_COPY = { "size", "maxlength", "autocomplete" };
    private static final String NBSP = "\u00a0";

    public XFormsInputHandler() {
        super(false);
    }

    protected void handleControl(String uri, String localname, String qName, Attributes attributes, String id, String effectiveId, XFormsSingleNodeControl xformsControl) throws SAXException {

        final XFormsInputControl inputControl = (XFormsInputControl) xformsControl;
        final ContentHandler contentHandler = handlerContext.getController().getOutput();
        final boolean isConcreteControl = inputControl != null;

        final AttributesImpl newAttributes;
        final boolean isDate;
        final boolean isBoolean;
        {
            final StringBuffer classes = getInitialClasses(localname, attributes, inputControl);
            if (!handlerContext.isGenerateTemplate()) {
                if (isConcreteControl) {
                    final String controlType = inputControl.getType();
                    isDate = XMLConstants.XS_DATE_EXPLODED_QNAME.equals(controlType) || XFormsConstants.XFORMS_DATE_EXPLODED_QNAME.equals(controlType);
                    isBoolean = XMLConstants.XS_BOOLEAN_EXPLODED_QNAME.equals(controlType) || XFormsConstants.XFORMS_BOOLEAN_EXPLODED_QNAME.equals(controlType);
                } else {
                    isDate = false;
                    isBoolean = false;
                }
            } else {
                isDate = false;
                isBoolean = false;
            }
            handleMIPClasses(classes, inputControl);
            newAttributes = getAttributes(attributes, classes.toString(), effectiveId);
        }

        if (isBoolean) {
            // Produce a boolean output

            // We try to look like an xforms:select[@appearance = 'full']
            final QName appearance = XFormsConstants.XFORMS_FULL_APPEARANCE_QNAME;
            final boolean isMany = true;

            final List items = new ArrayList(2);
            items.add(new XFormsItemUtils.Item(false, Collections.EMPTY_LIST, "", "true", 1));

            // NOTE: In the future, we may want to use other appearances provided by xforms:select
//            items.add(new XFormsSelect1Control.Item(false, Collections.EMPTY_LIST, "False", "false", 1));

            final XFormsSelect1Handler select1Handler = new XFormsSelect1Handler();
            select1Handler.setContentHandler(getContentHandler());
            select1Handler.setContext(getContext());
//            select1Handler.setDocumentLocator(get);
            select1Handler.start(uri, localname, qName, attributes);
            select1Handler.outputContent(attributes, id, effectiveId, localname, inputControl, items, isMany, appearance);

        } else {

            // Create xhtml:span
            final boolean isReadOnly = isConcreteControl && inputControl.isReadonly();
            final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
            final String spanQName = XMLUtils.buildQName(xhtmlPrefix, "span");
            final String inputQName = XMLUtils.buildQName(xhtmlPrefix, "input");
            contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName, newAttributes);
            {
                // Create xhtml:span for date display
                if (!isStaticReadonly(inputControl)) {
                    final StringBuffer spanClasses = new StringBuffer("xforms-date-display");
                    if (isReadOnly)
                        spanClasses.append(" xforms-readonly");
                    reusableAttributes.clear();
                    reusableAttributes.addAttribute("", "class", "class", ContentHandlerHelper.CDATA, spanClasses.toString());// TODO: check whether like in the XSTL version we need to copy other classes as well
                    contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName, reusableAttributes);
                    if (isConcreteControl && isDate) {
                        final String displayValueOrValue = inputControl.getDisplayValueOrExternalValue(pipelineContext);
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
                    if (!isStaticReadonly(inputControl)) {
                        // Regular mode

                        final StringBuffer inputClasses = new StringBuffer("xforms-input-input");

                        reusableAttributes.addAttribute("", "id", "id", ContentHandlerHelper.CDATA, "input-" + effectiveId);
                        reusableAttributes.addAttribute("", "type", "type", ContentHandlerHelper.CDATA, "text");
                        reusableAttributes.addAttribute("", "name", "name", ContentHandlerHelper.CDATA, effectiveId);


                        if (isConcreteControl) {
                            // Output value only for concrete control
                            final String value = inputControl.getExternalValue(pipelineContext);
                            reusableAttributes.addAttribute("", "value", "value", ContentHandlerHelper.CDATA, (value == null) ? "" : value);
                        } else {
                            reusableAttributes.addAttribute("", "value", "value", ContentHandlerHelper.CDATA, "");
                        }

                        reusableAttributes.addAttribute("", "class", "class", ContentHandlerHelper.CDATA,
                                (inputClasses.length() > 0) ? inputClasses.toString() : "");// TODO: check whether like in the XSTL version we need to copy other classes as well

                        handleReadOnlyAttribute(reusableAttributes, containingDocument, inputControl);

                        // Copy special attributes in xxforms namespace
                        copyAttributes(attributes, XFormsConstants.XXFORMS_NAMESPACE_URI, XXFORMS_ATTRIBUTES_TO_COPY, reusableAttributes);

                        // Handle accessibility attributes
                        handleAccessibilityAttributes(attributes, reusableAttributes);

                        contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "input", inputQName, reusableAttributes);
                        contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "input", inputQName);
                    } else {
                        // Read-only mode
                        if (isConcreteControl) {
                            // Output value only for concrete control
                            final String value = inputControl.getDisplayValueOrExternalValue(pipelineContext);
                            if (value != null)
                                contentHandler.characters(value.toCharArray(), 0, value.length());
                        }
                    }
                }

                // Create xhtml:span for date picker
                if (!isStaticReadonly(inputControl)) {
                    final StringBuffer spanClasses = new StringBuffer("xforms-showcalendar");
                    if (isReadOnly)
                        spanClasses.append(" xforms-showcalendar-readonly");

                    reusableAttributes.clear();
                    reusableAttributes.addAttribute("", "class", "class", ContentHandlerHelper.CDATA, spanClasses.toString());
                    reusableAttributes.addAttribute("", "id", "id", ContentHandlerHelper.CDATA, "showcalendar-" + effectiveId);

                    // HACK: Output XHTML image natively in order to help with the IE bug whereby IE reloads
                    // background images way too often.
                    reusableAttributes.addAttribute("", "src", "src", ContentHandlerHelper.CDATA,
                            rewriteResourceURL(XFormsConstants.CALENDAR_IMAGE_URI));

                    // TODO: xmlns:f declaration should be placed on xhtml:body
                    final String formattingPrefix = handlerContext.findFormattingPrefixDeclare();
                    reusableAttributes.addAttribute(XMLConstants.OPS_FORMATTING_URI, "url-norewrite", XMLUtils.buildQName(formattingPrefix, "url-norewrite"), ContentHandlerHelper.CDATA, "true");

                    final String imgQName = XMLUtils.buildQName(xhtmlPrefix, "img");
                    contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "img", imgQName, reusableAttributes);
                    contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "img", imgQName);

                    handlerContext.findFormattingPrefixUndeclare(formattingPrefix);
                }
            }
            contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName);
        }
    }
}
