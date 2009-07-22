/**
 * Copyright (C) 2009 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.processor.handlers;

import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.control.XFormsSingleNodeControl;
import org.orbeon.oxf.xforms.control.controls.XFormsInputControl;
import org.orbeon.oxf.xforms.itemset.Item;
import org.orbeon.oxf.xforms.itemset.Itemset;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.saxon.om.FastStringBuffer;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import java.util.Collections;

/**
 * Handle xforms:input.
 */
public class XFormsInputHandler extends XFormsControlLifecyleHandler {

    public XFormsInputHandler() {
        super(false);
    }

    protected void handleControlStart(String uri, String localname, String qName, Attributes attributes, String staticId, String effectiveId, XFormsSingleNodeControl xformsControl) throws SAXException {

        final XFormsInputControl inputControl = (XFormsInputControl) xformsControl;
        final ContentHandler contentHandler = handlerContext.getController().getOutput();
        final boolean isConcreteControl = inputControl != null;

        final String controlTypeName = (inputControl != null) ? inputControl.getBuiltinTypeName() : null;
        final String appearance = inputControl == null ? null : inputControl.getAppearance();

        final boolean isDateTime;
        final boolean isDateMinimal;
        final boolean isBoolean;
        if (!handlerContext.isTemplate()) {
            if (isConcreteControl) {
                isDateTime = "dateTime".equals(controlTypeName);
                isDateMinimal = "date".equals(controlTypeName) && "minimal".equals(appearance) ;
                isBoolean = "boolean".equals(controlTypeName);
            } else {
                isDateTime = false;
                isDateMinimal = false;
                isBoolean = false;
            }
        } else {
            isDateTime = false;
            isDateMinimal = false;
            isBoolean = false;
        }

        final AttributesImpl newAttributes;
        if (handlerContext.isNewXHTMLLayout()) {
            reusableAttributes.clear();
            newAttributes = reusableAttributes;
        } else {
            final FastStringBuffer classes = getInitialClasses(uri, localname, attributes, inputControl);
            handleMIPClasses(classes, getPrefixedId(), inputControl);
            newAttributes = getAttributes(attributes, classes.toString(), effectiveId);

            if (isConcreteControl) {
                // Output extension attributes in no namespace
                inputControl.addExtensionAttributes(newAttributes, "");
            }
        }

        if (isBoolean) {
            // Produce a boolean output

            final boolean isMany = true;
            final Itemset itemset = new Itemset();
            // NOTE: We have decided that it did not make much sense to encrypt the value for boolean. This also poses
            // a problem since the server does not send an itemset for new booleans, therefore the client cannot know
            // the encrypted value of "true". So we do not encrypt values.
            itemset.addChildItem(new Item(false, Collections.EMPTY_LIST, "", "true"));

            // NOTE: In the future, we may want to use other appearances provided by xforms:select
//            items.add(new XFormsSelect1Control.Item(false, Collections.EMPTY_LIST, "False", "false", 1));

            final XFormsSelect1Handler select1Handler = new XFormsSelect1Handler() {
                protected String getPrefixedId() {
                    return XFormsInputHandler.this.getPrefixedId();
                }
            };
            select1Handler.setContext(getContext());
            select1Handler.outputContent(attributes, staticId, effectiveId, uri, localname, inputControl, itemset, isMany, true);

        } else {

            // Create xhtml:span
//            final boolean isReadOnly = isConcreteControl && inputControl.isReadonly();
            final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
            final String spanQName = XMLUtils.buildQName(xhtmlPrefix, "span");
            final String inputQName = XMLUtils.buildQName(xhtmlPrefix, "input");

            if (!handlerContext.isNewXHTMLLayout())
                contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName, newAttributes);

            {
                // Create xhtml:input
                {
                    if (!isStaticReadonly(inputControl)) {
                        // Regular read-write mode

                        // Main input field
                        {
                            final String inputIdName = getFirstInputEffectiveId(effectiveId);// do as if this was in a component, noscript has to handle that

                            reusableAttributes.clear();
                            reusableAttributes.addAttribute("", "id", "id", ContentHandlerHelper.CDATA, inputIdName);
                            if (!isDateMinimal)
                                reusableAttributes.addAttribute("", "type", "type", ContentHandlerHelper.CDATA, "text");
                            // Use effective id for name of first field
                            reusableAttributes.addAttribute("", "name", "name", ContentHandlerHelper.CDATA, inputIdName);
                            final FastStringBuffer inputClasses = new FastStringBuffer("xforms-input-input");
                            if (isConcreteControl) {
                                // Output value only for concrete control
                                final String formattedValue = inputControl.getFirstValueUseFormat(pipelineContext);
                                if (!isDateMinimal) {
                                    // Regular case, value goes to input control
                                    reusableAttributes.addAttribute("", "value", "value", ContentHandlerHelper.CDATA, formattedValue);
                                } else {
                                    // "Minimal date", value goes to @alt attribute on image
                                    reusableAttributes.addAttribute("", "alt", "alt", ContentHandlerHelper.CDATA, formattedValue);
                                }

                                final String firstType = inputControl.getFirstValueType();
                                if (firstType != null) {
                                    inputClasses.append(" xforms-type-");
                                    inputClasses.append(firstType);
                                }
                                if (appearance != null) {
                                    inputClasses.append(" xforms-input-appearance-");
                                    inputClasses.append(appearance);
                                }

                                // Output xxforms:* extension attributes
                                inputControl.addExtensionAttributes(reusableAttributes, XFormsConstants.XXFORMS_NAMESPACE_URI);

                            } else {
                                reusableAttributes.addAttribute("", "value", "value", ContentHandlerHelper.CDATA, "");
                            }

                            reusableAttributes.addAttribute("", "class", "class", ContentHandlerHelper.CDATA, inputClasses.toString());

                            handleReadOnlyAttribute(reusableAttributes, containingDocument, inputControl);

                            // Handle accessibility attributes
                            handleAccessibilityAttributes(attributes, reusableAttributes);
                            if (isDateMinimal) {
                                final String imgQName = XMLUtils.buildQName(xhtmlPrefix, "img");
                                reusableAttributes.addAttribute("", "src", "src", ContentHandlerHelper.CDATA, XFormsConstants.CALENDAR_IMAGE_URI);
                                reusableAttributes.addAttribute("", "title", "title", ContentHandlerHelper.CDATA, "");

                                contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "img", imgQName, reusableAttributes);
                                contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "img", imgQName);
                            } else {
                                contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "input", inputQName, reusableAttributes);
                                contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "input", inputQName);
                            }
                        }

                        // Add second field for dateTime's time part
                        // NOTE: In the future, we probably want to do this as an XBL component
                        if (isDateTime) {

                            final String inputIdName = getSecondInputEffectiveId(effectiveId);// do as if this was in a component, noscript has to handle that

                            reusableAttributes.clear();
                            reusableAttributes.addAttribute("", "id", "id", ContentHandlerHelper.CDATA, inputIdName);
                            reusableAttributes.addAttribute("", "src", "src", ContentHandlerHelper.CDATA, XFormsConstants.CALENDAR_IMAGE_URI);
                            reusableAttributes.addAttribute("", "title", "title", ContentHandlerHelper.CDATA, "");
                            reusableAttributes.addAttribute("", "alt", "alt", ContentHandlerHelper.CDATA, "");
                            // TODO: Is this an appropriate name? Noscript must be able to find this
                            reusableAttributes.addAttribute("", "name", "name", ContentHandlerHelper.CDATA, inputIdName);

                            final FastStringBuffer inputClasses = new FastStringBuffer("xforms-input-input");
                            if (isConcreteControl) {
                                // Output value only for concrete control
                                final String inputValue = inputControl.getSecondValueUseFormat(pipelineContext);
                                reusableAttributes.addAttribute("", "value", "value", ContentHandlerHelper.CDATA, inputValue);

                                final String secondType = inputControl.getSecondValueType();
                                if (secondType != null) {
                                    inputClasses.append(" xforms-type-");
                                    inputClasses.append(secondType);
                                }
                            } else {
                                reusableAttributes.addAttribute("", "value", "value", ContentHandlerHelper.CDATA, "");
                            }

                            reusableAttributes.addAttribute("", "class", "class", ContentHandlerHelper.CDATA, inputClasses.toString());

                            handleReadOnlyAttribute(reusableAttributes, containingDocument, inputControl);

                            // TODO: set @size and @maxlength

                            // Handle accessibility attributes
                            handleAccessibilityAttributes(attributes, reusableAttributes);

                            contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "input", inputQName, reusableAttributes);
                            contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "input", inputQName);
                        }

                    } else {
                        // Read-only mode
                        if (isConcreteControl) {
                            final String formattedValue = inputControl.getReadonlyValueUseFormat(pipelineContext);
                            final String outputValue = (formattedValue != null) ? formattedValue : inputControl.getExternalValue(pipelineContext);
                            if (outputValue != null)
                                contentHandler.characters(outputValue.toCharArray(), 0, outputValue.length());
                        }
                    }
                }
            }
            if (!handlerContext.isNewXHTMLLayout())
                contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName);
        }
    }

    private String getFirstInputEffectiveId(String effectiveId) {

        // TODO: make this change when: 1) xforms-server-submit.xpl correctly replaces values and 2) test-xforms-controls.xhtml is changed as well
//        return XFormsUtils.appendToEffectiveId(effectiveId, "$xforms-input-1");
        return effectiveId + "$xforms-input-1";
    }

    private String getSecondInputEffectiveId(String effectiveId) {

        // TODO: make this change when: 1) xforms-server-submit.xpl correctly replaces values and 2) test-xforms-controls.xhtml is changed as well
//        return XFormsUtils.appendToEffectiveId(effectiveId, "$xforms-input-2");
        return effectiveId + "$xforms-input-2";
    }

    @Override
    protected void handleLabel(String staticId, String effectiveId, Attributes attributes, XFormsSingleNodeControl xformsControl, boolean isTemplate) throws SAXException {
        handleLabelHintHelpAlert(effectiveId, getFirstInputEffectiveId(effectiveId), "label", xformsControl, isTemplate);
    }

    @Override
    protected void handleAlert(String staticId, String effectiveId, Attributes attributes, XFormsSingleNodeControl xformsControl, boolean isTemplate) throws SAXException {
        handleLabelHintHelpAlert(effectiveId, getFirstInputEffectiveId(effectiveId), "alert", xformsControl, isTemplate);
    }

    @Override
    protected void handleHint(String staticId, String effectiveId, XFormsSingleNodeControl xformsControl, boolean isTemplate) throws SAXException {
        handleLabelHintHelpAlert(effectiveId, getFirstInputEffectiveId(effectiveId), "hint", xformsControl, isTemplate);
    }

    @Override
    protected void handleHelp(String staticId, String effectiveId, XFormsSingleNodeControl xformsControl, boolean isTemplate) throws SAXException {
        handleLabelHintHelpAlert(effectiveId, getFirstInputEffectiveId(effectiveId), "help", xformsControl, isTemplate);
    }
}